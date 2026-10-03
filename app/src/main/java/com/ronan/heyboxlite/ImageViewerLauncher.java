package com.ronan.heyboxlite;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

final class ImageViewerLauncher {
    private ImageViewerLauncher() {}

    static void open(Activity activity, ImageView source, String[] urls, int index) {
        open(activity, source, urls, index, false);
    }

    static void openOriginal(Activity activity, ImageView source, String url) {
        open(activity, source, new String[]{url}, 0, true);
    }

    private static void open(Activity activity, ImageView source, String[] urls,
                             int index, boolean loadOriginalImmediately) {
        int currentIndex = Math.max(0, Math.min(urls.length - 1, index));
        String current = urls.length == 0 ? "" : urls[currentIndex];
        Drawable drawable = source == null ? null : source.getDrawable();
        Bitmap preview = previewBitmap(source, drawable);
        long previewId = ImageViewerActivity.preparePreview(current, preview, source);

        Intent intent = new Intent(activity, ImageViewerActivity.class);
        intent.putExtra(ImageViewerActivity.EXTRA_URL, current);
        intent.putExtra(ImageViewerActivity.EXTRA_PREVIEW_ID, previewId);
        intent.putExtra(ImageViewerActivity.EXTRA_LOAD_ORIGINAL_IMMEDIATELY,
                loadOriginalImmediately);
        if (urls.length > 1) {
            intent.putExtra(ImageViewerActivity.EXTRA_URLS, urls);
            intent.putExtra(ImageViewerActivity.EXTRA_INDEX, currentIndex);
        }
        Rect bounds = ImageTransitionSource.visibleBoundsOnScreen(source);
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            int height = activity.getResources().getDisplayMetrics().heightPixels;
            bounds.set(0, 0, Math.max(1, width), Math.max(1, height));
        }
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_X, bounds.centerX());
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_Y, bounds.centerY());
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_WIDTH, bounds.width());
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_HEIGHT, bounds.height());
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }

    private static Bitmap previewBitmap(ImageView source, Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        if (source == null || drawable == null) return null;
        Rect bounds = ImageTransitionSource.visibleBoundsOnScreen(source);
        int width = bounds.width() > 0 ? bounds.width() : source.getWidth();
        int height = bounds.height() > 0 ? bounds.height() : source.getHeight();
        if (width <= 0 || height <= 0) return null;
        int longest = Math.max(width, height);
        if (longest > 768) {
            float scale = 768.0f / longest;
            width = Math.max(1, Math.round(width * scale));
            height = Math.max(1, Math.round(height * scale));
        }
        Bitmap preview = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Rect originalBounds = new Rect(drawable.getBounds());
        try {
            drawable.setBounds(0, 0, width, height);
            drawable.draw(new Canvas(preview));
            return preview;
        } finally {
            drawable.setBounds(originalBounds);
        }
    }
}
