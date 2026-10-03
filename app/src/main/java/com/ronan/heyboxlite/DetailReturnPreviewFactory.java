package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Bitmap;
import android.widget.ImageView;

/** Builds the native detail return layer without adding more UI state to MainActivity. */
final class DetailReturnPreviewFactory {
    interface SnapshotProvider {
        Bitmap get(String key);
    }

    private DetailReturnPreviewFactory() {
    }

    static ImageView create(Activity activity, ThemeTokens tokens, String targetKey,
                            SnapshotProvider pageSnapshots,
                            SnapshotProvider fullScreenSnapshots) {
        ImageView snapshot = new ImageView(activity);
        snapshot.setBackgroundColor(tokens == null ? 0xff202124 : tokens.panel);
        snapshot.setScaleType(ImageView.ScaleType.FIT_XY);
        Bitmap bitmap = pageSnapshots.get(targetKey);
        if (bitmap == null || bitmap.isRecycled()) bitmap = fullScreenSnapshots.get(targetKey);
        if (bitmap != null && !bitmap.isRecycled()) snapshot.setImageBitmap(bitmap);
        return snapshot;
    }
}
