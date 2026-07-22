package com.ronan.heyboxlite;

import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

final class MotionSpec {
    static final long PRESS_IN_MS = 70L;
    static final long PRESS_OUT_MS = 150L;
    static final long ENTER_MS = 220L;
    static final long ENTER_LITE_MS = 150L;
    static final long PAGE_MS = 240L;
    static final long IMAGE_MS = 220L;
    static final long DIALOG_MS = 240L;
    static final long DIALOG_LITE_MS = 160L;
    static final long STAGGER_MS = 22L;
    static final long TRANSITION_FULL_MS = 280L;
    static final long TRANSITION_LITE_MS = 160L;
    static final Interpolator STANDARD = input -> {
        float inverse = 1.0f - input;
        return 1.0f - inverse * inverse * inverse;
    };
    static final Interpolator EMPHASIZED_DECELERATE = input -> {
        float inverse = 1.0f - input;
        return 1.0f - inverse * inverse * inverse * inverse;
    };
    static final Interpolator EASE_OUT = new DecelerateInterpolator(1.7f);
    static final Interpolator SPRING = new OvershootInterpolator(1.08f);

    private MotionSpec() {}
}
