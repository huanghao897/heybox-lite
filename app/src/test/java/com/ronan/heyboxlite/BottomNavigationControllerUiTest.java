package com.ronan.heyboxlite;

import static com.ronan.heyboxlite.NativeRoundUiTestSupport.bounds;
import static com.ronan.heyboxlite.NativeRoundUiTestSupport.host;
import static com.ronan.heyboxlite.NativeRoundUiTestSupport.layoutActivity;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class,
        qualifiers = "w192dp-h192dp-round-watch-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class BottomNavigationControllerUiTest {
    private ActivityController<Activity> owner;

    @After public void tearDown() {
        if (owner != null) owner.pause().stop().destroy();
        Motions.setLevel(MotionLevel.REDUCED);
    }

    @Test public void round192NormalScaleRendersAndAcceptsPaddedTargetTaps() throws IOException {
        verifyDock(192, 192, true, 100);
    }

    @Test public void round192MinimumScaleRendersAndAcceptsPaddedTargetTaps() throws IOException {
        verifyDock(192, 192, true, SessionStore.MIN_UI_SCALE);
    }

    @Test @Config(qualifiers = "w227dp-h227dp-round-watch-mdpi")
    public void round227NormalScaleRendersAndAcceptsPaddedTargetTaps() throws IOException {
        verifyDock(227, 227, true, 100);
    }

    @Test @Config(qualifiers = "w227dp-h227dp-round-watch-mdpi")
    public void round227MinimumScaleRendersAndAcceptsPaddedTargetTaps() throws IOException {
        verifyDock(227, 227, true, SessionStore.MIN_UI_SCALE);
    }

    @Test @Config(qualifiers = "w240dp-h320dp-notround-watch-mdpi")
    public void square240x320NormalScaleRendersAndAcceptsPaddedTargetTaps() throws IOException {
        verifyDock(240, 320, false, 100);
    }

    @Test @Config(qualifiers = "w240dp-h320dp-notround-watch-mdpi")
    public void square240x320MinimumScaleRendersAndAcceptsPaddedTargetTaps() throws IOException {
        verifyDock(240, 320, false, SessionStore.MIN_UI_SCALE);
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-watch-xhdpi")
    public void denseRound192NormalScaleUsesPhysicalPixels() throws IOException {
        verifyDock(192, 192, true, 100);
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-watch-xhdpi")
    public void denseRound192MinimumScaleUsesPhysicalPixels() throws IOException {
        verifyDock(192, 192, true, SessionStore.MIN_UI_SCALE);
    }

    private void verifyDock(int widthDp, int heightDp, boolean round, int configuredScale)
            throws IOException {
        owner = host();
        Activity activity = owner.get();
        SessionStore session = new SessionStore(activity);
        session.setUiScale(configuredScale);
        assertEquals(round, session.usesRoundLayout());
        assertTrue(RoundLayoutMetrics.isWatchDisplay(activity));
        ThemeTokens tokens = ThemeTokens.of(true, 0, 0);
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(tokens.background);
        activity.setContentView(root);
        List<String> clicks = new ArrayList<>();
        int[] feedbackCalls = {0};
        BottomNavigationController navigation = new BottomNavigationController(activity, session,
                tokens, round, root, (view, action) -> {
                    feedbackCalls[0]++;
                    action.run();
                });
        navigation.addItem("Feed", "feed", R.drawable.ic_nav_home, () -> clicks.add("feed"));
        navigation.addItem("Profile", "profile", R.drawable.ic_nav_profile, () -> clicks.add("profile"));
        LinearLayout dock = (LinearLayout) root.getChildAt(0);
        assertEquals(View.GONE, dock.getVisibility());
        navigation.select("feed");
        navigation.setVisible(true, false, false);
        layoutActivity(activity);

        float density = activity.getResources().getDisplayMetrics().density;
        int width = Math.round(widthDp * density);
        int height = Math.round(heightDp * density);
        assertEquals(width, root.getWidth());
        assertEquals(height, root.getHeight());
        ResponsiveDock.Dimensions expected = ResponsiveDock.fromScreen(width, height, round,
                session.uiScale() / 100.0f);
        Rect frame = bounds(root, dock);
        assertEquals(expected.width, frame.width());
        assertEquals(expected.height, frame.height());
        assertEquals(expected.marginBottom, height - frame.bottom);
        assertEquals((width - expected.width) / 2, frame.left);
        assertEquals(expected.height + expected.marginBottom
                + UiComponents.dp(activity, 8, session.uiScale() / 100.0f),
                navigation.bottomSafeSpace());
        assertTrue(navigation.bottomSafeSpace() > height - frame.top);
        if (round) {
            assertInCircle(frame, width, height);
            assertTrue("The enlarged dock must stay close to the bottom",
                    height - frame.bottom <= Math.ceil(width * 0.04f));
        }
        assertEquals(2, dock.getChildCount());
        assertSelection(dock, 0);
        String screenshot = (round ? "round" : "square") + "-" + widthDp + "x" + heightDp
                + "-ui" + configuredScale + "-" + width + "x" + height + "px";
        capture(root, dock, tokens, round, screenshot + "-feed");

        Rect previous = null;
        for (int index = 0; index < dock.getChildCount(); index++) {
            ImageView item = (ImageView) dock.getChildAt(index);
            String key = index == 0 ? "feed" : "profile";
            assertEquals(key, item.getTag());
            assertEquals(index == 0 ? "Feed" : "Profile", item.getContentDescription());
            assertTrue(item.isClickable());
            assertNotNull(item.getDrawable());
            Rect target = bounds(root, item);
            assertTrue(frame.contains(target));
            assertTrue("Full padded target must be wider than its glyph", target.width() > expected.iconSize);
            assertTrue("Full padded target must be taller than its glyph", target.height() > expected.iconSize);
            assertTrue(item.getPaddingLeft() > 0 && item.getPaddingTop() > 0);
            if (previous != null) assertFalse("Navigation targets cannot overlap", Rect.intersects(previous, target));
            previous = target;
            if (round) assertInCircle(target, width, height);
            // Exercise all four padded corners, not ImageView.performClick or only the icon center.
            for (float x : new float[]{target.left + 0.5f, target.right - 0.5f}) {
                for (float y : new float[]{target.top + 0.5f, target.bottom - 0.5f}) {
                    int before = clicks.size();
                    tap(root, x, y);
                    assertEquals("One action per physical tap", before + 1, clicks.size());
                    assertEquals(key, clicks.get(before));
                }
            }
        }
        assertEquals(8, feedbackCalls[0]);
        navigation.select("profile");
        assertSelection(dock, 1);
        capture(root, dock, tokens, round, screenshot + "-profile");

        navigation.setVisible(false, false, false);
        assertEquals(View.GONE, dock.getVisibility());
        int before = clicks.size();
        dispatch(root, MotionEvent.ACTION_DOWN, frame.exactCenterX(), frame.exactCenterY());
        dispatch(root, MotionEvent.ACTION_UP, frame.exactCenterX(), frame.exactCenterY());
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("A hidden island must not retain active touch targets", before, clicks.size());
        navigation.setVisible(true, false, false);
        navigation.finishMotion();
        assertEquals(1.0f, dock.getAlpha(), 0.0f);
        assertEquals(0.0f, dock.getTranslationY(), 0.0f);
    }

    private static void assertSelection(LinearLayout dock, int active) {
        for (int index = 0; index < dock.getChildCount(); index++) {
            View item = dock.getChildAt(index);
            assertEquals(index == active ? 1.0f : 0.62f, item.getAlpha(), 0.0f);
            if (index == active) assertNotNull(item.getBackground());
            else assertNull(item.getBackground());
        }
    }

    private static void assertInCircle(Rect rect, int width, int height) {
        double radius = Math.min(width, height) / 2.0;
        for (int x : new int[]{rect.left, rect.right}) {
            for (int y : new int[]{rect.top, rect.bottom}) {
                double dx = x - width / 2.0;
                double dy = y - height / 2.0;
                assertTrue("Hit bounds outside circular display: " + rect,
                        dx * dx + dy * dy <= radius * radius);
            }
        }
    }

    private static void tap(FrameLayout root, float x, float y) {
        assertTrue(dispatch(root, MotionEvent.ACTION_DOWN, x, y));
        assertTrue(dispatch(root, MotionEvent.ACTION_UP, x, y));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static boolean dispatch(FrameLayout root, int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        try {
            return root.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static void capture(FrameLayout root, LinearLayout dock, ThemeTokens tokens,
                                boolean round, String name) throws IOException {
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            root.draw(new Canvas(bitmap));
            File file = new File("build/outputs/ui-regression/dock-" + name + ".png");
            File directory = file.getParentFile();
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream output = new FileOutputStream(file)) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            System.out.println("Native dock screenshot: " + file.getAbsolutePath());
            Rect frame = bounds(root, dock);
            int painted = 0;
            for (int y = 0; y < bitmap.getHeight(); y++) {
                for (int x = 0; x < bitmap.getWidth(); x++) {
                    int pixel = bitmap.getPixel(x, y);
                    if (pixel == tokens.background) continue;
                    int red = Color.red(pixel);
                    int green = Color.green(pixel);
                    int blue = Color.blue(pixel);
                    assertTrue("Dock must retain the neutral black/gray palette",
                            Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) <= 6);
                    if (frame.contains(x, y)) painted++;
                    if (round) {
                        double dx = x + 0.5 - bitmap.getWidth() / 2.0;
                        double dy = y + 0.5 - bitmap.getHeight() / 2.0;
                        double radius = bitmap.getWidth() / 2.0;
                        assertTrue("Unmasked native rendering crosses the circular edge",
                                dx * dx + dy * dy <= radius * radius);
                    }
                }
            }
            assertTrue("Native capsule rendering must not be blank", painted > frame.width() * frame.height() / 2);
            for (int index = 0; index < dock.getChildCount(); index++) {
                ImageView item = (ImageView) dock.getChildAt(index);
                Rect target = bounds(root, item);
                RectF glyph = new RectF(item.getDrawable().getBounds());
                item.getImageMatrix().mapRect(glyph);
                glyph.offset(target.left + item.getPaddingLeft(), target.top + item.getPaddingTop());
                assertTrue("Drawable footprint must fit its touch target", new RectF(target).contains(glyph));
                Bitmap hidden = renderWithImageAlpha(root, item, 0);
                Bitmap faint = renderWithImageAlpha(root, item, 24);
                try {
                    double coverage = assertGlyphInk(bitmap, hidden, glyph, item, tokens);
                    System.out.println("Native dock glyph ink: " + name + "/" + item.getTag()
                            + " " + glyph + " coverage=" + coverage);
                    assertThrows("Selection fill alone must not count as icon ink", AssertionError.class,
                            () -> assertGlyphInk(hidden, hidden, glyph, item, tokens));
                    assertThrows("Nearly transparent icons must still fail", AssertionError.class,
                            () -> assertGlyphInk(faint, hidden, glyph, item, tokens));
                } finally {
                    hidden.recycle();
                    faint.recycle();
                }
            }
        } finally {
            bitmap.recycle();
        }
    }

    private static Bitmap renderWithImageAlpha(FrameLayout root, ImageView item, int imageAlpha) {
        int previous = item.getImageAlpha();
        try {
            item.setImageAlpha(imageAlpha);
            Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(bitmap));
            return bitmap;
        } finally {
            item.setImageAlpha(previous);
        }
    }

    private static double assertGlyphInk(Bitmap visible, Bitmap hidden, RectF glyph,
                                         ImageView item, ThemeTokens tokens) {
        Rect pixels = new Rect();
        glyph.roundOut(pixels);
        int foreground = Color.red(item.getAlpha() == 1.0f ? tokens.text : tokens.subtle);
        double ink = 0.0;
        double upperInk = 0.0;
        double lowerInk = 0.0;
        for (int y = pixels.top; y < pixels.bottom; y++) {
            for (int x = pixels.left; x < pixels.right; x++) {
                int surface = Color.red(hidden.getPixel(x, y));
                double fullContrast = (foreground - surface) * item.getAlpha();
                double coverage = Math.max(0.0,
                        (Color.red(visible.getPixel(x, y)) - surface) / fullContrast);
                ink += coverage;
                if (y + 0.5 < glyph.centerY()) upperInk += coverage;
                else lowerInk += coverage;
            }
        }
        // Integrate antialiased strokes over the actual drawable viewport, excluding selection fill.
        double area = glyph.width() * glyph.height();
        String diagnostic = item.getTag() + " " + glyph + ": ink=" + ink / area
                + ", upper=" + upperInk / area + ", lower=" + lowerInk / area;
        assertTrue("Icon must paint meaningful scaled ink: " + diagnostic, ink >= area * 0.08);
        assertTrue("Both halves of the icon must render: " + diagnostic,
                upperInk >= area * 0.015 && lowerInk >= area * 0.015);
        return ink / area;
    }
}
