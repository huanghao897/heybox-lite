package com.ronan.heyboxlite;

final class ImageMemoryBudget {
    private ImageMemoryBudget() {}

    static int bitmapPixels(long heapBytes) {
        // ARGB pixels use four bytes; reserve at most one eighth of the heap per decoded image.
        return (int) Math.max(256 * 1024L, Math.min(5_000_000L, heapBytes / 32L));
    }
}
