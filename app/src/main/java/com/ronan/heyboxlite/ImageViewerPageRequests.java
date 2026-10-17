package com.ronan.heyboxlite;

/** Bitmap work retained for one resident viewer page. */
final class ImageViewerPageRequests {
    private int generation;
    private ImageLoadRequest preview;
    private ImageLoadRequest original;

    int generation() { return generation; }
    boolean isCurrent(int expected) { return generation == expected; }
    boolean originalPending() { return original != null; }

    void setPreview(ImageLoadRequest request) { preview = request; }
    void setOriginal(ImageLoadRequest request) { original = request; }
    void finishPreview() { preview = null; }
    void finishOriginal() { original = null; }

    void cancel() {
        generation++;
        if (preview != null) preview.cancel();
        if (original != null) original.cancel();
        preview = null;
        original = null;
    }
}
