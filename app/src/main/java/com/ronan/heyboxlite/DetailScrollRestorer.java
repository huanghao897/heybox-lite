package com.ronan.heyboxlite;

import android.view.ViewTreeObserver;
import android.widget.ScrollView;

/** Applies a saved detail offset before the first visible frame. */
final class DetailScrollRestorer {
    private DetailScrollRestorer() {
    }

    static void beforeFirstDraw(ScrollView scroll, int targetY) {
        if (scroll == null || targetY <= 0) return;
        final ViewTreeObserver observer = scroll.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                scroll.scrollTo(0, targetY);
                if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                return true;
            }
        });
    }
}
