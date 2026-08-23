package com.ronan.heyboxlite;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

final class ImageViewerLauncher {
    private ImageViewerLauncher() {}

    static void open(Activity activity, ImageView source, String[] urls, int index) {
        int currentIndex = Math.max(0, Math.min(urls.length - 1, index));
        String current = urls.length == 0 ? "" : urls[currentIndex];
        Drawable drawable = source.getDrawable();
        Bitmap preview = drawable instanceof BitmapDrawable
                ? ((BitmapDrawable) drawable).getBitmap() : null;
        long previewId = ImageViewerActivity.preparePreview(current, preview, source);

        Intent intent = new Intent(activity, ImageViewerActivity.class);
        intent.putExtra(ImageViewerActivity.EXTRA_URL, current);
        intent.putExtra(ImageViewerActivity.EXTRA_PREVIEW_ID, previewId);
        if (urls.length > 1) {
            intent.putExtra(ImageViewerActivity.EXTRA_URLS, urls);
            intent.putExtra(ImageViewerActivity.EXTRA_INDEX, currentIndex);
        }
        Rect bounds = ImageTransitionSource.visibleBoundsOnScreen(source);
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_X, bounds.centerX());
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_Y, bounds.centerY());
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_WIDTH, bounds.width());
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_HEIGHT, bounds.height());
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }
}
