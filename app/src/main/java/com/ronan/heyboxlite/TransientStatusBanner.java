package com.ronan.heyboxlite;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class TransientStatusBanner extends LinearLayout {
    private static final long MIN_VISIBLE_MS = 420L;

    private final LoadingSpinnerView spinner;
    private final int enterOffset;
    private final int exitOffset;
    private int animationSerial;
    private boolean requestedVisible;
    private long visibleSince;

    TransientStatusBanner(Context context, ThemeTokens tokens, float uiScale,
                          float textScale, String message) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(context, 11, uiScale), dp(context, 7, uiScale),
                dp(context, 12, uiScale), dp(context, 7, uiScale));
        setMinimumHeight(dp(context, 34, uiScale));
        Compat.setBackground(this, UiComponents.statusBanner(context, tokens, uiScale));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setElevation(dp(context, 8, uiScale));
        }

        spinner = new LoadingSpinnerView(context);
        spinner.setColor(tokens.text);
        int spinnerSize = dp(context, 15, uiScale);
        addView(spinner, new LayoutParams(spinnerSize, spinnerSize));

        TextView label = UiComponents.label(context, message, 11.0f,
                tokens.text, textScale);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        label.setMaxWidth(Math.max(1, Math.min(dp(context, 170, uiScale),
                screenWidth - dp(context, 72, uiScale))));
        LayoutParams labelParams = new LayoutParams(-2, -2);
        labelParams.leftMargin = dp(context, 7, uiScale);
        addView(label, labelParams);

        enterOffset = dp(context, 9, uiScale);
        exitOffset = dp(context, 5, uiScale);
        setClickable(false);
        setFocusable(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        }
        dismissImmediately();
    }

    void showBanner() {
        if (requestedVisible && getVisibility() == VISIBLE) {
            return;
        }
        boolean alreadyVisible = getVisibility() == VISIBLE;
        requestedVisible = true;
        int serial = ++animationSerial;
        animate().cancel();
        setVisibility(VISIBLE);
        spinner.setVisibility(VISIBLE);
        visibleSince = SystemClock.elapsedRealtime();

        if (Motions.off() || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            settleVisible();
            return;
        }
        if (!alreadyVisible) {
            setAlpha(0.0f);
            setTranslationY(-enterOffset);
            setScaleX(Motions.full() ? 0.975f : 1.0f);
            setScaleY(Motions.full() ? 0.975f : 1.0f);
        }
        animate().alpha(1.0f).translationY(0.0f).scaleX(1.0f).scaleY(1.0f)
                .setDuration(Motions.full() ? MotionSpec.ENTER_MS
                        : MotionSpec.ENTER_LITE_MS)
                .setInterpolator(MotionSpec.EMPHASIZED_DECELERATE)
                .withEndAction(() -> {
                    if (serial == animationSerial && requestedVisible) {
                        settleVisible();
                    }
                })
                .start();
    }

    void hideBanner() {
        if (!requestedVisible) {
            return;
        }
        requestedVisible = false;
        int serial = ++animationSerial;
        animate().cancel();
        if (Motions.off() || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN
                || getWindowToken() == null) {
            dismissImmediately();
            return;
        }
        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - visibleSince);
        long delay = Math.max(0L, MIN_VISIBLE_MS - elapsed);
        postDelayed(() -> animateOut(serial), delay);
    }

    void dismissImmediately() {
        requestedVisible = false;
        animationSerial++;
        animate().cancel();
        spinner.setVisibility(GONE);
        setVisibility(GONE);
        setAlpha(1.0f);
        setTranslationX(0.0f);
        setTranslationY(0.0f);
        setScaleX(1.0f);
        setScaleY(1.0f);
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void animateOut(int serial) {
        if (serial != animationSerial || requestedVisible) {
            return;
        }
        if (getWindowToken() == null) {
            dismissImmediately();
            return;
        }
        animate().alpha(0.0f).translationY(-exitOffset)
                .scaleX(Motions.full() ? 0.985f : 1.0f)
                .scaleY(Motions.full() ? 0.985f : 1.0f)
                .setDuration(Motions.full() ? MotionSpec.DIALOG_MS
                        : MotionSpec.DIALOG_LITE_MS)
                .setInterpolator(MotionSpec.EASE_OUT)
                .withEndAction(() -> {
                    if (serial == animationSerial && !requestedVisible) {
                        dismissImmediately();
                    }
                })
                .start();
    }

    private void settleVisible() {
        setVisibility(VISIBLE);
        spinner.setVisibility(VISIBLE);
        setAlpha(1.0f);
        setTranslationX(0.0f);
        setTranslationY(0.0f);
        setScaleX(1.0f);
        setScaleY(1.0f);
    }

    @Override
    protected void onDetachedFromWindow() {
        dismissImmediately();
        super.onDetachedFromWindow();
    }

    private static int dp(Context context, int value, float scale) {
        return Math.round(value * context.getResources().getDisplayMetrics().density * scale);
    }
}
