package com.ronan.heyboxlite;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/** One transform drives both the image and its crop; aspect ratio never changes. */
final class ImageViewerTransition {
    private ValueAnimator animation;

    void enter(View image, View root, RectF display, RectF source, long duration,
               TimeInterpolator interpolator, Runnable complete) {
        animate(image, crop(image, root, display, source), Frame.full(image), duration,
                interpolator, complete);
    }

    void exit(View image, View root, RectF display, RectF target, long duration,
              TimeInterpolator interpolator, Runnable complete) {
        animate(image, Frame.current(image), crop(image, root, display, target), duration,
                interpolator, complete);
    }

    void cancel() {
        if (animation == null) return;
        animation.removeAllListeners();
        animation.removeAllUpdateListeners();
        animation.cancel();
        animation = null;
    }

    private Frame crop(View image, View root, RectF display, RectF source) {
        int[] location = new int[2];
        int[] rootLocation = new int[2];
        image.getLocationOnScreen(location);
        root.getLocationOnScreen(rootLocation);
        return Frame.crop(display, source, location[0] - rootLocation[0] - offsetX(image),
                location[1] - rootLocation[1] - offsetY(image),
                image.getWidth(), image.getHeight());
    }

    static float offsetX(View image) {
        return image.getTranslationX() + image.getPivotX() * (1f - image.getScaleX());
    }

    static float offsetY(View image) {
        return image.getTranslationY() + image.getPivotY() * (1f - image.getScaleY());
    }

    private void animate(View image, Frame from, Frame to, long duration,
                         TimeInterpolator interpolator, Runnable complete) {
        cancel();
        from.apply(image);
        ValueAnimator running = ValueAnimator.ofFloat(0f, 1f);
        animation = running;
        running.setDuration(duration);
        running.setInterpolator(interpolator);
        running.addUpdateListener(value -> Frame.between(from, to,
                (Float) value.getAnimatedValue()).apply(image));
        running.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animator) {
                if (animation != running) return;
                animation = null;
                to.apply(image);
                complete.run();
            }
        });
        running.start();
    }

    static final class Frame {
        final float scale;
        final float x;
        final float y;
        final RectF clip;

        Frame(float scale, float x, float y, RectF clip) {
            this.scale = scale;
            this.x = x;
            this.y = y;
            this.clip = clip;
        }

        static Frame full(View image) {
            return new Frame(1f, 0f, 0f, new RectF(0, 0, image.getWidth(), image.getHeight()));
        }

        static Frame current(View image) {
            Rect clip = image.getClipBounds();
            return new Frame(image.getScaleX(), offsetX(image), offsetY(image),
                    clip == null ? new RectF(0, 0, image.getWidth(), image.getHeight()) : new RectF(clip));
        }

        static Frame crop(RectF display, RectF target, float left, float top,
                          int width, int height) {
            float scale = Math.max(target.width() / display.width(),
                    target.height() / display.height());
            float x = target.centerX() - left - (display.centerX() - left) * scale;
            float y = target.centerY() - top - (display.centerY() - top) * scale;
            RectF clip = new RectF((target.left - left - x) / scale,
                    (target.top - top - y) / scale,
                    (target.right - left - x) / scale,
                    (target.bottom - top - y) / scale);
            clip.intersect(0, 0, width, height);
            return new Frame(scale, x, y, clip);
        }

        static Frame between(Frame from, Frame to, float progress) {
            return new Frame(lerp(from.scale, to.scale, progress),
                    lerp(from.x, to.x, progress), lerp(from.y, to.y, progress),
                    new RectF(lerp(from.clip.left, to.clip.left, progress),
                            lerp(from.clip.top, to.clip.top, progress),
                            lerp(from.clip.right, to.clip.right, progress),
                            lerp(from.clip.bottom, to.clip.bottom, progress)));
        }

        void apply(View image) {
            image.setPivotX(0f);
            image.setPivotY(0f);
            image.setScaleX(scale);
            image.setScaleY(scale);
            image.setTranslationX(x);
            image.setTranslationY(y);
            image.setAlpha(1f);
            Rect bounds = new Rect();
            clip.roundOut(bounds);
            image.setClipBounds(bounds);
        }

        private static float lerp(float start, float end, float progress) {
            return start + (end - start) * progress;
        }
    }
}
