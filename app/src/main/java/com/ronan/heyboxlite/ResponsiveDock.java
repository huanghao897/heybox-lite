package com.ronan.heyboxlite;

final class ResponsiveDock {
    private static final float WIDTH_RATIO = 0.28f;
    private static final float HEIGHT_TO_WIDTH_RATIO = 0.34f;

    private ResponsiveDock() {}

    static Dimensions fromScreen(int widthPixels, int heightPixels) {
        return fromScreen(widthPixels, heightPixels, false);
    }

    static Dimensions fromScreen(int widthPixels, int heightPixels,
                                 boolean roundScreen) {
        int shortEdge = Math.max(1, Math.min(widthPixels, heightPixels));
        float widthRatio = roundScreen ? 0.27f : WIDTH_RATIO;
        int width = Math.max(roundScreen ? 68 : 72,
                Math.round(shortEdge * widthRatio));
        width = Math.min(width, Math.max(1, shortEdge - 16));
        float heightRatio = roundScreen ? 0.40f : HEIGHT_TO_WIDTH_RATIO;
        int height = Math.min(Math.max(roundScreen ? 30 : 28,
                        Math.round(width * heightRatio)),
                Math.max(1, shortEdge - 4));
        int marginBottom = Math.max(3, Math.round(shortEdge
                * (roundScreen ? 0.075f : 0.01f)));
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
