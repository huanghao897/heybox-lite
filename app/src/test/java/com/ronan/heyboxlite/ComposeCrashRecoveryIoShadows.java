package com.ronan.heyboxlite;

import android.app.Activity;
import android.content.Context;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only I/O boundaries. No crash fixture is sent to a server or public Downloads. */
public final class ComposeCrashRecoveryIoShadows {
    private ComposeCrashRecoveryIoShadows() {}

    @Implements(value = CrashUploads.class, isInAndroidSdk = false)
    public static class Uploads {
        public static final AtomicInteger calls = new AtomicInteger();

        @Implementation protected static void schedule(Context context) {
            calls.incrementAndGet();
        }
    }

    @Implements(value = DiagnosticsExporter.class, isInAndroidSdk = false)
    public static class Exporter {
        public static final AtomicInteger calls = new AtomicInteger();
        public static volatile String name;
        public static volatile String report;
        public static volatile String result;
        public static volatile CountDownLatch gate = new CountDownLatch(0);
        public static volatile CountDownLatch completed = new CountDownLatch(0);

        public static void reset() {
            calls.set(0);
            name = null;
            report = null;
            result = null;
            gate = new CountDownLatch(0);
            completed = new CountDownLatch(1);
        }

        @Implementation protected static String save(Activity activity, String fileName, String value) {
            calls.incrementAndGet();
            name = fileName;
            report = value;
            try {
                return gate.await(30, TimeUnit.SECONDS) ? result : null;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return null;
            } finally {
                completed.countDown();
            }
        }
    }
}
