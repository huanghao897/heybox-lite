package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CrownScrollControllerTest {
    @Test
    public void scalesDistanceWithConfiguredSpeed() {
        CrownScrollController controller = new CrownScrollController();

        assertEquals(-11, controller.distance(1.0f, 44, 50));
        assertEquals(-44, controller.distance(1.0f, 44, 100));
        assertEquals(-73, controller.distance(1.0f, 44, 200));
    }

    @Test
    public void keepsSpeedAboveSliderRange() {
        assertEquals(5, CrownScrollController.clampSpeed(1));
        assertEquals(125, CrownScrollController.clampSpeed(125));
        assertEquals(500, CrownScrollController.clampSpeed(500));
        assertEquals(Integer.MAX_VALUE,
                CrownScrollController.clampSpeed(Integer.MAX_VALUE));
    }

    @Test
    public void appliesSpeedAboveSliderRangeToDistanceAndFrameLimit() {
        CrownScrollController controller = new CrownScrollController();

        assertEquals(-165, controller.distance(1.0f, 100,
                CrownScrollController.SLIDER_MAX_SPEED_PERCENT));
        controller.reset();
        assertEquals(-360, controller.distance(1.0f, 100, 500));
        assertEquals(450, controller.frameLimit(100, 500));
        assertTrue(controller.frameLimit(100, 500) > controller.frameLimit(100,
                CrownScrollController.SLIDER_MAX_SPEED_PERCENT));
    }

    @Test
    public void saturatedDistancesDoNotLeaveOverflowInTheRemainder() {
        for (int direction : new int[]{-1, 1}) {
            CrownScrollController controller = new CrownScrollController();
            int saturated = -direction * Integer.MAX_VALUE;

            assertEquals(saturated, controller.distance(direction, Integer.MAX_VALUE,
                    Integer.MAX_VALUE));
            assertEquals(saturated, controller.distance(direction, Integer.MAX_VALUE,
                    Integer.MAX_VALUE));
            assertEquals(-direction * 11, controller.distance(direction * 0.25f, 44, 100));
            assertEquals(Integer.MAX_VALUE,
                    controller.frameLimit(Integer.MAX_VALUE, Integer.MAX_VALUE));
        }
    }

    @Test
    public void rejectsNonFiniteRotaryInputWithoutPoisoningLaterTicks() {
        CrownScrollController controller = new CrownScrollController();

        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY}) {
            assertEquals(0, controller.distance(invalid, 44, Integer.MAX_VALUE));
            assertEquals(0, controller.distance(1.0f, 44, Integer.MAX_VALUE, invalid));
        }
        assertEquals(-44, controller.distance(1.0f, 44, 100));
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
    public void clampsLargeAxisMagnitudeToOneStep() {
        CrownScrollController controller = new CrownScrollController();

        assertEquals(-44, controller.distance(8.0f, 44, 100));
    }

    @Test
    public void appliesDeviceAxisGainBeforeClamping() {
        CrownScrollController controller = new CrownScrollController();

        assertEquals(-18, controller.distance(0.1f, 44, 100, 4.0f));
    }

    @Test
    public void keepsDefaultAxisGainBehaviorUnchanged() {
        CrownScrollController defaultController = new CrownScrollController();
        CrownScrollController explicitController = new CrownScrollController();

        assertEquals(defaultController.distance(0.25f, 44, 100),
                explicitController.distance(0.25f, 44, 100, 1.0f));
    }

    @Test
    public void lowestSpeedRemainsMonotonic() {
        CrownScrollController controller = new CrownScrollController();
        int total = 0;

        for (int i = 0; i < 100; i++) {
            total += controller.distance(1.0f, 84, 5);
        }

        assertEquals(-21, total);
    }

    @Test
    public void boundsQueuedDistanceWithoutDiscardingDirection() {
        assertEquals(30, CrownScrollController.coalesceBounded(25, 20, 30));
        assertEquals(-30, CrownScrollController.coalesceBounded(-25, -20, 30));
        assertEquals(5, CrownScrollController.coalesceBounded(-5, 10, 30));
        assertEquals(Integer.MAX_VALUE, CrownScrollController.coalesceBounded(
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(-Integer.MAX_VALUE, CrownScrollController.coalesceBounded(
                -Integer.MAX_VALUE, -Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
