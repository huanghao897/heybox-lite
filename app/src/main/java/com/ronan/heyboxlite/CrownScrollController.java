package com.ronan.heyboxlite;

final class CrownScrollController {
    static final int MIN_SPEED_PERCENT = 5;
    static final int MAX_SPEED_PERCENT = 200;
    static final int DEFAULT_SPEED_PERCENT = 80;
    static final float MAX_AXIS_VALUE = 1.0f;

    private float remainder;
    private int lastDirection;

    int distance(float axis, int baseStepPixels, int speedPercent) {
        if (axis == 0.0f || Float.isNaN(axis) || Float.isInfinite(axis)
                || baseStepPixels <= 0) {
            return 0;
        }

        int direction = axis > 0.0f ? 1 : -1;
        if (this.lastDirection != 0 && direction != this.lastDirection) {
            this.remainder = 0.0f;
        }
        this.lastDirection = direction;

        float boundedAxis = Math.max(-MAX_AXIS_VALUE, Math.min(MAX_AXIS_VALUE, axis));
        float scaled = remainder - boundedAxis * baseStepPixels
                * responseFactor(speedPercent);
        int pixels = Math.round(scaled);
        remainder = scaled - pixels;
        return pixels;
    }

    void reset() {
        remainder = 0.0f;
        lastDirection = 0;
    }

    int frameLimit(int baseStepPixels, int speedPercent) {
        if (baseStepPixels <= 0) return 1;
        return Math.max(1, Math.round(baseStepPixels
                * responseFactor(speedPercent) * 1.25f));
    }

    static int clampSpeed(int value) {
        return Math.max(MIN_SPEED_PERCENT, Math.min(MAX_SPEED_PERCENT, value));
    }

    static int coalesceBounded(int current, int addition, int limit) {
        int bound = Math.max(1, limit);
        long result = (long) current + addition;
        return (int) Math.max(-bound, Math.min(bound, result));
    }

    private static float responseFactor(int speedPercent) {
        float ratio = clampSpeed(speedPercent) / 100.0f;
        return ratio <= 1.0f ? ratio * ratio : 1.0f + (ratio - 1.0f) * 0.65f;
    }
}
