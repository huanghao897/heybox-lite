package com.ronan.heyboxlite;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

final class PullRefreshListView extends ListView {
    private final int touchSlop;
    private final FrameLayout refreshHeaderContainer;
    private final TextView refreshHeader;
    private final int triggerHeight;
    private final int maxPullHeight;
    private final float uiScale;
    private final int mutedColor;
    private final int accentColor;
    private float startX;
    private float startY;
    private boolean trackingPull;
    private boolean pulling;
    private boolean refreshing;
    private ValueAnimator headerAnimator;
    private Runnable refreshAction;

    PullRefreshListView(Context context, int backgroundColor, int mutedColor,
                        int accentColor, float uiScale, float textScale) {
        super(context);
        this.uiScale = uiScale;
        this.mutedColor = mutedColor;
        this.accentColor = accentColor;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.triggerHeight = dp(58);
        this.maxPullHeight = this.triggerHeight + dp(28);

        this.refreshHeaderContainer = new FrameLayout(context);
        this.refreshHeaderContainer.setBackgroundColor(backgroundColor);
        this.refreshHeaderContainer.setMinimumHeight(0);
        this.refreshHeaderContainer.setLayoutParams(new AbsListView.LayoutParams(-1, 0));

        this.refreshHeader = new TextView(context);
        this.refreshHeader.setText("");
        this.refreshHeader.setTextSize(12.0f * textScale);
        this.refreshHeader.setTextColor(mutedColor);
        this.refreshHeader.setGravity(Gravity.CENTER);
        this.refreshHeader.setAlpha(0.0f);
        this.refreshHeaderContainer.addView(this.refreshHeader,
                new FrameLayout.LayoutParams(-1, -1));

        addHeaderView(this.refreshHeaderContainer, null, false);
        hideHeaderImmediately();
    }

    void setPullRefreshAction(Runnable action) {
        this.refreshAction = action;
    }

    boolean isRefreshing() {
        return this.refreshing;
    }

    void setRefreshing(boolean value) {
        if (value) {
            this.refreshing = true;
            this.trackingPull = false;
            this.pulling = false;
            requestParentDisallowIntercept(false);
            cancelHeaderAnimation();
            setHeaderHeight(Math.max(this.triggerHeight, headerHeight()));
            this.refreshHeader.setText("正在刷新...");
            this.refreshHeader.setTextColor(this.accentColor);
            this.refreshHeader.setAlpha(1.0f);
            return;
        }

        this.refreshing = false;
        this.trackingPull = false;
        this.pulling = false;
        requestParentDisallowIntercept(false);
        if (headerHeight() == 0) {
            hideHeaderImmediately();
        } else {
            this.refreshHeader.setText("");
            animateHeaderTo(0);
        }
    }

    void cancelRefresh() {
        this.refreshing = false;
        this.trackingPull = false;
        this.pulling = false;
        requestParentDisallowIntercept(false);
        hideHeaderImmediately();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginTouch(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (this.refreshing || (!this.trackingPull && !this.pulling)) {
                    return super.onTouchEvent(event);
                }
                float dx = event.getX() - this.startX;
                float dy = event.getY() - this.startY;
                if (isHorizontalGesture(dx, dy) || dy <= 0.0f || !isAtTop()) {
                    cancelPullGesture();
                    return super.onTouchEvent(event);
                }
                this.pulling = true;
                requestParentDisallowIntercept(true);
                updatePullHeader(dy);
                return true;
            case MotionEvent.ACTION_UP:
                if (this.pulling) {
                    finishPullGesture(true);
                    performClick();
                    return true;
                }
                clearTouchState();
                break;
            case MotionEvent.ACTION_CANCEL:
                if (this.pulling) {
                    finishPullGesture(false);
                    return true;
                }
                clearTouchState();
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginTouch(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!this.refreshing && this.trackingPull) {
                    float dx = event.getX() - this.startX;
                    float dy = event.getY() - this.startY;
                    if (isHorizontalGesture(dx, dy) || dy <= 0.0f || !isAtTop()) {
                        cancelPullGesture();
                    } else if (dy > this.touchSlop) {
                        this.pulling = true;
                        requestParentDisallowIntercept(true);
                        return true;
                    }
                } else if (this.pulling) {
                    requestParentDisallowIntercept(true);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (this.pulling) {
                    return true;
                }
                clearTouchState();
                break;
            default:
                break;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelRefresh();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void beginTouch(MotionEvent event) {
        cancelHeaderAnimation();
        if (!this.refreshing && headerHeight() > 0) {
            hideHeaderImmediately();
        }
        this.startX = event.getX();
        this.startY = event.getY();
        this.trackingPull = !this.refreshing && isAtTop();
        this.pulling = false;
    }

    private void finishPullGesture(boolean released) {
        boolean shouldRefresh = released && headerHeight() >= this.triggerHeight
                && this.refreshAction != null;
        this.trackingPull = false;
        this.pulling = false;
        requestParentDisallowIntercept(false);
        if (!shouldRefresh) {
            animateHeaderTo(0);
            return;
        }
        setRefreshing(true);
        this.refreshAction.run();
    }

    private void cancelPullGesture() {
        boolean wasPulling = this.pulling;
        this.trackingPull = false;
        this.pulling = false;
        requestParentDisallowIntercept(false);
        if (wasPulling || headerHeight() > 0) {
            animateHeaderTo(0);
        }
    }

    private void clearTouchState() {
        this.trackingPull = false;
        this.pulling = false;
        requestParentDisallowIntercept(false);
    }

    private void updatePullHeader(float dy) {
        int height = Math.min(this.maxPullHeight,
                Math.max(0, Math.round((dy - this.touchSlop) * 0.62f)));
        setHeaderHeight(height);
        if (height == 0) {
            this.refreshHeader.setText("");
            this.refreshHeader.setAlpha(0.0f);
            return;
        }
        boolean ready = height >= this.triggerHeight;
        this.refreshHeader.setText(ready ? "松开刷新" : "下拉刷新");
        this.refreshHeader.setTextColor(ready ? this.accentColor : this.mutedColor);
        this.refreshHeader.setAlpha(Math.min(1.0f, height / (float) this.triggerHeight));
    }

    private boolean isHorizontalGesture(float dx, float dy) {
        return Math.abs(dx) > Math.abs(dy) * 1.15f;
    }

    private boolean isAtTop() {
        if (!canScrollVertically(-1)) {
            return true;
        }
        View firstChild = getChildAt(0);
        return getFirstVisiblePosition() <= 1 && firstChild != null
                && firstChild.getTop() >= getPaddingTop();
    }

    private int headerHeight() {
        ViewGroup.LayoutParams params = this.refreshHeaderContainer.getLayoutParams();
        return params == null ? 0 : Math.max(0, params.height);
    }

    private void setHeaderHeight(int requestedHeight) {
        int height = Math.max(0, requestedHeight);
        ViewGroup.LayoutParams params = this.refreshHeaderContainer.getLayoutParams();
        boolean created = params == null;
        if (params == null) {
            params = new AbsListView.LayoutParams(-1, height);
        }
        if (!created && params.height == height) {
            return;
        }
        params.height = height;
        this.refreshHeaderContainer.setLayoutParams(params);
        this.refreshHeaderContainer.requestLayout();
    }

    private void animateHeaderTo(int target) {
        int normalizedTarget = Math.max(0, target);
        int start = headerHeight();
        cancelHeaderAnimation();
        if (start == normalizedTarget) {
            if (normalizedTarget == 0) hideHeaderImmediately();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofInt(start, normalizedTarget);
        this.headerAnimator = animator;
        animator.setDuration(160L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            if (this.headerAnimator != animator) return;
            int height = (Integer) value.getAnimatedValue();
            setHeaderHeight(height);
            this.refreshHeader.setAlpha(this.triggerHeight <= 0 ? 0.0f
                    : Math.min(1.0f, height / (float) this.triggerHeight));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (PullRefreshListView.this.headerAnimator != animator) return;
                PullRefreshListView.this.headerAnimator = null;
                if (normalizedTarget == 0) {
                    hideHeaderImmediately();
                } else {
                    setHeaderHeight(normalizedTarget);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (PullRefreshListView.this.headerAnimator == animator) {
                    PullRefreshListView.this.headerAnimator = null;
                }
            }
        });
        animator.start();
    }

    private void cancelHeaderAnimation() {
        ValueAnimator animator = this.headerAnimator;
        this.headerAnimator = null;
        if (animator != null) animator.cancel();
    }

    private void hideHeaderImmediately() {
        cancelHeaderAnimation();
        setHeaderHeight(0);
        this.refreshHeader.setText("");
        this.refreshHeader.setTextColor(this.mutedColor);
        this.refreshHeader.setAlpha(0.0f);
    }

    private void requestParentDisallowIntercept(boolean disallow) {
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(disallow);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density * this.uiScale);
    }
}
