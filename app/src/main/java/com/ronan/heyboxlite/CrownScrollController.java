package com.ronan.heyboxlite;

final class CrownScrollController {
    static final int MIN_SPEED_PERCENT = 5;
    static final int MAX_SPEED_PERCENT = 200;
    static final int DEFAULT_SPEED_PERCENT = 80;

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

        float normalizedAxis = Math.max(-1.0f, Math.min(1.0f, axis));
        float scaled = remainder - normalizedAxis * baseStepPixels
                * responseFactor(speedPercent);
        int pixels = Math.round(scaled);
        remainder = scaled - pixels;
        return pixels;
    }

    int frameLimit(int baseStepPixels, int speedPercent) {
        if (baseStepPixels <= 0) return 1;
        return Math.max(1, Math.round(baseStepPixels * responseFactor(speedPercent)));
    }

    void reset() {
        remainder = 0.0f;
        lastDirection = 0;
    }

    static int clampSpeed(int value) {
        return Math.max(MIN_SPEED_PERCENT, Math.min(MAX_SPEED_PERCENT, value));
    }

    static int coalesceBounded(int current, int addition, int limit) {
        int bound = Math.max(1, limit);
        long result = (long) current + addition;
        return (int) Math.max(-bound, Math.min(bound, result));
    }

    static boolean isStableListWindow(int adapterCount, int listCount,
                                      int firstVisible, int childCount) {
        return adapterCount > 0 && adapterCount == listCount
                && childCount > 0 && firstVisible >= 0
                && firstVisible < adapterCount
                && firstVisible + childCount <= adapterCount;
    }

    private static float responseFactor(int speedPercent) {
        float ratio = clampSpeed(speedPercent) / 100.0f;
        return ratio <= 1.0f ? ratio * ratio : ratio;
    }
}
