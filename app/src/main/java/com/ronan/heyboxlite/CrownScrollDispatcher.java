package com.ronan.heyboxlite;

import android.os.Build;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListAdapter;
import android.widget.ScrollView;

final class CrownScrollDispatcher {
    interface TargetProvider {
        View resolve(int direction);
    }

    private final TargetProvider targetProvider;
    private final Runnable feedback;
    private final Runnable applyPending = this::applyPending;
    private View pendingTarget;
    private int pendingDistance;
    private int pendingLimit;
    private boolean posted;

    CrownScrollDispatcher(TargetProvider targetProvider, Runnable feedback) {
        this.targetProvider = targetProvider;
        this.feedback = feedback;
    }

    boolean enqueue(int distance, int frameLimit) {
        if (distance == 0) return false;
        int direction = distance > 0 ? 1 : -1;
        View target = this.targetProvider.resolve(direction);
        if (target == null) return false;

        if (this.pendingTarget != null && this.pendingTarget != target) cancel();
        this.pendingTarget = target;
        this.pendingLimit = Math.max(1, frameLimit);
        this.pendingDistance = CrownScrollController.coalesceBounded(
                this.pendingDistance, distance, this.pendingLimit);
        if (!this.posted) {
            this.posted = true;
            postOnNextFrame(target, this.applyPending);
        }
        return true;
    }

    void cancel() {
        if (this.pendingTarget != null) {
            this.pendingTarget.removeCallbacks(this.applyPending);
        }
        this.pendingTarget = null;
        this.pendingDistance = 0;
        this.pendingLimit = 0;
        this.posted = false;
    }

    private void applyPending() {
        View target = this.pendingTarget;
        int distance = this.pendingDistance;
        this.pendingTarget = null;
        this.pendingDistance = 0;
        this.pendingLimit = 0;
        this.posted = false;
        if (target == null || distance == 0 || target.getParent() == null
                || target.getVisibility() != View.VISIBLE || target.isLayoutRequested()) {
            return;
        }

        int direction = distance > 0 ? 1 : -1;
        if (target != this.targetProvider.resolve(direction)) return;
        if (target instanceof ScrollView) {
            target.scrollBy(0, distance);
            this.feedback.run();
        } else if (target instanceof AbsListView) {
            scrollList((AbsListView) target, distance);
        }
    }

    private void scrollList(AbsListView list, int distance) {
        ListAdapter adapter = list.getAdapter();
        if (adapter == null || !CrownScrollController.isStableListWindow(
                adapter.getCount(), list.getCount(), list.getFirstVisiblePosition(),
                list.getChildCount())) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            list.scrollListBy(distance);
        } else {
            list.smoothScrollBy(distance, 1);
        }
        this.feedback.run();
    }

    private static void postOnNextFrame(View view, Runnable action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.postOnAnimation(action);
        } else {
            view.postDelayed(action, 16L);
        }
    }
}
