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
}
