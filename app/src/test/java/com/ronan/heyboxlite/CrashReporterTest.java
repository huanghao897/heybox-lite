package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class CrashReporterTest {
    @Test
    public void ignoresOnlyRuntimeShutdownThreadCreation() {
        assertTrue(CrashReporter.isRuntimeShutdown(
                new InternalError("Thread starting during runtime shutdown")));
        assertFalse(CrashReporter.isRuntimeShutdown(new InternalError("other")));
        assertFalse(CrashReporter.isRuntimeShutdown(
                new IllegalStateException("Thread starting during runtime shutdown")));
    }

    @Test
    public void longCrashReportKeepsCauseAndThreadTailWithinByteLimit() {
        StringBuilder value = new StringBuilder("error: java.lang.StackOverflowError\n中文异常\n");
        for (int i = 0; i < 12000; i++) {
            value.append("\tat sample.recursive(Call.java:1)\n");
        }
        value.append("\tat android.app.ActivityThread.main(ActivityThread.java:9998)\n");

        String trimmed = CrashReporter.trim(value.toString());

        assertTrue(trimmed.startsWith("error: java.lang.StackOverflowError"));
        assertTrue(trimmed.contains("... crash stack truncated ..."));
        assertTrue(trimmed.endsWith("ActivityThread.main(ActivityThread.java:9998)\n"));
        assertTrue(trimmed.getBytes(StandardCharsets.UTF_8).length <= 96 * 1024);
        assertEquals(-1, trimmed.indexOf('\uFFFD'));
    }
}
