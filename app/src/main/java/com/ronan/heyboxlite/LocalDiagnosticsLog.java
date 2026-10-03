package com.ronan.heyboxlite;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Owns event, crash-adjacent and native-sign diagnostics storage. */
final class LocalDiagnosticsLog {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int MAX_LOG_BYTES = 96 * 1024;
    private static final Object SESSION_LOCK = new Object();
    private static final ExecutorService LOG_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "heybox-event-log");
                thread.setDaemon(true);
                return thread;
            });
    private static String processSessionId;
    private static long processSessionStartedAt;
    private static boolean processSessionLogPrepared;

    private final Context context;
    private final File diagnosticsDir;
    private final String sessionId;
    private final long sessionStartedAt;

    LocalDiagnosticsLog(Context context, File fallbackRoot) {
        this.context = context.getApplicationContext();
        File external = this.context.getExternalFilesDir(null);
        this.diagnosticsDir = new File(external == null ? fallbackRoot : external, "diagnostics");
        this.diagnosticsDir.mkdirs();
        synchronized (SESSION_LOCK) {
            if (processSessionId == null || processSessionId.isEmpty()) {
                processSessionId = UUID.randomUUID().toString();
                processSessionStartedAt = System.currentTimeMillis();
                processSessionLogPrepared = false;
            }
            this.sessionId = processSessionId;
            this.sessionStartedAt = processSessionStartedAt;
            if (!processSessionLogPrepared) {
                processSessionLogPrepared = true;
                LOG_EXECUTOR.execute(this::resetSessionLogLocked);
            }
        }
    }

    void log(String message) {
        CrashBreadcrumbs.record(message);
        String line = timestamp() + "  " + (message == null ? "" : message) + "\n";
        File file = logFile();
        LOG_EXECUTOR.execute(() -> appendEvent(file, line));
    }

    String recentLog() {
        awaitEventWrites();
        synchronized (SESSION_LOCK) {
            return read(logFile());
        }
    }

    String previousLog() {
        synchronized (SESSION_LOCK) {
            return read(previousLogFile());
        }
    }

    String crashLog() {
        return CrashReporter.latestCrashReport(context);
    }

    String previousCrashLog() {
        return CrashReporter.previousCrashReport(context);
    }

    String nativeSignLog() {
        synchronized (SESSION_LOCK) {
            return read(nativeSignLogFile());
        }
    }

    static void appendNativeSignLog(Context context, String message) {
        if (context == null) return;
        synchronized (SESSION_LOCK) {
            Context app = context.getApplicationContext();
            File external = app.getExternalFilesDir(null);
            File dir = new File(external == null
                    ? new File(app.getFilesDir(), "offline-cache") : external, "diagnostics");
            File file = new File(dir, "native-sign.log");
            String line = timestampNow() + "  " + (message == null ? "" : message) + "\n";
            String previous = readStatic(file);
            String next = previous + line;
            if (next.length() > MAX_LOG_BYTES) {
                next = next.substring(Math.max(0, next.length() - MAX_LOG_BYTES));
            }
            writeStatic(file, next);
        }
    }

    String sessionId() {
        return sessionId;
    }

    long sessionStartedAt() {
        return sessionStartedAt;
    }

    File writeDiagnostics(String text) {
        diagnosticsDir.mkdirs();
        String value = text == null ? "" : text;
        File output = new File(diagnosticsDir,
                "heybox-lite-diagnostics-" + timestampFile() + ".txt");
        write(output, value);
        write(new File(diagnosticsDir, "heybox-lite-diagnostics-latest.txt"), value);
        return output;
    }

    private static void appendEvent(File file, String line) {
        synchronized (SESSION_LOCK) {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileOutputStream output = new FileOutputStream(file, true)) {
                output.write(line.getBytes(UTF_8));
            } catch (IOException ignored) {
                return;
            }
            if (file.length() <= MAX_LOG_BYTES + 8 * 1024L) return;
            String value = readStatic(file);
            if (value.length() > MAX_LOG_BYTES) {
                value = value.substring(value.length() - MAX_LOG_BYTES);
                int firstLine = value.indexOf('\n');
                if (firstLine >= 0 && firstLine + 1 < value.length()) {
                    value = value.substring(firstLine + 1);
                }
            }
            writeStatic(file, value);
        }
    }

    private static void awaitEventWrites() {
        try {
            Future<?> barrier = LOG_EXECUTOR.submit(() -> { });
            barrier.get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private File logFile() {
        return new File(diagnosticsDir, "events-session.log");
    }

    private File previousLogFile() {
        return new File(diagnosticsDir, "events-previous-session.log");
    }

    private File nativeSignLogFile() {
        return new File(diagnosticsDir, "native-sign.log");
    }

    private void resetSessionLogLocked() {
        String previous = read(logFile());
        if (!previous.trim().isEmpty()) write(previousLogFile(), previous);
        write(logFile(), "sessionId: " + sessionId + "\n"
                + "sessionStartedLocal: " + timestamp(sessionStartedAt) + "\n"
                + "sessionStartedMillis: " + sessionStartedAt + "\n");
        write(nativeSignLogFile(), "sessionId: " + sessionId + "\n"
                + "sessionStartedLocal: " + timestamp(sessionStartedAt) + "\n"
                + "sessionStartedMillis: " + sessionStartedAt + "\n");
    }

    private void write(File file, String value) {
        writeStatic(file, value);
    }

    private static void writeStatic(File file, String value) {
        try {
            byte[] bytes = (value == null ? "" : value).getBytes(UTF_8);
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(bytes);
            }
        } catch (IOException | SecurityException ignored) {
        }
    }

    private String read(File file) {
        return readStatic(file);
    }

    private static String readStatic(File file) {
        try {
            if (file == null || !file.exists()) return "";
        } catch (SecurityException ignored) {
            return "";
        }
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return new String(output.toByteArray(), UTF_8);
        } catch (OutOfMemoryError ignored) {
            return "";
        } catch (IOException | SecurityException ignored) {
            return "";
        }
    }

    private String timestamp() {
        return timestamp(System.currentTimeMillis());
    }

    private static String timestampNow() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private String timestamp(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
    }

    private String timestampFile() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }
}
