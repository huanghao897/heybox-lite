package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CrownScrollControllerTest {
    @Test
    public void scalesDistanceWithConfiguredSpeed() {
        CrownScrollController controller = new CrownScrollController();

        assertEquals(-22, controller.distance(1.0f, 44, 50));
        assertEquals(-44, controller.distance(1.0f, 44, 100));
        assertEquals(-88, controller.distance(1.0f, 44, 200));
    }

    @Test
    public void clampsSpeedToSupportedRange() {
        assertEquals(25, CrownScrollController.clampSpeed(1));
        assertEquals(125, CrownScrollController.clampSpeed(125));
        assertEquals(200, CrownScrollController.clampSpeed(500));
    }

    @Test
    public void accumulatesSmallRotarySteps() {
        CrownScrollController controller = new CrownScrollController();
        int total = 0;

        for (int i = 0; i < 10; i++) {
            total += controller.distance(0.01f, 44, 100);
        }

        assertTrue(total < 0);
        assertEquals(-4, total);
    }

    @Test
    public void changesDirectionWithoutCarryingOldRemainder() {
        CrownScrollController controller = new CrownScrollController();
        controller.distance(0.01f, 44, 100);

        assertEquals(22, controller.distance(-0.5f, 44, 100));
    }

    @Test
    public void coalescesEventsWithoutIntegerOverflow() {
        assertEquals(75, CrownScrollController.coalesce(40, 35));
        assertEquals(Integer.MAX_VALUE,
                CrownScrollController.coalesce(Integer.MAX_VALUE, 1));
        assertEquals(Integer.MIN_VALUE,
                CrownScrollController.coalesce(Integer.MIN_VALUE, -1));
    }

    @Test
    public void acceptsOnlyConsistentVisibleListWindows() {
        assertTrue(CrownScrollController.isStableListWindow(20, 20, 4, 6));
        assertTrue(!CrownScrollController.isStableListWindow(20, 19, 4, 6));
        assertTrue(!CrownScrollController.isStableListWindow(20, 20, 18, 3));
        assertTrue(!CrownScrollController.isStableListWindow(0, 0, 0, 0));
    }
}
