package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class CacheMaintenanceLegacyTest {
    @Test public void pre24BranchClearsOldUnleasedDetailsUpdatesAndRegionFiles() throws Exception {
        int sdk = Build.VERSION.SDK_INT;
        try {
            ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 23);
            verifyLegacyCleanup();
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", sdk);
        }
    }

    private void verifyLegacyCleanup() throws Exception {
        ActivityController<Activity> owner = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = owner.get();
        CacheMaintenance maintenance = new CacheMaintenance(activity, new LocalCache(activity),
                new Handler(Looper.getMainLooper()));
        Field worker = CacheMaintenance.class.getDeclaredField("executor");
        worker.setAccessible(true);
        ExecutorService executor = (ExecutorService) worker.get(maintenance);
        long now = System.currentTimeMillis();
        File detail = put(activity.getFilesDir(), "offline-cache/details/legacy.json", 30L,
                now - 86_400_000L);
        File update = put(activity.getCacheDir(), "updates/heybox-Lite-update-2.19-222.apk", 40L,
                now - 86_400_000L);
        File region = put(activity.getCacheDir(), "long-image-legacy.img", 50L, now - 86_400_000L);
        File current = put(activity.getCacheDir(), "long-image-current.img", 60L, now + 60_000L);
        File reading = put(activity.getCacheDir(), "long-image-reading.img", 70L, now - 86_400_000L);
        try (StorageFileLeases.Lease ignored = StorageFileLeases.acquire(reading)) {
            AtomicReference<DisposableStorage.ClearResult> result = new AtomicReference<>();
            maintenance.clearDisposableStorage(result::set);
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            shadowOf(Looper.getMainLooper()).idle();
            assertNotNull(result.get());
            assertEquals(120L, result.get().reclaimedBytes);
            assertFalse(detail.exists());
            assertFalse(update.exists());
            assertFalse(region.exists());
            assertTrue(current.isFile());
            assertTrue(reading.isFile());
        } finally {
            maintenance.close();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            owner.pause().stop().destroy();
        }
    }

    private static File put(File root, String path, long bytes, long modified) throws Exception {
        File file = new File(root, path);
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        try (RandomAccessFile output = new RandomAccessFile(file, "rw")) { output.setLength(bytes); }
        assertTrue(file.setLastModified(modified));
        return file;
    }
}
