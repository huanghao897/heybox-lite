package com.ronan.heyboxlite;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LongImageSourceCloseRaceTest {
    @Rule public final TemporaryFolder files = new TemporaryFolder();

    @Test public void closeImmediatelyBeforeDecoderUnlockReleasesLeaseWithoutASecondClose() throws Exception {
        File file = files.newFile("long-image-close.img");
        Bitmap image = Bitmap.createBitmap(16, 800, Bitmap.Config.ARGB_8888);
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { image.recycle(); }
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(file.getAbsolutePath(), false);
        StorageFileLeases.Lease lease = StorageFileLeases.acquire(file);
        Constructor<LongImageSource> constructor = LongImageSource.class.getDeclaredConstructor(
                BitmapRegionDecoder.class, File.class, StorageFileLeases.Lease.class);
        constructor.setAccessible(true);
        LongImageSource source = constructor.newInstance(decoder, file, lease);
        PausedUnlock lock = new PausedUnlock();
        Field monitor = LongImageSource.class.getDeclaredField("lock");
        monitor.setAccessible(true);
        monitor.set(source, lock);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Future<Bitmap> decoded = worker.submit(() -> source.decode(new Rect(0, 0, 16, 64), 1));
        try {
            assertTrue(lock.unlocking.await(5, TimeUnit.SECONDS));
            source.close();
            assertFalse("Close cannot wait for a native decode on the UI thread", decoded.isDone());
            assertTrue(StorageFileLeases.inUse(file));
            lock.resume.countDown();
            Bitmap tile = decoded.get(5, TimeUnit.SECONDS);
            assertNotNull(tile);
            tile.recycle();
            assertTrue(decoder.isRecycled());
            assertFalse(StorageFileLeases.inUse(file));
            assertFalse(file.exists());
            assertNull(source.decode(new Rect(0, 0, 16, 64), 1));
        } finally {
            lock.resume.countDown();
            worker.shutdown();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
            source.close();
        }
    }

    private static final class PausedUnlock extends ReentrantLock {
        final CountDownLatch unlocking = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        final AtomicBoolean first = new AtomicBoolean(true);

        @Override public void unlock() {
            try {
                if (first.compareAndSet(true, false)) {
                    unlocking.countDown();
                    assertTrue(resume.await(5, TimeUnit.SECONDS));
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError(error);
            } finally { super.unlock(); }
        }
    }
}
