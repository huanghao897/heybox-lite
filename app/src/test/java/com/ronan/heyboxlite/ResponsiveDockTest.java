package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ResponsiveDockTest {
    @Test
    public void scalesWithScreenShortEdge() {
        ResponsiveDock.Dimensions small = ResponsiveDock.fromScreen(360, 360);
        ResponsiveDock.Dimensions medium = ResponsiveDock.fromScreen(408, 408);
        ResponsiveDock.Dimensions large = ResponsiveDock.fromScreen(480, 480);

        assertEquals(101, small.width);
        assertEquals(114, medium.width);
        assertEquals(134, large.width);
        assertTrue(small.height < medium.height);
        assertTrue(medium.height < large.height);
    }

    @Test
    public void usesShortEdgeInPortrait() {
        assertEquals(101, ResponsiveDock.fromScreen(360, 800).width);
    }

    @Test
    public void staysInsideVerySmallScreens() {
        ResponsiveDock.Dimensions dimensions = ResponsiveDock.fromScreen(80, 80);
        assertTrue(dimensions.width < 80);
        assertTrue(dimensions.height > 0);
        assertTrue(dimensions.iconSize < dimensions.height);
    }
}
