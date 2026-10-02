package com.ronan.heyboxlite;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;

/** Starts expensive image work only after the view enters the visible viewport. */
final class LazyImageBinder {
    private final View target;
    private final Runnable load;
    private final Rect visibleBounds = new Rect();
    private boolean loaded;
    private boolean listening;

    private final ViewTreeObserver.OnScrollChangedListener scrollListener =
            this::tryLoad;
    private final View.OnAttachStateChangeListener attachListener =
            new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                    addScrollListener();
                    view.post(LazyImageBinder.this::tryLoad);
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    removeScrollListener();
                }
            };

    private LazyImageBinder(View target, Runnable load) {
        this.target = target;
        this.load = load;
        target.addOnAttachStateChangeListener(this.attachListener);
        if (target.getWindowToken() != null) addScrollListener();
        target.post(this::tryLoad);
    }

    static void bind(View target, Runnable load) {
        if (target == null || load == null) return;
        new LazyImageBinder(target, load);
    }

    private void tryLoad() {
        if (this.loaded || !this.target.isShown()
                || this.target.getWindowVisibility() != View.VISIBLE) return;
        if (!this.target.getGlobalVisibleRect(this.visibleBounds)
                || this.visibleBounds.isEmpty()) return;
        this.loaded = true;
        removeScrollListener();
        this.target.removeOnAttachStateChangeListener(this.attachListener);
        this.load.run();
    }

    private void addScrollListener() {
        if (this.listening) return;
        ViewTreeObserver observer = this.target.getViewTreeObserver();
        if (!observer.isAlive()) return;
        observer.addOnScrollChangedListener(this.scrollListener);
        this.listening = true;
    }

    private void removeScrollListener() {
        if (!this.listening) return;
        ViewTreeObserver observer = this.target.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnScrollChangedListener(this.scrollListener);
        this.listening = false;
    }
}
