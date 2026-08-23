package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}
