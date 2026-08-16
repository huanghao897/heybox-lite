package com.ronan.heyboxlite;

final class ResponsiveDock {
    private static final float RECTANGULAR_WIDTH_RATIO = 0.28f;
    private static final float ROUND_WIDTH_RATIO = 0.27f;
    private static final float HEIGHT_TO_WIDTH_RATIO = 0.34f;
    private static final float RECTANGULAR_BOTTOM_MARGIN_RATIO = 0.01f;
    private static final float ROUND_BOTTOM_MARGIN_RATIO = 0.022f;

    private ResponsiveDock() {}

    static Dimensions fromScreen(int widthPixels, int heightPixels) {
        return fromScreen(widthPixels, heightPixels, false);
    }

    static Dimensions fromScreen(int widthPixels, int heightPixels,
                                 boolean roundScreen) {
        int shortEdge = Math.max(1, Math.min(widthPixels, heightPixels));
        float widthRatio = roundScreen ? ROUND_WIDTH_RATIO : RECTANGULAR_WIDTH_RATIO;
        int width = scaled(shortEdge, widthRatio);
        int height = scaled(width, HEIGHT_TO_WIDTH_RATIO);
        int marginBottom = scaled(shortEdge, roundScreen
                ? ROUND_BOTTOM_MARGIN_RATIO : RECTANGULAR_BOTTOM_MARGIN_RATIO);
        int paddingHorizontal = Math.max(1, Math.round(height * 0.08f));
        int paddingVertical = Math.max(1, Math.round(height * 0.05f));
        int itemMargin = Math.max(1, Math.round(height * 0.05f));
        int iconSize = Math.min(scaled(height, 0.52f), Math.max(1, height - 2));
        return new Dimensions(width, height, marginBottom, paddingHorizontal,
                paddingVertical, itemMargin, iconSize);
    }

    private static int scaled(int extent, float ratio) {
        return Math.max(1, Math.round(extent * ratio));
    }

    static final class Dimensions {
        final int width;
        final int height;
        final int marginBottom;
        final int paddingHorizontal;
        final int paddingVertical;
        final int itemMargin;
        final int iconSize;

        Dimensions(int width, int height, int marginBottom, int paddingHorizontal,
                   int paddingVertical, int itemMargin, int iconSize) {
            this.width = width;
            this.height = height;
            this.marginBottom = marginBottom;
            this.paddingHorizontal = paddingHorizontal;
            this.paddingVertical = paddingVertical;
            this.itemMargin = itemMargin;
            this.iconSize = iconSize;
        }
    }
}
