package com.ronan.heyboxlite;

import org.junit.Test;
import static org.junit.Assert.*;

public class CrashTextTest {
    @Test public void recursiveStackKeepsRootCauseWithoutUnlimitedBuffer() {
        RuntimeException failure = new RuntimeException("outer",
                new IllegalStateException("root cause"));
        StackTraceElement[] frames = new StackTraceElement[30_000];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new StackTraceElement("recursive.Class", "call", "Class.java", i);
        }
        failure.setStackTrace(frames);
        String stack = CrashText.stack(failure);
        assertTrue(stack.startsWith("java.lang.RuntimeException: outer"));
        assertTrue(stack.contains("root cause"));
        assertTrue(stack.contains("truncated"));
        assertTrue(stack.length() < 15_000);
    }

    @Test public void uiSummaryCannotGrowWithStack() {
        assertEquals("java.lang.IllegalStateException", CrashText.summary(
                "version: 2.15\nerror: java.lang.IllegalStateException\n"
                + "at one.Frame()\nat another.Frame()"));
    }

    @Test public void credentialsAreNotIncludedInAutomaticBreadcrumbs() {
        CrashBreadcrumbs.record("GET https://example.invalid/api?pkey=testsecret");
        CrashBreadcrumbs.record("password=my-password token=my-token x_heybox_id=123456");
        CrashBreadcrumbs.record("response=private content");
        String text = CrashBreadcrumbs.snapshot();
        assertFalse(text.contains("testsecret"));
        assertFalse(text.contains("my-password"));
        assertFalse(text.contains("my-token"));
        assertFalse(text.contains("123456"));
        assertFalse(text.contains("private content"));
    }

    @Test public void intentionalTestIsDistinguishableFromRealFailure() {
        String text = CrashText.stack(new CrashTestController.ManualCrashTestException());
        assertTrue(text.contains("intentionalCrashTest: true"));
    }
}
