package com.ronan.heyboxlite;

import android.app.Activity;
import android.os.Build;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.View;

import cc.star0.wear.lib.cnwearoverlay.runtime.CnWearOverlay;

/**
 * Selects the vendor Wear scroll tick when the device exposes it, with a platform fallback.
 *
 * <p>The cnwearoverlay runtime only resolves vendor haptic behavior. Crown input, speed and scrolling
 * remain owned by the existing CrownInputHandler/CrownScrollDispatcher chain.
 */
final class CrownHapticProvider {
    private static final long MIN_FEEDBACK_INTERVAL_MS = 36L;

    private final Activity activity;
    private final SessionStore session;
    private long lastFeedbackAt;

    CrownHapticProvider(Activity activity, SessionStore session) {
        this.activity = activity;
        this.session = session;
    }

    void performScrollTick(View contentRoot) {
        if (!this.session.crownHapticsEnabled()) return;

        long now = SystemClock.uptimeMillis();
        if (now - this.lastFeedbackAt < MIN_FEEDBACK_INTERVAL_MS) return;
        this.lastFeedbackAt = now;

        View target = contentRoot == null
                ? this.activity.getWindow().getDecorView() : contentRoot;

        if (CnWearOverlay.isActive()) {
            if (target.performHapticFeedback(CnWearOverlay.getScrollTick())) {
                return;
            }
            // Xiaomi maps CLOCK_TICK to a texture effect; use the overlay's corrected mapping.
            if (CnWearOverlay.isXiaomi()
                    && CnWearOverlay.performHapticFeedback(
                    target, HapticFeedbackConstants.CLOCK_TICK)) {
                return;
            }
        }

        int platformEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                ? HapticFeedbackConstants.CLOCK_TICK
                : HapticFeedbackConstants.KEYBOARD_TAP;
        target.performHapticFeedback(platformEffect);
    }
}
