package com.ronan.heyboxlite;

final class ImageDismissPolicy {
    static final float TOUCH_SLOP_MULTIPLIER = 4.0f;
    private static final float DISMISS_DISTANCE_RATIO = 0.12f;
    private static final float MAX_SCALE_REDUCTION = 0.40f;

    private ImageDismissPolicy() {
    }

    static float effectiveDistance(float rawDistance, float touchThreshold) {
        if (rawDistance > touchThreshold) return rawDistance - touchThreshold;
        if (rawDistance < -touchThreshold) return rawDistance + touchThreshold;
        return 0.0f;
    }

    static float progress(float distance, int height) {
        return Math.min(1.0f, Math.abs(distance) / Math.max(1.0f, height));
    }

    static float scale(float progress) {
        return 1.0f - Math.min(MAX_SCALE_REDUCTION, Math.max(0.0f, progress));
    }

    static boolean shouldDismiss(float distance, int height) {
        return Math.abs(distance) > Math.max(1, height) * DISMISS_DISTANCE_RATIO;
    }
}
