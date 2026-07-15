package com.ronan.heyboxlite;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

@SuppressLint("ViewConstructor")
final class DetailPager extends FrameLayout {
    interface Listener {
        boolean canSwipeBack();

        void onPageChanged();

        void onReturn();
    }

    private static final int PAGE_ARTICLE = 0;
    private static final int PAGE_COMMENTS = 1;
    private final Dp dimensions;
    private final Listener listener;
    private final int touchSlop;
    private float startX;
    private float startY;
    private long startTime;
    private int startScrollX;
    private int currentPage;
    private boolean dragging;
    private boolean returning;
    private View returnPreview;
    private boolean realReturnView;
    private ValueAnimator settleAnimator;

    DetailPager(Context context, Dp dimensions, Listener listener) {
        super(context);
        this.dimensions = dimensions;
        this.listener = listener;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    void setPages(View preview, View article, View comments) {
        removeAllViews();
        this.returnPreview = preview;
        this.realReturnView = false;
        addView(preview, new FrameLayout.LayoutParams(-1, -1));
        addView(article, new FrameLayout.LayoutParams(-1, -1));
        addView(comments, new FrameLayout.LayoutParams(-1, -1));
        this.currentPage = PAGE_ARTICLE;
        post(() -> scrollTo(pageScrollX(this.currentPage), 0));
    }

    void setReturnView(View view) {
        if (view == null) return;
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        if (this.returnPreview != null) removeView(this.returnPreview);
        this.returnPreview = view;
        this.realReturnView = true;
        Motions.resetTree(view);
        addView(view, 0, new FrameLayout.LayoutParams(-1, -1));
        requestLayout();
    }

    View takeReturnView() {
        if (!this.realReturnView || this.returnPreview == null) return null;
        View view = this.returnPreview;
        removeView(view);
        this.returnPreview = null;
        this.realReturnView = false;
        Motions.resetTree(view);
        return view;
    }

    boolean showingComments() {
        return this.currentPage == PAGE_COMMENTS;
    }

    void showArticle(boolean animate) {
        settleToPage(PAGE_ARTICLE, animate);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        if (this.returnPreview != null) this.returnPreview.layout(-width, 0, 0, height);
        if (getChildCount() > PAGE_COMMENTS) getChildAt(PAGE_COMMENTS).layout(0, 0, width, height);
        if (getChildCount() > 2) getChildAt(2).layout(width, 0, width * 2, height);
        if (changed) post(() -> scrollTo(pageScrollX(this.currentPage), 0));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelSettle();
                this.startX = event.getX();
                this.startY = event.getY();
                this.startTime = event.getEventTime();
                this.startScrollX = getScrollX();
                this.dragging = false;
                this.returning = false;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                this.dragging = false;
                this.returning = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - this.startX;
                float dy = event.getY() - this.startY;
                if ((this.currentPage != PAGE_ARTICLE || dx <= 0.0f || this.listener.canSwipeBack())
                        && Math.abs(dx) > this.touchSlop * 2
                        && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                    this.dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            default:
                break;
        }
        return this.dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelSettle();
                this.startX = event.getX();
                this.startY = event.getY();
                this.startTime = event.getEventTime();
                this.startScrollX = getScrollX();
                this.dragging = true;
                this.returning = false;
                break;
            case MotionEvent.ACTION_UP:
                finishHorizontalDrag(event);
                performClick();
                break;
            case MotionEvent.ACTION_MOVE:
                dragTo(event.getX() - this.startX);
                break;
            case MotionEvent.ACTION_CANCEL:
                settleToPage(this.currentPage, true);
                this.dragging = false;
                this.returning = false;
                break;
            default:
                break;
        }
        return true;
    }

    private void dragTo(float dx) {
        int width = Math.max(1, getWidth());
        int min = this.currentPage == PAGE_ARTICLE && this.listener.canSwipeBack() ? -width : 0;
        scrollTo(Math.max(min, Math.min(width, this.startScrollX - Math.round(dx))), 0);
    }

    private void finishHorizontalDrag(MotionEvent event) {
        int width = Math.max(1, getWidth());
        float dx = event.getX() - this.startX;
        long duration = Math.max(1L, event.getEventTime() - this.startTime);
        float velocity = dx * 1000.0f / duration;
        if (this.currentPage == PAGE_ARTICLE && getScrollX() < 0) {
            boolean enoughDistance = Math.abs(getScrollX()) > Math.max(this.dimensions.dp(52), width * 0.24f);
            boolean enoughVelocity = velocity > this.dimensions.dp(320);
            this.dragging = false;
            if (enoughDistance || enoughVelocity) settleToReturn();
            else settleToPage(PAGE_ARTICLE, true);
            return;
        }
        int target = getScrollX() > width / 2 ? PAGE_COMMENTS : PAGE_ARTICLE;
        if (Math.abs(velocity) > this.dimensions.dp(360)) target = velocity < 0.0f ? PAGE_COMMENTS : PAGE_ARTICLE;
        this.dragging = false;
        settleToPage(target, true);
    }

    private void settleToPage(int page, boolean animate) {
        cancelSettle();
        this.currentPage = page;
        this.listener.onPageChanged();
        int destination = pageScrollX(page);
        if (!animate || Motions.off()) {
            scrollTo(destination, 0);
            return;
        }
        int from = getScrollX();
        int distance = Math.abs(destination - from);
        if (distance < this.dimensions.dp(2)) {
            scrollTo(destination, 0);
            return;
        }
        this.settleAnimator = ValueAnimator.ofInt(from, destination);
        this.settleAnimator.setDuration(Math.max(160, Math.min(300, distance / 3)));
        this.settleAnimator.setInterpolator(new DecelerateInterpolator());
        this.settleAnimator.addUpdateListener(value -> scrollTo((Integer) value.getAnimatedValue(), 0));
        this.settleAnimator.start();
    }

    private int pageScrollX(int page) {
        return page == PAGE_COMMENTS ? Math.max(0, getWidth()) : 0;
    }

    private void settleToReturn() {
        cancelSettle();
        this.returning = true;
        int from = getScrollX();
        int destination = -Math.max(1, getWidth());
        if (Motions.off()) {
            scrollTo(destination, 0);
            this.returning = false;
            this.listener.onReturn();
            return;
        }
        int distance = Math.abs(destination - from);
        this.settleAnimator = ValueAnimator.ofInt(from, destination);
        this.settleAnimator.setDuration(Math.max(150, Math.min(280, distance / 3)));
        this.settleAnimator.setInterpolator(new DecelerateInterpolator());
        this.settleAnimator.addUpdateListener(value -> scrollTo((Integer) value.getAnimatedValue(), 0));
        this.settleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                settleAnimator = null;
                if (returning) {
                    returning = false;
                    listener.onReturn();
                }
            }
        });
        this.settleAnimator.start();
    }

    private void cancelSettle() {
        if (this.settleAnimator == null) return;
        this.returning = false;
        this.settleAnimator.cancel();
        this.settleAnimator = null;
    }

    void cancelMotion() {
        cancelSettle();
        scrollTo(pageScrollX(this.currentPage), 0);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
