package com.ronan.heyboxlite;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import java.util.WeakHashMap;

/** Main-thread presentation only; downloading and caching remain in ImageLoader. */
final class ImageRevealAnimation {
    private static final WeakHashMap<ImageView, ValueAnimator> ANIMATORS = new WeakHashMap<>();

    private ImageRevealAnimation() {}

    static void show(ImageView view, Bitmap bitmap) {
        view.animate().cancel();
        clear(view);
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setImageBitmap(bitmap);
        Object marker = view.getTag();
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            view.post(() -> {
                if (marker == view.getTag()) start(view);
            });
            return;
        }
        start(view);
    }

    static void clear(ImageView view) {
        ValueAnimator animator = ANIMATORS.remove(view);
        if (animator != null) animator.cancel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            view.setClipBounds(null);
        }
    }

    private static void start(ImageView view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) return;
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) {
            view.setClipBounds(null);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofInt(0, height);
        ANIMATORS.put(view, animator);
        animator.setDuration(260);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            int bottom = (Integer) animation.getAnimatedValue();
            view.setClipBounds(new Rect(0, 0, width, bottom));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                release(view, animation);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                release(view, animation);
            }
        });
        view.setClipBounds(new Rect(0, 0, width, 0));
        animator.start();
    }

    private static void release(ImageView view, Animator animation) {
        if (ANIMATORS.get(view) == animation) {
            ANIMATORS.remove(view);
            view.setClipBounds(null);
        }
    }
}
