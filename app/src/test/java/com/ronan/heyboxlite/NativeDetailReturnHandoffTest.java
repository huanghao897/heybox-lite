package com.ronan.heyboxlite;

import static org.junit.Assert.*;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import androidx.activity.ComponentActivity;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.platform.ViewCompositionStrategy;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import kotlin.Unit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, manifest = Config.NONE,
        qualifiers = "w480dp-h800dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativeDetailReturnHandoffTest {
    private ActivityController<ComponentActivity> controller;
    private ComponentActivity activity;
    private FrameLayout root;
    private FrameLayout body;
    private FrameLayout nativeLayer;
    private FrameLayout destinationLayer;
    private View destination;
    private DetailPager pager;
    private int returns;
    private int releases;
    private Runnable returning;

    @Before public void setUp() {
        Motions.setLevel(MotionLevel.OFF);
        controller = Robolectric.buildActivity(ComponentActivity.class).setup().visible();
        activity = controller.get();
        root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);
        body = new FrameLayout(activity);
        root.addView(body, bounds(300, 420, 40, 72));
        nativeLayer = new FrameLayout(activity);
        body.addView(nativeLayer, bounds(224, 320, 20, 32));
        destinationLayer = new FrameLayout(activity);
        body.addView(destinationLayer, bounds(224, 320, 20, 32));
        destination = new View(activity);
        destination.setBackgroundColor(Color.MAGENTA);
        destinationLayer.addView(destination, new FrameLayout.LayoutParams(-1, -1));
        destinationLayer.setVisibility(View.INVISIBLE);
        pager = new DetailPager(activity, value -> value, false, new DetailPager.Listener() {
            @Override public boolean canSwipeBack() { return true; }
            @Override public void onPageChanged() { }
            @Override public void onReturn() { returns++; if (returning != null) returning.run(); }
        });
        nativeLayer.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root);
    }

    @After public void tearDown() {
        controller.pause().stop().destroy();
        Motions.setLevel(MotionLevel.REDUCED);
    }

    @Test public void committedReturnTransfersOriginalLiveViewWithoutWindowDetachOrScrollLoss() {
        ProbeScrollView preview = scrollPreview();
        install(preview);
        assertTrue("Preview content must be scrollable: content=" + preview.getChildAt(0).getHeight()
                + ", viewport=" + preview.getHeight(), preview.getChildAt(0).getHeight() > preview.getHeight());
        preview.scrollTo(0, 185);
        assertEquals(185, preview.getScrollY());
        Object window = preview.getWindowToken();
        commitReturn();
        assertEquals(185, preview.getScrollY());
        NativeDetailReturnHandoff handoff = adopt(preview);

        assertNotNull(handoff);
        assertSame(preview, handoff.getChildAt(0));
        assertSame(window, preview.getWindowToken());
        assertTrue(preview.isAttachedToWindow());
        assertEquals(1, preview.attachments);
        assertEquals(0, preview.detachments);
        assertEquals(185, preview.getScrollY());
        nativeLayer.removeAllViews();
        layoutRoot();
        Shadows.shadowOf(activity.getMainLooper()).idle();
        assertEquals(0, preview.detachments);
        assertEquals(185, preview.getScrollY());
        assertNull(pager.takeReturnView());
        assertNull(adopt(preview));
        assertEquals(1, returns);
        handoff.dispose();
        assertEquals(1, preview.detachments);
    }

    @Test public void realComposeCompositionSurvivesAdoptionAndIsDisposedOnlyOnRelease() {
        AtomicInteger compositions = new AtomicInteger();
        AtomicInteger detachments = new AtomicInteger();
        ComposeView preview = new ComposeView(activity);
        preview.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow.INSTANCE);
        preview.setContent((composer, changed) -> {
            compositions.incrementAndGet();
            return Unit.INSTANCE;
        });
        preview.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) { detachments.incrementAndGet(); }
        });
        install(preview);
        Shadows.shadowOf(activity.getMainLooper()).idle();
        assertTrue(preview.getHasComposition());
        int composed = compositions.get();
        assertTrue(composed > 0);
        commitReturn();
        NativeDetailReturnHandoff handoff = adopt(preview);
        assertNotNull(handoff);
        nativeLayer.removeAllViews();
        layoutRoot();
        Shadows.shadowOf(activity.getMainLooper()).idle();
        assertSame(preview, handoff.getChildAt(0));
        assertTrue(preview.getHasComposition());
        assertEquals(composed, compositions.get());
        assertEquals(0, detachments.get());
        handoff.dispose();
        assertFalse(preview.getHasComposition());
        assertEquals(1, detachments.get());
    }

    @Test public void handoffUsesBodyAndDestinationBoundsInsteadOfActivityRoot() {
        ProbeScrollView preview = scrollPreview();
        install(preview);
        commitReturn();
        NativeDetailReturnHandoff handoff = adopt(preview);
        assertNotNull(handoff);
        layoutRoot();
        assertSame(body, handoff.getParent());
        int[] actual = new int[2];
        int[] expected = new int[2];
        handoff.getLocationOnScreen(actual);
        destinationLayer.getLocationOnScreen(expected);
        assertArrayEquals(expected, actual);
        assertEquals(224, handoff.getWidth());
        assertEquals(320, handoff.getHeight());
        assertEquals(224, preview.getWidth());
        assertEquals(320, preview.getHeight());
        assertEquals(0, preview.getLeft());
        assertEquals(0, preview.getTop());
        assertTrue(handoff.getHeight() < root.getHeight());
    }

    @Test public void invisibleOrLayoutPendingDestinationCannotReleasePaintedPreview() {
        ProbeScrollView preview = scrollPreview();
        install(preview);
        commitReturn();
        NativeDetailReturnHandoff handoff = adopt(preview);
        assertNotNull(handoff);
        nativeLayer.removeAllViews();
        handoff.awaitDestination(() -> true);
        layoutRoot();
        handoff.getViewTreeObserver().dispatchOnPreDraw();
        assertSame(body, handoff.getParent());
        assertEquals(0, releases);
        Bitmap covered = render();
        assertEquals(Color.GREEN, covered.getPixel(80, 144));
        covered.recycle();

        destinationLayer.setVisibility(View.VISIBLE);
        destination.requestLayout();
        destination.getViewTreeObserver().dispatchOnPreDraw();
        assertSame(body, handoff.getParent());
        layoutRoot();
        destination.getViewTreeObserver().dispatchOnPreDraw();
        assertNull(handoff.getParent());
        assertEquals(1, releases);
        assertEquals(1, preview.detachments);
        Bitmap returned = render();
        assertEquals(Color.MAGENTA, returned.getPixel(80, 144));
        returned.recycle();
    }

    @Test public void destinationRemovalDisposesOwnershipWithoutAnyLaterCallback() throws Exception {
        ProbeScrollView preview = scrollPreview();
        install(preview);
        commitReturn();
        NativeDetailReturnHandoff handoff = adopt(preview);
        AtomicInteger readinessCalls = new AtomicInteger();
        handoff.awaitDestination(() -> { readinessCalls.incrementAndGet(); return true; });
        ViewTreeObserver.OnPreDrawListener queued = preDraw(handoff);
        destinationLayer.removeView(destination);
        Shadows.shadowOf(activity.getMainLooper()).idle();
        int calls = readinessCalls.get();
        assertNull(handoff.getParent());
        assertEquals(1, releases);
        assertEquals(1, preview.detachments);
        queued.onPreDraw();
        handoff.dispose();
        handoff.awaitDestination(() -> { fail("Disposed handoff must not consult the destination"); return true; });
        assertEquals(calls, readinessCalls.get());
        assertEquals(1, releases);
    }

    @Test public void transparentOrFadingDestinationDoesNotExposeABlankFrame() {
        ProbeScrollView preview = scrollPreview();
        install(preview);
        commitReturn();
        NativeDetailReturnHandoff handoff = adopt(preview);
        handoff.awaitDestination(() -> true);
        nativeLayer.removeAllViews();
        destinationLayer.setVisibility(View.VISIBLE);
        layoutRoot();
        destination.setAlpha(0f);
        destination.getViewTreeObserver().dispatchOnPreDraw();
        assertSame(body, handoff.getParent());
        destination.setAlpha(0.5f);
        destination.getViewTreeObserver().dispatchOnPreDraw();
        assertSame(body, handoff.getParent());
        destination.setAlpha(1f);
        destinationLayer.setAlpha(0.5f);
        destination.getViewTreeObserver().dispatchOnPreDraw();
        assertSame(body, handoff.getParent());
        destinationLayer.setAlpha(1f);
        destination.getViewTreeObserver().dispatchOnPreDraw();
        assertNull(handoff.getParent());
        assertEquals(1, releases);
    }

    @Test public void hostDisposalAndStalePreDrawCannotReleaseAnotherOwnersView() throws Exception {
        ProbeScrollView preview = scrollPreview();
        install(preview);
        commitReturn();
        NativeDetailReturnHandoff handoff = adopt(preview);
        handoff.awaitDestination(() -> true);
        ViewTreeObserver.OnPreDrawListener queued = preDraw(handoff);
        root.removeView(body);
        Shadows.shadowOf(activity.getMainLooper()).idle();
        assertEquals(1, releases);
        assertEquals(1, preview.detachments);
        FrameLayout nextOwner = new FrameLayout(activity);
        root.addView(nextOwner, new FrameLayout.LayoutParams(-1, -1));
        View next = new View(activity);
        nextOwner.addView(next);
        queued.onPreDraw();
        assertSame(nextOwner, next.getParent());
        assertEquals(1, releases);
    }

    @Test public void changedDestinationCancelsRatherThanRetryingAnObsoleteRoute() {
        ProbeScrollView preview = scrollPreview();
        install(preview);
        commitReturn();
        NativeDetailReturnHandoff handoff = adopt(preview);
        boolean[] current = { true };
        handoff.awaitDestination(() -> current[0]);
        current[0] = false;
        destination.getViewTreeObserver().dispatchOnPreDraw();
        assertNull(handoff.getParent());
        assertEquals(1, releases);
        assertEquals(1, preview.detachments);
    }

    @Test public void uncommittedCancelledOrWrongPreviewCannotBeAdopted() {
        ProbeScrollView preview = scrollPreview();
        install(preview);
        assertNull(adopt(preview));
        commitReturn();
        assertNull(adopt(new View(activity)));
        pager.cancelMotion();
        assertNull(adopt(preview));
        assertSame(pager, preview.getParent());
        assertEquals(0, releases);
    }

    @Test public void nativeReturnViewIsNeverStolenByComposeHandoff() {
        View fallback = new View(activity);
        install(fallback);
        ProbeScrollView nativeReturn = scrollPreview();
        pager.setReturnView(nativeReturn);
        layoutRoot();
        assertTrue("Native return content must be scrollable: content=" + nativeReturn.getChildAt(0).getHeight()
                + ", viewport=" + nativeReturn.getHeight(), nativeReturn.getChildAt(0).getHeight() > nativeReturn.getHeight());
        nativeReturn.scrollTo(0, 127);
        assertEquals(127, nativeReturn.getScrollY());
        commitReturn();
        assertEquals(127, nativeReturn.getScrollY());
        assertNull(adopt(nativeReturn));
        assertSame(nativeReturn, pager.takeReturnView());
        assertEquals(127, nativeReturn.getScrollY());
        assertNull(nativeReturn.getParent());
        assertNull(pager.takeReturnView());
    }

    @Test public void progressiveReplacementCannotStealTheSettlingPagersPreview() {
        ProbeScrollView original = scrollPreview();
        install(original);
        DetailPager replacement = new DetailPager(activity, value -> value, false,
                new DetailPager.Listener() {
                    @Override public boolean canSwipeBack() { return true; }
                    @Override public void onPageChanged() { }
                    @Override public void onReturn() { fail("Replacement did not own this gesture"); }
                });
        ProbeScrollView newlyCreated = scrollPreview();
        replacement.setPages(newlyCreated, new View(activity), new View(activity));
        replacement.setVisibility(View.INVISIBLE);
        nativeLayer.addView(replacement, new FrameLayout.LayoutParams(-1, -1));
        layoutRoot();
        NativeDetailReturnHandoff[] adopted = new NativeDetailReturnHandoff[1];
        returning = () -> {
            DetailPager owner = NativeDetailReturnHandoff.returningPager(replacement);
            assertSame(pager, owner);
            assertSame(original, owner.returnPreviewForHandoff());
            adopted[0] = NativeDetailReturnHandoff.adopt(owner, original,
                    destinationLayer, () -> releases++);
        };
        commitReturn();
        assertNotNull(adopted[0]);
        assertSame(original, adopted[0].getChildAt(0));
        assertSame(replacement, NativeDetailReturnHandoff.returningPager(replacement));
        nativeLayer.removeAllViews();
        assertEquals(0, original.detachments);
        assertEquals(1, newlyCreated.detachments);
        adopted[0].dispose();
        assertEquals(1, original.detachments);
    }

    private void install(View preview) {
        pager.setPages(preview, new View(activity), new View(activity));
        layoutRoot();
        Shadows.shadowOf(activity.getMainLooper()).idle();
    }

    private void commitReturn() {
        touch(MotionEvent.ACTION_DOWN, 16, 0);
        touch(MotionEvent.ACTION_MOVE, 190, 400);
        touch(MotionEvent.ACTION_UP, 190, 600);
        assertEquals(-pager.getWidth(), pager.getScrollX());
    }

    private void touch(int action, int x, long time) {
        MotionEvent event = MotionEvent.obtain(0, time, action, x, 100, 0);
        pager.dispatchTouchEvent(event);
        event.recycle();
    }

    private NativeDetailReturnHandoff adopt(View preview) {
        return NativeDetailReturnHandoff.adopt(pager, preview, destinationLayer, () -> releases++);
    }

    private ProbeScrollView scrollPreview() {
        ProbeScrollView view = new ProbeScrollView(activity);
        view.setFocusable(false);
        view.setBackgroundColor(Color.GREEN);
        View content = new View(activity);
        content.setMinimumHeight(1200);
        content.setBackgroundColor(Color.GREEN);
        view.addView(content, new ScrollView.LayoutParams(-1, 1200));
        return view;
    }

    private void layoutRoot() {
        root.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 480, 800);
    }

    private Bitmap render() {
        Bitmap bitmap = Bitmap.createBitmap(480, 800, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static FrameLayout.LayoutParams bounds(int width, int height, int left, int top) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = left;
        params.topMargin = top;
        return params;
    }

    private static ViewTreeObserver.OnPreDrawListener preDraw(NativeDetailReturnHandoff handoff)
            throws Exception {
        Field field = NativeDetailReturnHandoff.class.getDeclaredField("preDraw");
        field.setAccessible(true);
        return (ViewTreeObserver.OnPreDrawListener) field.get(handoff);
    }

    private static final class ProbeScrollView extends ScrollView {
        int attachments;
        int detachments;
        ProbeScrollView(ComponentActivity activity) { super(activity); }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); attachments++; }
        @Override protected void onDetachedFromWindow() { detachments++; super.onDetachedFromWindow(); }
    }
}
