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
        assertEquals(5, CrownScrollController.clampSpeed(1));
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
    public void keepsDirectionChangesResponsive() {
        CrownScrollController controller = new CrownScrollController();
        controller.distance(0.01f, 44, 100);

        assertEquals(22, controller.distance(-0.5f, 44, 100));
    }

    @Test
    public void preservesUsefulFastAxisMagnitude() {
        CrownScrollController controller = new CrownScrollController();

        assertEquals(-176, controller.distance(8.0f, 44, 100));
    }

    @Test
    public void lowestSpeedRemainsMonotonic() {
        CrownScrollController controller = new CrownScrollController();
        int total = 0;

        for (int i = 0; i < 20; i++) {
            total += controller.distance(1.0f, 84, 5);
        }

        assertEquals(-84, total);
    }
}
