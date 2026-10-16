package com.ronan.heyboxlite;

import static com.ronan.heyboxlite.NativeRoundUiTestSupport.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
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
    private static final String BASE = "\u67e5\u770b\u539f\u56fe";
    private static final String SIZE = BASE + "\uff08123.4MB\uff09";
    private ActivityController<Activity> controller;

    @After public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
        Motions.setLevel(MotionLevel.REDUCED);
    }

    @Test public void legacyFixedBottomInsetCutsARealMeasuredSizeCapsule() {
        controller = host();
        Activity activity = controller.get();
        FrameLayout legacy = new FrameLayout(activity);
        TextView label = original(activity, SIZE);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = 12;
        legacy.addView(label, params);
        measure(legacy, 192, 192);
        assertTrue("Real measured size label exceeds the old bottom chord", label.getWidth() > 94);
        assertFalse("Legacy bottom corners must reproduce the regression",
                inCircle(bounds(legacy, label), 192, 192));
    }

    @Test public void round192DynamicSizeAndStateChangesKeepTheWholeTouchTargetInsideTheCircle() {
        verifyDynamicLabels();
    }

    @Test public void sizeCapsuleEdgesAreTouchableAndDisabledStatesDoNotStartAnotherLoad() {
        Fixture f = fixture(true);
        f.original.setText(SIZE);
        measure(f.root, 192, 192);
        assertChrome(f);
        Rect rect = bounds(f.root, f.original);
        for (int x : new int[]{rect.left + 1, rect.right - 1}) {
            for (int y : new int[]{rect.top + 1, rect.bottom - 1}) {
                long now = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
                MotionEvent up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0);
                try {
                    assertTrue(f.root.dispatchTouchEvent(down));
                    assertTrue(f.root.dispatchTouchEvent(up));
                } finally {
                    down.recycle();
                    up.recycle();
                }
                shadowOf(Looper.getMainLooper()).idle();
            }
        }
        assertArrayEquals(new int[]{0, 0, 4}, f.clicks);
        f.original.setText("\u5df2\u662f\u539f\u56fe");
        f.original.setEnabled(false);
        measure(f.root, 192, 192);
        assertChrome(f);
        tap(f.root, f.original);
        assertEquals(4, f.clicks[2]);
        assertFalse(f.original.isEnabled());
    }

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    public void round227DynamicSizeAndStateChangesKeepAllChromeInsideTheCircle() {
        verifyDynamicLabels();
    }

    @Test @Config(fontScale = 1.5f)
    public void largeSystemTypeKeepsTheCapsuleAndNeighbouringControlsSeparate() {
        verifyDynamicLabels();
    }

    @Test @Config(fontScale = 1.5f)
    public void smallWindowAndLargeSystemTypeRetainAllThreeClickTargets() {
        Fixture f = fixture(true);
        f.original.setText(SIZE);
        measure(f.root, 160, 160);
        assertChrome(f);
        tap(f.root, f.original);
        tap(f.root, f.close);
        tap(f.root, f.download);
        assertArrayEquals(new int[]{1, 1, 1}, f.clicks);
    }

    @Test public void measuredLineSpacingCannotPushDownloadIntoCloseOnASmallWindow() {
        Fixture f = fixture(true);
        f.original.setText(SIZE);
        f.original.setLineSpacing(8f, 1f);
        measure(f.root, 160, 160);
        assertChrome(f);
        assertEquals(SIZE, f.original.getText().toString());
        assertTrue(f.original.getLayout().getHeight() <= f.original.getHeight()
                - f.original.getCompoundPaddingTop() - f.original.getCompoundPaddingBottom());
        tap(f.root, f.original);
        tap(f.root, f.close);
        tap(f.root, f.download);
        assertArrayEquals(new int[]{1, 1, 1}, f.clicks);
    }

    @Test public void actualWindowResizeAndHiddenOriginalRelayoutWithoutAPostedCallback() {
        Fixture f = fixture(true);
        measure(f.root, 227, 227);
        f.original.setVisibility(View.GONE);
        measure(f.root, 192, 192);
        assertChrome(f);
        f.original.setText(SIZE);
        f.original.setVisibility(View.VISIBLE);
        measure(f.root, 160, 160);
        assertChrome(f);
        tap(f.root, f.original);
        assertEquals(1, f.clicks[2]);
    }

    @Test @Config(fontScale = 1.5f)
    public void veryLongSizesAndPageCountsStayBoundedWithoutLosingTheirAccessibleText() {
        Fixture f = fixture(true);
        String hugeSize = BASE + "\uff088796093022208MB\uff09";
        f.original.setText(hugeSize);
        f.counter.setText("2147483646/2147483647");
        measure(f.root, 192, 192);
        assertChrome(f);
        assertEquals(hugeSize, f.original.getText().toString());
        assertEquals("2147483646/2147483647", f.counter.getText().toString());
        AccessibilityNodeInfo info = f.original.createAccessibilityNodeInfo();
        assertEquals(hugeSize, info.getText().toString());
        info.recycle();
        assertTrue(f.original.getLayout().getHeight() <= f.original.getHeight()
                - f.original.getCompoundPaddingTop() - f.original.getCompoundPaddingBottom());
        tap(f.root, f.original);
        tap(f.root, f.close);
        tap(f.root, f.download);
        assertArrayEquals(new int[]{1, 1, 1}, f.clicks);
    }

    @Test @Config(qualifiers = "w240dp-h320dp-notround-mdpi")
    public void squareKeepsThePlainFrameAndExistingChromeGeometry() {
        Fixture f = fixture(false);
        measure(f.root, 240, 320);
        assertEquals(FrameLayout.class, f.root.getClass());
        assertEquals(40, f.download.getWidth());
        assertEquals(40, f.download.getHeight());
        assertEquals(16, f.download.getLeft());
        assertEquals(320 - 16, f.original.getBottom());
        assertEquals((240 - f.original.getWidth()) / 2, f.original.getLeft());
        assertEquals(Integer.MAX_VALUE, f.original.getMaxLines());
        tap(f.root, f.original);
        tap(f.root, f.download);
        assertArrayEquals(new int[]{0, 1, 1}, f.clicks);
    }

    @Test public void uncoveredImageAreaStillReceivesAMultiPointerGesture() {
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
        MotionEvent.PointerCoords first = pointer(88, 84);
        MotionEvent.PointerCoords second = pointer(104, 84);
        MotionEvent pinch = MotionEvent.obtain(now, now + 20,
                MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2,
                new MotionEvent.PointerProperties[]{one, two},
                new MotionEvent.PointerCoords[]{first, second}, 0, 0, 1, 1, 0, 0, 0, 0);
        assertTrue(f.root.dispatchTouchEvent(pinch));
        pinch.recycle();
        MotionEvent cancel = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_CANCEL, 96, 84, 0);
        f.root.dispatchTouchEvent(cancel);
        cancel.recycle();
        assertEquals(2, f.maxPointers[0]);
        assertArrayEquals(new int[]{0, 0, 0}, f.clicks);
        assertEquals(new Rect(0, 0, 192, 192), bounds(f.root, f.imageSurface));
    }

    private void verifyDynamicLabels() {
        Fixture f = fixture(true);
        int diameter = f.root.getResources().getDisplayMetrics().widthPixels;
        for (String label : new String[]{BASE, SIZE, "\u52a0\u8f7d\u4e2d",
                "\u91cd\u8bd5\u539f\u56fe", "\u5df2\u662f\u539f\u56fe"}) {
            f.original.setText(label);
            measure(f.root, diameter, diameter);
            assertChrome(f);
            assertTextFits(f.original);
            tap(f.root, f.original);
        }
        tap(f.root, f.close);
        tap(f.root, f.download);
        assertArrayEquals(new int[]{1, 1, 5}, f.clicks);
    }

    private Fixture fixture(boolean round) {
        controller = host();
        Activity activity = controller.get();
        Fixture f = new Fixture();
        f.root = ImageViewerRoundChrome.create(activity, round);
        f.imageSurface = new View(activity);
        f.imageSurface.setOnTouchListener((view, event) -> {
            f.maxPointers[0] = Math.max(f.maxPointers[0], event.getPointerCount());
            return true;
        });
        f.root.addView(f.imageSurface, new FrameLayout.LayoutParams(-1, -1));
        f.download = new ImageView(activity);
        FrameLayout.LayoutParams downloadParams = new FrameLayout.LayoutParams(40, 40,
                Gravity.BOTTOM | Gravity.START);
        downloadParams.leftMargin = round ? 36 : 16;
        downloadParams.bottomMargin = round ? 36 : 16;
        f.root.addView(f.download, downloadParams);
        f.download.setOnClickListener(ignored -> f.clicks[1]++);
        if (round) {
            f.close = new ImageView(activity);
            f.root.addView(f.close, new FrameLayout.LayoutParams(40, 40));
            f.close.setOnClickListener(ignored -> f.clicks[0]++);
        }
        f.counter = new TextView(activity);
        f.counter.setText("2/12");
        f.counter.setTextSize(12);
        f.counter.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        f.counter.setPadding(10, 3, 10, 3);
        FrameLayout.LayoutParams counterParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.END);
        counterParams.topMargin = round ? 36 : 16;
        counterParams.rightMargin = round ? 36 : 16;
        f.root.addView(f.counter, counterParams);
        f.original = original(activity, BASE);
        FrameLayout.LayoutParams originalParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        originalParams.bottomMargin = round ? 36 : 16;
        f.root.addView(f.original, originalParams);
        f.original.setOnClickListener(ignored -> f.clicks[2]++);
        ImageViewerRoundChrome.bind(f.root, f.close, f.counter, f.download, f.original);
        activity.setContentView(f.root);
        return f;
    }

    private static TextView original(Activity activity, String label) {
        TextView text = new TextView(activity);
        text.setText(label);
        text.setTextSize(12);
        text.setGravity(Gravity.CENTER);
        text.setPadding(14, 7, 14, 7);
        return text;
    }

    private static void measure(FrameLayout root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private static void assertChrome(Fixture f) {
        View[] controls = {f.close, f.download, f.counter, f.original};
        for (int i = 0; i < controls.length; i++) {
            View view = controls[i];
            if (view == null || view.getVisibility() == View.GONE) continue;
            Rect rect = bounds(f.root, view);
            assertInCircle(rect, f.root.getWidth(), f.root.getHeight());
            if (view != f.counter) {
                assertTrue("Clickable chrome must retain a 44dp target", rect.width() >= 44
                        && rect.height() >= 44);
            }
            for (int j = i + 1; j < controls.length; j++) {
                if (controls[j] == null || controls[j].getVisibility() == View.GONE) continue;
                assertFalse("Chrome targets must not overlap: " + rect + " / "
                        + bounds(f.root, controls[j]), Rect.intersects(rect, bounds(f.root, controls[j])));
            }
        }
    }

    private static boolean inCircle(Rect rect, int width, int height) {
        float radius = Math.min(width, height) / 2f - 1;
        for (int x : new int[]{rect.left, rect.right}) {
            for (int y : new int[]{rect.top, rect.bottom}) {
                if (Math.hypot(x - width / 2f, y - height / 2f) >= radius) return false;
            }
        }
        return true;
    }

    private static MotionEvent.PointerCoords pointer(float x, float y) {
        MotionEvent.PointerCoords pointer = new MotionEvent.PointerCoords();
        pointer.x = x;
        pointer.y = y;
        pointer.pressure = 1;
        pointer.size = 1;
        return pointer;
    }

    private static final class Fixture {
        FrameLayout root;
        View imageSurface;
        ImageView close;
        ImageView download;
        TextView counter;
        TextView original;
        final int[] clicks = new int[3];
        final int[] maxPointers = new int[1];
    }
}
