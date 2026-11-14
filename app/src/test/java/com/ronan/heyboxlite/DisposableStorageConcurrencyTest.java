package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ronan.heyboxlite.StorageTestFiles.*;
import static org.junit.Assert.*;

public class DisposableStorageConcurrencyTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private StorageTestFiles files;
    private final Object imageWriter = new Object();

    @Before public void setUp() throws Exception {
        files = new StorageTestFiles(temporary.newFolder());
    }

    @Test public void snapshotCompletesWhileImageAndLeaseMonitorsAreHeldByAnotherThread() throws Exception {
        File image = files.image("https://example.com/a.jpg", "thumb", 10);
        files.put(files.files, "offline-cache/details/123.json", 20, OLD);
        ExecutorService worker = worker();
        try (StorageFileLeases.Lease ignored = StorageFileLeases.acquire(image)) {
            synchronized (imageWriter) {
                synchronized (StorageFileLeases.LOCK) {
                    DisposableStorage.Snapshot snapshot = worker.submit(() -> storage().snapshot(NOW))
                            .get(5, TimeUnit.SECONDS);
                    assertEquals(20L, snapshot.disposableBytes);
                    assertEquals(10L, snapshot.deferredBytes);
                }
            }
        } finally {
            stop(worker);
        }
    }

    @Test public void pausedRealDirectoryWalkAllowsSourceLeasesAndCacheWrites() throws Exception {
        File image = files.image("https://example.com/a.jpg", "thumb", 10);
        CountDownLatch scanning = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        DisposableStorage storage = storage(directory -> {
            assertFalse(Thread.holdsLock(imageWriter));
            assertFalse(Thread.holdsLock(StorageFileLeases.LOCK));
            if (directory.equals(files.files)) {
                scanning.countDown();
                await(resume);
            }
            return directory.listFiles();
        });
        ExecutorService scanner = worker();
        ExecutorService foreground = worker();
        Future<DisposableStorage.Snapshot> result = scanner.submit(() -> storage.snapshot(NOW));
        try {
            assertTrue(scanning.await(5, TimeUnit.SECONDS));
            foreground.submit(() -> {
                synchronized (imageWriter) {
                    try (StorageFileLeases.Lease ignored = StorageFileLeases.acquire(image)) {
                        LocalStorageWrites.write(new File(files.files, "offline-cache/details/new.json"), "{}");
                    } catch (Exception error) {
                        throw new AssertionError(error);
                    }
                }
            }).get(5, TimeUnit.SECONDS);
            resume.countDown();
            assertTrue(result.get(5, TimeUnit.SECONDS).complete);
        } finally {
            resume.countDown();
            stop(foreground);
            stop(scanner);
        }
        assertTrue(new File(files.files, "offline-cache/details/new.json").isFile());
    }

    @Test public void postClearInventoryDoesNotKeepEitherDeletionMonitor() throws Exception {
        files.image("https://example.com/a.jpg", "thumb", 10);
        AtomicInteger walks = new AtomicInteger();
        CountDownLatch scanningAfter = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        DisposableStorage storage = storage(directory -> {
            assertFalse(Thread.holdsLock(imageWriter));
            assertFalse(Thread.holdsLock(StorageFileLeases.LOCK));
            if (directory.equals(files.files) && walks.incrementAndGet() == 2) {
                scanningAfter.countDown();
                await(resume);
            }
            return directory.listFiles();
        });
        ExecutorService cleaner = worker();
        ExecutorService foreground = worker();
        Future<DisposableStorage.ClearResult> result = cleaner.submit(() -> storage.clear(NOW));
        try {
            assertTrue(scanningAfter.await(5, TimeUnit.SECONDS));
            foreground.submit(() -> {
                synchronized (imageWriter) {
                    synchronized (StorageFileLeases.LOCK) {
                        assertTrue(new File(files.files, "offline-cache/images").isDirectory());
                    }
                }
            }).get(5, TimeUnit.SECONDS);
            resume.countDown();
            assertEquals(10L, result.get(5, TimeUnit.SECONDS).reclaimedBytes);
        } finally {
            resume.countDown();
            stop(foreground);
            stop(cleaner);
        }
    }

    @Test public void waitingForWriterDoesNotHoldLeaseLockAndRechecksReplacement() throws Exception {
        File image = files.image("https://example.com/a.jpg", "thumb", 10);
        DisposableStorage storage = storage();
        DisposableStorage.Snapshot before = storage.snapshot(NOW);
        ExecutorService cleaner = worker();
        ExecutorService foreground = worker();
        Future<DisposableStorage.ClearResult> result;
        try {
            synchronized (imageWriter) {
                CountDownLatch started = new CountDownLatch(1);
                Thread[] cleaningThread = new Thread[1];
                result = cleaner.submit(() -> {
                    cleaningThread[0] = Thread.currentThread();
                    started.countDown();
                    return storage.remove(before, NOW, 0L);
                });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                awaitBlocked(cleaningThread[0]);
                foreground.submit(() -> {
                    try (StorageFileLeases.Lease ignored = StorageFileLeases.acquire(image)) {
                        assertTrue(StorageFileLeases.inUse(image));
                    } catch (Exception error) {
                        throw new AssertionError(error);
                    }
                }).get(5, TimeUnit.SECONDS);
                files.put(image.getParentFile(), image.getName(), 30, NOW - 1L);
            }
            assertEquals(0L, result.get(5, TimeUnit.SECONDS).reclaimedBytes);
            assertEquals(30L, image.length());
        } finally {
            stop(foreground);
            stop(cleaner);
        }
    }

    @Test public void savedRevisionRechecksSameSizeSameTimestampManifestWhileWaitingForWriter() throws Exception {
        String source = "https://example.com/b.jpg";
        File image = files.image(source, "thumb", 10);
        File manifest = files.manifest("123", "https://example.com/a.jpg");
        long length = manifest.length();
        long modified = manifest.lastModified();
        DisposableStorage storage = storage();
        DisposableStorage.Snapshot before = storage.snapshot(NOW);
        ExecutorService cleaner = worker();
        Future<DisposableStorage.ClearResult> result;
        try {
            synchronized (imageWriter) {
                CountDownLatch started = new CountDownLatch(1);
                Thread[] cleaningThread = new Thread[1];
                result = cleaner.submit(() -> {
                    cleaningThread[0] = Thread.currentThread();
                    started.countDown();
                    return storage.remove(before, NOW, 0L);
                });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                awaitBlocked(cleaningThread[0]);
                JSONObject entry = new JSONObject().put("item", new JSONObject().put("linkid", "123"))
                        .put("images", new JSONArray().put(source)).put("updated_at", 1);
                LocalStorageWrites.write(manifest, new JSONArray().put(entry).toString());
                assertEquals(length, manifest.length());
                assertTrue(manifest.setLastModified(modified));
            }
            DisposableStorage.ClearResult cleared = result.get(5, TimeUnit.SECONDS);
            assertEquals(0L, cleared.reclaimedBytes);
            assertEquals(0L, cleared.after.disposableBytes);
            assertTrue(image.isFile());
        } finally {
            stop(cleaner);
        }
    }

    @Test public void openDetailWriteLeaseSurvivesConcurrentDeletionEvenWithoutMetadataChanges() throws Exception {
        File detail = files.put(files.files, "offline-cache/details/123.json", 20, OLD);
        DisposableStorage storage = storage();
        DisposableStorage.Snapshot before = storage.snapshot(NOW);
        ExecutorService cleaner = worker();
        try (StorageFileLeases.Lease ignored = StorageFileLeases.acquire(detail);
             FileOutputStream output = new FileOutputStream(detail, true)) {
            assertEquals(0L, cleaner.submit(() -> storage.remove(before, NOW, 0L))
                    .get(5, TimeUnit.SECONDS).reclaimedBytes);
            assertTrue(detail.exists());
            output.write(1);
        } finally {
            stop(cleaner);
        }
        assertEquals(21L, detail.length());
    }

    private DisposableStorage storage() {
        return storage(File::listFiles);
    }

    private DisposableStorage storage(DisposableStorage.ListDirectory list) {
        return new DisposableStorage(files.roots, START, File::delete, imageWriter, list);
    }

    private static ExecutorService worker() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "storage-lock-test");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void stop(ExecutorService worker) throws Exception {
        worker.shutdownNow();
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static void awaitBlocked(Thread thread) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        assertEquals(Thread.State.BLOCKED, thread.getState());
    }
}
