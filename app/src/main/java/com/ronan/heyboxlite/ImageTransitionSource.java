package com.ronan.heyboxlite;

import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/** Calculates the drawable bounds used by the preview-to-viewer transition. */
final class ImageTransitionSource {
    private ImageTransitionSource() {
    }

    static Rect visibleBoundsOnScreen(ImageView source) {
        Rect fallback = new Rect();
        if (source == null || !source.getGlobalVisibleRect(fallback)
                || fallback.width() <= 0 || fallback.height() <= 0) {
            return fallback;
        }

        Drawable drawable = source.getDrawable();
        if (drawable == null || drawable.getIntrinsicWidth() <= 0
                || drawable.getIntrinsicHeight() <= 0) {
            return fallback;
        }

        Rect drawableBounds = drawable.getBounds();
        RectF local = new RectF(
                drawableBounds.left,
                drawableBounds.top,
                drawableBounds.right,
                drawableBounds.bottom);
        if (local.width() <= 0 || local.height() <= 0) {
            local.set(0.0f, 0.0f, drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight());
        }
        source.getImageMatrix().mapRect(local);

        int[] location = new int[2];
        source.getLocationOnScreen(location);
        local.offset(location[0], location[1]);

        RectF visible = new RectF(fallback);
        if (!local.intersect(visible) || local.width() <= 1.0f || local.height() <= 1.0f) {
            return fallback;
        }
        return new Rect(
                Math.round(local.left),
                Math.round(local.top),
                Math.round(local.right),
                Math.round(local.bottom));
    }
}
