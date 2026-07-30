package com.ronan.heyboxlite;

final class CrownScrollController {
    static final int MIN_SPEED_PERCENT = 50;
    static final int MAX_SPEED_PERCENT = 200;
    static final int DEFAULT_SPEED_PERCENT = 100;

    private float remainder;
    private int lastDirection;

    int distance(float axis, int baseStepPixels, int speedPercent) {
        if (axis == 0.0f || Float.isNaN(axis) || Float.isInfinite(axis)
                || baseStepPixels <= 0) {
            return 0;
        }

        int direction = axis > 0.0f ? 1 : -1;
        if (lastDirection != 0 && direction != lastDirection) {
            remainder = 0.0f;
        }
        lastDirection = direction;

        float scaled = remainder - axis * baseStepPixels
                * clampSpeed(speedPercent) / 100.0f;
        int pixels = Math.round(scaled);
        remainder = scaled - pixels;
        return pixels;
    }

    void reset() {
        remainder = 0.0f;
        lastDirection = 0;
    }

    static int clampSpeed(int value) {
        return Math.max(MIN_SPEED_PERCENT, Math.min(MAX_SPEED_PERCENT, value));
    }
}
