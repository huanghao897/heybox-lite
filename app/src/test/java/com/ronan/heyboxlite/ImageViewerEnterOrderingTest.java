package com.ronan.heyboxlite;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.util.LruCache;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.time.Duration;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "w200dp-h300dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class ImageViewerEnterOrderingTest {
    private static final String URL = "https://viewer.example/portrait.jpg";
    private final int previousMotion = Motions.level();
    private final Duration previousFrameDelay = ShadowChoreographer.getFrameDelay();
    private final Bitmap preview = bitmap(100, 200);
    private final Bitmap original = bitmap(200, 400);
    private ActivityController<ImageViewerActivity> owner;

    @Before public void pauseFramesAndCacheOriginal() {
        ShadowChoreographer.setPaused(true);
        ShadowChoreographer.setFrameDelay(Duration.ofSeconds(2));
        imageCache().put(URL, original);
    }

    @After public void releaseViewerAndClock() {
        if (owner != null) owner.pause().stop().destroy();
        imageCache().remove(URL);
        Motions.setLevel(previousMotion);
        ShadowChoreographer.setFrameDelay(previousFrameDelay);
        ShadowChoreographer.setPaused(false);
    }

    @Test public void anOverdueOriginalWaitsForTheActualEnterCompletion() {
        ZoomImageView image = open(URL, true, MotionLevel.FULL);
        ImageViewerTransition transition = ReflectionHelpers.getField(owner.get(), "sharedTransition");
        assertNotNull(ReflectionHelpers.getField(transition, "animation"));
        assertSame(preview, currentBitmap(image));
        RectF fitted = new RectF();
        image.imageDisplayRect(fitted);
        assertTrue(fitted.left >= 0 && fitted.top >= 0);
        assertTrue(fitted.right <= image.getWidth() && fitted.bottom <= image.getHeight());

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(600));

        assertNotNull("Slow frame delivery must keep the entering image stable",
                ReflectionHelpers.getField(transition, "animation"));
        assertSame("Elapsed wall time alone cannot replace the entering preview",
                preview, currentBitmap(image));
        finishFrames();
        assertSame(original, currentBitmap(image));
        assertEquals(1f, image.getScaleX(), 0f);
        assertEquals(image.getScaleX(), image.getScaleY(), 0f);
        assertNull(image.getClipBounds());
    }

    @Test public void closingDuringEnterNeverAppliesTheDeferredOriginal() {
        ZoomImageView image = open(URL, true, MotionLevel.FULL);
        owner.get().onBackPressed();
        finishFrames();
        assertTrue(owner.get().isFinishing());
        assertSame(preview, currentBitmap(image));
    }

    @Test public void motionOffUpgradesWithoutWaitingForAnAnimationTimer() {
        ZoomImageView image = open(URL, true, MotionLevel.OFF);
        ShadowLooper.shadowMainLooper().idle();
        assertEquals("Motion-off entry should not need an animation frame", "FINISHED",
                ReflectionHelpers.getField(owner.get(), "pageEnter").toString());
        assertSame("Original fixture must still be cached", original, imageCache().get(URL));
        assertSame(original, currentBitmap(image));
        assertNull(image.getClipBounds());
    }

    @Test public void longImageTilesCannotReplaceThePreviewWhileItIsEntering() {
        open("", false, MotionLevel.FULL);
        boolean[] candidates = ReflectionHelpers.getField(owner.get(), "longCandidates");
        candidates[0] = true;
        ReflectionHelpers.callInstanceMethod(owner.get(), "maybeLoadLongPage",
                ReflectionHelpers.ClassParameter.from(int.class, 0));
        boolean[] loading = ReflectionHelpers.getField(owner.get(), "longLoading");
        assertFalse(loading[0]);
    }

    private ZoomImageView open(String url, boolean immediateOriginal, int motion) {
        if (motion == MotionLevel.OFF) {
            ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
            ShadowChoreographer.setPaused(false);
        }
        Context context = ApplicationProvider.getApplicationContext();
        new SessionStore(context).setMotionLevel(motion);
        Intent intent = new Intent(context, ImageViewerActivity.class)
                .putExtra(ImageViewerActivity.EXTRA_URL, url)
                .putExtra(ImageViewerActivity.EXTRA_PREVIEW_ID,
                        ImageViewerActivity.preparePreview(url, preview, null))
                .putExtra(ImageViewerActivity.EXTRA_LOAD_ORIGINAL_IMMEDIATELY, immediateOriginal)
                .putExtra(ImageViewerActivity.EXTRA_ORIGIN_X, 50)
                .putExtra(ImageViewerActivity.EXTRA_ORIGIN_Y, 100)
                .putExtra(ImageViewerActivity.EXTRA_ORIGIN_WIDTH, 54)
                .putExtra(ImageViewerActivity.EXTRA_ORIGIN_HEIGHT, 54);
        owner = Robolectric.buildActivity(ImageViewerActivity.class, intent).setup();
        FrameLayout root = ReflectionHelpers.getField(owner.get(), "root");
        int exactWidth = android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY);
        int exactHeight = android.view.View.MeasureSpec.makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY);
        root.measure(exactWidth, exactHeight);
        root.layout(0, 0, 200, 300);
        // Deliver the mounting frame, then keep later frames beyond the timer under test.
        if (motion == MotionLevel.OFF) ShadowLooper.shadowMainLooper().idle();
        else ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(2));
        ZoomImageView[] images = ReflectionHelpers.getField(owner.get(), "images");
        assertTrue("Viewer must be mounted before checking animation order", images[0].isAttachedToWindow());
        return images[0];
    }

    private void finishFrames() {
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
        ShadowChoreographer.setPaused(false);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(3));
    }

    private Bitmap currentBitmap(ZoomImageView image) {
        return ((BitmapDrawable) image.getDrawable()).getBitmap();
    }

    private static Bitmap bitmap(int width, int height) {
        Bitmap value = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        value.setDensity(160);
        return value;
    }

    private static LruCache<String, Bitmap> imageCache() {
        return ReflectionHelpers.getStaticField(ImageLoader.class, "CACHE");
    }
}
