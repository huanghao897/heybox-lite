package com.ronan.heyboxlite;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

/** The View owns callbacks; workers only retain a weak binding, never a View or Activity. */
final class ImageViewBinding {
    private static final int TAG = 0x7f0b0d02;
    final String key;
    private final WeakReference<ImageView> view;
    private final boolean animate;
    private ImageLoader.IntoBitmapCallback bitmapCallback;
    private ImageLoader.IntoCallback gifCallback;
    private ImageLoadRequest request;
    private boolean cancelled;

    private ImageViewBinding(ImageView view, String key, boolean animate) {
        this.view = new WeakReference<>(view);
        this.key = key;
        this.animate = animate;
    }

    static ImageViewBinding create(ImageView view, String key, boolean animate) {
        cancel(view);
        ImageViewBinding binding = new ImageViewBinding(view, key, animate);
        view.setTag(TAG, binding);
        view.setTag(key);
        return binding;
    }

    static ImageViewBinding current(ImageView view) {
        Object value = view.getTag(TAG);
        return value instanceof ImageViewBinding ? (ImageViewBinding) value : null;
    }

    void startBitmap(String url, String fallback, int target, ImageLoader.IntoBitmapCallback callback) {
        bitmapCallback = callback;
        WeakReference<ImageViewBinding> owner = new WeakReference<>(this);
        request = ImageLoader.requestInto(url, fallback, target, bitmap -> {
            ImageViewBinding binding = owner.get();
            if (binding != null) binding.bitmap(bitmap);
        });
    }

    void startGif(String url, ImageLoader.IntoCallback callback) {
        gifCallback = callback;
        WeakReference<ImageViewBinding> owner = new WeakReference<>(this);
        request = ImageLoader.requestGif(url, drawable -> {
            ImageViewBinding binding = owner.get();
            if (binding != null) binding.gif(drawable);
        });
    }

    private ImageView target() {
        ImageView target = view.get();
        return !cancelled && target != null && current(target) == this
                && key.equals(target.getTag()) ? target : null;
    }

    void bitmap(Bitmap bitmap) {
        ImageView target = target();
        if (target == null) return;
        request = null;
        if (bitmap != null) {
            if (animate) ImageRevealAnimation.show(target, bitmap);
            else {
                ImageRevealAnimation.clear(target);
                target.setAlpha(1f);
                target.setScaleX(1f);
                target.setScaleY(1f);
                target.setImageBitmap(bitmap);
            }
        }
        ImageLoader.IntoBitmapCallback callback = bitmapCallback;
        bitmapCallback = null;
        if (callback != null) callback.onComplete(bitmap != null, bitmap);
    }

    private void gif(Drawable drawable) {
        ImageView target = target();
        if (target == null) return;
        request = null;
        if (drawable != null) GifSupport.apply(target, drawable);
        ImageLoader.IntoCallback callback = gifCallback;
        gifCallback = null;
        if (callback != null) callback.onComplete(drawable != null);
    }

    static void cancel(ImageView view) {
        ImageViewBinding binding = current(view);
        if (binding == null) return;
        binding.cancelled = true;
        binding.bitmapCallback = null;
        binding.gifCallback = null;
        if (binding.request != null) binding.request.cancel();
        binding.request = null;
        view.setTag(TAG, null);
    }
}
