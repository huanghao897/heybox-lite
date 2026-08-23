package com.ronan.heyboxlite;

import android.app.Activity;
import android.os.Build;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ScrollView;

final class CrownInputHandler {
    interface Host {
        String screen();
        View detailScrollTarget();
        View feedScrollTarget();
        View searchScrollTarget();
        View contentRoot();
    }

    private static final int AXIS_ROTARY_SCROLL = 26;

    private final Activity activity;
    private final SessionStore session;
    private final Host host;
    private final CrownScrollController scrollController = new CrownScrollController();
    private final CrownScrollDispatcher dispatcher =
            new CrownScrollDispatcher(this::findScrollTarget, this::performFeedback);
    private long lastFeedbackAt;

    CrownInputHandler(Activity activity, SessionStore session, Host host) {
        this.activity = activity;
        this.session = session;
        this.host = host;
    }

    boolean handle(MotionEvent event) {
        if (event == null || event.getActionMasked() != MotionEvent.ACTION_SCROLL) return false;
        float axis = event.getAxisValue(AXIS_ROTARY_SCROLL);
        if (axis == 0f) axis = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if (axis == 0f) return false;
        if (!session.crownScrollEnabled()) {
            reset();
            return true;
        }
        int baseStep = dp(28);
        int speed = session.crownScrollSpeed();
        int distance = scrollController.distance(axis, baseStep, speed);
        if (distance != 0) {
            dispatcher.enqueue(distance, scrollController.frameLimit(baseStep, speed));
        }
        return true;
    }

    CrownScrollController scrollController() {
        return scrollController;
    }

    void cancel() {
        dispatcher.cancel();
    }

    void reset() {
        scrollController.reset();
        dispatcher.cancel();
    }

    private View findScrollTarget(int direction) {
        View target;
        String screen = host.screen();
        if ("detail".equals(screen)) {
            target = host.detailScrollTarget();
        } else if ("feed".equals(screen)) {
            target = host.feedScrollTarget();
        } else if ("search".equals(screen)) {
            target = host.searchScrollTarget();
        } else {
            target = findScrollableView(host.contentRoot(), direction);
        }
        if (target == null || !target.canScrollVertically(direction)) {
            target = findScrollableView(host.contentRoot(), direction);
        }
        return target;
    }

    private void performFeedback() {
        if (!session.crownHapticsEnabled()) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastFeedbackAt < 36L) return;
        lastFeedbackAt = now;
        int effect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                ? HapticFeedbackConstants.CLOCK_TICK
                : HapticFeedbackConstants.KEYBOARD_TAP;
        View content = host.contentRoot();
        View target = content == null ? activity.getWindow().getDecorView() : content;
        target.performHapticFeedback(effect);
    }

    private static View findScrollableView(View view, int direction) {
        if (view == null || view.getVisibility() != View.VISIBLE) return null;
        if ((view instanceof ScrollView || view instanceof AbsListView)
                && view.canScrollVertically(direction)) {
            return view;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = group.getChildCount() - 1; index >= 0; index--) {
            View target = findScrollableView(group.getChildAt(index), direction);
            if (target != null) return target;
        }
        return null;
    }

    private int dp(int value) {
        float scale = session.uiScale() / 100f;
        return Math.round(value * activity.getResources().getDisplayMetrics().density * scale);
    }
}
