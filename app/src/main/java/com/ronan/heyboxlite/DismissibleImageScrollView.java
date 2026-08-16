package com.ronan.heyboxlite;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.ScrollView;

final class DismissibleImageScrollView extends ScrollView {
    private final float pullTouchThreshold;
    private float downRawX;
    private float downRawY;
    private boolean pulling;
    private ImagePullListener pullListener;

    DismissibleImageScrollView(Context context) {
        super(context);
        pullTouchThreshold = ViewConfiguration.get(context).getScaledTouchSlop()
                * ImageDismissPolicy.TOUCH_SLOP_MULTIPLIER;
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    void setPullListener(ImagePullListener listener) {
        pullListener = listener;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                pulling = false;
                if (pullListener != null) pullListener.onInteractionStart();
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!pulling && shouldStartPull(dx, dy)) {
                    pulling = true;
                    cancelScrollGesture(event);
                    ViewParent parent = getParent();
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                }
                if (pulling) {
                    dispatchPull(dx, dy);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (pulling) {
                    finishPull(event, false);
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (pulling) {
                    finishPull(event, true);
                    return true;
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (pulling) {
                    finishPull(event, true);
                    return true;
                }
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean shouldStartPull(float dx, float dy) {
        if (Math.abs(dy) <= pullTouchThreshold
                || Math.abs(dy) <= Math.abs(dx) * 1.18f) {
            return false;
        }
        return dy > 0.0f ? !canScrollVertically(-1) : !canScrollVertically(1);
    }

    private void dispatchPull(float dx, float dy) {
        float distance = ImageDismissPolicy.effectiveDistance(dy, pullTouchThreshold);
        if (pullListener != null) {
            pullListener.onPull(dx * 0.5f, distance,
                    ImageDismissPolicy.progress(distance, getHeight()));
        }
    }

    private void finishPull(MotionEvent event, boolean cancelled) {
        float distance = ImageDismissPolicy.effectiveDistance(
                event.getRawY() - downRawY, pullTouchThreshold);
        boolean dismiss = !cancelled
                && ImageDismissPolicy.shouldDismiss(distance, getHeight());
        pulling = false;
        if (pullListener != null) pullListener.onPullEnd(dismiss);
    }

    private void cancelScrollGesture(MotionEvent event) {
        MotionEvent cancel = MotionEvent.obtain(event);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }
}
