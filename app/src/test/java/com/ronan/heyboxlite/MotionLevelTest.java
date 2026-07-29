package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class MotionLevelTest {
    @Test
    public void clampKeepsSupportedLevels() {
        assertEquals(MotionLevel.OFF, MotionLevel.clamp(MotionLevel.OFF));
        assertEquals(MotionLevel.REDUCED, MotionLevel.clamp(MotionLevel.REDUCED));
        assertEquals(MotionLevel.FULL, MotionLevel.clamp(MotionLevel.FULL));
    }

    @Test
    public void clampRestrictsOutOfRangeValues() {
        assertEquals(MotionLevel.OFF, MotionLevel.clamp(-1));
        assertEquals(MotionLevel.FULL, MotionLevel.clamp(3));
    }
}
