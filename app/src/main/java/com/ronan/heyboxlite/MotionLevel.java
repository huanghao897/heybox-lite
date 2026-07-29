package com.ronan.heyboxlite;

final class MotionLevel {
    static final int OFF = 0;
    static final int REDUCED = 1;
    static final int FULL = 2;

    private MotionLevel() {}

    static int clamp(int value) {
        return Math.max(OFF, Math.min(FULL, value));
    }
}
