package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ImageZoomPolicyTest {
    @Test
    public void advancesThroughTwoZoomLevelsAndBackToFit() {
        int first = ImageZoomPolicy.nextLevel(0);
        int second = ImageZoomPolicy.nextLevel(first);
        int fit = ImageZoomPolicy.nextLevel(second);

        assertEquals(1, first);
        assertEquals(2.35f, ImageZoomPolicy.targetScale(first, 1.0f), 0.001f);
        assertEquals(2, second);
        assertEquals(4.0f, ImageZoomPolicy.targetScale(second, 1.0f), 0.001f);
        assertEquals(0, fit);
        assertEquals(1.0f, ImageZoomPolicy.targetScale(fit, 1.0f), 0.001f);
    }

    @Test
    public void thirdDoubleTapReturnsToFitRegardlessOfAnimationProgress() {
        assertEquals(0, ImageZoomPolicy.nextLevel(2));
    }

    @Test
    public void firstZoomFillsLongImageWidth() {
        assertEquals(5.0f, ImageZoomPolicy.targetScale(1, 5.0f), 0.001f);
        assertEquals(7.75f, ImageZoomPolicy.targetScale(2, 5.0f), 0.001f);
    }
}
