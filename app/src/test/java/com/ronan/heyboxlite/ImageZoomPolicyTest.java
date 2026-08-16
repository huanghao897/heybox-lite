package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ImageZoomPolicyTest {
    @Test
    public void advancesThroughTwoZoomLevelsAndBackToFit() {
        int first = ImageZoomPolicy.nextLevel(0, 1.0f);
        int second = ImageZoomPolicy.nextLevel(first, 2.35f);
        int fit = ImageZoomPolicy.nextLevel(second, 4.0f);

        assertEquals(1, first);
        assertEquals(2.35f, ImageZoomPolicy.targetScale(first, 1.0f), 0.001f);
        assertEquals(2, second);
        assertEquals(4.0f, ImageZoomPolicy.targetScale(second, 1.0f), 0.001f);
        assertEquals(0, fit);
        assertEquals(1.0f, ImageZoomPolicy.targetScale(fit, 1.0f), 0.001f);
    }

    @Test
    public void firstZoomFillsLongImageWidth() {
        assertEquals(5.0f, ImageZoomPolicy.targetScale(1, 5.0f), 0.001f);
        assertEquals(7.75f, ImageZoomPolicy.targetScale(2, 5.0f), 0.001f);
    }
}
