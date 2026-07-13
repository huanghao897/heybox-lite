package com.ronan.heyboxlite;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;

final class PullRefreshListView extends ListView {
    private final int touchSlop;
    private final TextView refreshHeader;
    private final int triggerHeight;
    private final float uiScale;
    private final int mutedColor;
    private final int accentColor;
    private float startX;
    private float startY;
    private boolean trackingPull;
    private boolean pulling;
    private boolean refreshing;
    private Runnable refreshAction;

    PullRefreshListView(Context context, int backgroundColor, int mutedColor,
                        int accentColor, float uiScale, float textScale) {
        super(context);
        this.uiScale = uiScale;
        this.mutedColor = mutedColor;
        this.accentColor = accentColor;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        triggerHeight = dp(58);
        refreshHeader = new TextView(context);
        refreshHeader.setText("下拉刷新");
        refreshHeader.setTextSize(12.0f * textScale);
        refreshHeader.setTextColor(mutedColor);
        refreshHeader.setGravity(Gravity.CENTER);
        refreshHeader.setAlpha(0.0f);
        refreshHeader.setBackgroundColor(backgroundColor);
        addHeaderView(refreshHeader, null, false);
        setHeaderHeight(0);
    }

    void setPullRefreshAction(Runnable action) {
        refreshAction = action;
    }

    void setRefreshing(boolean value) {
        refreshing = value;
        refreshHeader.animate().cancel();
        if (value) {
            refreshHeader.setText("正在刷新...");
            refreshHeader.setTextColor(accentColor);
            setHeaderHeight(Math.max(triggerHeight, headerHeight()));
            refreshHeader.setAlpha(1.0f);
            return;
        }
        refreshHeader.setText("下拉刷新");
        refreshHeader.setTextColor(mutedColor);
        if (!pulling) animateHeaderTo(0);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!pulling && !refreshing && event.getActionMasked() == MotionEvent.ACTION_MOVE
                && isAtTop() && event.getY() - startY > touchSlop) {
            trackingPull = true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                startY = event.getY();
                trackingPull = isAtTop() && !refreshing;
                pulling = false;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (pulling) {
                    boolean shouldRefresh = headerHeight() >= triggerHeight;
                    pulling = false;
                    trackingPull = false;
                    if (shouldRefresh) {
                        setRefreshing(true);
                        if (refreshAction != null) refreshAction.run();
                        return true;
                    }
                    animateHeaderTo(0);
                    return true;
                }
                trackingPull = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - startX;
                float dy = event.getY() - startY;
                if (Math.abs(dx) > Math.abs(dy) * 1.15f) {
                    trackingPull = false;
                } else if ((trackingPull || pulling) && dy > touchSlop * 0.55f
                        && isAtTop() && !refreshing) {
                    pulling = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    int height = Math.min(triggerHeight + dp(28),
                            Math.round((dy - touchSlop * 0.55f) * 0.62f));
                    setHeaderHeight(Math.max(0, height));
                    refreshHeader.setText(height >= triggerHeight ? "松开刷新" : "下拉刷新");
                    refreshHeader.setTextColor(height >= triggerHeight ? accentColor : mutedColor);
                    refreshHeader.setAlpha(Math.min(1.0f, height / (float) triggerHeight));
                    return true;
                }
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
                startX = event.getX();
                startY = event.getY();
                trackingPull = isAtTop() && !refreshing;
                pulling = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!refreshing) {
                    float dx = event.getX() - startX;
                    float dy = event.getY() - startY;
                    if (isAtTop() && dy > touchSlop * 0.75f
                            && Math.abs(dy) > Math.abs(dx) * 1.12f) {
                        trackingPull = true;
                        return true;
                    }
                }
                break;
            default:
                break;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private boolean isAtTop() {
        if (getChildCount() != 0 && canScrollVertically(-1)) {
            return getFirstVisiblePosition() <= 1 && getChildAt(0) != null
                    && getChildAt(0).getTop() >= getPaddingTop();
        }
        return true;
    }

    private int headerHeight() {
        ViewGroup.LayoutParams params = refreshHeader.getLayoutParams();
        return params == null ? 0 : params.height;
    }

    private void setHeaderHeight(int height) {
        ViewGroup.LayoutParams params = refreshHeader.getLayoutParams();
        if (params == null) params = new AbsListView.LayoutParams(-1, height);
        if (Math.abs(params.height - height) < dp(2)) return;
        params.height = Math.max(0, height);
        refreshHeader.setLayoutParams(params);
    }

    private void animateHeaderTo(int target) {
        int start = headerHeight();
        if (start == target) return;
        ValueAnimator animator = ValueAnimator.ofInt(start, target);
        animator.setDuration(160L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            int height = (Integer) value.getAnimatedValue();
            setHeaderHeight(height);
            refreshHeader.setAlpha(triggerHeight <= 0 ? 0.0f
                    : Math.min(1.0f, height / (float) triggerHeight));
        });
        animator.start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density * uiScale);
    }
}
