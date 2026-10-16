package com.ronan.heyboxlite;

import static com.ronan.heyboxlite.NativeRoundUiTestSupport.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.TextView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowWebView;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "w192dp-h192dp-round-mdpi",
        shadows = CheckinCaptchaRoundUiTest.FramedWebViewShadow.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class CheckinCaptchaRoundUiTest {
    private static final String URI = "https://heyboxlite.xyz/checkin/lite/captcha?appid=123456";
    private ActivityController<CheckinCaptchaActivity> controller;

    @After public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test public void round192RetryAndCloseReceiveRealTouchesInsideTheSafeChord() {
        verifyToolbar(true, false);
    }

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    public void round227RetryAndCloseStayInsideTheCircle() {
        verifyToolbar(true, false);
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi", fontScale = 1.5f)
    public void roundLargeSystemTypeDoesNotOverlapTheNativeButtons() {
        verifyToolbar(true, false);
    }

    @Test @Config(fontScale = 1.5f)
    public void roundLargeSystemTypeRemainsTouchableAfterTheWindowShrinks() {
        verifyShrunkStatus(0f, 1f);
    }

    @Test @Config(fontScale = 1.5f)
    public void roundToolbarIncludesTheMeasuredFallbackLineSpacingWithoutClipping() {
        verifyShrunkStatus(4f, 1.15f);
    }

    @Test @Config(qualifiers = "w192dp-h192dp-notround-mdpi")
    public void explicitRoundIntentUsesTheSameSafeToolbarOnASquareDisplay() {
        verifyToolbar(true, true);
    }

    @Test @Config(qualifiers = "w240dp-h320dp-notround-mdpi")
    public void squareRetainsTheTopToolbarAndItsWebView() {
        verifyToolbar(false, false);
    }

    @Test public void roundToolbarRecomputesItsChordWhenTheMeasuredWindowShrinks() {
        CheckinCaptchaActivity activity = open(false);
        ViewGroup decor = layoutActivity(activity);
        NativeCaptchaLayout root = first(decor, NativeCaptchaLayout.class);
        revealRetry(activity);
        root.measure(View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 160, 160);
        for (ImageButton button : all(root, ImageButton.class)) {
            assertInCircle(bounds(root, button), 160, 160);
        }
        assertWebViewport(root, first(root, WebView.class));
    }

    private void verifyToolbar(boolean round, boolean forceRound) {
        CheckinCaptchaActivity activity = open(forceRound);
        ViewGroup decor = layoutActivity(activity);
        NativeCaptchaLayout root = first(decor, NativeCaptchaLayout.class);
        WebView webView = first(root, WebView.class);
        assertEquals(URI, shadowOf(webView).getLastLoadedUrl());
        assertTrue("Verification page must retain positive layout space", webView.getHeight() > 0);
        assertWebViewport(root, webView);
        ImageButton close = button(root, "\u5173\u95ed\u5b89\u5168\u9a8c\u8bc1");
        ImageButton retry = button(root, "\u91cd\u65b0\u52a0\u8f7d\u5b89\u5168\u9a8c\u8bc1");
        assertEquals(View.GONE, retry.getVisibility());
        assertToolbarGeometry(root, close, round);
        revealRetry(activity);
        decor = layoutActivity(activity);
        assertEquals(View.VISIBLE, retry.getVisibility());
        assertWebViewport(root, webView);
        assertToolbarGeometry(root, retry, round);
        assertToolbarGeometry(root, close, round);
        assertFalse(Rect.intersects(bounds(root, retry), bounds(root, close)));
        TextView status = first(root, TextView.class);
        Rect textBounds = bounds(root, status);
        int statusInkRight = textBounds.right - status.getCompoundPaddingRight();
        assertTrue("Status text may not draw under retry", statusInkRight <= bounds(root, retry).left);
        assertNotNull(status.getLayout());
        assertTrue(status.getLayout().getHeight() <= status.getHeight());
        tap(decor, retry);
        assertFalse(activity.isFinishing());
        assertEquals(View.GONE, retry.getVisibility());
        assertEquals(URI, shadowOf(webView).getLastLoadedUrl());
        decor = layoutActivity(activity);
        tap(decor, close);
        assertTrue(activity.isFinishing());
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).getResultCode());
        assertEquals("user_cancelled", shadowOf(activity).getResultIntent()
                .getStringExtra(CheckinCaptchaActivity.EXTRA_DIAGNOSTIC));
    }

    private CheckinCaptchaActivity open(boolean forceRound) {
        Intent intent = CheckinCaptchaActivity.intent(RuntimeEnvironment.getApplication(), URI, forceRound);
        controller = Robolectric.buildActivity(CheckinCaptchaActivity.class, intent).setup().visible();
        CheckinCaptchaActivity activity = controller.get();
        assertFalse("Trusted local test URI must open the real native screen", activity.isFinishing());
        return activity;
    }

    private static void revealRetry(CheckinCaptchaActivity activity) {
        ReflectionHelpers.callInstanceMethod(activity, "showRetry",
                ClassParameter.from(String.class, "invalid_result"));
    }

    private static ImageButton button(View root, String description) {
        for (ImageButton button : all(root, ImageButton.class)) {
            if (description.contentEquals(button.getContentDescription())) return button;
        }
        throw new AssertionError("Missing native captcha button: " + description);
    }

    private static void assertToolbarGeometry(NativeCaptchaLayout root, ImageButton button,
                                               boolean round) {
        Rect rect = bounds(root, button);
        assertTrue(new Rect(0, 0, root.getWidth(), root.getHeight()).contains(rect));
        if (round) {
            assertInCircle(rect, root.getWidth(), root.getHeight());
            assertTrue(root.getChildAt(0).getTop() > 0);
        } else {
            assertEquals(0, root.getChildAt(0).getTop());
            assertEquals(root.getWidth(), root.getChildAt(0).getWidth());
            assertEquals(Math.round(46 * root.getResources().getDisplayMetrics().density),
                    root.getChildAt(0).getHeight());
        }
    }

    private void verifyShrunkStatus(float spacingExtra, float spacingMultiplier) {
        CheckinCaptchaActivity activity = open(false);
        NativeCaptchaLayout root = first(layoutActivity(activity), NativeCaptchaLayout.class);
        TextView status = first(root, TextView.class);
        float textSize = status.getTextSize();
        status.setLineSpacing(spacingExtra, spacingMultiplier);
        revealRetry(activity);
        root.measure(View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 160, 160);
        assertEquals(textSize, status.getTextSize(), 0f);
        assertNotNull(status.getLayout());
        assertTrue(status.getLayout().getHeight() <= status.getHeight());
        assertTrue("Large-type two-line status must expand the old fixed toolbar",
                root.getChildAt(0).getHeight() > 46);
        assertInCircle(bounds(root, root.getChildAt(0)), 160, 160);
        assertWebViewport(root, first(root, WebView.class));
        ImageButton close = button(root, "\u5173\u95ed\u5b89\u5168\u9a8c\u8bc1");
        ImageButton retry = button(root, "\u91cd\u65b0\u52a0\u8f7d\u5b89\u5168\u9a8c\u8bc1");
        for (ImageButton button : new ImageButton[]{close, retry}) {
            assertToolbarGeometry(root, button, true);
            assertEquals(40, button.getWidth());
            assertEquals(40, button.getHeight());
        }
        assertFalse(Rect.intersects(bounds(root, close), bounds(root, retry)));
        tap(root, retry);
        assertEquals(View.GONE, retry.getVisibility());
        assertFalse(activity.isFinishing());
        tap(root, close);
        assertTrue(activity.isFinishing());
        assertEquals("user_cancelled", shadowOf(activity).getResultIntent()
                .getStringExtra(CheckinCaptchaActivity.EXTRA_DIAGNOSTIC));
    }

    private static void assertWebViewport(NativeCaptchaLayout root, WebView webView) {
        View viewport = (View) webView.getParent();
        assertTrue("Weighted page container must receive the remaining height", viewport.getHeight() > 0);
        assertEquals(root.getHeight() - root.getChildAt(0).getBottom(), viewport.getHeight());
        assertEquals(viewport.getMeasuredWidth(), webView.getMeasuredWidth());
        assertEquals(viewport.getMeasuredHeight(), webView.getMeasuredHeight());
        assertTrue("Verification page must retain positive layout space", webView.getHeight() > 0);
        assertEquals(bounds(root, viewport), bounds(root, webView));
    }

    @Implements(WebView.class)
    public static class FramedWebViewShadow extends ShadowWebView {
        @RealObject private WebView webView;

        @Implementation
        protected boolean setFrame(int left, int top, int right, int bottom) {
            // Robolectric's provider is a no-op; real providers forward this to View.setFrame.
            return Shadow.directlyOn(webView, View.class, "setFrame",
                    ClassParameter.from(int.class, left), ClassParameter.from(int.class, top),
                    ClassParameter.from(int.class, right), ClassParameter.from(int.class, bottom));
        }
    }
}
