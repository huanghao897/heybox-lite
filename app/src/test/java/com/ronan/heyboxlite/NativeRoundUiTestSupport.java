package com.ronan.heyboxlite;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Rect;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowSystemClock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class NativeRoundUiTestSupport {
    private NativeRoundUiTestSupport() {}

    static ActivityController<Activity> host() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        activity.setTheme(R.style.AppTheme);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        activity.setContentView(new FrameLayout(activity));
        controller.visible();
        layoutActivity(activity);
        Motions.setLevel(MotionLevel.OFF);
        return controller;
    }

    static ViewGroup layoutActivity(Activity activity) {
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        int width = attributes.width > 0 ? attributes.width
                : activity.getResources().getDisplayMetrics().widthPixels;
        int height = attributes.height > 0 ? attributes.height
                : activity.getResources().getDisplayMetrics().heightPixels;
        measure(decor, width, height, View.MeasureSpec.EXACTLY);
        return decor;
    }

    static ViewGroup layoutDialog(Dialog dialog) {
        ViewGroup decor = (ViewGroup) dialog.getWindow().getDecorView();
        int width = dialog.getWindow().getAttributes().width;
        assertTrue("Dialog must set a bounded width", width > 0);
        measure(decor, width, dialog.getContext().getResources().getDisplayMetrics().heightPixels,
                View.MeasureSpec.AT_MOST);
        return decor;
    }

    static void resizeHost(Activity activity, int width, int height) {
        activity.getWindow().setLayout(width, height);
        ViewGroup decor = layoutActivity(activity);
        // Manual fixture layout must deliver the same notification as a ViewRoot traversal.
        decor.getViewTreeObserver().dispatchOnGlobalLayout();
        shadowOf(Looper.getMainLooper()).idle();
    }

    static ViewGroup assertDialogFitsHost(Dialog dialog, int width, int height) {
        int extent = (int) (Math.min(width, height) * 0.68f);
        assertEquals("Actual Window width must follow the host", extent,
                dialog.getWindow().getAttributes().width);
        ViewGroup decor = layoutDialog(dialog);
        assertEquals(extent, decor.getWidth());
        NativeDialogLayout scroll = first(decor, NativeDialogLayout.class);
        assertEquals("Scroll viewport must be remeasured, not merely visually clipped", extent,
                scroll.getWidth());
        assertEquals("MATCH_PARENT content must follow the resized viewport", extent,
                scroll.getChildAt(0).getWidth());
        assertFrameInCircle(decor, width, height);
        return decor;
    }

    private static void measure(ViewGroup root, int width, int height, int heightMode) {
        for (int i = 0; i < 2; i++) {
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, heightMode));
            root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
            shadowOf(Looper.getMainLooper()).idle();
        }
        assertTrue(root.getWidth() > 0 && root.getHeight() > 0);
    }

    static <T extends View> T first(View root, Class<T> type) {
        List<T> matches = all(root, type);
        assertFalse("Missing " + type.getSimpleName(), matches.isEmpty());
        return matches.get(0);
    }

    static <T extends View> List<T> all(View root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        if (type.isInstance(root)) matches.add(type.cast(root));
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                matches.addAll(all(group.getChildAt(i), type));
            }
        }
        return matches;
    }

    static TextView text(View root, String text) {
        for (TextView view : all(root, TextView.class)) {
            if (text.contentEquals(view.getText())) return view;
        }
        throw new AssertionError("Missing text: " + text);
    }

    static Rect bounds(ViewGroup root, View view) {
        Rect rect = new Rect();
        // The conversion subtracts the descendant's own scroll as well as its ancestors'.
        view.getDrawingRect(rect);
        root.offsetDescendantRectToMyCoords(view, rect);
        return rect;
    }

    static void assertFrameInCircle(ViewGroup decor) {
        int screenWidth = decor.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = decor.getResources().getDisplayMetrics().heightPixels;
        assertFrameInCircle(decor, screenWidth, screenHeight);
    }

    static void assertFrameInCircle(ViewGroup decor, int screenWidth, int screenHeight) {
        assertTrue(decor.getWidth() <= Math.min(screenWidth, screenHeight) * 0.68f + 1);
        assertTrue(decor.getHeight() <= Math.min(screenWidth, screenHeight) * 0.68f + 1);
        Rect frame = new Rect((screenWidth - decor.getWidth()) / 2,
                (screenHeight - decor.getHeight()) / 2,
                (screenWidth + decor.getWidth()) / 2, (screenHeight + decor.getHeight()) / 2);
        assertInCircle(frame, screenWidth, screenHeight);
    }

    static void assertInCircle(Rect rect, int width, int height) {
        float radius = Math.min(width, height) / 2f - 1;
        assertTrue("Positive target bounds: " + rect, rect.width() > 0 && rect.height() > 0);
        for (int x : new int[]{rect.left, rect.right}) {
            for (int y : new int[]{rect.top, rect.bottom}) {
                assertTrue("Corner outside circle: " + rect + " in " + width + "x" + height,
                        Math.hypot(x - width / 2f, y - height / 2f) < radius);
            }
        }
    }

    static void assertTextFits(TextView text) {
        assertNotNull(text.getLayout());
        assertTrue("Text must not be height-clipped: " + text.getText(),
                text.getLayout().getHeight() <= text.getHeight()
                        - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom());
        for (int i = 0; i < text.getLayout().getLineCount(); i++) {
            assertEquals("Action label must not be ellipsized", 0,
                    text.getLayout().getEllipsisCount(i));
        }
    }

    static void swipeUp(ViewGroup root, ScrollView scroll) {
        Rect viewport = bounds(root, scroll);
        float x = viewport.exactCenterX();
        float from = viewport.top + viewport.height() * 0.85f;
        float to = viewport.top + viewport.height() * 0.15f;
        long now = SystemClock.uptimeMillis();
        event(root, now, now, MotionEvent.ACTION_DOWN, x, from);
        for (int i = 1; i <= 8; i++) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(20));
            event(root, now, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE,
                    x, from + (to - from) * i / 8f);
        }
        // End a settled drag, not a fling whose next tap only stops its animation.
        ShadowSystemClock.advanceBy(Duration.ofMillis(200));
        event(root, now, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, to);
        ShadowSystemClock.advanceBy(Duration.ofMillis(40));
        event(root, now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, to);
        shadowOf(Looper.getMainLooper()).idle();
    }

    static void reach(ViewGroup root, ScrollView scroll, View target) {
        target.requestRectangleOnScreen(new Rect(0, 0, target.getWidth(), target.getHeight()), true);
        shadowOf(Looper.getMainLooper()).idle();
        Rect viewport = bounds(root, scroll);
        Rect targetBounds = bounds(root, target);
        assertTrue("Target must be fully inside its scroll viewport: " + targetBounds
                + " in " + viewport + ", scrollY=" + scroll.getScrollY(),
                viewport.contains(targetBounds));
    }

    static void tap(ViewGroup root, View view) {
        Rect rect = bounds(root, view);
        assertTrue(new Rect(0, 0, root.getWidth(), root.getHeight()).contains(rect));
        long now = SystemClock.uptimeMillis();
        assertTrue(event(root, now, now, MotionEvent.ACTION_DOWN,
                rect.exactCenterX(), rect.exactCenterY()));
        ShadowSystemClock.advanceBy(Duration.ofMillis(40));
        assertTrue(event(root, now, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                rect.exactCenterX(), rect.exactCenterY()));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static boolean event(ViewGroup root, long down, long time, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, time, action, x, y, 0);
        try {
            return root.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }
}
