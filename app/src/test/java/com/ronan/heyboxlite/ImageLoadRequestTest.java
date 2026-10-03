package com.ronan.heyboxlite;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class ImageLoadRequestTest {
    private final Handler main = new Handler(Looper.getMainLooper());

    @Test public void cancellingAQueuedImageRemovesTheDecodeTaskBeforeItRuns() throws Exception {
        ThreadPoolExecutor executor = executor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger decodes = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        try {
            executor.execute(() -> { started.countDown(); await(release); });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            ImageLoadRequest request = ImageLoadRequest.submit(executor, main, () -> {
                decodes.incrementAndGet();
                return null;
            }, bitmap -> callbacks.incrementAndGet());
            assertEquals(1, executor.getQueue().size());
            request.cancel();
            assertTrue(executor.getQueue().isEmpty());
            release.countDown();
            executor.submit(() -> {}).get(10, TimeUnit.SECONDS);
            ShadowLooper.shadowMainLooper().idle();
            assertEquals(0, decodes.get());
            assertEquals(0, callbacks.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test public void cancellingAnInFlightImageDoesNotInterruptDecodeOrDeliverStalePixels() throws Exception {
        ThreadPoolExecutor executor = executor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();
        try {
            ImageLoadRequest request = ImageLoadRequest.submit(executor, main, () -> {
                started.countDown();
                await(release);
                finished.incrementAndGet();
                return null;
            }, bitmap -> callbacks.incrementAndGet());
            assertTrue(started.await(10, TimeUnit.SECONDS));
            request.cancel();
            release.countDown();
            executor.submit(() -> {}).get(10, TimeUnit.SECONDS);
            ShadowLooper.shadowMainLooper().idle();
            assertEquals(1, finished.get());
            assertEquals(0, callbacks.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test public void memoryResultsAreDeliveredOnMainAndCanBeCancelledBeforeDelivery() {
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        AtomicInteger delivered = new AtomicInteger();
        ImageLoadRequest.deliver(main, bitmap, value -> {
            assertSame(bitmap, value);
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            delivered.incrementAndGet();
        });
        ImageLoadRequest cancelled = ImageLoadRequest.deliver(main, bitmap, value -> fail("Cancelled callback"));
        cancelled.cancel();
        ShadowLooper.shadowMainLooper().idle();
        assertEquals(1, delivered.get());
    }

    private static ThreadPoolExecutor executor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Worker latch timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("A cancelled image must not interrupt a running decode", error);
        }
    }
}
