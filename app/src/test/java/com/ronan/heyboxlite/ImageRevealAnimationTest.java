package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;

import java.time.Duration;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ImageRevealAnimationTest {
    @Test public void showingAnImageResetsTransformsAndReleasesTheClipAfterAnimation() {
        ImageView view = imageView();
        Bitmap bitmap = Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888);
        view.setAlpha(0.3f);
        view.setScaleX(0.5f);
        view.setScaleY(0.5f);

        ImageRevealAnimation.show(view, bitmap);

        assertSame(bitmap, ((BitmapDrawable) view.getDrawable()).getBitmap());
        assertEquals(1f, view.getAlpha(), 0f);
        assertEquals(1f, view.getScaleX(), 0f);
        assertEquals(1f, view.getScaleY(), 0f);
        assertNotNull(view.getClipBounds());
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(400));
        assertNull(view.getClipBounds());
    }

    @Test public void cancellingAnImageClearsTheClipAndInvalidatesItsSource() {
        ImageView view = imageView();
        view.setTag("old-image");
        ImageRevealAnimation.show(view, Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888));

        ImageLoader.cancel(view);

        assertNull(view.getTag());
        assertNull(view.getClipBounds());
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(400));
        assertNull(view.getClipBounds());
    }

    @Test public void aDeferredOldSourceCannotRestartRevealAfterTheViewIsRebound() {
        ActivityController<Activity> owner = Robolectric.buildActivity(Activity.class).setup();
        try {
            ImageView view = new ImageView(owner.get());
            owner.get().setContentView(view);
            view.layout(0, 0, 0, 0);
            view.setTag("old-image");
            ImageRevealAnimation.show(view, Bitmap.createBitmap(20, 40, Bitmap.Config.ARGB_8888));
            view.setTag("new-image");
            view.layout(0, 0, 20, 40);
            Rect clip = new Rect(1, 2, 18, 38);
            view.setClipBounds(clip);

            ShadowLooper.shadowMainLooper().idle();

            assertEquals(clip, view.getClipBounds());
            ImageRevealAnimation.clear(view);
        } finally {
            owner.pause().stop().destroy();
        }
    }

    private static ImageView imageView() {
        ImageView view = new ImageView(ApplicationProvider.getApplicationContext());
        view.layout(0, 0, 20, 40);
        return view;
    }
}
