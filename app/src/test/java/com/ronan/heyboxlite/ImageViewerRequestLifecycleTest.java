package com.ronan.heyboxlite;

import static com.ronan.heyboxlite.NativeRoundUiTestSupport.layoutActivity;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import androidx.test.core.app.ApplicationProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "w240dp-h320dp-notround-mdpi",
        shadows = ImageViewerRequestLifecycleTest.Images.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ImageViewerRequestLifecycleTest {
    private static final String[] URLS = {"https://viewer.example/one.jpg",
            "https://viewer.example/two.jpg", "https://viewer.example/three.jpg"};
    private final int previousMotion = Motions.level();
    private ActivityController<ImageViewerActivity> owner;

    @Before public void resetRequests() {
        Images.previews.clear();
        Images.originals.clear();
        Images.longImages.clear();
    }

    @After public void closeViewer() {
        if (owner != null) owner.pause().stop().destroy();
        Motions.setLevel(previousMotion);
    }

    @Test public void revisitingANeighbourDoesNotQueueItsPendingOriginalAgain() {
        open(true);
        Images.previews.get(0).complete(bitmap(80, 80));
        assertEquals(1, Images.originals.size());
        select(1);
        select(0);
        assertEquals(2, Images.originals.size());
        assertEquals(URLS[0], Images.originals.get(0).url);
        assertEquals(URLS[1], Images.originals.get(1).url);
        Images.originals.get(0).complete(bitmap(160, 160));
        boolean[] loaded = ReflectionHelpers.getField(owner.get(), "originalLoaded");
        assertTrue(loaded[0]);
    }

    @Test public void releasingADistantPageCancelsItsPreviewAndRejectsLateDelivery() {
        open(true);
        Pending preview = Images.previews.get(0);
        select(2);
        releaseDelay();
        assertTrue(preview.cancelled());
        preview.complete(bitmap(80, 80));
        assertNull(images()[0].getDrawable());
        boolean[] loaded = ReflectionHelpers.getField(owner.get(), "loaded");
        assertFalse(loaded[0]);
        select(0);
        assertEquals(2, Images.previews.stream().filter(request -> URLS[0].equals(request.url)).count());
        assertTrue(loaded[0]);
    }

    @Test public void releasingADistantPageCancelsItsOriginalAndRejectsLateDelivery() {
        open(true);
        Images.previews.get(0).complete(bitmap(80, 80));
        Pending original = Images.originals.get(0);
        select(2);
        releaseDelay();
        assertTrue(original.cancelled());
        original.complete(bitmap(160, 160));
        assertNull(images()[0].getDrawable());
        boolean[] loaded = ReflectionHelpers.getField(owner.get(), "originalLoaded");
        assertFalse(loaded[0]);
        select(0);
        assertEquals(2, Images.originals.stream().filter(request -> URLS[0].equals(request.url)).count());
    }

    @Test public void destroyedViewerCancelsResidentPreviewAndOriginalRequests() {
        open(true);
        Images.previews.get(0).complete(bitmap(80, 80));
        Pending neighbour = Images.previews.get(1);
        Pending original = Images.originals.get(0);
        ZoomImageView[] views = images();
        owner.pause().stop().destroy();
        owner = null;
        assertTrue(neighbour.cancelled());
        assertTrue(original.cancelled());
        neighbour.complete(bitmap(80, 80));
        original.complete(bitmap(160, 160));
        assertNull(views[0].getDrawable());
        assertNull(views[1].getDrawable());
    }

    @Test public void leavingAnUnfinishedLongImageReleasesItsPlaceholderAndAllowsRetry() {
        open(false);
        Images.previews.get(0).complete(bitmap(64, 256));
        assertEquals(1, Images.longImages.size());
        ImageLoader.LongImageCallback old = Images.longImages.get(0);
        old.onStart(64, 256, 2);
        Bitmap partialTile = bitmap(64, 128);
        old.onTile(0, partialTile);
        DismissibleImageScrollView[] scrolls = ReflectionHelpers.getField(owner.get(), "longViews");
        FrameLayout[] pages = ReflectionHelpers.getField(owner.get(), "imagePages");
        DismissibleImageScrollView abandoned = scrolls[0];
        assertNotNull(abandoned);
        assertEquals(View.INVISIBLE, abandoned.getVisibility());
        select(1);
        assertTrue(old.cancelled());
        assertNull(scrolls[0]);
        assertNull(abandoned.getParent());
        assertTrue(partialTile.isRecycled());
        select(0);
        assertEquals(2, Images.longImages.size());
        ImageLoader.LongImageCallback retried = Images.longImages.get(1);
        retried.onStart(64, 256, 2);
        old.onComplete(false);
        assertNotNull("The cancelled completion cannot remove the new long image", scrolls[0]);
        retried.onTile(0, bitmap(64, 128));
        retried.onTile(1, bitmap(64, 128));
        retried.onComplete(true);
        assertEquals(View.VISIBLE, scrolls[0].getVisibility());
        assertSame(pages[0], scrolls[0].getParent());
        DismissibleImageScrollView complete = scrolls[0];
        select(1);
        select(0);
        assertSame("A completed neighbour remains available", complete, scrolls[0]);
        assertEquals(2, Images.longImages.size());
    }

    private void open(boolean originalImmediately) {
        Context context = ApplicationProvider.getApplicationContext();
        SessionStore session = new SessionStore(context);
        session.setMotionLevel(MotionLevel.OFF);
        session.setRoundScreen(false);
        session.setOriginalImages(false);
        Intent intent = new Intent(context, ImageViewerActivity.class)
                .putExtra(ImageViewerActivity.EXTRA_URLS, URLS)
                .putExtra(ImageViewerActivity.EXTRA_LOAD_ORIGINAL_IMMEDIATELY, originalImmediately);
        owner = Robolectric.buildActivity(ImageViewerActivity.class, intent).setup().visible();
        layoutActivity(owner.get());
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void select(int index) {
        ImagePagerCore pager = ReflectionHelpers.getField(owner.get(), "pager");
        ImagePagerCore.PagerListener listener = ReflectionHelpers.getField(pager, "listener");
        pager.setPage(index);
        listener.onPage(index);
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void releaseDelay() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(301));
    }

    private ZoomImageView[] images() { return ReflectionHelpers.getField(owner.get(), "images"); }

    private static Bitmap bitmap(int width, int height) {
        Bitmap value = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        value.setDensity(160);
        return value;
    }

    private static final class Pending {
        final String url;
        final ImageLoader.Callback callback;
        final ImageLoadRequest request;

        Pending(String url, ImageLoader.Callback callback) {
            this.url = url;
            this.callback = callback;
            request = ImageLoadRequest.deliver(new Handler(Looper.getMainLooper()), null, ignored -> {});
        }

        boolean cancelled() {
            return ((AtomicBoolean) ReflectionHelpers.getField(request, "cancelled")).get();
        }

        void complete(Bitmap value) {
            // Invoke directly to simulate an already-dispatched callback racing with cancellation.
            callback.onLoaded(value);
            shadowOf(Looper.getMainLooper()).idle();
        }
    }

    @Implements(ImageLoader.class)
    public static class Images {
        static final List<Pending> previews = new ArrayList<>();
        static final List<Pending> originals = new ArrayList<>();
        static final List<ImageLoader.LongImageCallback> longImages = new ArrayList<>();
        @Implementation protected static void init(Context context) {}
        @Implementation protected static ImageLoadRequest loadCancellable(
                String url, int target, ImageLoader.Callback callback) {
            Pending pending = new Pending(url, callback);
            previews.add(pending);
            return pending.request;
        }
        @Implementation protected static ImageLoadRequest loadOriginalCancellable(
                String url, int target, ImageLoader.Callback callback) {
            Pending pending = new Pending(url, callback);
            originals.add(pending);
            return pending.request;
        }
        @Implementation protected static void loadLongImageTiles(
                String url, int target, ImageLoader.LongImageCallback callback) {
            longImages.add(callback);
        }
    }
}
