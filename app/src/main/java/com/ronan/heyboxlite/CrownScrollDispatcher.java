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
        if (this.pendingDistance != 0
                && Integer.signum(this.pendingDistance) != Integer.signum(distance)) {
            this.pendingDistance = 0;
        }
        int backlogLimit = this.pendingLimit * 3;
        this.pendingDistance = CrownScrollController.coalesceBounded(
                this.pendingDistance, distance, backlogLimit);
        if (!this.posted) {
            schedule(target);
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
        this.posted = false;
        if (target == null || this.pendingDistance == 0) {
            clearPending();
            return;
        }

        int direction = this.pendingDistance > 0 ? 1 : -1;
        if (target.getParent() == null || target.getVisibility() != View.VISIBLE) {
            clearPending();
            return;
        }
        if (target.getWidth() <= 0 || target.getHeight() <= 0) {
            schedule(target);
            return;
        }
        if (target.isLayoutRequested()) {
            schedule(target);
            return;
        }
        if (target != this.targetProvider.resolve(direction)
                || !target.canScrollVertically(direction)) {
            clearPending();
            return;
        }

        int distance = CrownScrollController.coalesceBounded(
                0, this.pendingDistance, this.pendingLimit);
        boolean applied = false;
        if (target instanceof ScrollView) {
            target.scrollBy(0, distance);
            applied = true;
        } else if (target instanceof AbsListView) {
            applied = scrollList((AbsListView) target, distance);
        }
        if (!applied) {
            clearPending();
            return;
        }
        this.pendingDistance -= distance;
        this.feedback.run();
        if (this.pendingDistance == 0) {
            clearPending();
        } else {
            schedule(target);
        }
    }

    private boolean scrollList(AbsListView list, int distance) {
        ListAdapter adapter = list.getAdapter();
        if (adapter == null || adapter.getCount() == 0) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            list.scrollListBy(distance);
        } else {
            list.smoothScrollBy(distance, 1);
        }
        return true;
    }

    private void schedule(View target) {
        if (this.posted || target == null) return;
        this.posted = true;
        postOnNextFrame(target, this.applyPending);
    }

    private void clearPending() {
        if (this.pendingTarget != null && this.posted) {
            this.pendingTarget.removeCallbacks(this.applyPending);
        }
        this.pendingTarget = null;
        this.pendingDistance = 0;
        this.pendingLimit = 0;
        this.posted = false;
    }

    private static void postOnNextFrame(View view, Runnable action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.postOnAnimation(action);
        } else {
            view.postDelayed(action, 16L);
        }
    }
}
