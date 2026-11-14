package com.ronan.heyboxlite;

/** Pure geometry and worst-case ARGB allocation planning for viewport region decoding. */
final class ImageDecodePolicy {
    private ImageDecodePolicy() {}

    static long tileBytes(long heapBytes) {
        return Math.min(12L * 1024 * 1024, ImageMemoryBudget.decodePixels(heapBytes) * 4L);
    }

    static int sample(int width, int sourceTileHeight, int targetWidth, long perTileBytes) {
        int sample = 1;
        while (sample < (1 << 29)
                && ceil(width, sample * 2) >= Math.max(1, targetWidth)) sample *= 2;
        while (sample < (1 << 30)
                && bytes(width, sourceTileHeight, sample) > perTileBytes) sample *= 2;
        return sample;
    }

    static long bytes(int width, int height, int sample) {
        return (long) ceil(width, sample) * ceil(height, sample) * 4L;
    }

    static int ceil(int value, int divisor) {
        return (int) (((long) value + divisor - 1) / divisor);
    }

    static int sourceTileHeight(int width, int height, int viewportWidth, int viewportHeight) {
        long rows = (long) width * Math.max(1, viewportHeight) / Math.max(1, viewportWidth) / 2;
        return (int) Math.max(1, Math.min(height, rows));
    }
}
