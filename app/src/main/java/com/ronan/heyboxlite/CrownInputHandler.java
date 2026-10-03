package com.ronan.heyboxlite;

import android.app.Activity;
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
        boolean scrollCompose(int distance);
    }

    /** Android rotary encoder source bit; kept as a literal for minSdk 14 builds. */
    private static final int SOURCE_ROTARY_ENCODER = 0x00400000;
    private static final int AXIS_ROTARY_SCROLL = 26;

    private final Activity activity;
    private final SessionStore session;
    private final Host host;
    private final CrownScrollController scrollController = new CrownScrollController();
    private final CrownScrollDispatcher dispatcher =
            new CrownScrollDispatcher(this::findScrollTarget, this::performFeedback);
    private final CrownHapticProvider hapticProvider;
    private final float axisGain;

    CrownInputHandler(Activity activity, SessionStore session, Host host) {
        this.activity = activity;
        this.session = session;
        this.host = host;
        this.hapticProvider = new CrownHapticProvider(activity, session);
        this.axisGain = CrownDeviceProfile.scrollAxisGain(activity);
    }

    boolean handle(MotionEvent event) {
        if (event == null || event.getActionMasked() != MotionEvent.ACTION_SCROLL) return false;
        float axis = rotaryAxis(event);
        if (axis == 0f) return false;
        if (!session.crownScrollEnabled()) {
            reset();
            // Leave the event to the focused view/system when Lite crown scrolling is off.
            return false;
        }
        int baseStep = dp(12);
        int speed = session.crownScrollSpeed();
        int distance = scrollController.distance(axis, baseStep, speed, axisGain);
        if (distance != 0) {
            if (host.scrollCompose(distance)) {
                hapticProvider.performScrollTick(host.contentRoot());
            } else {
                dispatcher.enqueue(distance, scrollController.frameLimit(baseStep, speed, axisGain));
            }
        }
        return true;
    }

    /**
     * Wear OS normally reports the crown/bezel on AXIS_SCROLL. Some Samsung
     * firmware exposes the same rotary source through VSCROLL or HSCROLL,
     * matching the generic Android rotary contract. Read the source-specific
     * axis first and keep the legacy scroll-wheel fallback for older watches.
     */
    private static float rotaryAxis(MotionEvent event) {
        boolean rotarySource = (event.getSource() & SOURCE_ROTARY_ENCODER)
                == SOURCE_ROTARY_ENCODER;
        if (rotarySource) {
            float axis = event.getAxisValue(AXIS_ROTARY_SCROLL);
            if (axis != 0f) return axis;
            axis = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (axis != 0f) return axis;
            return event.getAxisValue(MotionEvent.AXIS_HSCROLL);
        }

        float axis = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if (axis != 0f) return axis;
        axis = event.getAxisValue(AXIS_ROTARY_SCROLL);
        if (axis != 0f) return axis;
        return event.getAxisValue(MotionEvent.AXIS_HSCROLL);
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
        this.hapticProvider.performScrollTick(host.contentRoot());
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
