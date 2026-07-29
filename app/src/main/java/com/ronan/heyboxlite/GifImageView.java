package com.ronan.heyboxlite;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

final class GifImageView extends ImageView {
    private final Rect visibleBounds = new Rect();
    private final ViewTreeObserver.OnScrollChangedListener scrollListener =
            this::updatePlayback;

    GifImageView(Context context) {
        super(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewTreeObserver observer = getViewTreeObserver();
        if (observer.isAlive()) observer.addOnScrollChangedListener(scrollListener);
        post(this::updatePlayback);
    }

    @Override
    protected void onDetachedFromWindow() {
        ViewTreeObserver observer = getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnScrollChangedListener(scrollListener);
        GifSupport.setRunning(getDrawable(), false);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updatePlayback();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updatePlayback();
    }

    void updatePlayback() {
        boolean visible = isShown()
                && getWindowVisibility() == View.VISIBLE
                && getGlobalVisibleRect(visibleBounds)
                && !visibleBounds.isEmpty();
        GifSupport.setRunning(getDrawable(), visible);
    }
}
