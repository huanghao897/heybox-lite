package com.ronan.heyboxlite;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.ScrollView;

/** Programmatic-only layout requiring the owning activity and an existing dialog content tree. */
@SuppressLint("ViewConstructor")
final class NativeDialogLayout extends ScrollView {
    private final Activity activity;
    private final boolean round;
    private final float scale;
    private Window dialogWindow;
    private ViewTreeObserver boundsObserver;
    private int windowExtent;
    private final ViewTreeObserver.OnGlobalLayoutListener boundsListener = this::updateWindowBounds;

    NativeDialogLayout(Activity activity, boolean round, float scale, View content) {
        super(activity);
        this.activity = activity;
        this.round = round;
        this.scale = scale;
        setFillViewport(false);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        addView(content, new LayoutParams(-1, -2));
    }

    void bindWindow(Window window) {
        if (!round) return;
        dialogWindow = window;
        if (isAttachedToWindow()) observeWindowBounds();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (round && dialogWindow != null) observeWindowBounds();
    }

    @Override
    protected void onDetachedFromWindow() {
        // Dismiss detaches this view; leave the caller's OnDismissListener untouched.
        if (boundsObserver != null) {
            ViewTreeObserver observer = boundsObserver.isAlive() ? boundsObserver
                    : activity.getWindow().getDecorView().getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(boundsListener);
        }
        boundsObserver = null;
        windowExtent = 0;
        super.onDetachedFromWindow();
    }

    private void observeWindowBounds() {
        if (boundsObserver == null) {
            boundsObserver = activity.getWindow().getDecorView().getViewTreeObserver();
            boundsObserver.addOnGlobalLayoutListener(boundsListener);
        }
        updateWindowBounds();
    }

    private void updateWindowBounds() {
        if (boundsObserver == null || dialogWindow == null) return;
        int extent = roundExtent(availableBounds(activity));
        boolean resized = windowExtent != extent;
        windowExtent = extent;
        if (dialogWindow.getAttributes().width != extent) {
            dialogWindow.setLayout(extent, dialogWindow.getAttributes().height);
        }
        if (resized) requestLayout();
    }

    static int width(Activity activity, boolean round, float scale) {
        Rect bounds = availableBounds(activity);
        if (round) return roundExtent(bounds);
        int width = bounds.width();
        int preferred = Math.max(dp(activity, 220, scale),
                Math.min(width - dp(activity, 28, scale), dp(activity, 360, scale)));
        return Math.max(1, Math.min(width - dp(activity, 8, scale), preferred));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Rect bounds = availableBounds(activity);
        int limit = round ? roundExtent(bounds)
                : Math.max(1, bounds.height() - dp(activity, 16, scale));
        if (round) {
            int mode = MeasureSpec.getMode(widthMeasureSpec);
            int width = mode == MeasureSpec.UNSPECIFIED ? limit
                    : Math.min(limit, MeasureSpec.getSize(widthMeasureSpec));
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(width,
                    mode == MeasureSpec.UNSPECIFIED ? MeasureSpec.AT_MOST : mode);
        }
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            limit = Math.min(limit, MeasureSpec.getSize(heightMeasureSpec));
        }
        super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
    }

    private static int roundExtent(Rect bounds) {
        return Math.max(1, (int) (Math.min(bounds.width(), bounds.height()) * 0.68f));
    }

    private static Rect availableBounds(Activity activity) {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        View decor = activity.getWindow().getDecorView();
        Rect visible = new Rect();
        decor.getWindowVisibleDisplayFrame(visible);
        if (visible.width() > 0) width = Math.min(width, visible.width());
        if (visible.height() > 0) height = Math.min(height, visible.height());
        View content = decor.findViewById(android.R.id.content);
        if (content != null) {
            if (content.getWidth() > 0) width = Math.min(width, content.getWidth());
            if (content.getHeight() > 0) height = Math.min(height, content.getHeight());
        }
        return new Rect(0, 0, Math.max(1, width), Math.max(1, height));
    }

    private static int dp(Activity activity, int value, float scale) {
        return UiComponents.dp(activity, value, scale);
    }
}
