package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CacheMaintenanceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private ActivityController<Activity> owner;
    private CacheMaintenance maintenance;
    private ExecutorService executor;
    private StorageTestFiles files;
    private final AtomicBoolean cleared = new AtomicBoolean();
    private final AtomicBoolean clearedOnMain = new AtomicBoolean();
    private final AtomicBoolean deletedOffMain = new AtomicBoolean();

    @Before public void setUp() throws Exception {
        owner = Robolectric.buildActivity(Activity.class).setup();
        files = new StorageTestFiles(temporary.newFolder());
        executor = Executors.newSingleThreadExecutor();
        DisposableStorage storage = new DisposableStorage(files.roots, System.currentTimeMillis(), file -> {
            deletedOffMain.set(Looper.myLooper() != Looper.getMainLooper());
            return file.delete();
        });
        maintenance = new CacheMaintenance(owner.get(), new LocalCache(owner.get()),
                new Handler(Looper.getMainLooper()), storage, executor, () -> {
                    clearedOnMain.set(Looper.myLooper() == Looper.getMainLooper());
                    cleared.set(true);
                });
    }

    @After public void tearDown() throws Exception {
        maintenance.close();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        owner.pause().stop().destroy();
    }

    @Test public void diskWorkIsOffMainAndMemoryClearPrecedesCallbackOnMain() throws Exception {
        File image = image(100);
        AtomicReference<DisposableStorage.ClearResult> result = new AtomicReference<>();
        maintenance.clearDisposableStorage(value -> {
            assertTrue(cleared.get());
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            result.set(value);
        });
        awaitDisk();
        assertFalse(image.exists());
        assertTrue(deletedOffMain.get());
        assertFalse(cleared.get());
        assertNull(result.get());

        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(clearedOnMain.get());
        assertEquals(100L, result.get().reclaimedBytes);
        assertEquals(0L, result.get().after.disposableBytes);
    }

    @Test public void finishingActivityStillEvictsMemoryButReceivesNoUiCallback() throws Exception {
        image(100);
        AtomicBoolean callback = new AtomicBoolean();
        maintenance.clearDisposableStorage(result -> callback.set(true));
        awaitDisk();
        owner.get().finish();

        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(cleared.get());
        assertTrue(clearedOnMain.get());
        assertFalse(callback.get());
    }

    @Test public void closeDoesNotCancelAlreadyPostedMemoryEviction() throws Exception {
        image(100);
        AtomicBoolean callback = new AtomicBoolean();
        maintenance.clearDisposableStorage(result -> callback.set(true));
        awaitDisk();
        maintenance.close();

        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(clearedOnMain.get());
        assertFalse(callback.get());
    }

    @Test public void observerReceivesCompletedRemainderAndUnsubscriptionStopsDelivery() throws Exception {
        image(100);
        AtomicReference<DisposableStorage.Snapshot> snapshot = new AtomicReference<>();
        CacheMaintenance.StorageCallback observer = snapshot::set;
        maintenance.observeStorage(observer);
        awaitDisk();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(100L, snapshot.get().disposableBytes);
        maintenance.clearDisposableStorage(null);
        awaitDisk();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0L, snapshot.get().disposableBytes);
        maintenance.removeStorageObserver(observer);
        snapshot.set(null);
        maintenance.refreshStorage();
        awaitDisk();
        shadowOf(Looper.getMainLooper()).idle();
        assertNull(snapshot.get());
    }

    private File image(long bytes) throws Exception {
        return files.put(files.files, "offline-cache/images/" + StorageTestFiles.imageName(
                "https://example.com/a.jpg", "thumb"), bytes, System.currentTimeMillis() - 1000L);
    }

    private void awaitDisk() throws Exception {
        executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
    }
}
