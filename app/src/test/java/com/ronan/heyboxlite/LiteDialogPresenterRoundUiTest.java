package com.ronan.heyboxlite;

import static com.ronan.heyboxlite.NativeRoundUiTestSupport.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "w192dp-h192dp-round-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LiteDialogPresenterRoundUiTest {
    private ActivityController<Activity> controller;
    private AlertDialog dialog;

    @After public void tearDown() {
        if (dialog != null) dialog.dismiss();
        if (controller != null) controller.pause().stop().destroy();
        Motions.setLevel(MotionLevel.REDUCED);
    }

    @Test public void round192LongBodyAndAllThreeActionsAreScrollableAndTouchable() {
        verifyRoundActions(false);
    }

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    public void round227LargeTypeDoesNotClipLongActionLabels() {
        verifyRoundActions(true);
    }

    @Test public void round192MaximumScalesKeepEveryActionReachable() {
        verifyRoundActions(true);
    }

    @Test @Config(qualifiers = "w240dp-h240dp-notround-mdpi")
    public void squareRetainsTheLegacyBodyScrollAndTouchableShortActions() {
        controller = host();
        Activity activity = controller.get();
        SessionStore session = new SessionStore(activity);
        dialog = new LiteDialogPresenter(activity, session, ThemeTokens.of(true, 0, 0))
                .show("Title", "Body", "OK", null, "Back", null, null, null);
        ViewGroup decor = layoutDialog(dialog);
        assertTrue(all(decor, NativeDialogLayout.class).isEmpty());
        assertEquals(1, all(decor, MaxHeightScrollView.class).size());
        assertTrue(decor.getWidth() <= 240);
        TextView action = text(decor, "OK");
        assertTextFits(action);
        tap(decor, action);
        assertFalse(dialog.isShowing());
    }

    @Test public void backDismissesTheSameReturnedAlertDialogExactlyOnceWithoutRunningCommands() {
        controller = host();
        Activity activity = controller.get();
        int[] counts = new int[2];
        dialog = new LiteDialogPresenter(activity, new SessionStore(activity),
                ThemeTokens.of(true, 0, 0)).show("Notice", "Body", "OK", () -> counts[0]++,
                null, null, null, null);
        layoutDialog(dialog);
        dialog.setOnDismissListener(ignored -> counts[1]++);
        dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
        dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(dialog.isShowing());
        assertEquals(0, counts[0]);
        assertEquals(1, counts[1]);
    }

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    public void shownRoundDialogResizesItsWindowAndContentAndRebindsAfterDismissal() {
        controller = host();
        Activity activity = controller.get();
        int[] counts = new int[2];
        dialog = new LiteDialogPresenter(activity, new SessionStore(activity),
                ThemeTokens.of(true, 0, 0)).show("Notice",
                "One long notice line.\nTwo\nThree\nFour\nFive\nSix\nSeven\nEight\nNine\nTen",
                "OK", () -> counts[0]++, null, null, null, null);
        dialog.setOnDismissListener(ignored -> counts[1]++);
        ViewGroup initial = assertDialogFitsHost(dialog, 227, 227);
        int initialHeight = initial.getHeight();
        resizeHost(activity, 160, 160);
        ViewGroup decor = assertDialogFitsHost(dialog, 160, 160);
        assertTrue("Height must shrink with the already-shown Window", decor.getHeight() < initialHeight);
        NativeDialogLayout scroll = first(decor, NativeDialogLayout.class);
        TextView action = text(decor, "OK");
        assertTextFits(action);
        reach(decor, scroll, action);
        tap(decor, action);
        assertFalse(dialog.isShowing());
        assertEquals(1, counts[0]);
        assertEquals("Caller dismissal listener must remain installed", 1, counts[1]);

        int detachedWidth = dialog.getWindow().getAttributes().width;
        resizeHost(activity, 192, 192);
        assertEquals("Dismissed content must stop observing its host", detachedWidth,
                dialog.getWindow().getAttributes().width);
        dialog.show();
        shadowOf(Looper.getMainLooper()).idle();
        assertDialogFitsHost(dialog, 192, 192);
        resizeHost(activity, 160, 160);
        assertDialogFitsHost(dialog, 160, 160);
        dialog.dismiss();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, counts[0]);
        assertEquals(2, counts[1]);
    }

    private void verifyRoundActions(boolean large) {
        controller = host();
        Activity activity = controller.get();
        SessionStore session = new SessionStore(activity);
        if (large) {
            session.setUiScale(SessionStore.MAX_UI_SCALE);
            session.setTextScale(SessionStore.MAX_TEXT_SCALE);
        }
        LiteDialogPresenter presenter = new LiteDialogPresenter(activity, session,
                ThemeTokens.of(true, 0, 0));
        String positive = "Open another installer";
        String negative = "Keep this download";
        String neutral = "Download in browser";
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < 30; i++) message.append("A long notice line.\n");
        for (String label : new String[]{neutral, negative, positive}) {
            List<String> events = new ArrayList<>();
            dialog = presenter.show("A wrapped notice title", message.toString(),
                    positive, () -> events.add(positive), negative, () -> events.add(negative),
                    neutral, () -> events.add(neutral));
            dialog.setOnDismissListener(ignored -> events.add("dismiss"));
            ViewGroup decor = layoutDialog(dialog);
            assertFrameInCircle(decor);
            NativeDialogLayout scroll = first(decor, NativeDialogLayout.class);
            assertTrue(all(decor, MaxHeightScrollView.class).isEmpty());
            assertTrue(scroll.getChildAt(0).getHeight() > scroll.getHeight());
            swipeUp(decor, scroll);
            assertTrue("A real drag must scroll the combined dialog content", scroll.getScrollY() > 0);
            TextView action = text(decor, label);
            assertTextFits(action);
            reach(decor, scroll, action);
            tap(decor, action);
            assertFalse(dialog.isShowing());
            assertEquals(2, events.size());
            assertTrue(events.contains(label));
            assertTrue(events.contains("dismiss"));
        }
    }
}
