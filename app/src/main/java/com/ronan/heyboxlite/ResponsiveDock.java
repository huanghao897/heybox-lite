package com.ronan.heyboxlite;

final class ResponsiveDock {
    private static final float WIDTH_RATIO = 0.28f;
    private static final float HEIGHT_TO_WIDTH_RATIO = 0.34f;

    private ResponsiveDock() {}

    static Dimensions fromScreen(int widthPixels, int heightPixels) {
        int shortEdge = Math.max(1, Math.min(widthPixels, heightPixels));
        int width = Math.max(72, Math.round(shortEdge * WIDTH_RATIO));
        width = Math.min(width, Math.max(1, shortEdge - 16));
        int height = Math.min(Math.max(28, Math.round(width * HEIGHT_TO_WIDTH_RATIO)),
                Math.max(1, shortEdge - 4));
        int marginBottom = Math.max(3, Math.round(shortEdge * 0.01f));
        int paddingHorizontal = Math.max(2, Math.round(height * 0.08f));
        int paddingVertical = Math.max(1, Math.round(height * 0.05f));
        int itemMargin = Math.max(1, Math.round(height * 0.05f));
        int iconSize = Math.min(Math.max(14, Math.round(height * 0.52f)),
                Math.max(1, height - 4));
        return new Dimensions(width, height, marginBottom, paddingHorizontal,
                paddingVertical, itemMargin, iconSize);
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
