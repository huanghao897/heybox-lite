package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

final class BottomNavigationController {
    interface PressFeedback {
        void run(View view, Runnable action);
    }

    private final Activity activity;
    private final SessionStore session;
    private final ThemeTokens tokens;
    private final ResponsiveDock.Dimensions dimensions;
    private final LinearLayout view;
    private final PressFeedback pressFeedback;
    private boolean visible;
    private int animationSerial;

    BottomNavigationController(Activity activity, SessionStore session, ThemeTokens tokens,
                               boolean roundLayout, FrameLayout parent,
                               PressFeedback pressFeedback) {
        this.activity = activity;
        this.session = session;
        this.tokens = tokens;
        this.pressFeedback = pressFeedback;
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int height = activity.getResources().getDisplayMetrics().heightPixels;
        this.dimensions = ResponsiveDock.fromScreen(width, height, roundLayout);
        this.view = new LinearLayout(activity);
        this.view.setGravity(17);
        this.view.setPadding(dimensions.paddingHorizontal, dimensions.paddingVertical,
                dimensions.paddingHorizontal, dimensions.paddingVertical);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.view.setElevation(dp(10));
        }
        Compat.setBackground(this.view, UiComponents.dock(activity, tokens, uiScale()));
        this.view.setVisibility(View.GONE);
        this.view.setAlpha(0.0f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dimensions.width, dimensions.height, 81);
        params.setMargins(0, 0, 0, dimensions.marginBottom);
        parent.addView(this.view, params);
    }

    void addItem(String label, String key, int drawable, Runnable click) {
        ImageView item = new ImageView(activity);
        item.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int itemWidth = Math.max(dimensions.iconSize,
                (dimensions.width - dimensions.paddingHorizontal * 2
                        - dimensions.itemMargin * 4) / 2);
        int itemHeight = Math.max(dimensions.iconSize,
                dimensions.height - dimensions.paddingVertical * 2);
        int horizontalInset = Math.max(0, (itemWidth - dimensions.iconSize) / 2);
        int verticalInset = Math.max(0, (itemHeight - dimensions.iconSize) / 2);
        item.setPadding(horizontalInset, verticalInset, horizontalInset, verticalInset);
        item.setAdjustViewBounds(false);
        Drawable icon = Compat.tintedDrawable(activity, drawable, tokens.muted);
        if (icon != null) {
            icon.setBounds(0, 0, dimensions.iconSize, dimensions.iconSize);
            item.setImageDrawable(icon);
        }
        item.setColorFilter(tokens.muted);
        item.setContentDescription(label);
        item.setTag(key);
        item.setOnClickListener(clicked -> pressFeedback.run(item, click));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1.0f);
        params.setMargins(dimensions.itemMargin, 0, dimensions.itemMargin, 0);
        view.addView(item, params);
    }

    void select(String key) {
        for (int index = 0; index < view.getChildCount(); index++) {
            View item = view.getChildAt(index);
            boolean active = key != null && key.equals(item.getTag());
            item.setAlpha(active ? 1.0f : 0.62f);
            if (item instanceof ImageView) {
                ((ImageView) item).setColorFilter(active ? tokens.text : tokens.subtle);
                Compat.setBackground(item, active
                        ? UiComponents.navSelection(activity, tokens, uiScale()) : null);
            }
        }
    }

    void setVisible(boolean show, boolean animate, boolean shellAnimating) {
        animate = animate && !Motions.off();
        if (show && visible && view.getVisibility() == View.VISIBLE
                && view.getAlpha() > 0.98f && Math.abs(view.getTranslationY()) < 1.0f) {
            return;
        }
        if (!show && !visible && view.getVisibility() != View.VISIBLE) return;
        int serial = ++animationSerial;
        boolean wasHidden = view.getVisibility() != View.VISIBLE;
        view.animate().cancel();
        visible = show;
        if (show) {
            view.setVisibility(View.VISIBLE);
            if (!animate || shellAnimating) {
                view.setAlpha(1.0f);
                view.setTranslationY(0.0f);
                return;
            }
            if (wasHidden || view.getAlpha() <= 0.0f) {
                view.setAlpha(0.0f);
                view.setTranslationY(dp(22));
            }
            view.animate().alpha(1.0f).translationY(0.0f)
                    .setDuration(170L).setInterpolator(MotionSpec.EASE_OUT).start();
            return;
        }
        if (!animate) {
            view.setAlpha(0.0f);
            view.setTranslationY(0.0f);
            view.setVisibility(View.GONE);
            return;
        }
        view.animate().alpha(0.0f).translationY(dp(22)).setDuration(120L)
                .setInterpolator(new DecelerateInterpolator()).start();
        view.postDelayed(() -> {
            if (serial != animationSerial || visible) return;
            view.setVisibility(View.GONE);
            view.setTranslationY(0.0f);
        }, 140L);
    }

    void finishMotion() {
        animationSerial++;
        view.animate().cancel();
        view.setTranslationY(0.0f);
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
        view.setAlpha(visible ? 1.0f : 0.0f);
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    int bottomSafeSpace() {
        return dimensions.height + dimensions.marginBottom + dp(8);
    }

    private int dp(int value) {
        return UiComponents.dp(activity, value, uiScale());
    }

    private float uiScale() {
        return session.uiScale() / 100.0f;
    }
}
