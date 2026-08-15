package com.ronan.heyboxlite;

import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

final class MotionSpec {
    static final long PRESS_IN_MS = 60L;
    static final long PRESS_OUT_MS = 105L;
    static final long ENTER_MS = 180L;
    static final long ENTER_LITE_MS = 130L;
    static final long PAGE_MS = 190L;
    static final long IMAGE_MS = 170L;
    static final long DIALOG_MS = 150L;
    static final long DIALOG_LITE_MS = 115L;
    static final long STAGGER_MS = 24L;
    static final long TRANSITION_FULL_MS = 220L;
    static final long TRANSITION_LITE_MS = 150L;
    static final long WATCH_TRANSITION_MS = 210L;
    static final long WATCH_SETTLE_MIN_MS = 150L;
    static final long WATCH_SETTLE_MAX_MS = 250L;
    static final float WATCH_AXIS_RATIO = 1.24f;
    static final float WATCH_DRAG_RESPONSE = 0.92f;
    static final float WATCH_COMMIT_DISTANCE_RATIO = 0.28f;
    static final int WATCH_COMMIT_VELOCITY_DP = 420;
    static final int WATCH_TOUCH_SLOP_MULTIPLIER = 2;
    static final Interpolator EMPHASIZED_DECELERATE = input -> {
        float inverse = 1.0f - input;
        return 1.0f - inverse * inverse * inverse * inverse;
    };
    static final Interpolator EASE_OUT = new DecelerateInterpolator(1.7f);
    /** 轻微回弹，用于按压等动画的收尾。 */
    static final Interpolator SPRING = new OvershootInterpolator(1.12f);

    private MotionSpec() {}
}
