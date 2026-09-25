package com.ronan.heyboxlite;

import android.content.Context;
import android.os.SystemClock;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CrashUploads {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "heybox-crash-upload");
        thread.setDaemon(true);
        return thread;
    });
    private static boolean running;
    private static long lastAttempt = -60_000L;

    private CrashUploads() {}

    static synchronized void schedule(Context context) {
        long now = SystemClock.elapsedRealtime();
        if (running || now - lastAttempt < 60_000L) return;
        running = true;
        lastAttempt = now;
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                if (CrashReporter.isMainProcess(app)) CrashExitHistory.collect(app);
                CrashReporter.store(app).drain(report -> DiagnosticsClient.send(
                        "", report, DiagnosticSource.fromReport(report)));
            } catch (IOException | RuntimeException | OutOfMemoryError ignored) {
                // Keep the outbox for the next launch. Do not recursively report transport errors.
            } finally {
                synchronized (CrashUploads.class) { running = false; }
            }
        });
    }
}
