package com.ronan.heyboxlite;

import android.os.Build;
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
        run(container, next, forward, false);
    }

    /** push=true 时新旧页面同速整宽平移（顶层左右切换）；否则层级式覆盖推入/滑出。 */
    void run(FrameLayout container, View next, boolean forward, boolean push) {
        if (container == null || next == null) return;
        finishNow();
        Motions.reset(next);
        if (Motions.off() || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            container.removeAllViews();
            container.addView(next, params());
            return;
        }
        View old = container.getChildCount() == 0
                ? null : container.getChildAt(container.getChildCount() - 1);
        boolean oldIsLoading = old != null && ("loading".equals(old.getTag())
                || "detail_loading".equals(old.getTag()));
        if (old == null || old == next || oldIsLoading || container.getWidth() <= 0) {
            container.removeAllViews();
            container.addView(next, params());
            return;
        }

        final View oldView = old;
        Motions.reset(oldView);
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
        if (!Motions.full()) {
            runLite(transition, end, width, forward, push);
            return;
        }
        if (push) {
            next.setTranslationX(forward ? width : -width);
            next.setAlpha(1.0f);
            next.post(() -> {
                if (current != transition) return;
                next.animate().translationX(0.0f)
                        .setDuration(MotionSpec.TRANSITION_FULL_MS)
                        .setInterpolator(MotionSpec.EASE_OUT)
                        .withEndAction(end)
                        .start();
                oldView.animate()
                        .translationX(forward ? -width : width)
                        .setDuration(MotionSpec.TRANSITION_FULL_MS)
                        .setInterpolator(MotionSpec.EASE_OUT)
                        .start();
            });
            return;
        }
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
    }

    private void runLite(Transition transition, Runnable end, int width,
                         boolean forward, boolean push) {
        View oldView = transition.oldView;
        View next = transition.nextView;
        float nextOffset;
        float oldOffset;
        if (push) {
            nextOffset = (forward ? 1.0f : -1.0f) * width * 0.14f;
            oldOffset = (forward ? -1.0f : 1.0f) * width * 0.08f;
        } else {
            nextOffset = (forward ? 1.0f : -1.0f) * width * 0.10f;
            oldOffset = (forward ? -1.0f : 1.0f) * width * 0.12f;
        }
        next.setTranslationX(nextOffset);
        next.setAlpha(0.82f);
        next.post(() -> {
            if (current != transition) return;
            next.animate().translationX(0.0f).alpha(1.0f)
                    .setDuration(MotionSpec.TRANSITION_LITE_MS)
                    .setInterpolator(MotionSpec.EASE_OUT)
                    .withEndAction(end)
                    .start();
            oldView.animate().translationX(oldOffset).alpha(0.0f)
                    .setDuration(MotionSpec.TRANSITION_LITE_MS)
                    .setInterpolator(MotionSpec.EASE_OUT)
                    .start();
        });
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
