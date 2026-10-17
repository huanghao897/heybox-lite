package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ResponsiveDockTest {
    @Test
    public void scalesAcrossCommonRectangularWatchSizes() {
        ResponsiveDock.Dimensions small = ResponsiveDock.fromScreen(360, 480);
        ResponsiveDock.Dimensions medium = ResponsiveDock.fromScreen(408, 544);
        ResponsiveDock.Dimensions large = ResponsiveDock.fromScreen(480, 640);

        assertEquals(144, small.width);
        assertEquals(163, medium.width);
        assertEquals(192, large.width);
        assertTrue(small.height < medium.height);
        assertTrue(medium.height < large.height);
        assertTrue(small.iconSize < medium.iconSize);
        assertTrue(medium.iconSize < large.iconSize);
    }

    @Test
    public void keepsScalingOnCompactScreens() {
        ResponsiveDock.Dimensions compact = ResponsiveDock.fromScreen(180, 240);
        ResponsiveDock.Dimensions small = ResponsiveDock.fromScreen(240, 320);
        ResponsiveDock.Dimensions medium = ResponsiveDock.fromScreen(360, 480);

        assertTrue(compact.width < small.width);
        assertTrue(small.width < medium.width);
        assertTrue(compact.height < small.height);
        assertTrue(small.height < medium.height);
    }

    @Test
    public void usesShortEdgeRegardlessOfAspectRatio() {
        assertEquals(144, ResponsiveDock.fromScreen(360, 480).width);
        assertEquals(144, ResponsiveDock.fromScreen(360, 360).width);
        assertEquals(144, ResponsiveDock.fromScreen(480, 360).width);
    }

    @Test
    public void staysInsideVerySmallScreens() {
        ResponsiveDock.Dimensions dimensions = ResponsiveDock.fromScreen(80, 80);
        assertTrue(dimensions.width < 80);
        assertTrue(dimensions.height > 0);
        assertTrue(dimensions.iconSize < dimensions.height);
    }

    @Test
    public void roundLayoutStaysNearBottomWithoutCrossingCircle() {
        ResponsiveDock.Dimensions rectangular =
                ResponsiveDock.fromScreen(360, 480, false);
        ResponsiveDock.Dimensions round =
                ResponsiveDock.fromScreen(360, 360, true);

        assertTrue(round.width < rectangular.width);
        assertTrue(round.height <= rectangular.height);
        assertTrue(round.marginBottom > rectangular.marginBottom);
        assertTrue(round.marginBottom <= Math.ceil(360 * 0.04f));
        assertBoundsInCircle(360, round);
    }

    @Test
    public void enlargesRequestedWatchSizesAtNormalAndMinimumScale() {
        assertDimensions(192, 192, true, 1.0f, 73, 25);
        assertDimensions(192, 192, true, 0.5f, 36, 12);
        assertDimensions(227, 227, true, 1.0f, 86, 29);
        assertDimensions(227, 227, true, 0.5f, 43, 15);
        assertDimensions(240, 320, false, 1.0f, 96, 33);
        assertDimensions(240, 320, false, 0.5f, 48, 16);
    }

    @Test
    public void scalesFromPhysicalPixelsRatherThanAFixedDpSize() {
        for (boolean round : new boolean[]{false, true}) {
            for (float scale : new float[]{0.5f, 0.78f, 1.0f, 1.6f}) {
                ResponsiveDock.Dimensions mdpi =
                        ResponsiveDock.fromScreen(192, round ? 192 : 256, round, scale);
                ResponsiveDock.Dimensions xhdpi =
                        ResponsiveDock.fromScreen(384, round ? 384 : 512, round, scale);
                assertEquals(mdpi.width * 2, xhdpi.width, 1);
                assertEquals(mdpi.height * 2, xhdpi.height, 1);
                assertEquals(mdpi.iconSize * 2, xhdpi.iconSize, 1);
            }
        }
    }

    @Test
    public void enlargedRoundHitBoundsStayInsideCircleAcrossSupportedScales() {
        for (int diameter : new int[]{80, 192, 227, 240, 360, 454, 480}) {
            int previousWidth = 0;
            for (float scale : new float[]{0.5f, 0.78f, 0.82f, 1.0f, 1.25f, 1.6f}) {
                ResponsiveDock.Dimensions dock =
                        ResponsiveDock.fromScreen(diameter, diameter, true, scale);
                assertBoundsInCircle(diameter, dock);
                assertTrue(dock.width >= previousWidth);
                previousWidth = dock.width;
                assertTrue("Even maximum scale must remain near the bottom",
                        dock.marginBottom <= Math.ceil(diameter * 0.11f));
                if (scale <= 1.0f) {
                    assertTrue("Normal/minimum sizing must not need a large bottom lift",
                            dock.marginBottom <= Math.ceil(diameter * 0.04f));
                }
            }
        }
    }

    @Test
    public void preservesEffectiveWatchScaleAndExistingMinimumClamp() {
        ResponsiveDock.Dimensions compact = ResponsiveDock.fromScreen(192, 192, true, 0.78f);
        assertEquals(57, compact.width);
        assertEquals(19, compact.height);
        ResponsiveDock.Dimensions minimum = ResponsiveDock.fromScreen(192, 192, true, 0.39f);
        assertEquals(36, minimum.width);
        assertEquals(12, minimum.height);
        assertTrue(minimum.iconSize < compact.iconSize);
        assertEquals(ResponsiveDock.fromScreen(192, 192, true, 0.5f).marginBottom,
                minimum.marginBottom);
    }

    private static void assertDimensions(int width, int height, boolean round, float scale,
                                         int expectedWidth, int expectedHeight) {
        ResponsiveDock.Dimensions dock = ResponsiveDock.fromScreen(width, height, round, scale);
        assertEquals(expectedWidth, dock.width);
        assertEquals(expectedHeight, dock.height);
        int shortEdge = Math.min(width, height);
        float oldWidthRatio = round ? 0.27f : 0.28f;
        int oldWidth = Math.round(shortEdge * oldWidthRatio * scale);
        int oldHeight = Math.round(oldWidth * 0.34f);
        assertTrue("Moderate enlargement at every requested size/scale", dock.width > oldWidth);
        assertTrue(dock.width <= Math.ceil(oldWidth * 1.45f));
        assertTrue(dock.height > oldHeight);
        assertTrue(dock.iconSize > Math.round(oldHeight * 0.52f));
        int itemWidth = (dock.width - dock.paddingHorizontal * 2 - dock.itemMargin * 4) / 2;
        int itemHeight = dock.height - dock.paddingVertical * 2;
        assertTrue("Touch area must extend beyond the icon horizontally", itemWidth > dock.iconSize);
        assertTrue("Touch area must extend beyond the icon vertically", itemHeight > dock.iconSize);
        if (round) assertBoundsInCircle(shortEdge, dock);
    }

    private static void assertBoundsInCircle(int diameter, ResponsiveDock.Dimensions dock) {
        int left = (diameter - dock.width) / 2;
        int top = diameter - dock.marginBottom - dock.height;
        double radius = diameter / 2.0;
        for (int x : new int[]{left, left + dock.width}) {
            for (int y : new int[]{top, top + dock.height}) {
                double dx = x - radius;
                double dy = y - radius;
                assertTrue("Entire dock/touch bounds must be inside " + diameter + "px circle",
                        dx * dx + dy * dy <= radius * radius);
            }
        }
    }
}
