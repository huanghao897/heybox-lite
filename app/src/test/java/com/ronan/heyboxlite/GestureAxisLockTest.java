package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GestureAxisLockTest {
    @Test public void locksHorizontalGestureAgainstLaterVerticalMotion() {
        int axis = GestureAxisLock.resolve(GestureAxisLock.NONE,
                24.0f, 4.0f, 8.0f, 8.0f, 1.15f, false);

        assertEquals(GestureAxisLock.HORIZONTAL, axis);
        assertEquals(GestureAxisLock.HORIZONTAL, GestureAxisLock.resolve(
                axis, 3.0f, 40.0f, 8.0f, 8.0f, 1.15f, false));
    }

    @Test public void locksVerticalGestureAgainstLaterHorizontalMotion() {
        int axis = GestureAxisLock.resolve(GestureAxisLock.NONE,
                4.0f, 24.0f, 8.0f, 8.0f, 1.15f, false);

        assertEquals(GestureAxisLock.VERTICAL, axis);
        assertEquals(GestureAxisLock.VERTICAL, GestureAxisLock.resolve(
                axis, 40.0f, 3.0f, 8.0f, 8.0f, 1.15f, false));
    }

    @Test public void waitsForADecisiveAxisAndCancelsOnMultiTouch() {
        assertEquals(GestureAxisLock.NONE, GestureAxisLock.resolve(
                GestureAxisLock.NONE, 10.0f, 9.0f, 8.0f, 8.0f, 1.15f, false));
        assertEquals(GestureAxisLock.MULTI_TOUCH, GestureAxisLock.resolve(
                GestureAxisLock.NONE, 0.0f, 0.0f, 8.0f, 8.0f, 1.15f, true));
        assertEquals(GestureAxisLock.MULTI_TOUCH, GestureAxisLock.resolve(
                GestureAxisLock.HORIZONTAL, 20.0f, 0.0f, 8.0f, 8.0f, 1.15f, true));
    }
}
