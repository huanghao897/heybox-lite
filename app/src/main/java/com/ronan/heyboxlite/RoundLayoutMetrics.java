package com.ronan.heyboxlite;

final class RoundLayoutMetrics {
    static final float PAGE_HORIZONTAL_RATIO = 0.085f;
    static final float HEADER_HORIZONTAL_RATIO = 0.12f;
    static final float SEARCH_HORIZONTAL_RATIO = 0.05f;
    static final float PAGE_TOP_RATIO = 0.11f;
    static final float SUBPAGE_TOP_RATIO = 0.09f;

    private RoundLayoutMetrics() {
    }

    static int componentInset(int extentPx, float targetRatio, int minimumPx) {
        int safeExtent = Math.max(0, extentPx);
        int targetPx = Math.round(safeExtent * Math.max(0.0f, targetRatio));
        return Math.max(Math.max(0, minimumPx), targetPx);
    }

    static int headerInnerInset(int widthPx) {
        return Math.max(0, Math.round(widthPx
                * (HEADER_HORIZONTAL_RATIO - PAGE_HORIZONTAL_RATIO)));
    }
}
