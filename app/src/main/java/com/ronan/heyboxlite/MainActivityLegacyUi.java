package com.ronan.heyboxlite;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Compatibility surface for the remaining native fallback pages.
 * Compose owns the normal route; this class keeps the old path isolated and
 * prevents generic View factories from growing MainActivity again.
 */
final class MainActivityLegacyUi {
    private final MainActivity activity;

    MainActivityLegacyUi(MainActivity activity) {
        this.activity = activity;
    }

    void transitionTo(View next) {
        CrashBreadcrumbs.screen(activity.screen);
        if (activity.crownInput != null) activity.crownInput.cancel();
        if (!"detail".equals(activity.screen) && activity.readingTimeTracker != null) {
            activity.readingTimeTracker.pause();
        }
        boolean back = activity.pendingBackTransition;
        boolean push = activity.pendingLateralPush;
        activity.pendingBackTransition = false;
        activity.pendingLateralPush = false;
        ensurePageBackdrop(next);
        if (activity.shellAnimating) {
            activity.pageTransitions.finishNow();
            activity.content.removeAllViews();
            activity.content.addView(next, match());
            return;
        }
        activity.pageTransitions.setCompactMotion(activity.usesWatchLayout());
        activity.pageTransitions.run(activity.content, next, !back, push);
    }

    void cancelAllMotion() {
        activity.pageTransitions.finishNow();
        if (activity.detailPager != null) activity.detailPager.cancelMotion();
        if (activity.content instanceof BackSwipeFrameLayout) {
            ((BackSwipeFrameLayout) activity.content).cancelMotion();
        }
        Motions.resetTree(activity.shellRoot);
        if (activity.feedPage != null) activity.feedPage.finishMotion();
        activity.shellAnimating = false;
        activity.pendingBackTransition = false;
        activity.pendingLateralPush = false;
        if (activity.bottomNavigation != null) activity.bottomNavigation.finishMotion();
    }

    void showLoading() {
        hideLoading();
        LoadingSpinnerView progress = new LoadingSpinnerView(activity);
        progress.setTag("loading");
        progress.setColor(activity.PRIMARY);
        activity.content.addView(progress, new FrameLayout.LayoutParams(dp(38), dp(38), 17));
    }

    void hideLoading() {
        removeViewWithTag("loading");
    }

    void showDetailLoadingOverlay() {
        removeViewWithTag("detail_loading");
        activity.content.addView(detailLoadingPage(), match());
    }

    void showMessage(String message) {
        activity.content.removeAllViews();
        FrameLayout box = new FrameLayout(activity);
        box.setBackgroundColor(activity.BG);
        TextView view = text(message, 13.0f, activity.MUTED);
        view.setGravity(17);
        view.setPadding(dp(18), dp(16), dp(18), dp(16));
        view.setMaxWidth(dp(260));
        view.setLineSpacing(0.0f, 1.15f);
        box.addView(view, new FrameLayout.LayoutParams(-1, -2, 17));
        activity.content.addView(box, match());
    }

    TextView icon(String value) {
        TextView view = text(value, 23.0f, activity.TEXT);
        view.setGravity(17);
        view.setContentDescription(value);
        return view;
    }

    TextView text(String value, float size, int color) {
        TextView view = new TextView(activity);
        view.setText(value == null ? "" : value);
        view.setTextSize(sp(size));
        view.setTextColor(color);
        view.setTypeface(android.graphics.Typeface.DEFAULT);
        Compat.setLetterSpacing(view, 0.0f);
        return view;
    }

    GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    void setIcon(TextView view, int resource, int color, int size) {
        Drawable drawable = Compat.tintedDrawable(activity, resource, color);
        if (drawable == null) return;
        drawable.setBounds(0, 0, dp(size), dp(size));
        view.setCompoundDrawables(null, drawable, null, null);
    }

    void runWithPressFeedback(View view, Runnable action) {
        if (action == null) return;
        if (!Motions.off() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.animate().cancel();
            view.animate().scaleX(0.975f).scaleY(0.975f)
                    .setDuration(MotionSpec.PRESS_IN_MS)
                    .setInterpolator(MotionSpec.EASE_OUT)
                    .withEndAction(() -> view.animate().scaleX(1.0f).scaleY(1.0f)
                            .setDuration(MotionSpec.PRESS_OUT_MS)
                            .setInterpolator(Motions.full() ? MotionSpec.SPRING : MotionSpec.EASE_OUT)
                            .start())
                    .start();
        }
        if (!activity.isFinishing()) action.run();
    }

    LinearLayout vertical(int color) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    void addTop(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(view, params);
    }

    FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    int dp(int value) {
        float scale = activity.session == null ? 1.0f : activity.session.uiScale() / 100.0f;
        return Math.round(value * activity.getResources().getDisplayMetrics().density * scale);
    }

    float sp(float value) {
        float scale = activity.session == null ? 1.0f : activity.session.textScale() / 100.0f;
        return value * scale;
    }

    void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    private View detailLoadingPage() {
        FrameLayout page = new FrameLayout(activity);
        page.setTag("detail_loading");
        page.setBackgroundColor(activity.BG);
        LoadingSpinnerView progress = new LoadingSpinnerView(activity);
        progress.setColor(activity.PRIMARY);
        page.addView(progress, new FrameLayout.LayoutParams(dp(38), dp(38), 17));
        return page;
    }

    void removeViewWithTag(String tag) {
        if (tag == null || activity.content == null) return;
        for (int index = activity.content.getChildCount() - 1; index >= 0; index--) {
            View child = activity.content.getChildAt(index);
            if (tag.equals(child.getTag())) activity.content.removeViewAt(index);
        }
    }

    void ensurePageBackdrop(View view) {
        if (view == null) return;
        Drawable background = view.getBackground();
        if (background == null || (background instanceof ColorDrawable
                && Color.alpha(((ColorDrawable) background).getColor()) == 0)) {
            view.setBackgroundColor(activity.BG);
        }
    }
}
