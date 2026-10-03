package com.ronan.heyboxlite;

import static org.junit.Assert.*;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, manifest = Config.NONE,
        qualifiers = "w240dp-h320dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class DetailPagerReturnLifecycleTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private Parent parent;
    private DetailPager pager;
    private View preview;
    private int returns;
    private int pageChanges;

    @Before public void setUp() {
        Motions.setLevel(MotionLevel.REDUCED);
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
        activity = controller.get();
        parent = new Parent(activity);
        pager = new DetailPager(activity, value -> value, false, new DetailPager.Listener() {
            @Override public boolean canSwipeBack() { return true; }
            @Override public void onPageChanged() { pageChanges++; }
            @Override public void onReturn() { returns++; }
        });
        preview = new View(activity);
        pager.setPages(preview, new View(activity), new View(activity));
        parent.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(parent);
        layoutParent();
        Shadows.shadowOf(activity.getMainLooper()).idle();
    }

    @After public void tearDown() {
        controller.pause().stop().destroy();
        Motions.setLevel(MotionLevel.REDUCED);
    }

    @Test public void cancelledDragKeepsPreviewAndReleasesTheOriginalGestureOwner() {
        touch(MotionEvent.ACTION_DOWN, 16, 0);
        touch(MotionEvent.ACTION_MOVE, 156, 300);
        assertTrue(parent.disallowed);
        assertTrue(pager.getScrollX() < 0);
        touch(MotionEvent.ACTION_CANCEL, 156, 400);
        Shadows.shadowOf(activity.getMainLooper()).idleFor(Duration.ofMillis(400));
        assertEquals(0, returns);
        assertEquals(0, pager.getScrollX());
        assertSame(pager, preview.getParent());
        assertFalse(parent.disallowed);
        assertFalse(pager.canHandoffReturnPreview(preview));
    }

    @Test public void detachCancelsSettleAndRejectsItsAlreadyCapturedEndCallback() throws Exception {
        beginReturnSettle();
        ValueAnimator animator = animator();
        List<Animator.AnimatorListener> queued = new ArrayList<>(animator.getListeners());
        parent.removeView(pager);
        assertFalse(parent.disallowed);
        for (Animator.AnimatorListener listener : queued) listener.onAnimationEnd(animator);
        animator.end();
        Shadows.shadowOf(activity.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(0, returns);
        assertEquals(0, pager.getScrollX());
        assertFalse(pager.canHandoffReturnPreview(preview));
    }

    @Test public void newGestureCancelsOldSettleWithoutLettingOldEndCompleteNewReturn() throws Exception {
        beginReturnSettle();
        ValueAnimator old = animator();
        List<Animator.AnimatorListener> queued = new ArrayList<>(old.getListeners());
        touch(MotionEvent.ACTION_DOWN, 16, 1000);
        touch(MotionEvent.ACTION_MOVE, 196, 1300);
        touch(MotionEvent.ACTION_UP, 196, 1400);
        ValueAnimator current = animator();
        assertNotSame(old, current);
        for (Animator.AnimatorListener listener : queued) listener.onAnimationEnd(old);
        assertEquals(0, returns);
        assertSame(current, animator());
        current.end();
        assertEquals(1, returns);
        assertEquals(-pager.getWidth(), pager.getScrollX());
        assertTrue(pager.canHandoffReturnPreview(preview));
    }

    @Test public void pendingLayoutAlignmentCannotSnapCommittedReturnBackToArticle() throws Exception {
        beginReturnSettle();
        animator().end();
        assertEquals(1, returns);
        layoutParent();
        Shadows.shadowOf(activity.getMainLooper()).idleFor(Duration.ofMillis(300));
        assertEquals(-pager.getWidth(), pager.getScrollX());
        assertTrue(pager.canHandoffReturnPreview(preview));
    }

    @Test public void pageReplacementRejectsOldReturnAndItsQueuedAlignment() throws Exception {
        beginReturnSettle();
        ValueAnimator old = animator();
        List<Animator.AnimatorListener> queued = new ArrayList<>(old.getListeners());
        View replacement = new View(activity);
        pager.setPages(replacement, new View(activity), new View(activity));
        for (Animator.AnimatorListener listener : queued) listener.onAnimationEnd(old);
        layoutParent();
        Shadows.shadowOf(activity.getMainLooper()).idleFor(Duration.ofMillis(400));
        assertEquals(0, returns);
        assertEquals(0, pager.getScrollX());
        assertNull(preview.getParent());
        assertSame(pager, replacement.getParent());
        assertFalse(pager.canHandoffReturnPreview(replacement));
    }

    @Test public void detachedPageAlignmentDoesNotMoveAResumedNativeDetail() {
        parent.removeView(pager);
        pager.showComments(false);
        Shadows.shadowOf(activity.getMainLooper()).idle();
        assertTrue(pager.showingComments());
        assertEquals(pager.getWidth(), pager.getScrollX());
        parent.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        layoutParent();
        Shadows.shadowOf(activity.getMainLooper()).idle();
        assertTrue(pager.showingComments());
        assertEquals(pager.getWidth(), pager.getScrollX());
        assertEquals(0, returns);
        assertEquals(1, pageChanges);
    }

    @Test public void nativeReturnViewCanStillBeTakenWithoutAnyComposeCommit() {
        View nativeReturn = new View(activity);
        nativeReturn.setTranslationX(20);
        pager.setReturnView(nativeReturn);
        assertNull(preview.getParent());
        assertSame(pager, nativeReturn.getParent());
        assertSame(nativeReturn, pager.takeReturnView());
        assertNull(nativeReturn.getParent());
        assertEquals(0f, nativeReturn.getTranslationX(), 0f);
        assertNull(pager.takeReturnView());
        assertEquals(0, returns);
    }

    private void beginReturnSettle() throws Exception {
        touch(MotionEvent.ACTION_DOWN, 16, 0);
        touch(MotionEvent.ACTION_MOVE, 176, 300);
        touch(MotionEvent.ACTION_UP, 176, 400);
        assertNotNull(animator());
        assertEquals(0, returns);
        assertFalse(parent.disallowed);
    }

    private void touch(int action, int x, long time) {
        MotionEvent event = MotionEvent.obtain(0, time, action, x, 100, 0);
        pager.dispatchTouchEvent(event);
        event.recycle();
    }

    private ValueAnimator animator() throws Exception {
        Field field = DetailPager.class.getDeclaredField("settleAnimator");
        field.setAccessible(true);
        return (ValueAnimator) field.get(pager);
    }

    private void layoutParent() {
        parent.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 240, 320);
    }

    private static final class Parent extends FrameLayout {
        boolean disallowed;
        Parent(Activity activity) { super(activity); }
        @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
            disallowed = disallow;
            super.requestDisallowInterceptTouchEvent(disallow);
        }
    }
}
