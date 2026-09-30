package com.ronan.heyboxlite;

final class ImageMemoryBudget {
    private static final long LOW_MEMORY_HEAP = 128L * 1024L * 1024L;

    private ImageMemoryBudget() {}

    static int bitmapPixels(long heapBytes) {
        // ARGB pixels use four bytes; reserve at most one eighth of the heap per decoded image.
        return (int) Math.max(256 * 1024L, Math.min(5_000_000L, heapBytes / 32L));
    }

    /**
     * Low-memory watches need a smaller per-image ceiling than the general cache budget.
     * Several detail images can remain attached to the view tree at the same time, so
     * reserving one eighth of the heap for each decoded image is not safe on 128 MB devices.
     */
    static int decodePixels(long heapBytes) {
        if (heapBytes <= LOW_MEMORY_HEAP) {
            return (int) Math.max(256 * 1024L,
                    Math.min(1_000_000L, Math.max(0L, heapBytes / 128L)));
        }
        return bitmapPixels(heapBytes);
    }

    static int decodeTargetPx(long heapBytes, int requested) {
        int value = requested <= 0 ? 720 : requested;
        int ceiling = heapBytes <= LOW_MEMORY_HEAP ? 640 : 2400;
        return Math.max(96, Math.min(value, ceiling));
    }
}
