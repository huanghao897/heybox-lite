package com.ronan.heyboxlite;

final class ImageZoomPolicy {
    static final float MIN_DOUBLE_TAP_ZOOM = 2.35f;
    static final float MAX_ZOOM = 12.0f;
    private static final float ROUND_PINCH_GAIN = 2.6f;

    private ImageZoomPolicy() {}

    static int nextLevel(int currentLevel) {
        if (currentLevel == 1) return 2;
        if (currentLevel == 2) return 0;
        return 1;
    }

    static float targetScale(int level, float widthFillZoom) {
        if (level == 1) {
            return Math.min(MAX_ZOOM,
                    Math.max(MIN_DOUBLE_TAP_ZOOM, widthFillZoom));
        }
        if (level == 2) {
            return Math.min(MAX_ZOOM,
                    Math.max(4.0f, widthFillZoom * 1.55f));
        }
        return 1.0f;
    }

    static float pinchFactor(float detectorFactor, boolean roundDisplay) {
        if (!roundDisplay) return detectorFactor;
        float adjusted = 1.0f + (detectorFactor - 1.0f) * ROUND_PINCH_GAIN;
        return Math.max(0.72f, Math.min(1.38f, adjusted));
    }
}
