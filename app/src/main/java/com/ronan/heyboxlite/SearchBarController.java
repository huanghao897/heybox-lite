package com.ronan.heyboxlite;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import java.util.Map;
import java.util.WeakHashMap;

final class SearchBarController {
    private final Activity activity;
    private final Handler handler;
    private final float uiScale;
    private final Map<View, Integer> contentTops = new WeakHashMap<>();
    private final Map<View, Boolean> visibility = new WeakHashMap<>();

    SearchBarController(Activity activity, Handler handler, float uiScale) {
        this.activity = activity;
        this.handler = handler;
        this.uiScale = uiScale;
    }

    void prepare(View searchBar, int contentTop) {
        this.contentTops.put(searchBar, contentTop);
        this.visibility.put(searchBar, true);
        searchBar.animate().cancel();
        searchBar.setVisibility(View.VISIBLE);
        searchBar.setEnabled(true);
        searchBar.setAlpha(1.0f);
        searchBar.setTranslationY(0.0f);
        setChildrenEnabled(searchBar, true);
    }

    int contentTop(View searchBar, int fallback) {
        Integer value = this.contentTops.get(searchBar);
        return value == null ? fallback : value;
    }

    void setVisible(View searchBar, boolean visible) {
        if (searchBar == null) return;
        Boolean current = this.visibility.get(searchBar);
        if (current != null && current == visible) return;
        this.visibility.put(searchBar, visible);
        searchBar.animate().cancel();
        if (Motions.off()) {
            applyFinalState(searchBar, visible);
            return;
        }
        if (visible) {
            searchBar.setVisibility(View.VISIBLE);
            searchBar.setEnabled(true);
            setChildrenEnabled(searchBar, true);
            searchBar.animate().alpha(1.0f).translationY(0.0f)
                    .setDuration(120L)
                    .start();
            return;
        }
        clearFocus(searchBar);
        searchBar.setEnabled(false);
        setChildrenEnabled(searchBar, false);
        searchBar.animate().alpha(0.0f).translationY(-dp(12))
                .setDuration(110L)
                .start();
        this.handler.postDelayed(() -> {
            Boolean latest = this.visibility.get(searchBar);
            if (latest != null && !latest) searchBar.setVisibility(View.GONE);
        }, 120L);
    }

    void clear() {
        for (View view : this.visibility.keySet()) {
            if (view != null) {
                view.animate().cancel();
                view.setAlpha(1.0f);
                view.setTranslationY(0.0f);
            }
        }
        this.contentTops.clear();
        this.visibility.clear();
    }

    private void applyFinalState(View searchBar, boolean visible) {
        searchBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        searchBar.setEnabled(visible);
        setChildrenEnabled(searchBar, visible);
        searchBar.setAlpha(visible ? 1.0f : 0.0f);
        searchBar.setTranslationY(0.0f);
    }

    private void clearFocus(View view) {
        view.clearFocus();
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            clearFocus(group.getChildAt(i));
        }
    }

    private void setChildrenEnabled(View view, boolean enabled) {
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setEnabled(enabled);
            setChildrenEnabled(child, enabled);
        }
    }

    private int dp(int value) {
        return Math.round(value * this.activity.getResources()
                .getDisplayMetrics().density * this.uiScale);
    }
}
