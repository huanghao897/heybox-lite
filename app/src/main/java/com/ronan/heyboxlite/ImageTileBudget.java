package com.ronan.heyboxlite;

/** Process-wide leases include decoding, pending main delivery and displayed tiles. */
final class ImageTileBudget {
    static final ImageTileBudget SHARED = new ImageTileBudget(
            ImageDecodePolicy.tileBytes(Runtime.getRuntime().maxMemory()));
    final long limit;
    private long used;
    private long peak;

    ImageTileBudget(long limit) { this.limit = Math.max(4, limit); }

    synchronized Lease reserve(long bytes) {
        if (bytes <= 0 || bytes > limit - used) return null;
        used += bytes;
        peak = Math.max(peak, used);
        return new Lease(this, bytes);
    }

    synchronized long used() { return used; }
    synchronized long peak() { return peak; }

    static final class Lease {
        private final ImageTileBudget owner;
        private long bytes;
        Lease(ImageTileBudget owner, long bytes) { this.owner = owner; this.bytes = bytes; }
        void close() {
            synchronized (owner) { owner.used -= bytes; bytes = 0; }
        }
    }
}
