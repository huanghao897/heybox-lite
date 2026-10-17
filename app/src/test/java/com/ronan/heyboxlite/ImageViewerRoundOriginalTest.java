package com.ronan.heyboxlite;

import static com.ronan.heyboxlite.NativeRoundUiTestSupport.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "w192dp-h192dp-round-mdpi",
        shadows = ImageViewerRoundOriginalTest.Images.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ImageViewerRoundOriginalTest {
    private ActivityController<ImageViewerActivity> owner;
    private final int previousMotion = Motions.level();

    @Before public void prepareImages() {
        Images.originalUrls.clear();
        Images.callbacks.clear();
        Images.sizeProbes = 0;
        Images.preview = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(Images.preview);
        canvas.drawColor(Color.rgb(236, 239, 244));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(12);
        for (int i = 0; i < 7; i++) {
            paint.setColor(i % 2 == 0 ? Color.rgb(40, 116, 204) : Color.WHITE);
            canvas.drawRoundRect(14, 19 + i * 24, 178, 39 + i * 24, 5, 5, paint);
            paint.setColor(i % 2 == 0 ? Color.WHITE : Color.DKGRAY);
            canvas.drawText("Image detail " + (i + 1), 23, 33 + i * 24, paint);
        }
    }

    @After public void cleanup() {
        if (owner != null) owner.pause().stop().destroy();
        Motions.setLevel(previousMotion);
    }

    @Test public void roundLoadsOriginalAutomaticallyAndKeepsBothSmallControlsTouchable() throws Exception {
        FrameLayout root = open(false, false);
        assertEquals(java.util.Arrays.asList("https://viewer.example/one.jpg"), Images.originalUrls);
        assertSame(Images.preview, bitmap(0));
        Bitmap original = Bitmap.createScaledBitmap(Images.preview, 384, 384, true);
        Images.callbacks.get(0).onLoaded(original);
        shadowOf(Looper.getMainLooper()).idle();
        assertSame(original, bitmap(0));
        assertEquals(View.GONE, originalButton().getVisibility());
        assertEquals(0, Images.sizeProbes);
        capture(root, "round-image-compact-original");
        ImageView close = ReflectionHelpers.getField(owner.get(), "closeButton");
        ImageView download = ReflectionHelpers.getField(owner.get(), "download");
        assertInCircle(bounds(root, close), 192, 192);
        assertInCircle(bounds(root, download), 192, 192);
        assertEquals("下载图片", download.getContentDescription());
        tap(root, close);
        assertTrue(owner.get().isFinishing());
    }

    @Test public void failedOriginalKeepsPreviewAndDoesNotRestoreTheRemovedButton() {
        open(false, false);
        Images.callbacks.get(0).onLoaded(null);
        shadowOf(Looper.getMainLooper()).idle();
        assertSame(Images.preview, bitmap(0));
        assertEquals(View.GONE, originalButton().getVisibility());
        assertFalse(owner.get().isFinishing());
    }

    @Test @Config(qualifiers = "w192dp-h192dp-notround-mdpi")
    public void forcedRoundAlsoDefaultsToOriginalEvenWhenTheManualSettingIsOff() {
        open(true, false);
        assertEquals(1, Images.originalUrls.size());
        assertEquals(View.GONE, originalButton().getVisibility());
    }

    @Test public void eachSelectedPageLoadsItsOwnOriginalWithoutUpgradingNeighbours() {
        open(false, true);
        assertEquals(1, Images.originalUrls.size());
        ImagePagerCore pager = ReflectionHelpers.getField(owner.get(), "pager");
        ImagePagerCore.PagerListener listener = ReflectionHelpers.getField(pager, "listener");
        pager.setPage(1);
        listener.onPage(1);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(java.util.Arrays.asList("https://viewer.example/one.jpg",
                "https://viewer.example/two.jpg"), Images.originalUrls);
        assertEquals("2/2", ((TextView) ReflectionHelpers.getField(owner.get(), "counter")).getText().toString());
        Images.callbacks.get(0).onLoaded(null);
        assertEquals(View.GONE, originalButton().getVisibility());
    }

    @Test @Config(qualifiers = "w240dp-h320dp-notround-mdpi")
    public void squareRetainsTheManualOriginalSettingAndButton() {
        open(false, false);
        assertTrue(Images.originalUrls.isEmpty());
        assertEquals(View.VISIBLE, originalButton().getVisibility());
        originalButton().performClick();
        assertEquals(1, Images.originalUrls.size());
    }

    private FrameLayout open(boolean forceRound, boolean multiple) {
        Context context = ApplicationProvider.getApplicationContext();
        SessionStore session = new SessionStore(context);
        session.setMotionLevel(MotionLevel.OFF);
        session.setRoundScreen(forceRound);
        session.setOriginalImages(!forceRound);
        Intent intent = new Intent(context, ImageViewerActivity.class)
                .putExtra(ImageViewerActivity.EXTRA_URL, "https://viewer.example/one.jpg");
        if (multiple) intent.putExtra(ImageViewerActivity.EXTRA_URLS,
                new String[]{"https://viewer.example/one.jpg", "https://viewer.example/two.jpg"});
        owner = Robolectric.buildActivity(ImageViewerActivity.class, intent).setup().visible();
        FrameLayout root = ReflectionHelpers.getField(owner.get(), "root");
        layoutActivity(owner.get());
        shadowOf(Looper.getMainLooper()).idle();
        layoutActivity(owner.get());
        return root;
    }

    private Bitmap bitmap(int index) {
        ZoomImageView[] images = ReflectionHelpers.getField(owner.get(), "images");
        return ((BitmapDrawable) images[index].getDrawable()).getBitmap();
    }

    private TextView originalButton() { return ReflectionHelpers.getField(owner.get(), "original"); }

    private static void capture(FrameLayout root, String name) throws Exception {
        Bitmap image = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(image));
        File file = new File("build/outputs/ui-regression/" + name + ".png");
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { image.recycle(); }
    }

    @Implements(ImageLoader.class)
    public static class Images {
        static Bitmap preview;
        static int sizeProbes;
        static final List<String> originalUrls = new ArrayList<>();
        static final List<ImageLoader.Callback> callbacks = new ArrayList<>();
        @Implementation protected static void init(Context context) {}
        @Implementation protected static ImageLoadRequest loadCancellable(
                String url, int target, ImageLoader.Callback callback) {
            return ImageLoadRequest.deliver(new Handler(Looper.getMainLooper()), preview, callback);
        }
        @Implementation protected static ImageLoadRequest loadOriginalCancellable(
                String url, int target, ImageLoader.Callback callback) {
            originalUrls.add(url);
            callbacks.add(callback);
            return ImageLoadRequest.deliver(new Handler(Looper.getMainLooper()), null, ignored -> {});
        }
        @Implementation protected static void probeOriginalSize(String url, ImageLoader.SizeCallback callback) {
            sizeProbes++;
            callback.onSize(1024);
        }
    }
}
