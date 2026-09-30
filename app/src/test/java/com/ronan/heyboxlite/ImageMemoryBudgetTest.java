package com.ronan.heyboxlite;

import org.junit.Test;
import static org.junit.Assert.*;

public class ImageMemoryBudgetTest {
    @Test public void smallHeapDoesNotAllocateTwentyMegabyteBitmap() {
        assertEquals(1024 * 1024, ImageMemoryBudget.bitmapPixels(32L * 1024 * 1024));
        assertEquals(2 * 1024 * 1024, ImageMemoryBudget.bitmapPixels(64L * 1024 * 1024));
    }

    @Test public void largeHeapRetainsCurrentQualityCeiling() {
        assertEquals(5_000_000, ImageMemoryBudget.bitmapPixels(512L * 1024 * 1024));
        assertTrue(ImageMemoryBudget.bitmapPixels(0) > 0);
    }

    @Test public void lowMemoryDecodeBudgetAllowsSeveralAttachedImages() {
        assertEquals(262_144, ImageMemoryBudget.decodePixels(32L * 1024 * 1024));
        assertEquals(524_288, ImageMemoryBudget.decodePixels(64L * 1024 * 1024));
        assertEquals(1_000_000, ImageMemoryBudget.decodePixels(128L * 1024 * 1024));
        assertEquals(640, ImageMemoryBudget.decodeTargetPx(
                128L * 1024 * 1024, 900));
    }

    @Test public void normalHeapKeepsRequestedTargetWithinCeiling() {
        assertEquals(900, ImageMemoryBudget.decodeTargetPx(
                256L * 1024 * 1024, 900));
        assertEquals(2400, ImageMemoryBudget.decodeTargetPx(
                256L * 1024 * 1024, 4000));
    }
}
