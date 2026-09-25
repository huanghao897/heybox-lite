package com.ronan.heyboxlite;

import java.util.ArrayDeque;

/** Small in-memory operational tail; never waits for the cache executor during a crash. */
final class CrashBreadcrumbs {
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static volatile String screen = "startup";

    private CrashBreadcrumbs() {}

    static void screen(String value) { screen = CrashText.shortLine(value, 80); }
    static String screen() { return screen; }

    static synchronized void record(String message) {
        String safe = DiagnosticSanitizer.redact(message);
        // Automatic telemetry needs operations/timings, not request queries or content excerpts.
        safe = safe.replaceAll("(https?://[^\\s?]+)\\?[^\\s]*", "$1?<redacted>");
        if (safe.contains("response=") || safe.contains("body=") || safe.contains("text=")
                || safe.contains("title=") || safe.contains("keyword=")) return;
        if (LINES.size() == 24) LINES.removeFirst();
        LINES.addLast(CrashText.shortLine(safe, 240));
    }

    static synchronized String snapshot() {
        StringBuilder out = new StringBuilder();
        for (String line : LINES) out.append(line).append('\n');
        return out.toString();
    }
}
