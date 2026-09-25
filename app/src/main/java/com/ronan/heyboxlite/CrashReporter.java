package com.ronan.heyboxlite;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

final class CrashReporter {
    private static final AtomicBoolean CRASHING = new AtomicBoolean();
    private static boolean installed;
    private static boolean recoveryProcess;
    private static volatile byte[] emergencyReserve;
    private static int releasedReserveBytes;
    private static String processName = "";

    private CrashReporter() {}

    static synchronized void install(Context context) {
        if (installed) return;
        installed = true;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        final Context owner = app;
        emergencyReserve = new byte[64 * 1024];
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            if (CRASHING.compareAndSet(false, true)) {
                releasedReserveBytes = emergencyReserve == null ? 0 : emergencyReserve.length;
                emergencyReserve = null;
                try {
                    if (!isRuntimeShutdown(error)) {
                        DiagnosticSource source =
                                error instanceof CrashTestController.ManualCrashTestException
                                ? DiagnosticSource.CRASH_TEST : DiagnosticSource.AUTO_CRASH;
                        writeCrash(owner, thread, error, source);
                        if (!recoveryProcess) openRecoveryScreen(owner);
                    }
                } finally {
                    if (previous != null) previous.uncaughtException(thread, error);
                    Process.killProcess(Process.myPid());
                    System.exit(10);
                }
            } else {
                // Let the first crashing thread finish its durable write.
                try { Thread.sleep(2000L); } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    static void setProcess(String name) {
        processName = name == null ? "" : name;
        recoveryProcess = processName.endsWith(":crash");
    }

    static boolean isMainProcess(Context context) {
        return context.getPackageName().equals(processName);
    }

    static boolean isRuntimeShutdown(Throwable error) {
        return error instanceof InternalError
                && "Thread starting during runtime shutdown".equals(error.getMessage());
    }

    static void recordNonFatal(Context context, String operation, RuntimeException error) {
        if (context == null || error == null) return;
        writeCrash(context, Thread.currentThread(),
                new IllegalStateException(CrashText.shortLine(operation, 80), error),
                DiagnosticSource.AUTO_ERROR);
        CrashUploads.schedule(context);
    }

    static CrashReportStore store(Context context) {
        return new CrashReportStore(new File(context.getFilesDir(), "crash-reports"));
    }

    static String latestCrashReport(Context context) {
        String report = store(context).latest();
        return report.isEmpty() ? CrashReportStore.read(legacyFile(context, "crash-latest.log"))
                : report;
    }

    static String previousCrashReport(Context context) {
        String report = store(context).previous();
        return report.isEmpty() ? CrashReportStore.read(legacyFile(context, "crash-previous.log"))
                : report;
    }

    private static File legacyFile(Context context, String name) {
        File external = context.getExternalFilesDir(null);
        return new File(new File(external == null ? context.getFilesDir() : external,
                "diagnostics"), name);
    }

    private static void writeCrash(Context context, Thread thread, Throwable error,
                                   DiagnosticSource source) {
        try {
            String report = metadata(context, source)
                    + "thread: " + (thread == null ? "" : thread.getName()) + "\n"
                    + "screen: " + CrashBreadcrumbs.screen() + "\n"
                    + "error: " + error.getClass().getName() + "\n\n"
                    + CrashText.limit(CrashText.stack(error), 18 * 1024)
                    + "\nrecent events:\n" + CrashBreadcrumbs.snapshot();
            store(context).enqueue(report);
        } catch (Throwable recordingFailure) {
            // The crash path must still terminate cleanly, including on exhausted heaps.
            try {
                store(context).enqueue("heybox Lite crash report\n" + source.reportHeader()
                        + "error: "
                        + error.getClass().getName() + "\nstack unavailable: "
                        + recordingFailure.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }
        }
    }

    static String metadata(Context context, DiagnosticSource source) {
        Runtime runtime = Runtime.getRuntime();
        return "heybox Lite crash report\n"
                + source.reportHeader()
                + "version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                + "device: " + Build.MANUFACTURER + " " + Build.MODEL + " / Android "
                + Build.VERSION.RELEASE + " api " + Build.VERSION.SDK_INT + "\n"
                + "package: " + context.getPackageName() + "\n"
                + "process: " + processName + "\n"
                + "crashTimeMillis: " + System.currentTimeMillis() + "\n"
                + "heapMaxBytes: " + runtime.maxMemory() + "\n"
                + "emergencyReserveReleased: " + releasedReserveBytes + "\n"
                + "heapUsedBytes: " + (runtime.totalMemory() - runtime.freeMemory()) + "\n";
    }

    private static void openRecoveryScreen(Context context) {
        try {
            Intent intent = new Intent(context, CrashRecoveryActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            context.startActivity(intent);
            Thread.sleep(180L);
        } catch (Exception | LinkageError ignored) {
            // Launch restrictions cannot prevent next-start delivery of the outbox.
        }
    }

    static String trim(String value) {
        return CrashText.limit(value, 96 * 1024);
    }
}
