package com.ronan.heyboxlite;

import static com.ronan.heyboxlite.NativeRoundUiTestSupport.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "w192dp-h192dp-round-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class UpdateInstallerRoundUiTest {
    private ActivityController<Activity> controller;
    private AlertDialog dialog;

    @After public void tearDown() {
        if (dialog != null) dialog.dismiss();
        if (controller != null) controller.pause().stop().destroy();
        Motions.setLevel(MotionLevel.REDUCED);
    }

    @Test public void round192DownloadFrameAndFinalHintFitWithoutStartingADownload() {
        verifyDownload(false, true);
    }

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    public void round227MaximumScalesKeepTheDownloadProgressAndHintReachable() {
        verifyDownload(true, true);
    }

    @Test public void round192MaximumScalesDoNotRestoreTheOld220dpMinimum() {
        verifyDownload(true, true);
    }

    @Test @Config(qualifiers = "w240dp-h240dp-notround-mdpi")
    public void squareDownloadKeepsItsPreferredWidthAndReadableContent() {
        verifyDownload(false, false);
    }

    @Test public void contentHeightRespectsATighterParentLimit() {
        controller = host();
        Activity activity = controller.get();
        TextView longBody = new TextView(activity);
        longBody.setText("one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten");
        NativeDialogLayout scroll = new NativeDialogLayout(activity, true, 1f, longBody);
        scroll.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.AT_MOST));
        assertTrue(scroll.getMeasuredHeight() <= 48);
        assertTrue(longBody.getMeasuredHeight() > scroll.getMeasuredHeight());
    }

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    public void shownDownloadTracksWidthAndHeightShrinksAndStopsObservingOnDismiss() {
        controller = host();
        Activity activity = controller.get();
        SessionStore session = new SessionStore(activity);
        ThemeTokens tokens = ThemeTokens.of(session.darkMode(), 0, 0);
        UpdateInstaller installer = new UpdateInstaller(activity, session, new LocalCache(activity),
                new Handler(Looper.getMainLooper()), new LiteDialogPresenter(activity, session, tokens),
                tokens, ignored -> fail("Resizing must not start download/install work"));
        TextView state = UiComponents.label(activity,
                "Preparing\nOne\nTwo\nThree\nFour\nFive\nSix\nSeven\nEight", 12f, tokens.muted, 1f);
        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        dialog = installer.showDownloadDialog(progress, state);
        int[] dismisses = new int[1];
        dialog.setOnDismissListener(ignored -> dismisses[0]++);
        int initialHeight = assertDialogFitsHost(dialog, 227, 227).getHeight();
        progress.setProgress(73);
        resizeHost(activity, 160, 227);
        ViewGroup decor = assertDialogFitsHost(dialog, 160, 227);
        assertTrue(decor.getHeight() < initialHeight);
        int widthShrunkHeight = decor.getHeight();
        resizeHost(activity, 160, 144);
        decor = assertDialogFitsHost(dialog, 160, 144);
        assertTrue("A height-only host shrink must also narrow the Window",
                decor.getHeight() < widthShrunkHeight);
        assertEquals(73, progress.getProgress());
        NativeDialogLayout scroll = first(decor, NativeDialogLayout.class);
        reach(decor, scroll, progress);
        List<TextView> labels = all(scroll, TextView.class);
        TextView hint = labels.get(labels.size() - 1);
        assertTextFits(hint);
        reach(decor, scroll, hint);
        dialog.dismiss();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, dismisses[0]);
        int detachedWidth = dialog.getWindow().getAttributes().width;
        resizeHost(activity, 227, 227);
        assertEquals("Dismissed download must stop observing its host", detachedWidth,
                dialog.getWindow().getAttributes().width);
    }

    private void verifyDownload(boolean large, boolean round) {
        controller = host();
        Activity activity = controller.get();
        SessionStore session = new SessionStore(activity);
        session.setDarkMode(!large);
        if (large) {
            session.setUiScale(SessionStore.MAX_UI_SCALE);
            session.setTextScale(SessionStore.MAX_TEXT_SCALE);
        }
        ThemeTokens tokens = ThemeTokens.of(session.darkMode(), 0, 0);
        UpdateInstaller installer = new UpdateInstaller(activity, session, new LocalCache(activity),
                new Handler(Looper.getMainLooper()), new LiteDialogPresenter(activity, session, tokens),
                tokens, ignored -> fail("A layout-only test must not start download/install work"));
        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        TextView state = UiComponents.label(activity, "Preparing", 12f, tokens.muted,
                session.textScale() / 100f);
        dialog = installer.showDownloadDialog(progress, state);
        ViewGroup decor = layoutDialog(dialog);
        if (round) assertFrameInCircle(decor);
        else assertEquals(220, decor.getWidth());
        NativeDialogLayout scroll = first(decor, NativeDialogLayout.class);
        progress.setProgress(73);
        state.setText("Downloaded 73%, preparing the verified installer");
        decor = layoutDialog(dialog);
        assertEquals(73, progress.getProgress());
        assertTextFits(state);
        reach(decor, scroll, progress);
        List<TextView> labels = all(scroll, TextView.class);
        TextView hint = labels.get(labels.size() - 1);
        assertTextFits(hint);
        reach(decor, scroll, hint);
        if (round) assertFrameInCircle(decor);
        else {
            int contentWidth = scroll.getWidth();
            resizeHost(activity, 240, 200);
            decor = layoutDialog(dialog);
            assertEquals("Square Window width must retain its legacy preference", 220,
                    dialog.getWindow().getAttributes().width);
            assertEquals(220, decor.getWidth());
            assertEquals(contentWidth, scroll.getWidth());
        }
        MotionEvent outside = MotionEvent.obtain(0, 0, MotionEvent.ACTION_OUTSIDE, -1, -1, 0);
        try {
            dialog.onTouchEvent(outside);
        } finally {
            outside.recycle();
        }
        assertTrue("Download mask taps must remain non-cancelling", dialog.isShowing());
    }
}
