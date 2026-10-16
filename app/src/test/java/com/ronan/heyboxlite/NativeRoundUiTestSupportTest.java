package com.ronan.heyboxlite;

import static com.ronan.heyboxlite.NativeRoundUiTestSupport.*;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "w192dp-h192dp-round-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeRoundUiTestSupportTest {
    private ActivityController<Activity> controller;

    @After public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
        Motions.setLevel(MotionLevel.REDUCED);
    }

    @Test public void scrollViewportStaysFixedAndPartiallyClippedTargetsRetainTheirFullBounds() {
        Fixture f = fixture();
        Rect viewport = new Rect(10, 20, 150, 140);
        assertEquals(viewport, bounds(f.root, f.scroll));
        f.scroll.scrollTo(0, 160);
        assertEquals(160, f.scroll.getScrollY());
        assertEquals(viewport, bounds(f.root, f.scroll));
        Rect target = bounds(f.root, f.target);
        assertEquals(new Rect(10, 10, 150, 54), target);
        assertEquals(f.target.getHeight(), target.height());
        assertFalse("Clipped bounds must not pass the reachability check", viewport.contains(target));
    }

    @Test public void reachScrollsAClippedTargetFullyIntoTheViewportAndDispatchesItsTouch() {
        Fixture f = fixture();
        f.scroll.scrollTo(0, 160);
        reach(f.root, f.scroll, f.target);
        assertTrue(f.scroll.getScrollY() < 160);
        assertTrue(new Rect(10, 20, 150, 140).contains(bounds(f.root, f.target)));
        tap(f.root, f.target);
        assertEquals(1, f.clicks[0]);
    }

    @Test public void settledDragThenReachDoesNotConsumeTheFirstTapToStopAFling() {
        Fixture f = fixture();
        swipeUp(f.root, f.scroll);
        assertTrue("Real drag must still scroll the content", f.scroll.getScrollY() > 0);
        reach(f.root, f.scroll, f.target);
        tap(f.root, f.target);
        assertEquals("The first actual tap must run the target command", 1, f.clicks[0]);
    }

    private Fixture fixture() {
        controller = host();
        Activity activity = controller.get();
        Fixture f = new Fixture();
        f.root = new FrameLayout(activity);
        f.scroll = new ScrollView(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(140, 120);
        params.leftMargin = 10;
        params.topMargin = 20;
        f.root.addView(f.scroll, params);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(new View(activity), new LinearLayout.LayoutParams(-1, 150));
        f.target = new TextView(activity);
        f.target.setText("Action");
        f.target.setOnClickListener(ignored -> f.clicks[0]++);
        content.addView(f.target, new LinearLayout.LayoutParams(-1, 44));
        content.addView(new View(activity), new LinearLayout.LayoutParams(-1, 150));
        f.scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        activity.setContentView(f.root);
        f.root.measure(View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY));
        f.root.layout(0, 0, 160, 160);
        return f;
    }

    private static final class Fixture {
        FrameLayout root;
        ScrollView scroll;
        TextView target;
        final int[] clicks = new int[1];
    }
}
