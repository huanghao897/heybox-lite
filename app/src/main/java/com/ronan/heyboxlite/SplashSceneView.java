package com.ronan.heyboxlite;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared launch scene used by the real splash and its settings preview. */
final class SplashSceneView extends FrameLayout {
    private final float uiScale;
    private final ImageView mark;
    private final TextView message;
    private final TextView hint;

    SplashSceneView(Context context, String value, boolean dark,
                    float uiScale, float textScale, boolean showHint) {
        super(context);
        this.uiScale = Math.max(0.5f, uiScale);
        setBackgroundColor(dark ? Color.rgb(11, 11, 12) : Color.rgb(244, 244, 246));

        int foreground = dark ? Color.rgb(245, 245, 247) : Color.rgb(25, 25, 27);
        int muted = dark ? Color.rgb(142, 142, 147) : Color.rgb(99, 99, 104);
        boolean round = RoundLayoutMetrics.isRoundDisplay(context);
        boolean watch = RoundLayoutMetrics.isWatchDisplay(context);

        LinearLayout stack = new LinearLayout(context);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams stackParams = new FrameLayout.LayoutParams(
                -1, -2, Gravity.CENTER);
        stackParams.leftMargin = dp(round ? 22 : 18);
        stackParams.rightMargin = dp(round ? 22 : 18);
        addView(stack, stackParams);

        this.mark = new ImageView(context);
        this.mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Drawable logo = Compat.tintedDrawable(context, R.mipmap.about_app_mark, foreground);
        if (logo != null) this.mark.setImageDrawable(logo);
        int logoDp = round ? 58 : (watch ? 64 : 72);
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
                dp(logoDp), dp(logoDp));
        markParams.bottomMargin = dp(round ? 10 : 12);
        stack.addView(this.mark, markParams);

        this.message = UiComponents.label(context,
                TextUtils.isEmpty(value) ? SessionStore.DEFAULT_SPLASH_TEXT : value,
                round || watch ? 12.5f : 13.5f, foreground, textScale);
        this.message.setGravity(Gravity.CENTER);
        this.message.setIncludeFontPadding(false);
        this.message.setMaxLines(2);
        this.message.setEllipsize(TextUtils.TruncateAt.END);
        stack.addView(this.message, new LinearLayout.LayoutParams(-1, -2));

        this.hint = UiComponents.label(context, "点击任意位置退出预览",
                10.0f, muted, textScale);
        this.hint.setGravity(Gravity.CENTER);
        this.hint.setIncludeFontPadding(false);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                -1, dp(30), Gravity.BOTTOM);
        hintParams.leftMargin = dp(18);
        hintParams.rightMargin = dp(18);
        addView(this.hint, hintParams);
        this.hint.setVisibility(showHint ? View.VISIBLE : View.GONE);
    }

    void playEntrance() {
        Motions.resetTree(this);
        if (Motions.off()) return;
        this.mark.setAlpha(0.0f);
        float markStartScale = Motions.full() ? 0.94f : 0.96f;
        this.mark.setScaleX(markStartScale);
        this.mark.setScaleY(markStartScale);
        this.message.setAlpha(0.0f);
        this.message.setTranslationY(dp(Motions.full() ? 8 : 5));
        this.hint.setAlpha(0.0f);

        long iconDuration = Motions.full() ? MotionSpec.ENTER_MS : MotionSpec.ENTER_LITE_MS;
        long messageDelay = Motions.full() ? 90L : 65L;
        long messageDuration = Motions.full() ? MotionSpec.ENTER_MS : 140L;
        this.mark.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f)
                .setDuration(iconDuration)
                .setInterpolator(MotionSpec.EMPHASIZED_DECELERATE)
                .start();
        this.message.animate().alpha(1.0f).translationY(0.0f)
                .setStartDelay(messageDelay)
                .setDuration(messageDuration)
                .setInterpolator(MotionSpec.EMPHASIZED_DECELERATE)
                .start();
        if (this.hint.getVisibility() == View.VISIBLE) {
            this.hint.animate().alpha(1.0f)
                    .setStartDelay(messageDelay + messageDuration + 20L)
                    .setDuration(100L)
                    .setInterpolator(MotionSpec.EASE_OUT)
                    .start();
        }
    }

    void fadeOut(Runnable endAction) {
        animate().cancel();
        if (Motions.off()) {
            if (endAction != null) endAction.run();
            return;
        }
        animate().alpha(0.0f)
                .setDuration(fadeOutDuration())
                .setInterpolator(MotionSpec.EASE_OUT)
                .withEndAction(() -> {
                    if (endAction != null) endAction.run();
                })
                .start();
    }

    long fadeOutDuration() {
        return Motions.off() ? 0L : (Motions.full() ? 160L : 120L);
    }

    void cancelMotion() {
        Motions.resetTree(this);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density * this.uiScale);
    }
}
