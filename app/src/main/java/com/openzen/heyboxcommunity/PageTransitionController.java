package com.openzen.heyboxcommunity;

import android.view.View;
import android.widget.FrameLayout;

/** Moves two fully built pages through one transition owned by this controller. */
final class PageTransitionController {
    private Transition current;

    PageTransitionController() {}

    void finishNow() {
        Transition pending = current;
        current = null;
        if (pending != null) pending.finish();
    }

    void cancelNow() {
        Transition pending = current;
        current = null;
        if (pending != null) pending.finish();
    }

    boolean isRunning() {
        return current != null;
    }

    void run(FrameLayout container, View next, boolean forward) {
        if (container == null || next == null) return;
        finishNow();
        Motions.reset(next);
        View old = container.getChildCount() == 0
                ? null : container.getChildAt(container.getChildCount() - 1);
        boolean oldIsLoading = old != null && ("loading".equals(old.getTag())
                || "detail_loading".equals(old.getTag()));
        if (Motions.off() || old == null || old == next || container.getWidth() <= 0) {
            container.removeAllViews();
            container.addView(next, params());
            return;
        }
        if (oldIsLoading) {
            container.removeAllViews();
            container.addView(next, params());
            return;
        }

        final View oldView = old;
        for (int i = container.getChildCount() - 1; i >= 0; i--) {
            if (container.getChildAt(i) != oldView) container.removeViewAt(i);
        }
        if (forward) container.addView(next, params());
        else container.addView(next, Math.max(0, container.indexOfChild(oldView)), params());
        View blocker = new View(container.getContext());
        blocker.setTag("page_transition_blocker");
        blocker.setClickable(true);
        container.addView(blocker, params());

        Transition transition = new Transition(container, oldView, next, blocker);
        current = transition;
        Runnable end = () -> {
            if (current == transition) {
                current = null;
                transition.finish();
            }
        };

        int width = container.getWidth();
        if (Motions.full()) {
            next.setTranslationX(forward ? width : -width * 0.30f);
            next.setAlpha(forward ? 1.0f : 0.88f);
            next.post(() -> {
                if (current != transition) return;
                next.animate().translationX(0.0f).alpha(1.0f)
                        .setDuration(MotionSpec.TRANSITION_FULL_MS)
                        .setInterpolator(MotionSpec.EASE_OUT)
                        .withEndAction(end)
                        .start();
                oldView.animate()
                        .translationX(forward ? -width * 0.30f : width)
                        .alpha(forward ? 0.72f : 1.0f)
                        .setDuration(MotionSpec.TRANSITION_FULL_MS)
                        .setInterpolator(MotionSpec.EASE_OUT)
                        .start();
            });
        } else {
            next.setAlpha(0.0f);
            next.post(() -> {
                if (current != transition) return;
                next.animate().alpha(1.0f)
                        .setDuration(MotionSpec.TRANSITION_LITE_MS)
                        .setInterpolator(MotionSpec.EASE_OUT)
                        .withEndAction(end)
                        .start();
            });
        }
    }

    private static FrameLayout.LayoutParams params() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private static final class Transition {
        private final FrameLayout container;
        private final View oldView;
        private final View nextView;
        private final View blocker;
        private boolean finished;

        Transition(FrameLayout container, View oldView, View nextView, View blocker) {
            this.container = container;
            this.oldView = oldView;
            this.nextView = nextView;
            this.blocker = blocker;
        }

        void finish() {
            if (finished) return;
            finished = true;
            oldView.animate().cancel();
            nextView.animate().cancel();
            if (blocker.getParent() == container) container.removeView(blocker);
            if (oldView.getParent() == container) container.removeView(oldView);
            Motions.reset(oldView);
            Motions.reset(nextView);
        }
    }
}
