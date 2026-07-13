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

    private final int touchSlop;
    private final Dp dp;
    private final PagerListener listener;
    private float startX;
    private float startY;
    private long startTime;
    private int startScrollX;
    private int page;
    private boolean dragging;
    private boolean ignoring;
    private ValueAnimator settleAnimator;

    ImagePagerCore(Context context, Dp dp, PagerListener listener) {
        super(context);
        this.dp = dp;
        this.listener = listener;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
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
        scrollTo(this.page * width, 0);
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
                // 先声明占用，判定为垂直手势后再交还给正文滚动
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case 1:
            case 3:
                if (!this.dragging) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
            case 2:
                if (this.ignoring) {
                    break;
                }
                float dx = event.getX() - this.startX;
                float dy = event.getY() - this.startY;
                if (!this.dragging) {
                    if (Math.abs(dy) > this.touchSlop && Math.abs(dy) > Math.abs(dx)) {
                        this.ignoring = true;
                        getParent().requestDisallowInterceptTouchEvent(false);
                    } else if (Math.abs(dx) > this.touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        this.dragging = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
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
                this.dragging = true;
                break;
            case 1:
                finishDrag(event);
                performClick();
                break;
            case 2:
                dragTo(event.getX() - this.startX);
                break;
            case 3:
                this.dragging = false;
                settleTo(this.page, true);
                break;
        }
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
        this.settleAnimator.start();
    }

    private void cancelSettle() {
        if (this.settleAnimator != null) {
            this.settleAnimator.cancel();
            this.settleAnimator = null;
        }
    }
}
