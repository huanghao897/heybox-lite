package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowLooper;

import java.time.Duration;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ImageViewerTransitionTest {
    @After public void restoreFrameClock() { ShadowChoreographer.setPaused(false); }
    @Test public void portraitThumbnailCropsInsteadOfSquashingTheWholeImage() {
        verifyCrop(new RectF(50, 0, 150, 400), new RectF(20, 30, 74, 84), 0, 0, 200, 400);
    }

    @Test public void landscapeThumbnailCropsInsteadOfStretchingTheWholeImage() {
        verifyCrop(new RectF(0, 150, 400, 250), new RectF(20, 30, 74, 84), 0, 0, 400, 400);
    }

    @Test public void sourceAndViewerOffsetsAreInTheSameRootCoordinateSpace() {
        verifyCrop(new RectF(15, 130, 215, 230), new RectF(40, 50, 94, 104), 15, 30, 200, 300);
    }

    private void verifyCrop(RectF display, RectF target, float left, float top, int width, int height) {
        ImageViewerTransition.Frame cropped = ImageViewerTransition.Frame.crop(display, target,
                left, top, width, height);
        RectF projected = new RectF(cropped.clip);
        projected.left = left + cropped.x + projected.left * cropped.scale;
        projected.right = left + cropped.x + projected.right * cropped.scale;
        projected.top = top + cropped.y + projected.top * cropped.scale;
        projected.bottom = top + cropped.y + projected.bottom * cropped.scale;
        assertEquals(target.left, projected.left, 0.01f);
        assertEquals(target.top, projected.top, 0.01f);
        assertEquals(target.right, projected.right, 0.01f);
        assertEquals(target.bottom, projected.bottom, 0.01f);
        View view = new View(ApplicationProvider.getApplicationContext());
        view.layout(0, 0, width, height);
        for (float progress : new float[]{0f, 0.2f, 0.5f, 0.9f, 1f}) {
            ImageViewerTransition.Frame.between(cropped, ImageViewerTransition.Frame.full(view), progress)
                    .apply(view);
            assertEquals("Every animation frame must preserve aspect ratio",
                    view.getScaleX(), view.getScaleY(), 0f);
            assertTrue(view.getScaleX() > 0f);
        }
        assertEquals(1f, view.getScaleX(), 0.001f);
        assertEquals(0f, view.getTranslationY(), 0.001f);
    }

    @Test public void cancellingEnterPreventsItsOldCompletionAndExitKeepsTheCurrentFrame() {
        ShadowChoreographer.setPaused(true);
        FrameLayout root = new FrameLayout(ApplicationProvider.getApplicationContext());
        View image = new View(root.getContext());
        root.addView(image, new FrameLayout.LayoutParams(200, 400));
        root.layout(0, 0, 200, 400);
        image.layout(0, 0, 200, 400);
        RectF display = new RectF(50, 0, 150, 400);
        RectF source = new RectF(20, 30, 74, 84);
        ImageViewerTransition transition = new ImageViewerTransition();
        int[] completions = {0};
        transition.enter(image, root, display, source, 240, new LinearInterpolator(),
                () -> completions[0]++);
        for (int frame = 0; frame < 6; frame++) {
            ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(16));
        }
        assertEquals("The opening animation must still be running before 100ms", 0, completions[0]);
        transition.cancel();
        float scale = image.getScaleX();
        float x = image.getTranslationX();
        float y = image.getTranslationY();
        Rect clip = image.getClipBounds();
        transition.exit(image, root, display, source, 220, new LinearInterpolator(),
                () -> completions[0] += 10);
        assertEquals(scale, image.getScaleX(), 0f);
        assertEquals(x, image.getTranslationX(), 0f);
        assertEquals(y, image.getTranslationY(), 0f);
        assertEquals(clip, image.getClipBounds());
        ShadowChoreographer.setPaused(false);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(300));
        assertEquals(10, completions[0]);
    }

    @Test public void launcherRetainsTheActualThumbnailAndItsVisibleBounds() {
        ActivityController<Activity> owner = Robolectric.buildActivity(Activity.class).setup();
        try {
            Activity activity = owner.get();
            ImageView image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap bitmap = Bitmap.createBitmap(100, 400, Bitmap.Config.ARGB_8888);
            image.setImageBitmap(bitmap);
            activity.setContentView(image);
            image.layout(20, 30, 100, 90);
            Rect expected = ImageTransitionSource.visibleBoundsOnScreen(image);
            assertTrue(expected.width() > 0);

            ImageViewerLauncher.open(activity, image, new String[]{"original.jpg"}, 0);

            Intent opened = Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals(expected.width(), opened.getIntExtra(ImageViewerActivity.EXTRA_ORIGIN_WIDTH, 0));
            assertEquals(expected.height(), opened.getIntExtra(ImageViewerActivity.EXTRA_ORIGIN_HEIGHT, 0));
            ImagePreviewStore.Preview preview = ImagePreviewStore.claim(
                    opened.getLongExtra(ImageViewerActivity.EXTRA_PREVIEW_ID, 0));
            assertNotNull(preview);
            assertSame(image, preview.source.get());
            assertSame(bitmap, preview.bitmap.get());
        } finally { owner.pause().stop().destroy(); }
    }

    @Test public void aMissingThumbnailDoesNotPretendTheEntireScreenIsTheImageSource() {
        ActivityController<Activity> owner = Robolectric.buildActivity(Activity.class).setup();
        try {
            Activity activity = owner.get();
            ImageViewerLauncher.open(activity, null, new String[]{"original.jpg"}, 0);
            Intent opened = Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals(0, opened.getIntExtra(ImageViewerActivity.EXTRA_ORIGIN_WIDTH, -1));
            assertEquals(0, opened.getIntExtra(ImageViewerActivity.EXTRA_ORIGIN_HEIGHT, -1));
        } finally { owner.pause().stop().destroy(); }
    }
}
