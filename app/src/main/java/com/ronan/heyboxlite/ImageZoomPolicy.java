package com.ronan.heyboxlite;

final class ImageZoomPolicy {
    static final float MIN_DOUBLE_TAP_ZOOM = 2.35f;
    static final float MAX_ZOOM = 12.0f;

    private ImageZoomPolicy() {}

    static int nextLevel(int currentLevel, float currentScale) {
        if (currentLevel <= 0 || currentScale <= 1.05f) return 1;
        if (currentLevel == 1) return 2;
        return 0;
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
}
