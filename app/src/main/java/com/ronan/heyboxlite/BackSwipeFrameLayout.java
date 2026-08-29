package com.ronan.heyboxlite;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

final class BackSwipeFrameLayout extends FrameLayout {
    static final String TRANSITION_OVERLAY_TAG = "shell_transition_overlay";

    private static final int MODE_NONE = 0;
    private static final int MODE_TOP_LEVEL = 1;
    private static final int MODE_BACK = 2;

    interface Host {
        String shellScreenKey();

        boolean canStartShellSwipe();

        int shellTopLevelIndex();

        boolean hasShellSwipeTarget(float distanceX);

        String shellSwipeTarget(boolean topLevel, int direction);

        void captureShellState(String screenKey, View currentView);

        boolean shouldCaptureShellState();

        Preview createShellPreview(String targetKey, boolean back);

        void prepareShellView(View view);

        int shellDp(int value);

        void setShellGestureActive(boolean active);

        boolean compactShellMotion();

        void navigateAfterShellSwipe(boolean topLevel, int direction,
                                     String targetKey, Runnable settled);

        void adoptShellPreview(String targetKey);
    }

    static final class Preview {
        final View view;
        final boolean realView;

        Preview(View view, boolean realView) {
            this.view = view;
            this.realView = realView;
        }
    }

    private final Host host;
    private final int touchSlop;
    private float startX;
    private float startY;
    private long startTime;
    private boolean tracking;
    private boolean dragging;
    private int gestureMode = MODE_NONE;
    private int gestureDirection;
    private View dragChild;
    private View previewChild;
    private boolean previewIsReal;
    private String displayedScreenKey = "";
    private ValueAnimator swipeAnimator;
    private boolean shellInterceptionCandidate;

    BackSwipeFrameLayout(Context context, Host host) {
        super(context);
        this.host = host;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        if (child != previewChild) displayedScreenKey = host.shellScreenKey();
    }

    @Override
    public void removeAllViews() {
        if (host.shouldCaptureShellState()) {
            host.captureShellState(displayedScreenKey, currentShellChild());
        }
        super.removeAllViews();
        previewChild = null;
        previewIsReal = false;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept && shellInterceptionCandidate) return;
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!host.canStartShellSwipe()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginTracking(event);
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                tracking = false;
                dragging = false;
                shellInterceptionCandidate = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) return false;
                float dx = event.getX() - startX;
                float dy = event.getY() - startY;
                if (!startShellDragIfReady(dx, dy)) return false;
                dragShellTo(dx);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!dragging && !host.canStartShellSwipe()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginTracking(event);
                break;
            case MotionEvent.ACTION_UP:
                if (dragging) finishShellSwipe(event);
                else performClick();
                tracking = false;
                dragging = false;
                shellInterceptionCandidate = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - startX;
                float dy = event.getY() - startY;
                if (dragging || startShellDragIfReady(dx, dy)) dragShellTo(dx);
                break;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) settleShellDrag(0.0f, this::resetShellDrag);
                tracking = false;
                dragging = false;
                shellInterceptionCandidate = false;
                break;
            default:
                break;
        }
        return true;
    }

    private void beginTracking(MotionEvent event) {
        cancelSwipeAnimator();
        startX = event.getX();
        startY = event.getY();
        startTime = event.getEventTime();
        tracking = true;
        dragging = false;
        gestureMode = MODE_NONE;
        dragChild = null;
        shellInterceptionCandidate = host.canStartShellSwipe();
        super.requestDisallowInterceptTouchEvent(false);
    }

    private boolean startShellDragIfReady(float dx, float dy) {
        if (!tracking) return false;
        if (Math.abs(dy) > touchSlop * 2 && Math.abs(dy) > Math.abs(dx)) {
            tracking = false;
            shellInterceptionCandidate = false;
            return false;
        }
        boolean compact = host.compactShellMotion();
        if (Math.abs(dx) <= touchSlop * (compact
                ? MotionSpec.WATCH_TOUCH_SLOP_MULTIPLIER : 2)
                || Math.abs(dx) <= Math.abs(dy) * (compact
                ? MotionSpec.WATCH_AXIS_RATIO : 1.18f)
                || !host.hasShellSwipeTarget(dx)) {
            return false;
        }
        if (compact && !isSafeSwipeOrigin(dx)) return false;
        gestureMode = host.shellTopLevelIndex() >= 0 ? MODE_TOP_LEVEL : MODE_BACK;
        gestureDirection = gestureMode == MODE_BACK || dx >= 0.0f ? -1 : 1;
        dragChild = currentShellChild();
        if (dragChild == null) return false;
        host.prepareShellView(dragChild);
        installShellPreview(targetScreenKey());
        cancelSwipeAnimator();
        host.setShellGestureActive(true);
        dragging = true;
        return true;
    }

    private View currentShellChild() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child != previewChild && !TRANSITION_OVERLAY_TAG.equals(child.getTag())) {
                return child;
            }
        }
        return null;
    }

    private String targetScreenKey() {
        return host.shellSwipeTarget(gestureMode == MODE_TOP_LEVEL, gestureDirection);
    }

    private void installShellPreview(String targetKey) {
        removeShellPreview();
        Preview preview = host.createShellPreview(targetKey, gestureMode == MODE_BACK);
        previewChild = preview.view;
        previewIsReal = preview.realView;
        host.prepareShellView(previewChild);
        previewChild.setTranslationX(gestureDirection * Math.max(1, getWidth()));
        super.addView(previewChild, 0, new LayoutParams(-1, -1));
    }

    private void removeShellPreview() {
        if (previewChild == null) return;
        super.removeView(previewChild);
        previewChild = null;
        previewIsReal = false;
    }

    private void dragShellTo(float dx) {
        if (dragChild == null) return;
        int width = Math.max(1, getWidth());
        float exitSign = -gestureDirection;
        float visualDx = host.compactShellMotion()
                ? dx * MotionSpec.WATCH_DRAG_RESPONSE : dx;
        float offset = exitSign < 0.0f
                ? Math.max(-width, Math.min(0.0f, visualDx))
                : Math.max(0.0f, Math.min(width, visualDx));
        dragChild.setTranslationX(offset);
        dragChild.setAlpha(1.0f);
        if (previewChild != null) {
            previewChild.setTranslationX(gestureDirection * width + offset);
            previewChild.setAlpha(1.0f);
        }
    }

    private void finishShellSwipe(MotionEvent event) {
        float dx = event.getX() - startX;
        float dy = event.getY() - startY;
        long duration = Math.max(1L, event.getEventTime() - startTime);
        float velocity = dx * 1000.0f / duration;
        float offset = dragChild == null ? 0.0f : dragChild.getTranslationX();
        boolean compact = host.compactShellMotion();
        boolean enoughDistance = Math.abs(offset) > Math.max(host.shellDp(52),
                getWidth() * (compact ? MotionSpec.WATCH_COMMIT_DISTANCE_RATIO : 0.24f));
        boolean enoughVelocity = -gestureDirection * velocity
                > host.shellDp(compact ? MotionSpec.WATCH_COMMIT_VELOCITY_DP : 300);
        if (Math.abs(dx) <= Math.abs(dy) * (compact
                ? MotionSpec.WATCH_AXIS_RATIO : 1.1f)
                || (!enoughDistance && !enoughVelocity)) {
            settleShellDrag(0.0f, this::resetShellDrag);
            return;
        }
        settleShellDrag(-gestureDirection * Math.max(1, getWidth()),
                this::completeShellSwipe);
    }

    private void settleShellDrag(float targetX, Runnable end) {
        if (dragChild == null) {
            resetShellDrag();
            end.run();
            return;
        }
        cancelSwipeAnimator();
        float fromX = dragChild.getTranslationX();
        if (Motions.off() || Math.abs(fromX - targetX) < host.shellDp(1)) {
            dragChild.setTranslationX(targetX);
            if (previewChild != null) {
                previewChild.setTranslationX(gestureDirection * Math.max(1, getWidth())
                        + targetX);
            }
            end.run();
            return;
        }
        int distance = Math.round(Math.abs(fromX - targetX));
        int duration = host.compactShellMotion()
                ? Math.max((int) MotionSpec.WATCH_SETTLE_MIN_MS,
                Math.min((int) MotionSpec.WATCH_SETTLE_MAX_MS, distance / 2))
                : Math.max(120, Math.min(260, distance / 3));
        swipeAnimator = ValueAnimator.ofFloat(fromX, targetX);
        swipeAnimator.setDuration(duration);
        swipeAnimator.setInterpolator(MotionSpec.EASE_OUT);
        swipeAnimator.addUpdateListener(value -> {
            float x = (Float) value.getAnimatedValue();
            int width = Math.max(1, getWidth());
            if (dragChild != null) {
                dragChild.setTranslationX(x);
                dragChild.setAlpha(1.0f);
            }
            if (previewChild != null) {
                previewChild.setTranslationX(gestureDirection * width + x);
                previewChild.setAlpha(1.0f);
            }
        });
        swipeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                swipeAnimator = null;
                end.run();
            }
        });
        swipeAnimator.start();
    }

    private void completeShellSwipe() {
        String targetKey = targetScreenKey();
        if (previewIsReal) {
            completeRealShellSwipe(targetKey);
            return;
        }
        host.navigateAfterShellSwipe(gestureMode == MODE_TOP_LEVEL,
                gestureDirection, targetKey, () -> {
                    View newChild = currentShellChild();
                    if (newChild == null) {
                        resetShellDrag();
                        return;
                    }
                    dragChild = newChild;
                    newChild.setTranslationX(0.0f);
                    newChild.setAlpha(1.0f);
                    resetShellDrag();
                });
    }

    private void completeRealShellSwipe(String targetKey) {
        View oldChild = dragChild;
        View target = previewChild;
        if (target == null) {
            resetShellDrag();
            return;
        }
        Motions.resetTree(target);
        if (oldChild != null && oldChild.getParent() == this) super.removeView(oldChild);
        host.adoptShellPreview(targetKey);
        displayedScreenKey = targetKey;
        previewChild = null;
        previewIsReal = false;
        dragChild = null;
        gestureMode = MODE_NONE;
        gestureDirection = 0;
        dragging = false;
        host.setShellGestureActive(false);
    }

    private void resetShellDrag() {
        cancelSwipeAnimator();
        if (dragChild != null) {
            dragChild.setTranslationX(0.0f);
            dragChild.setAlpha(1.0f);
        }
        removeShellPreview();
        dragChild = null;
        gestureMode = MODE_NONE;
        gestureDirection = 0;
        dragging = false;
        host.setShellGestureActive(false);
    }

    private void cancelSwipeAnimator() {
        if (swipeAnimator == null) return;
        swipeAnimator.removeAllListeners();
        swipeAnimator.cancel();
        swipeAnimator = null;
    }

    private boolean isSafeSwipeOrigin(float dx) {
        int width = Math.max(1, getWidth());
        int topLevel = host.shellTopLevelIndex();
        if (topLevel < 0) return true;
        if (dx < 0.0f) return startX >= width * 0.54f;
        return startX <= width * 0.46f;
    }

    void cancelMotion() {
        tracking = false;
        dragging = false;
        shellInterceptionCandidate = false;
        resetShellDrag();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
