package com.ronan.heyboxlite;

import android.app.Activity;
import android.util.DisplayMetrics;

/** Centralizes shell spacing for round and rectangular watch displays. */
final class MainActivityLayout {
    private final Activity activity;
    private final SessionStore session;

    MainActivityLayout(Activity activity, SessionStore session) {
        this.activity = activity;
        this.session = session;
    }

    boolean usesRoundLayout() {
        return session.usesRoundLayout();
    }

    boolean usesWatchLayout() {
        return RoundLayoutMetrics.isWatchDisplay(activity);
    }

    int pageHorizontalPadding() {
        if (!usesRoundLayout()) return dp(8);
        return RoundLayoutMetrics.componentInset(
                screenMetrics().widthPixels, RoundLayoutMetrics.PAGE_HORIZONTAL_RATIO, dp(8));
    }

    int pageTopPadding() {
        if (!usesRoundLayout()) return dp(8);
        return RoundLayoutMetrics.componentInset(
                screenMetrics().heightPixels, RoundLayoutMetrics.PAGE_TOP_RATIO, dp(8));
    }

    int subpageTopPadding() {
        if (!usesRoundLayout()) return dp(8);
        return RoundLayoutMetrics.componentInset(
                screenMetrics().heightPixels, RoundLayoutMetrics.SUBPAGE_TOP_RATIO, dp(8));
    }

    int roundHeaderTopPadding() {
        return RoundLayoutMetrics.componentInset(
                screenMetrics().heightPixels, RoundLayoutMetrics.PAGE_TOP_RATIO, dp(9));
    }

    int horizontalInset(float ratio, int minimumDp) {
        return RoundLayoutMetrics.componentInset(
                screenMetrics().widthPixels, ratio, dp(minimumDp));
    }

    int headerInnerInset() {
        return usesRoundLayout()
                ? RoundLayoutMetrics.headerInnerInset(screenMetrics().widthPixels) : 0;
    }

    private DisplayMetrics screenMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        try {
            if (android.os.Build.VERSION.SDK_INT >= 17) {
                activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            } else {
                activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
            }
        } catch (RuntimeException error) {
            return activity.getResources().getDisplayMetrics();
        }
        return metrics;
    }

    private int dp(int value) {
        return UiComponents.dp(activity, value, session.uiScale() / 100.0f);
    }
}
