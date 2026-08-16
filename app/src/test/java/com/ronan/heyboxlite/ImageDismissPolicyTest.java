package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ImageDismissPolicyTest {
    @Test
    public void removesTouchThresholdWithoutChangingDirection() {
        assertEquals(8.0f, ImageDismissPolicy.effectiveDistance(40.0f, 32.0f), 0.001f);
        assertEquals(-8.0f, ImageDismissPolicy.effectiveDistance(-40.0f, 32.0f), 0.001f);
        assertEquals(0.0f, ImageDismissPolicy.effectiveDistance(20.0f, 32.0f), 0.001f);
    }

    @Test
    public void treatsUpwardAndDownwardPullsEqually() {
        assertEquals(0.25f, ImageDismissPolicy.progress(100.0f, 400), 0.001f);
        assertEquals(0.25f, ImageDismissPolicy.progress(-100.0f, 400), 0.001f);
        assertTrue(ImageDismissPolicy.shouldDismiss(49.0f, 400));
        assertTrue(ImageDismissPolicy.shouldDismiss(-49.0f, 400));
        assertFalse(ImageDismissPolicy.shouldDismiss(47.0f, 400));
        assertFalse(ImageDismissPolicy.shouldDismiss(-47.0f, 400));
    }

    @Test
    public void followsOfficialScaleCurve() {
        assertEquals(1.0f, ImageDismissPolicy.scale(0.0f), 0.001f);
        assertEquals(0.88f, ImageDismissPolicy.scale(0.12f), 0.001f);
        assertEquals(0.60f, ImageDismissPolicy.scale(1.0f), 0.001f);
    }
}
