package com.ronan.heyboxlite;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static com.ronan.heyboxlite.StorageTestFiles.*;
import static org.junit.Assert.*;

public class DisposableStorageTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private StorageTestFiles files;

    @Before public void setUp() throws Exception {
        files = new StorageTestFiles(temporary.newFolder());
    }

    @Test public void deletesActualOwnedFilesAcrossInternalAndExternalLocations() throws Exception {
        List<File> disposable = Arrays.asList(
                files.image("https://example.com/a.png", "thumbnail", 11),
                files.put(files.files, "offline-cache/images/"
                        + imageName("https://example.com/b.png", "thumb") + ".tmp", 12, OLD),
                files.put(files.files, "offline-cache/details/123.json", 13, OLD),
                files.put(files.files, "updates/heybox-Lite-update-2.19-123.apk", 14, OLD),
                files.put(files.files, "updates/heybox-Lite-update-2.19-124.apk.part", 15, OLD),
                files.put(files.cache, "updates/heybox-Lite-update-2.19-125.apk", 16, OLD),
                files.put(files.externalCache, "updates/heybox-Lite-update-2.19-126.apk", 17, OLD),
                files.put(files.external, "Download/heyboxlite/heybox-Lite-update-2.19-127.apk", 18, OLD),
                files.put(files.external, "diagnostics/heybox-lite-diagnostics-20261001-010203.txt", 19, OLD),
                files.put(files.files, "offline-cache/diagnostics/heybox-lite-diagnostics-20261002-010203.txt", 20, OLD),
                files.put(files.cache, "long-image-123.img", 21, OLD));
        DisposableStorage storage = files.storage();
        assertEquals(176L, storage.snapshot(NOW).disposableBytes);

        DisposableStorage.ClearResult result = storage.clear(NOW);

        assertEquals(176L, result.reclaimedBytes);
        assertEquals(disposable.size(), result.deletedFiles);
        assertEquals(0, result.failedFiles);
        assertEquals(0L, result.after.disposableBytes);
        assertTrue(result.after.complete);
        for (File file : disposable) assertFalse(file.toString(), file.exists());
        assertEquals(0L, storage.clear(NOW).reclaimedBytes);
    }

    @Test public void preservesSavedOfflineContentCredentialsPendingDiagnosticsAndUnknownData() throws Exception {
        String source = "https://example.com/saved.jpg";
        File manifest = files.manifest("saved/123", source);
        List<File> kept = Arrays.asList(manifest,
                files.put(files.files, "offline-cache/details/saved_123.json", 1, OLD),
                files.image(source, source, 2),
                files.image(source, source + "?thumb=260", 3),
                files.put(files.files, "offline-cache/saved-lists/recent-items.json", 4, OLD),
                files.put(files.files, "offline-cache/saved-lists/favorites.json", 5, OLD),
                files.put(files.preferences, "session.xml", 6, OLD),
                files.put(files.files, "keystore/session.key", 7, OLD),
                files.put(files.files, "crash-reports/report.pending", 8, OLD),
                files.put(files.files, "crash-reports/report.pending.tmp", 9, OLD),
                files.put(files.files, "crash-reports/latest.log", 10, OLD),
                files.put(files.external, "diagnostics/events-session.log", 11, OLD),
                files.put(files.external, "diagnostics/events-previous-session.log", 12, OLD),
                files.put(files.external, "diagnostics/native-sign.log", 13, OLD),
                files.put(files.external, "diagnostics/crash-latest.log", 14, OLD),
                files.put(files.external, "diagnostics/heybox-lite-diagnostics-latest.txt", 15, OLD),
                files.put(files.external, "diagnostics/report.pending", 16, OLD),
                files.put(files.external, "diagnostics/heybox-lite-diagnostics-20261113-010203.txt", 17, NOW - 1000),
                files.put(files.files, "updates/user-saved.apk", 18, OLD),
                files.put(files.cache, "unknown.bin", 19, OLD),
                files.put(files.externalCache, "unrecognized-cache.bin", 20, OLD),
                files.put(files.external, "Pictures/saved.jpg", 21, OLD),
                files.put(files.files, "offline-cache/images/unknown.img", 22, OLD),
                files.put(files.files, "updates/nested/heybox-Lite-update-2.19-123.apk", 23, OLD));
        File disposable = files.image("https://example.com/unpinned.jpg", "thumb", 100);

        DisposableStorage.ClearResult result = files.storage().clear(NOW);

        assertEquals(100L, result.reclaimedBytes);
        assertFalse(disposable.exists());
        assertTrue(result.after.retainedBytes > 0L);
        assertTrue(result.after.unrecognizedBytes > 0L);
        assertEquals(17L, result.after.deferredBytes);
        for (File file : kept) assertTrue(file.toString(), file.isFile());
    }

    @Test public void reportsInstalledAndRuntimeCodeSeparatelyFromDisposableData() throws Exception {
        File apk = files.put(files.installed, "base.apk", 101, OLD);
        File split = files.put(files.installed, "split_config.arm64_v8a.apk", 102, OLD);
        File nativeLib = files.put(files.installed, "lib/libsign.so", 103, OLD);
        File extracted = files.put(files.runtime, "libsign.so", 104, OLD);
        File fallback = files.put(files.files, "native_signer/libsign.so", 105, OLD);
        files.image("https://example.com/a.png", "thumb", 106);

        DisposableStorage.ClearResult result = files.storage().clear(NOW);

        assertEquals(306L, result.after.installedCodeBytes);
        assertEquals(209L, result.after.runtimeCodeBytes);
        assertEquals(106L, result.reclaimedBytes);
        for (File file : Arrays.asList(apk, split, nativeLib, extracted, fallback)) {
            assertTrue(file.exists());
        }
    }

    @Test public void defersCurrentProcessTemporaryDownloadsAndDetailWrites() throws Exception {
        List<File> active = Arrays.asList(
                files.put(files.cache, "long-image-456.img", 10, START),
                files.put(files.files, "updates/heybox-Lite-update-2.19-1.apk.part", 20, START + 1),
                files.put(files.files, "updates/heybox-Lite-update-2.19-2.apk", 30, START + 2),
                files.put(files.files, "offline-cache/images/"
                        + imageName("https://example.com/a.jpg", "thumb") + ".tmp", 40, START + 3),
                files.put(files.files, "offline-cache/details/123.json", 50, START + 4));

        DisposableStorage.ClearResult result = files.storage().clear(NOW);

        assertEquals(0L, result.reclaimedBytes);
        assertEquals(150L, result.after.deferredBytes);
        for (File file : active) assertTrue(file.exists());
    }

    @Test public void staleCandidatesAndConcurrentNewFilesAreNotClaimedAsReclaimed() throws Exception {
        File changed = files.image("https://example.com/changed.jpg", "thumb", 10);
        File vanished = files.image("https://example.com/vanished.jpg", "thumb", 20);
        DisposableStorage storage = files.storage();
        DisposableStorage.Snapshot before = storage.snapshot(NOW);
        files.put(changed.getParentFile(), changed.getName(), 30, NOW - 1);
        assertTrue(vanished.delete());
        File added = files.image("https://example.com/new.jpg", "thumb", 40);

        DisposableStorage.ClearResult result = storage.remove(before, NOW, 0L);

        assertEquals(30L, result.before.disposableBytes);
        assertEquals(0L, result.reclaimedBytes);
        assertEquals(40L, result.after.disposableBytes);
        assertEquals(30L, result.after.deferredBytes);
        assertTrue(changed.exists());
        assertTrue(added.exists());
    }

    @Test public void completedCurrentSessionImagesBecomeDisposableAtOneHundredMilliseconds() throws Exception {
        String original = "https://example.com/current.jpg";
        File image = files.put(files.files, "offline-cache/images/" + imageName(original, original),
                10, NOW - 99);
        DisposableStorage storage = files.storage();

        assertEquals(0L, storage.clear(NOW).reclaimedBytes);
        assertEquals(10L, storage.snapshot(NOW).deferredBytes);
        assertTrue(image.isFile());
        assertEquals(10L, storage.clear(NOW + 1L).reclaimedBytes);
        assertFalse(image.exists());
    }

    @Test public void deletionFailureReportsRemainingBytesInsteadOfThePreClearTotal() throws Exception {
        File failed = files.image("https://example.com/failed.jpg", "thumb", 10);
        File deleted = files.image("https://example.com/deleted.jpg", "thumb", 20);
        DisposableStorage storage = new DisposableStorage(files.roots, START,
                file -> !file.equals(failed) && file.delete());

        DisposableStorage.ClearResult result = storage.clear(NOW);

        assertEquals(30L, result.before.disposableBytes);
        assertEquals(20L, result.reclaimedBytes);
        assertEquals(10L, result.after.disposableBytes);
        assertEquals(1, result.failedFiles);
        assertEquals(1, result.deletedFiles);
        assertTrue(failed.isFile());
        assertFalse(deleted.exists());
    }

    @Test public void requiresVerifiedAbsenceEvenWhenDeleteReturnsSuccess() throws Exception {
        files.image("https://example.com/kept.jpg", "thumb", 10);
        DisposableStorage storage = new DisposableStorage(files.roots, START, file -> true);

        DisposableStorage.ClearResult result = storage.clear(NOW);

        assertEquals(0L, result.reclaimedBytes);
        assertEquals(10L, result.after.disposableBytes);
        assertEquals(1, result.failedFiles);
    }

    @Test public void rechecksNewlySavedOfflineReferencesBeforeDeletion() throws Exception {
        String source = "https://example.com/later.jpg";
        File image = files.image(source, "thumb", 10);
        File detail = files.put(files.files, "offline-cache/details/123.json", 20, OLD);
        DisposableStorage storage = files.storage();
        DisposableStorage.Snapshot before = storage.snapshot(NOW);
        files.manifest("123", source);

        DisposableStorage.ClearResult result = storage.remove(before, NOW, 0L);

        assertEquals(0L, result.reclaimedBytes);
        assertTrue(image.exists());
        assertTrue(detail.exists());
        assertEquals(0L, result.after.disposableBytes);
    }

    @Test public void expiredPruningPreservesPinnedContentRegardlessOfSavedAge() throws Exception {
        long expired = NOW - 40 * DAY;
        String pinnedUrl = "https://example.com/pinned.jpg";
        files.manifest("pinned", pinnedUrl);
        File pinned = files.image(pinnedUrl, "thumb", 10);
        assertTrue(pinned.setLastModified(expired));
        File savedDetail = files.put(files.files, "offline-cache/details/pinned.json", 20, expired);
        File oldImage = files.image("https://example.com/old.jpg", "thumb", 30);
        assertTrue(oldImage.setLastModified(expired));
        File oldDetail = files.put(files.files, "offline-cache/details/old.json", 40, expired);
        File recent = files.image("https://example.com/recent.jpg", "thumb", 50);
        File update = files.put(files.files, "updates/heybox-Lite-update-2.19-1.apk", 60, expired);

        DisposableStorage.ClearResult result = files.storage().pruneExpired(NOW, 30 * DAY);

        assertEquals(70L, result.reclaimedBytes);
        assertFalse(oldImage.exists());
        assertFalse(oldDetail.exists());
        for (File file : Arrays.asList(pinned, savedDetail, recent, update)) assertTrue(file.exists());
    }

    @Test public void sizeBudgetEvictsOnlyDisposableImagesNeverSavedOfflineContent() throws Exception {
        String pinned = "https://example.com/pinned.jpg";
        files.manifest("123", pinned);
        File saved = files.image(pinned, "thumb", 96L * 1024 * 1024);
        File extra = files.image("https://example.com/extra.jpg", "thumb", 100);

        DisposableStorage.ClearResult result = files.storage().pruneExpired(NOW, 30 * DAY);

        assertEquals(100L, result.reclaimedBytes);
        assertTrue(saved.exists());
        assertFalse(extra.exists());
    }

    @Test public void replacedDirectoryIsNotRecursivelyDeleted() throws Exception {
        File image = files.image("https://example.com/old.jpg", "thumb", 10);
        DisposableStorage storage = files.storage();
        DisposableStorage.Snapshot before = storage.snapshot(NOW);
        assertTrue(image.delete());
        assertTrue(image.mkdir());
        File nested = files.put(image, "do-not-delete.txt", 20, OLD);

        DisposableStorage.ClearResult result = storage.remove(before, NOW, 0L);

        assertEquals(0L, result.reclaimedBytes);
        assertTrue(nested.isFile());
    }

    @Test public void ignoresSymlinkEscapesWhenSupported() throws Exception {
        File outside = temporary.newFile();
        Files.write(outside.toPath(), new byte[]{1, 2, 3});
        File directory = new File(files.files, "updates");
        assertTrue(directory.mkdir());
        File link = new File(directory, "heybox-Lite-update-2.19-1.apk");
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath());
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException error) {
            org.junit.Assume.assumeNoException(error);
        }

        DisposableStorage.ClearResult result = files.storage().clear(NOW);

        assertEquals(0L, result.reclaimedBytes);
        assertFalse(result.after.complete);
        assertTrue(outside.isFile());
        assertTrue(Files.isSymbolicLink(link.toPath()));
    }
}
