package com.ronan.heyboxlite;

import static org.junit.Assert.*;
import static com.ronan.heyboxlite.NativeRoundUiTestSupport.layoutActivity;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TransitionSnapshotStoreTest {
    private final TransitionSnapshotStore store = new TransitionSnapshotStore();
    private ActivityController<Activity> owner;
    private FrameLayout root;

    @After public void release() {
        if (root != null) root.removeAllViews();
        if (owner != null) owner.pause().stop().destroy();
        store.releaseAll();
    }

    @Test public void releasingOneOverlayDoesNotRecycleTheOtherOverlaysBitmap() {
        Bitmap bitmap = bitmap();
        ImageView first = overlay();
        ImageView second = overlay();
        store.registerOverlay(first, bitmap);
        store.registerOverlay(second, bitmap);
        store.releaseOverlay(first);
        assertFalse("The second visible overlay still owns this bitmap", bitmap.isRecycled());
        store.releaseOverlay(second);
        assertTrue(bitmap.isRecycled());
    }

    @Test public void rebindingAnOverlayReleasesItsPreviousBitmap() {
        ImageView overlay = overlay();
        Bitmap previous = bitmap();
        Bitmap current = bitmap();
        store.registerOverlay(overlay, previous);
        store.registerOverlay(overlay, current);
        assertTrue("Replacing an overlay must not leave an unowned lease", previous.isRecycled());
        assertFalse(current.isRecycled());
        store.releaseOverlay(overlay);
        assertTrue(current.isRecycled());
    }

    @Test public void clearingSnapshotsDefersRecyclingUntilTheirLastOverlayLeaves() {
        View source = new View(RuntimeEnvironment.getApplication());
        source.layout(0, 0, 30, 30);
        store.capture("page", 2, source, Color.BLACK, null);
        Bitmap bitmap = store.get("page");
        assertNotNull(bitmap);
        ImageView overlay = overlay();
        store.registerOverlay(overlay, bitmap);
        store.clear();
        assertNull(store.get("page"));
        assertFalse(bitmap.isRecycled());
        store.releaseOverlay(overlay);
        assertTrue(bitmap.isRecycled());
    }

    @Test public void hostDisposalReleasesSharedOverlaysAndIsIdempotent() {
        Bitmap bitmap = bitmap();
        ImageView first = overlay();
        ImageView second = overlay();
        store.registerOverlay(first, bitmap);
        store.registerOverlay(second, bitmap);
        store.releaseAll();
        assertTrue(bitmap.isRecycled());
        store.releaseOverlay(first);
        store.releaseOverlay(second);
        store.releaseAll();
    }

    @Test public void attachedPreviewSurvivesCacheClearAndReleasesOnRemoval() {
        Bitmap bitmap = capture("page");
        ImageView preview = overlay();
        assertTrue(store.bindPreview(preview, "page"));
        mount(preview);
        assertTrue(preview.isAttachedToWindow());
        store.clear();
        assertNull(store.get("page"));
        assertFalse("A painted preview remains an owner after low-memory cache clearing", bitmap.isRecycled());
        assertSame(bitmap, ((BitmapDrawable) preview.getDrawable()).getBitmap());
        Bitmap frame = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888);
        preview.draw(new Canvas(frame));
        frame.recycle();
        root.removeView(preview);
        assertNull(preview.getDrawable());
        assertTrue(bitmap.isRecycled());
    }

    @Test public void sharedAttachedPreviewsKeepTheBitmapUntilTheLastRemoval() {
        Bitmap bitmap = capture("page");
        ImageView first = overlay();
        ImageView second = overlay();
        assertTrue(store.bindPreview(first, "page"));
        assertTrue(store.bindPreview(second, "page"));
        mount(first);
        root.addView(second, new FrameLayout.LayoutParams(30, 30));
        assertTrue(second.isAttachedToWindow());
        store.clear();
        root.removeView(first);
        assertNull(first.getDrawable());
        assertFalse(bitmap.isRecycled());
        root.removeView(second);
        assertNull(second.getDrawable());
        assertTrue(bitmap.isRecycled());
    }

    @Test public void retainedPreviewReacquiresItsSnapshotWhenMountedAgain() {
        Bitmap bitmap = capture("page");
        ImageView preview = overlay();
        assertTrue(store.bindPreview(preview, "page"));
        mount(preview);
        root.removeView(preview);
        assertNull(preview.getDrawable());
        assertFalse("The cache still owns the detached preview's bitmap", bitmap.isRecycled());
        root.addView(preview, new FrameLayout.LayoutParams(30, 30));
        assertSame(bitmap, ((BitmapDrawable) preview.getDrawable()).getBitmap());
        store.clear();
        assertFalse(bitmap.isRecycled());
        root.removeView(preview);
        assertTrue(bitmap.isRecycled());
    }

    private Bitmap capture(String key) {
        View source = new View(RuntimeEnvironment.getApplication());
        source.layout(0, 0, 30, 30);
        store.capture(key, 2, source, Color.BLACK, null);
        return store.get(key);
    }

    private void mount(ImageView preview) {
        owner = Robolectric.buildActivity(Activity.class).setup();
        root = new FrameLayout(owner.get());
        owner.get().setContentView(root);
        owner.visible();
        root.addView(preview, new FrameLayout.LayoutParams(30, 30));
        layoutActivity(owner.get());
    }

    private static Bitmap bitmap() { return Bitmap.createBitmap(20, 20, Bitmap.Config.RGB_565); }
    private static ImageView overlay() { return new ImageView(RuntimeEnvironment.getApplication()); }
}
