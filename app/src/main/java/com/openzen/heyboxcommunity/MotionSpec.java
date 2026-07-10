package com.openzen.heyboxcommunity;

import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

final class MotionSpec {
    static final long PRESS_IN_MS = 60L;
    static final long PRESS_OUT_MS = 105L;
    static final long ENTER_MS = 180L;
    static final long PAGE_MS = 190L;
    static final long IMAGE_MS = 170L;
    static final long DIALOG_MS = 150L;
    static final long STAGGER_MS = 24L;
    static final long TRANSITION_FULL_MS = 220L;
    static final Interpolator EASE_OUT = new DecelerateInterpolator(1.7f);
    /** 轻微回弹，用于按压等动画的收尾。 */
    static final Interpolator SPRING = new OvershootInterpolator(1.12f);

    private MotionSpec() {}
}
