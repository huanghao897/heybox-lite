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

        assertEquals(101, small.width);
        assertEquals(114, medium.width);
        assertEquals(134, large.width);
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
        assertEquals(101, ResponsiveDock.fromScreen(360, 480).width);
        assertEquals(101, ResponsiveDock.fromScreen(360, 360).width);
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
        assertTrue(round.marginBottom <= Math.round(360 * 0.025f));

        double radius = 360.0 / 2.0;
        double cornerX = round.width / 2.0;
        double cornerY = radius - round.marginBottom;
        assertTrue((cornerX * cornerX) + (cornerY * cornerY) <= radius * radius);
    }
}
