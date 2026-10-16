package com.ronan.heyboxlite;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class ImageViewerBitmapSwapTest {
    private final int previousMotion = Motions.level();
    @After public void restoreMotion() { Motions.setLevel(previousMotion); }

    @Test public void aSharperBitmapKeepsTheCurrentZoomAndVisibleImagePosition() {
        verifySharperBitmapKeepsItsPosition();
    }

    @Test @Config(qualifiers = "xhdpi")
    public void aSharperBitmapKeepsItsPositionWhenDrawableAndBitmapDensitiesDiffer() {
        verifySharperBitmapKeepsItsPosition();
    }

    private void verifySharperBitmapKeepsItsPosition() {
        Motions.setLevel(MotionLevel.OFF);
        ZoomImageView image = new ZoomImageView(ApplicationProvider.getApplicationContext());
        image.layout(0, 0, 200, 200);
        image.setImageBitmap(bitmap(100, 400));
        image.fitImage();
        long time = SystemClock.uptimeMillis();
        tap(image, time);
        tap(image, time + 100);
        assertTrue("Exercise the actual double-tap zoom before replacing its bitmap", image.isZoomed());
        RectF before = display(image);

        image.replaceBitmap(bitmap(200, 800));

        assertTrue(image.isZoomed());
        RectF after = display(image);
        assertEquals(before.left, after.left, 0.01f);
        assertEquals(before.top, after.top, 0.01f);
        assertEquals(before.right, after.right, 0.01f);
        assertEquals(before.bottom, after.bottom, 0.01f);
    }

    @Test public void firstLayoutFitsTheImageBeforeAnyFrameCanDraw() {
        verifyFirstLayout(false, new RectF(50, 0, 150, 400));
    }

    @Test @Config(qualifiers = "w200dp-h400dp-round-mdpi")
    public void roundFirstLayoutFitsTheImageBeforeAnyFrameCanDraw() {
        verifyFirstLayout(true, new RectF(55, 20, 145, 380));
    }

    private void verifyFirstLayout(boolean round, RectF expected) {
        ZoomImageView image = new ZoomImageView(ApplicationProvider.getApplicationContext());
        image.setRoundDisplay(round);
        image.setImageBitmap(bitmap(100, 400));
        image.layout(0, 0, 200, 400);
        assertEquals(expected, display(image));
    }

    @Test public void viewportResizeFitsSynchronouslyInsteadOfJumpingOnTheNextFrame() {
        ZoomImageView image = new ZoomImageView(ApplicationProvider.getApplicationContext());
        image.layout(0, 0, 200, 400);
        image.setImageBitmap(bitmap(100, 400));
        image.fitImage();
        image.layout(0, 0, 200, 200);
        assertEquals(new RectF(75, 0, 125, 200), display(image));
    }

    @Test public void aDifferentPreviewAspectCannotStretchTheNewBitmap() {
        Motions.setLevel(MotionLevel.OFF);
        ZoomImageView image = new ZoomImageView(ApplicationProvider.getApplicationContext());
        image.layout(0, 0, 200, 200);
        image.replaceBitmap(bitmap(100, 400));
        long time = SystemClock.uptimeMillis();
        tap(image, time);
        tap(image, time + 100);
        assertTrue(image.isZoomed());
        float width = display(image).width();
        image.replaceBitmap(bitmap(200, 600));
        float[] values = new float[9];
        image.getImageMatrix().getValues(values);
        assertEquals("A new decode must retain its own aspect ratio",
                values[Matrix.MSCALE_X], values[Matrix.MSCALE_Y], 0.001f);
        assertEquals(width, display(image).width(), 0.01f);
    }

    @Test public void aNormalPreviewUpgradeStillFitsTheNewBitmap() {
        ZoomImageView image = new ZoomImageView(ApplicationProvider.getApplicationContext());
        image.layout(0, 0, 200, 200);
        image.setImageBitmap(Bitmap.createBitmap(100, 400, Bitmap.Config.ARGB_8888));
        image.fitImage();
        image.replaceBitmap(Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888));
        assertFalse(image.isZoomed());
        RectF after = display(image);
        assertEquals(new RectF(0, 50, 200, 150), after);
    }

    private RectF display(ZoomImageView image) {
        RectF bounds = new RectF();
        image.imageDisplayRect(bounds);
        return bounds;
    }

    private Bitmap bitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(160);
        return bitmap;
    }

    private void tap(ZoomImageView image, long time) {
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 100, 100, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 20, MotionEvent.ACTION_UP, 100, 100, 0);
        try {
            image.onTouchEvent(down);
            image.onTouchEvent(up);
        } finally {
            down.recycle();
            up.recycle();
        }
    }
}
