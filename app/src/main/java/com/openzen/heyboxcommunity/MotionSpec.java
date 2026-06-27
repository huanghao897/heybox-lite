package com.openzen.heyboxcommunity;

import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

final class MotionSpec {
    static final long PRESS_IN_MS = 60L;
    static final long PRESS_OUT_MS = 105L;
    static final long ENTER_MS = 180L;
    static final long PAGE_MS = 190L;
    static final long IMAGE_MS = 170L;
    static final Interpolator EASE_OUT = new DecelerateInterpolator(1.7f);

    private MotionSpec() {}
}
