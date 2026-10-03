package com.ronan.heyboxlite;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/** 横向翻页容器：水平拖动翻图，垂直手势交还给正文滚动，翻页时通知指示器。 */
final class ImagePagerCore extends ViewGroup {
    interface PagerListener {
        void onPage(int page);
    }

    interface GestureGuard {
        boolean preserveSecondTap(float x, float y, long eventTime);
        boolean pagingBlocked();
    }

    private final int touchSlop;
    private final Dp dp;
    private final PagerListener listener;
    private final GestureGuard gestureGuard;
    private float startX;
    private float startY;
    private long startTime;
    private int startScrollX;
    private int page;
    private boolean dragging;
    private boolean ignoring;
    private boolean preservingSecondTap;
    private int gestureAxis = GestureAxisLock.NONE;
    private ValueAnimator settleAnimator;

    ImagePagerCore(Context context, Dp dp, PagerListener listener) {
        this(context, dp, listener, null);
    }

    ImagePagerCore(Context context, Dp dp, PagerListener listener,
                   GestureGuard gestureGuard) {
        super(context);
        this.dp = dp;
        this.listener = listener;
        this.gestureGuard = gestureGuard;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    void setPage(int page) {
        this.page = Math.max(0, page);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        int childWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int childHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(childWidth, childHeight);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).layout(i * width, 0, (i + 1) * width, height);
        }
        if (!this.dragging && this.settleAnimator == null) {
            scrollTo(this.page * width, 0);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (getChildCount() < 2) {
            return false;
        }
        switch (event.getActionMasked()) {
            case 0:
                cancelSettle();
                this.startX = event.getX();
                this.startY = event.getY();
                this.startTime = event.getEventTime();
                this.startScrollX = getScrollX();
                this.dragging = false;
                this.ignoring = false;
                this.gestureAxis = GestureAxisLock.NONE;
                this.preservingSecondTap = this.gestureGuard != null
                        && this.gestureGuard.preserveSecondTap(
                        event.getX(), event.getY(), event.getEventTime());
                // Do not block the shell on ACTION_DOWN. The outer back
                // gesture needs to see the stream until this pager has
                // positively identified a horizontal page drag.
                requestParentTouch(false);
                break;
            case 1:
            case 3:
                this.gestureAxis = GestureAxisLock.NONE;
                requestParentTouch(false);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                this.gestureAxis = GestureAxisLock.MULTI_TOUCH;
                this.ignoring = true;
                this.dragging = false;
                requestParentTouch(false);
                break;
            case 2:
                if (this.ignoring) {
                    break;
                }
                if (this.preservingSecondTap || event.getPointerCount() > 1
                        || (this.gestureGuard != null
                        && this.gestureGuard.pagingBlocked())) {
                    this.ignoring = true;
                    requestParentTouch(false);
                    break;
                }
                float dx = event.getX() - this.startX;
                float dy = event.getY() - this.startY;
                this.gestureAxis = GestureAxisLock.resolve(this.gestureAxis, dx, dy,
                        this.touchSlop, this.touchSlop, 1.12f,
                        event.getPointerCount() > 1);
                if (this.gestureAxis == GestureAxisLock.VERTICAL
                        || this.gestureAxis == GestureAxisLock.MULTI_TOUCH) {
                    this.ignoring = true;
                    this.dragging = false;
                    requestParentTouch(false);
                    break;
                }
                if (!this.dragging) {
                    if (Math.abs(dy) > this.touchSlop && Math.abs(dy) > Math.abs(dx)) {
                        this.ignoring = true;
                        requestParentTouch(false);
                    } else if (Math.abs(dx) > this.touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        this.dragging = true;
                        this.startX = event.getX();
                        this.startTime = event.getEventTime();
                        this.startScrollX = getScrollX();
                        requestParentTouch(true);
                    }
                }
                break;
        }
        return this.dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case 0:
                cancelSettle();
                this.startX = event.getX();
                this.startY = event.getY();
                this.startTime = event.getEventTime();
                this.startScrollX = getScrollX();
                this.dragging = false;
                this.ignoring = false;
                this.gestureAxis = GestureAxisLock.NONE;
                requestParentTouch(false);
                break;
            case 1:
                if (this.dragging && !this.ignoring
                        && this.gestureAxis == GestureAxisLock.HORIZONTAL) {
                    finishDrag(event);
                } else {
                    this.dragging = false;
                    settleTo(this.page, true);
                }
                this.ignoring = false;
                this.gestureAxis = GestureAxisLock.NONE;
                requestParentTouch(false);
                performClick();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                this.gestureAxis = GestureAxisLock.MULTI_TOUCH;
                this.dragging = false;
                this.ignoring = true;
                requestParentTouch(false);
                break;
            case 2:
                if (this.ignoring) break;
                float dx = event.getX() - this.startX;
                float dy = event.getY() - this.startY;
                this.gestureAxis = GestureAxisLock.resolve(this.gestureAxis, dx, dy,
                        this.touchSlop, this.touchSlop, 1.12f,
                        event.getPointerCount() > 1);
                if (this.gestureAxis == GestureAxisLock.VERTICAL
                        || this.gestureAxis == GestureAxisLock.MULTI_TOUCH) {
                    this.ignoring = true;
                    this.dragging = false;
                    requestParentTouch(false);
                    break;
                }
                if (!this.dragging && this.gestureAxis == GestureAxisLock.HORIZONTAL) {
                    this.dragging = true;
                    requestParentTouch(true);
                }
                if (this.dragging) {
                    dragTo(dx);
                }
                break;
            case 3:
                this.dragging = false;
                this.ignoring = true;
                this.gestureAxis = GestureAxisLock.NONE;
                settleTo(this.page, true);
                requestParentTouch(false);
                break;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void dragTo(float dx) {
        int width = Math.max(1, getWidth());
        int max = Math.max(0, (getChildCount() - 1) * width);
        int next = Math.max(0, Math.min(max, this.startScrollX - Math.round(dx)));
        scrollTo(next, 0);
    }

    private void finishDrag(MotionEvent event) {
        int width = Math.max(1, getWidth());
        float dx = event.getX() - this.startX;
        long duration = Math.max(1L, event.getEventTime() - this.startTime);
        float velocity = (dx * 1000.0f) / duration;
        int target = Math.round(getScrollX() / (float) width);
        if (Math.abs(velocity) > dp.dp(320)) {
            target = velocity < 0.0f ? this.page + 1 : this.page - 1;
        }
        this.dragging = false;
        settleTo(Math.max(0, Math.min(getChildCount() - 1, target)), true);
    }

    private void settleTo(int next, boolean animate) {
        cancelSettle();
        if (next != this.page) {
            this.page = next;
            if (this.listener != null) {
                this.listener.onPage(next);
            }
        }
        int destination = next * Math.max(1, getWidth());
        if (!animate || Math.abs(destination - getScrollX()) < dp.dp(2)) {
            scrollTo(destination, 0);
            return;
        }
        int fromX = getScrollX();
        int duration = Math.max(150, Math.min(280, Math.abs(destination - fromX) / 3));
        this.settleAnimator = ValueAnimator.ofInt(fromX, destination);
        this.settleAnimator.setDuration(duration);
        this.settleAnimator.setInterpolator(new DecelerateInterpolator());
        this.settleAnimator.addUpdateListener(value -> {
            scrollTo(((Integer) value.getAnimatedValue()).intValue(), 0);
        });
        this.settleAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (settleAnimator == animation) settleAnimator = null;
            }
        });
        this.settleAnimator.start();
    }

    void cancelSettle() {
        ValueAnimator animator = this.settleAnimator;
        this.settleAnimator = null;
        if (animator != null) animator.cancel();
        requestParentTouch(false);
    }

    private void requestParentTouch(boolean disallow) {
        ViewGroup parent = getParent() instanceof ViewGroup
                ? (ViewGroup) getParent() : null;
        if (parent != null) parent.requestDisallowInterceptTouchEvent(disallow);
    }

    @Override protected void onDetachedFromWindow() {
        this.dragging = false;
        this.ignoring = true;
        this.gestureAxis = GestureAxisLock.NONE;
        cancelSettle();
        super.onDetachedFromWindow();
    }
}
