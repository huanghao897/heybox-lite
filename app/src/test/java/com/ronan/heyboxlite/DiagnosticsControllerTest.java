package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DiagnosticsControllerTest {
    @Test public void missingFeedCursorIsRepresentedWithoutUnboxing() {
        assertEquals("none", DiagnosticsController.feedLastPullValue(null));
        assertEquals("0", DiagnosticsController.feedLastPullValue(0));
        assertEquals("12", DiagnosticsController.feedLastPullValue(12));
    }
}
