package com.ronan.heyboxlite;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.ImageView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Owns transition bitmaps and makes their lifetime explicit. */
final class TransitionSnapshotStore {
    private final Map<String, Bitmap> snapshots = new HashMap<>();
    private final Set<Bitmap> leased = new HashSet<>();
    private final Map<ImageView, Bitmap> overlays = new WeakHashMap<>();

    void capture(String key, int maxCount, View view, int background, LocalCache log) {
        if (key == null || key.isEmpty() || view == null) return;
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 1 || height <= 1) return;
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(background);
            view.draw(canvas);
            Bitmap previous = snapshots.put(key, bitmap);
            releaseIfUnused(previous);
            trim(maxCount);
        } catch (RuntimeException | OutOfMemoryError error) {
            if (log != null) {
                log.log("transition snapshot skipped error="
                        + error.getClass().getSimpleName());
            }
        }
    }

    Bitmap get(String key) {
        return key == null || key.isEmpty() ? null : snapshots.get(key);
    }

    void registerOverlay(ImageView overlay, Bitmap bitmap) {
        if (overlay == null || bitmap == null || bitmap.isRecycled()) return;
        overlays.put(overlay, bitmap);
        leased.add(bitmap);
    }

    void releaseOverlay(ImageView overlay) {
        if (overlay == null) return;
        Bitmap bitmap = overlays.remove(overlay);
        if (bitmap == null) return;
        leased.remove(bitmap);
        releaseIfUnused(bitmap);
    }

    void clear() {
        Set<Bitmap> removed = new HashSet<>(snapshots.values());
        snapshots.clear();
        for (Bitmap bitmap : removed) releaseIfUnused(bitmap);
    }

    void releaseAll() {
        clear();
        for (Bitmap bitmap : new HashSet<>(leased)) {
            leased.remove(bitmap);
            releaseIfUnused(bitmap);
        }
        overlays.clear();
    }

    private void trim(int maxCount) {
        while (snapshots.size() > Math.max(0, maxCount)) {
            Iterator<Map.Entry<String, Bitmap>> iterator = snapshots.entrySet().iterator();
            if (!iterator.hasNext()) return;
            Bitmap bitmap = iterator.next().getValue();
            iterator.remove();
            releaseIfUnused(bitmap);
        }
    }

    private void releaseIfUnused(Bitmap bitmap) {
        if (bitmap == null || leased.contains(bitmap)) return;
        for (Bitmap stored : snapshots.values()) {
            if (stored == bitmap) return;
        }
        if (!bitmap.isRecycled()) bitmap.recycle();
    }
}
