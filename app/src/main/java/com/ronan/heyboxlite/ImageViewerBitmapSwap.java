package com.ronan.heyboxlite;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;

/** Upgrade a preview without resetting the user's zoom or pan position. */
final class ImageViewerBitmapSwap {
    private ImageViewerBitmapSwap() {}

    static void replace(ZoomImageView image, Matrix matrix, Bitmap bitmap) {
        Drawable previous = image.getDrawable();
        image.cancelZoomAnimation();
        if (image.isZoomed() && previous != null) {
            int previousWidth = previous.getIntrinsicWidth();
            image.setImageBitmap(bitmap);
            float ratio = previousWidth / (float) image.getDrawable().getIntrinsicWidth();
            matrix.preScale(ratio, ratio);
            image.setImageMatrix(matrix);
        } else {
            image.setImageBitmap(bitmap);
            image.fitImage();
        }
    }
}
