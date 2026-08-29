package com.ronan.heyboxlite;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class CrashReporter {
    private static final Object LOCK = new Object();
    private static final int MAX_BYTES = 96 * 1024;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String TRUNCATED = "\n... crash stack truncated ...\n";
    private static final String PREFERENCES = "heybox_crash_reporter";
    private static final String HANDLED_FINGERPRINT = "handled_fingerprint";
    private static final String PENDING = "pending";
    private static boolean installed;

    private CrashReporter() {}

    static void install(Context context) {
        if (context == null) return;
        synchronized (LOCK) {
            if (installed) return;
            installed = true;
            Context app = context.getApplicationContext();
            Thread.UncaughtExceptionHandler previous =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                if (!isRuntimeShutdown(error)) {
                    writeCrash(app, thread, error);
                    openRecoveryScreen(app);
                }
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                } else {
                    Process.killProcess(Process.myPid());
                    System.exit(10);
                }
            });
        }
    }

    static boolean isRuntimeShutdown(Throwable error) {
        return error instanceof InternalError
                && "Thread starting during runtime shutdown".equals(error.getMessage());
    }

    static void recordNonFatal(Context context, String operation, RuntimeException error) {
        if (context == null || error == null || hasPendingCrashReport(context)) return;
        String name = operation == null || operation.trim().isEmpty()
                ? "background_operation" : operation.trim();
        writeCrash(context.getApplicationContext(), Thread.currentThread(),
                new IllegalStateException(name, error));
    }

    static String pendingCrashReport(Context context) {
        String report = latestCrashReport(context);
        if (report.isEmpty()) return "";
        String handled = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(HANDLED_FINGERPRINT, "");
        return fingerprint(report).equals(handled) ? "" : report;
    }

    static boolean hasPendingCrashReport(Context context) {
        if (context == null) return false;
        Context app = context.getApplicationContext();
        android.content.SharedPreferences preferences = app.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        if (preferences.contains(PENDING)) return preferences.getBoolean(PENDING, false);
        File file = new File(diagnosticsDir(app), "crash-latest.log");
        return file.isFile() && file.length() > 0L;
    }

    static String latestCrashReport(Context context) {
        if (context == null) return "";
        return read(new File(diagnosticsDir(context.getApplicationContext()), "crash-latest.log"));
    }

    @SuppressLint("ApplySharedPref")
    static void markHandled(Context context, String report) {
        if (context == null || report == null || report.isEmpty()) return;
        context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(HANDLED_FINGERPRINT, fingerprint(report))
                .putBoolean(PENDING, false)
                .commit();
    }

    private static void writeCrash(Context context, Thread thread, Throwable error) {
        synchronized (LOCK) {
            try {
                File dir = diagnosticsDir(context);
                File latest = new File(dir, "crash-latest.log");
                File previous = new File(dir, "crash-previous.log");
                String old = read(latest);
                if (!old.trim().isEmpty()) write(previous, old);
                StringWriter stack = new StringWriter();
                PrintWriter writer = new PrintWriter(stack);
                if (error != null) error.printStackTrace(writer);
                writer.flush();
                String text = "crashTimeLocal: " + timestamp(System.currentTimeMillis()) + "\n"
                        + "crashTimeMillis: " + System.currentTimeMillis() + "\n"
                        + "thread: " + (thread == null ? "" : thread.getName()) + "\n"
                        + "error: " + (error == null ? "" : error.getClass().getName()) + "\n\n"
                        + stack;
                write(latest, trim(DiagnosticSanitizer.redact(text)));
                context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                        .edit().putBoolean(PENDING, true).commit();
            } catch (Throwable ignored) {
                // Last-resort crash logging must never make the crash path worse.
            }
        }
    }

    private static void openRecoveryScreen(Context context) {
        try {
            Intent intent = new Intent(context, CrashRecoveryActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            context.startActivity(intent);
            Thread.sleep(180L);
        } catch (Throwable ignored) {
        }
    }

    private static File diagnosticsDir(Context context) {
        File external = context.getExternalFilesDir(null);
        File root = external == null ? context.getFilesDir() : external;
        File dir = new File(root, "diagnostics");
        dir.mkdirs();
        return dir;
    }

    static String trim(String value) {
        if (value == null) return "";
        byte[] bytes = value.getBytes(UTF_8);
        if (bytes.length <= MAX_BYTES) return value;
        byte[] marker = TRUNCATED.getBytes(UTF_8);
        int contentBudget = MAX_BYTES - marker.length;
        int prefixEnd = utf8PrefixEnd(bytes, contentBudget / 3);
        int suffixStart = utf8SuffixStart(bytes, contentBudget - prefixEnd);
        return new String(bytes, 0, prefixEnd, UTF_8)
                + TRUNCATED
                + new String(bytes, suffixStart, bytes.length - suffixStart, UTF_8);
    }

    private static int utf8PrefixEnd(byte[] bytes, int budget) {
        int end = Math.min(bytes.length, Math.max(0, budget));
        while (end > 0 && end < bytes.length && isUtf8Continuation(bytes[end])) end--;
        return end;
    }

    private static int utf8SuffixStart(byte[] bytes, int budget) {
        int start = Math.max(0, bytes.length - Math.max(0, budget));
        while (start < bytes.length && isUtf8Continuation(bytes[start])) start++;
        return start;
    }

    private static boolean isUtf8Continuation(byte value) {
        return (value & 0xC0) == 0x80;
    }

    private static void write(File file, String value) {
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write((value == null ? "" : value).getBytes("UTF-8"));
            }
        } catch (Exception ignored) {
        }
    }

    private static String read(File file) {
        if (file == null || !file.exists()) return "";
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String timestamp(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes("UTF-8"));
            StringBuilder output = new StringBuilder(digest.length * 2);
            for (byte item : digest) output.append(String.format(Locale.US, "%02x", item & 0xff));
            return output.toString();
        } catch (Exception ignored) {
            return value.length() + ":" + value.hashCode();
        }
    }
}
