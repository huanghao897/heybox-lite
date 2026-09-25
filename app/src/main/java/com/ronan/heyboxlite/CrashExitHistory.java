package com.ronan.heyboxlite;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

final class CrashExitHistory {
    private CrashExitHistory() {}

    static void collect(Context context) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) collectModern(context);
    }

    @TargetApi(Build.VERSION_CODES.R)
    @SuppressLint({"ApplySharedPref", "UseRequiresApi"})
    private static void collectModern(Context context) throws IOException {
        SharedPreferences state = context.getSharedPreferences("heybox_exit_history", 0);
        long now = System.currentTimeMillis();
        long since = state.getLong("checked_at", now);
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return;
        List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(
                context.getPackageName(), 0, 8);
        for (ApplicationExitInfo exit : exits) {
            if (exit.getTimestamp() <= since || !reportable(exit.getReason())) continue;
            String report = CrashReporter.metadata(context, DiagnosticSource.AUTO_EXIT)
                    + "error: process_exit_" + reason(exit.getReason()) + "\n"
                    + "exitProcess: " + exit.getProcessName() + "\n"
                    + "exitTimeMillis: " + exit.getTimestamp() + "\n"
                    + "exitStatus: " + exit.getStatus() + "\n"
                    + "pssKb: " + exit.getPss() + "\nrssKb: " + exit.getRss() + "\n"
                    + "description: " + CrashText.shortLine(exit.getDescription(), 500) + "\n";
            // Native traces may be binary protobufs. Do not treat them as arbitrary text/logcat.
            if (exit.getReason() == ApplicationExitInfo.REASON_ANR) {
                report += "\nANR trace:\n" + anrTrace(exit);
            }
            CrashReporter.store(context).enqueue(report);
        }
        state.edit().putLong("checked_at", now).commit();
    }

    @SuppressLint("InlinedApi")
    static boolean reportable(int reason) {
        return reason == ApplicationExitInfo.REASON_CRASH_NATIVE
                || reason == ApplicationExitInfo.REASON_ANR
                || reason == ApplicationExitInfo.REASON_LOW_MEMORY
                || reason == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE;
    }

    @TargetApi(Build.VERSION_CODES.R)
    @SuppressLint("UseRequiresApi")
    private static String anrTrace(ApplicationExitInfo exit) {
        try (InputStream stream = exit.getTraceInputStream()) {
            if (stream == null) return "unavailable";
            InputStreamReader reader = new InputStreamReader(stream, "UTF-8");
            char[] buffer = new char[4096];
            StringBuilder text = new StringBuilder();
            int count;
            while (text.length() < 6000
                    && (count = reader.read(buffer, 0,
                    Math.min(buffer.length, 6000 - text.length()))) > 0) {
                text.append(buffer, 0, count);
            }
            return text.toString();
        } catch (IOException | RuntimeException error) {
            return "unavailable";
        }
    }

    private static String reason(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "native_crash";
            case ApplicationExitInfo.REASON_ANR: return "anr";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "low_memory";
            default: return "resource_limit";
        }
    }
}
