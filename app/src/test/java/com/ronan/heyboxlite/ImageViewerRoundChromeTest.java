package com.ronan.heyboxlite;

import static com.ronan.heyboxlite.NativeRoundUiTestSupport.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
public class ImageViewerRoundChromeTest {
    private ActivityController<Activity> controller;

    @After public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
        Motions.setLevel(MotionLevel.REDUCED);
    }

    @Test public void round192UsesTwoSmallBottomCirclesAndATopCounter() {
        verifyRound(192);
    }

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    public void round227UsesTwoSmallBottomCirclesAndATopCounter() {
        verifyRound(227);
    }

    @Test @Config(qualifiers = "w192dp-h192dp-notround-mdpi")
    public void forcedRoundUsesTheSameCompactLayout() { verifyRound(192); }

    @Test @Config(fontScale = 1.5f)
    public void resizingAndLargePageCountsKeepControlsInsideTheCircle() {
        Fixture f = fixture(true);
        for (int size : new int[]{227, 192, 160}) {
            f.counter.setText("2147483646/2147483647");
            f.original.setText("重试原图");
            f.original.setVisibility(View.VISIBLE);
            measure(f.root, size, size);
            assertRoundChrome(f);
            assertEquals("2147483646/2147483647", f.counter.getText().toString());
            tap(f.root, f.close);
            tap(f.root, f.download);
        }
        assertArrayEquals(new int[]{3, 3, 0}, f.clicks);
    }

    @Test public void singleImageHidesTheCounterWithoutMovingTheBottomControls() {
        Fixture f = fixture(true);
        measure(f.root, 192, 192);
        Rect before = bounds(f.root, f.close);
        f.counter.setVisibility(View.GONE);
        measure(f.root, 192, 192);
        assertEquals(before, bounds(f.root, f.close));
        assertEquals(View.GONE, f.original.getVisibility());
        tapEdges(f, f.close);
        tapEdges(f, f.download);
        assertArrayEquals(new int[]{4, 4, 0}, f.clicks);
    }

    @Test @Config(qualifiers = "w240dp-h320dp-notround-mdpi")
    public void squareKeepsTheExistingChromeAndManualOriginalButton() {
        Fixture f = fixture(false);
        measure(f.root, 240, 320);
        assertEquals(FrameLayout.class, f.root.getClass());
        assertEquals(40, f.download.getWidth());
        assertEquals(40, f.download.getHeight());
        assertEquals(16, f.download.getLeft());
        assertEquals(320 - 16, f.original.getBottom());
        assertEquals((240 - f.original.getWidth()) / 2, f.original.getLeft());
        assertEquals(View.VISIBLE, f.original.getVisibility());
        tap(f.root, f.original);
        tap(f.root, f.download);
        assertArrayEquals(new int[]{0, 1, 1}, f.clicks);
    }

    @Test public void uncoveredImageStillReceivesMultiPointerGestures() {
        Fixture f = fixture(true);
        measure(f.root, 192, 192);
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 96, 84, 0);
        assertTrue(f.root.dispatchTouchEvent(down));
        down.recycle();
        MotionEvent.PointerProperties one = new MotionEvent.PointerProperties();
        one.id = 0;
        one.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerProperties two = new MotionEvent.PointerProperties();
        two.id = 1;
        two.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent pinch = MotionEvent.obtain(now, now + 20,
                MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2,
                new MotionEvent.PointerProperties[]{one, two},
                new MotionEvent.PointerCoords[]{pointer(88, 84), pointer(104, 84)},
                0, 0, 1, 1, 0, 0, 0, 0);
        assertTrue(f.root.dispatchTouchEvent(pinch));
        pinch.recycle();
        MotionEvent cancel = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_CANCEL, 96, 84, 0);
        f.root.dispatchTouchEvent(cancel);
        cancel.recycle();
        assertEquals(2, f.maxPointers[0]);
        assertArrayEquals(new int[]{0, 0, 0}, f.clicks);
        assertEquals(new Rect(0, 0, 192, 192), bounds(f.root, f.image));
    }

    private void verifyRound(int diameter) {
        Fixture f = fixture(true);
        measure(f.root, diameter, diameter);
        assertRoundChrome(f);
        assertTextFits(f.counter);
        tapEdges(f, f.close);
        tapEdges(f, f.download);
        assertArrayEquals(new int[]{4, 4, 0}, f.clicks);
    }

    private static void assertRoundChrome(Fixture f) {
        assertEquals(View.GONE, f.original.getVisibility());
        Rect close = bounds(f.root, f.close);
        Rect download = bounds(f.root, f.download);
        Rect counter = bounds(f.root, f.counter);
        assertInCircle(close, f.root.getWidth(), f.root.getHeight());
        assertInCircle(download, f.root.getWidth(), f.root.getHeight());
        assertInCircle(counter, f.root.getWidth(), f.root.getHeight());
        assertEquals(close.top, download.top);
        assertTrue(close.right < download.left);
        assertTrue(close.top > f.root.getHeight() / 2);
        assertTrue(counter.bottom < f.root.getHeight() / 3);
        for (ImageView icon : new ImageView[]{f.close, f.download}) {
            assertEquals(44, icon.getWidth());
            assertEquals(44, icon.getHeight());
            InsetDrawable background = (InsetDrawable) icon.getBackground();
            background.setBounds(0, 0, icon.getWidth(), icon.getHeight());
            assertEquals("Only the painted circle shrinks", 28, background.getDrawable().getBounds().width());
            assertEquals(16, icon.getWidth() - icon.getPaddingLeft() - icon.getPaddingRight());
        }
    }

    private Fixture fixture(boolean round) {
        controller = host();
        Activity activity = controller.get();
        Fixture f = new Fixture();
        f.root = ImageViewerRoundChrome.create(activity, round);
        f.image = new View(activity);
        f.image.setOnTouchListener((view, event) -> {
            f.maxPointers[0] = Math.max(f.maxPointers[0], event.getPointerCount());
            return true;
        });
        f.root.addView(f.image, new FrameLayout.LayoutParams(-1, -1));
        f.download = icon(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(40, 40,
                Gravity.BOTTOM | Gravity.START);
        params.leftMargin = params.bottomMargin = 16;
        f.root.addView(f.download, params);
        f.download.setOnClickListener(ignored -> f.clicks[1]++);
        if (round) {
            f.close = icon(activity);
            f.root.addView(f.close, new FrameLayout.LayoutParams(40, 40));
            f.close.setOnClickListener(ignored -> f.clicks[0]++);
        }
        f.counter = new TextView(activity);
        f.counter.setText("2/12");
        f.counter.setTextSize(12);
        f.counter.setPadding(10, 3, 10, 3);
        f.root.addView(f.counter, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        f.original = new TextView(activity);
        f.original.setText("查看原图");
        f.original.setPadding(14, 7, 14, 7);
        FrameLayout.LayoutParams originalParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        originalParams.bottomMargin = 16;
        f.root.addView(f.original, originalParams);
        f.original.setOnClickListener(ignored -> f.clicks[2]++);
        ImageViewerRoundChrome.bind(f.root, f.close, f.counter, f.download, f.original);
        activity.setContentView(f.root);
        return f;
    }

    private static ImageView icon(Activity activity) {
        ImageView view = new ImageView(activity);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(120, 0, 0, 0));
        background.setShape(GradientDrawable.OVAL);
        view.setBackground(background);
        return view;
    }

    private static void measure(FrameLayout root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private static void tapEdges(Fixture f, View target) {
        Rect rect = bounds(f.root, target);
        for (int x : new int[]{rect.left + 1, rect.right - 1}) {
            for (int y : new int[]{rect.top + 1, rect.bottom - 1}) {
                long now = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
                MotionEvent up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0);
                assertTrue(f.root.dispatchTouchEvent(down));
                assertTrue(f.root.dispatchTouchEvent(up));
                down.recycle();
                up.recycle();
                shadowOf(Looper.getMainLooper()).idle();
            }
        }
    }

    private static MotionEvent.PointerCoords pointer(float x, float y) {
        MotionEvent.PointerCoords point = new MotionEvent.PointerCoords();
        point.x = x;
        point.y = y;
        point.pressure = point.size = 1;
        return point;
    }

    private static final class Fixture {
        FrameLayout root;
        View image;
        ImageView close;
        ImageView download;
        TextView counter;
        TextView original;
        final int[] clicks = new int[3];
        final int[] maxPointers = new int[1];
    }
}
