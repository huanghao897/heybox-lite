package com.ronan.heyboxlite;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.test.core.app.ApplicationProvider;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ImageQueueBehaviorTest {
    private final Handler main = new Handler(Looper.getMainLooper());

    @Test public void recyclingRealBindingsRemovesAllQueuedDownloadsBeforeNetworkOrDecode() throws Exception {
        Field field = ImageLoader.class.getDeclaredField("SMALL_EXECUTOR");
        field.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(null);
        CountDownLatch started = new CountDownLatch(executor.getCorePoolSize());
        CountDownLatch release = new CountDownLatch(1);
        List<ImageView> views = new ArrayList<>();
        AtomicInteger callbacks = new AtomicInteger();
        try {
            for (int i = 0; i < executor.getCorePoolSize(); i++) executor.execute(() -> {
                started.countDown(); await(release);
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 16; i++) {
                ImageView view = new ImageView(ApplicationProvider.getApplicationContext());
                views.add(view);
                ImageLoader.into(view, "https://never-requested.invalid/" + i + ".png", 96,
                        success -> callbacks.incrementAndGet());
            }
            assertEquals(16, executor.getQueue().size());
            for (ImageView view : views) ImageLoader.cancel(view);
            assertTrue(executor.getQueue().isEmpty());
            release.countDown();
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, callbacks.get());
            assertTrue(views.stream().allMatch(view -> ImageViewBinding.current(view) == null));
        } finally {
            release.countDown();
            for (ImageView view : views) ImageLoader.cancel(view);
        }
    }

    @Test public void queueHasAHardLimitAndKeepsTheMostRecentVisibleRequests() throws Exception {
        ImageTaskExecutor executor = new ImageTaskExecutor(1, 3);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Integer> decoded = new ArrayList<>();
        AtomicInteger discarded = new AtomicInteger();
        AtomicInteger delivered = new AtomicInteger();
        try {
            executor.execute(() -> { started.countDown(); await(release); });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 8; i++) {
                int id = i;
                ImageLoadRequest.work(executor, main, () -> { decoded.add(id); return id; },
                        result -> delivered.incrementAndGet(), ignored -> {}, discarded::incrementAndGet);
                assertTrue(executor.getQueue().size() <= 3);
            }
            assertEquals(5, discarded.get());
            release.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(java.util.Arrays.asList(7, 6, 5), decoded);
            assertEquals(8, delivered.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test public void cancelledInFlightTaskSeesCancellationBeforeItsNextIoOrDecodeBoundary() throws Exception {
        ImageTaskExecutor executor = new ImageTaskExecutor(1, 3);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger laterStages = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        try {
            ImageLoadRequest request = ImageLoadRequest.submit(executor, main, () -> {
                started.countDown();
                await(release);
                if (!ImageWorkToken.stopped()) laterStages.incrementAndGet();
                return null;
            }, bitmap -> callbacks.incrementAndGet());
            assertTrue(started.await(5, TimeUnit.SECONDS));
            request.cancel();
            release.countDown();
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, laterStages.get());
            assertEquals(0, callbacks.get());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test public void failedWorkReleasesItsReservationAndDeliversAFailureInsteadOfStrandingOwner() throws Exception {
        ImageTaskExecutor executor = new ImageTaskExecutor(1, 3);
        ImageTileBudget budget = new ImageTileBudget(128);
        ImageTileBudget.Lease lease = budget.reserve(64);
        AtomicInteger failures = new AtomicInteger();
        try {
            ImageLoadRequest.work(executor, main, () -> { throw new IllegalArgumentException("bad region"); },
                    value -> { assertNull(value); failures.incrementAndGet(); }, ignored -> {}, lease::close);
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(0, budget.used());
            assertEquals(1, failures.get());
        } finally { executor.shutdownNow(); }
    }

    @Test public void overflowCompletesOfflineAndSizeRequestsInsteadOfLeavingCallersBusy() throws Exception {
        Field field = ImageLoader.class.getDeclaredField("LARGE_EXECUTOR");
        field.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(null);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<ImageLoadRequest> fillers = new ArrayList<>();
        AtomicInteger sizes = new AtomicInteger();
        AtomicInteger offline = new AtomicInteger();
        try {
            executor.execute(() -> { started.countDown(); await(release); });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            ImageLoader.probeOriginalSize("https://never-requested.invalid/original.png", bytes -> {
                assertEquals(-1L, bytes); sizes.incrementAndGet();
            });
            ImageLoader.prefetchOffline(ApplicationProvider.getApplicationContext(),
                    java.util.Collections.singletonList("https://never-requested.invalid/offline.png"),
                    96, bytes -> { assertEquals(0L, bytes); offline.incrementAndGet(); });
            for (int i = 0; i < 12; i++) {
                fillers.add(ImageLoadRequest.work(executor, main, () -> null,
                        ignored -> {}, ignored -> {}));
            }
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, sizes.get());
            assertEquals(1, offline.get());
            for (ImageLoadRequest request : fillers) request.cancel();
            assertTrue(executor.getQueue().isEmpty());
        } finally {
            for (ImageLoadRequest request : fillers) request.cancel();
            release.countDown();
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        }
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
}
