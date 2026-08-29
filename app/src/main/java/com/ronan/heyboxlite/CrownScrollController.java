package com.ronan.heyboxlite;

final class CrownScrollController {
    static final int MIN_SPEED_PERCENT = 5;
    static final int MAX_SPEED_PERCENT = 200;
    static final int DEFAULT_SPEED_PERCENT = 80;
    static final float MAX_AXIS_VALUE = 4.0f;

    private float remainder;

    int distance(float axis, int baseStepPixels, int speedPercent) {
        if (axis == 0.0f || Float.isNaN(axis) || Float.isInfinite(axis)
                || baseStepPixels <= 0) {
            return 0;
        }

        float boundedAxis = Math.max(-MAX_AXIS_VALUE, Math.min(MAX_AXIS_VALUE, axis));
        float scaled = remainder - boundedAxis * baseStepPixels
                * responseFactor(speedPercent);
        int pixels = Math.round(scaled);
        remainder = scaled - pixels;
        return pixels;
    }

    void reset() {
        remainder = 0.0f;
    }

    static int clampSpeed(int value) {
        return Math.max(MIN_SPEED_PERCENT, Math.min(MAX_SPEED_PERCENT, value));
    }

    private static float responseFactor(int speedPercent) {
        return clampSpeed(speedPercent) / 100.0f;
    }
}
