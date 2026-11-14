package com.ronan.heyboxlite;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class ImageWorkTokenTest {
    @Test public void cancellationMarksStoppedWithoutExecutingNetworkAbortOnCaller() {
        List<Runnable> pending = new ArrayList<>();
        ImageWorkToken token = new ImageWorkToken(pending::add);
        AtomicBoolean disconnected = new AtomicBoolean();
        ImageWorkToken.enter(token);
        try {
            ImageWorkToken.abortWith(() -> disconnected.set(true));
            token.cancel();
            token.cancel();
            assertTrue(ImageWorkToken.stopped());
            assertFalse(disconnected.get());
            assertEquals(1, pending.size());
            pending.get(0).run();
            assertTrue(disconnected.get());
        } finally { ImageWorkToken.leave(); }
    }

    @Test public void registrationAfterCancellationStillAbortsOffCaller() {
        List<Runnable> pending = new ArrayList<>();
        ImageWorkToken token = new ImageWorkToken(pending::add);
        token.cancel();
        ImageWorkToken.enter(token);
        try {
            ImageWorkToken.abortWith(() -> {});
            ImageWorkToken.abortWith(null);
            assertEquals(1, pending.size());
        } finally { ImageWorkToken.leave(); }
    }

    @Test public void blockedNetworkDisconnectDoesNotBlockCancelOrHoldTokenMonitor() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ImageWorkToken token = new ImageWorkToken();
        Thread caller = Thread.currentThread();
        AtomicBoolean wrongThread = new AtomicBoolean();
        ImageWorkToken.enter(token);
        try {
            ImageWorkToken.abortWith(() -> {
                wrongThread.set(Thread.currentThread() == caller);
                started.countDown();
                try { release.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            });
            token.cancel();
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertFalse(wrongThread.get());
            ImageWorkToken.abortWith(null);
            assertTrue(ImageWorkToken.stopped());
        } finally { release.countDown(); ImageWorkToken.leave(); }
    }
}
