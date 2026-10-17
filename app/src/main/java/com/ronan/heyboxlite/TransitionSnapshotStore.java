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

    boolean bindPreview(ImageView preview, String key) {
        Bitmap bitmap = get(key);
        if (bitmap == null || bitmap.isRecycled()) return false;
        preview.setImageBitmap(bitmap);
        registerOverlay(preview, bitmap);
        preview.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                // Retained native pages can mount again after their old preview was released.
                Bitmap current = get(key);
                if (current == null || current.isRecycled()) return;
                preview.setImageBitmap(current);
                registerOverlay(preview, current);
            }

            @Override public void onViewDetachedFromWindow(View view) {
                preview.setImageDrawable(null);
                releaseOverlay(preview);
            }
        });
        return true;
    }

    void registerOverlay(ImageView overlay, Bitmap bitmap) {
        if (overlay == null || bitmap == null || bitmap.isRecycled()) return;
        Bitmap previous = overlays.put(overlay, bitmap);
        releaseIfUnused(previous);
    }

    void releaseOverlay(ImageView overlay) {
        if (overlay == null) return;
        Bitmap bitmap = overlays.remove(overlay);
        if (bitmap == null) return;
        releaseIfUnused(bitmap);
    }

    void clear() {
        Set<Bitmap> removed = new HashSet<>(snapshots.values());
        snapshots.clear();
        for (Bitmap bitmap : removed) releaseIfUnused(bitmap);
    }

    void releaseAll() {
        Set<Bitmap> removed = new HashSet<>(snapshots.values());
        removed.addAll(overlays.values());
        snapshots.clear();
        overlays.clear();
        for (Bitmap bitmap : removed) releaseIfUnused(bitmap);
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
        if (bitmap == null || overlays.containsValue(bitmap)) return;
        for (Bitmap stored : snapshots.values()) {
            if (stored == bitmap) return;
        }
        if (!bitmap.isRecycled()) bitmap.recycle();
    }
}
