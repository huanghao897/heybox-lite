package com.ronan.heyboxlite;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class CrashReporter {
    private static final Object LOCK = new Object();
    private static final int MAX_BYTES = 96 * 1024;
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
                writeCrash(app, thread, error);
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                }
            });
        }
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
                write(latest, trim(text));
            } catch (Throwable ignored) {
                // Last-resort crash logging must never make the crash path worse.
            }
        }
    }

    private static File diagnosticsDir(Context context) {
        File external = context.getExternalFilesDir(null);
        File root = external == null ? context.getFilesDir() : external;
        File dir = new File(root, "diagnostics");
        dir.mkdirs();
        return dir;
    }

    private static String trim(String value) {
        if (value == null) return "";
        byte[] bytes;
        try {
            bytes = value.getBytes("UTF-8");
        } catch (Exception ignored) {
            return value.length() > MAX_BYTES ? value.substring(value.length() - MAX_BYTES) : value;
        }
        if (bytes.length <= MAX_BYTES) return value;
        // 按字节预算换算保留的字符数（UTF-8 中文最多 3 字节/字），避免截完仍超预算
        int keepChars = Math.max(1, MAX_BYTES / 3);
        int start = Math.max(0, value.length() - keepChars);
        return value.substring(start);
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
}
