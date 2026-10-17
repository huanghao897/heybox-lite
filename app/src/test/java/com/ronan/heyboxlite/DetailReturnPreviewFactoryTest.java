package com.ronan.heyboxlite;

import static com.ronan.heyboxlite.NativeRoundUiTestSupport.layoutActivity;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class DetailReturnPreviewFactoryTest {
    private final TransitionSnapshotStore pages = new TransitionSnapshotStore();
    private final TransitionSnapshotStore screens = new TransitionSnapshotStore();
    private final ThemeTokens theme = ThemeTokens.of(true, 0, 0);
    private ActivityController<Activity> owner;
    private FrameLayout root;

    @Before public void openHost() {
        owner = Robolectric.buildActivity(Activity.class).setup();
        root = new FrameLayout(owner.get());
        owner.get().setContentView(root);
        owner.visible();
        layoutActivity(owner.get());
    }

    @After public void closeHost() {
        root.removeAllViews();
        owner.pause().stop().destroy();
        pages.releaseAll();
        screens.releaseAll();
    }

    @Test public void pageSnapshotTakesPriorityAndOwnsTheAttachedPreview() {
        Bitmap page = capture(pages, Color.RED);
        Bitmap screen = capture(screens, Color.BLUE);
        ImageView preview = mountPreview();
        assertSame(page, bitmap(preview));
        pages.clear();
        screens.clear();
        assertFalse(page.isRecycled());
        assertTrue("The unused full-screen fallback has no preview owner", screen.isRecycled());
        assertDraws(preview);
        root.removeView(preview);
        assertNull(preview.getDrawable());
        assertTrue(page.isRecycled());
    }

    @Test public void missingPageSnapshotUsesTheFullScreenFallbackWithTheSameLifetime() {
        Bitmap screen = capture(screens, Color.BLUE);
        ImageView preview = mountPreview();
        assertSame(screen, bitmap(preview));
        screens.clear();
        assertFalse(screen.isRecycled());
        assertDraws(preview);
        root.removeView(preview);
        assertNull(preview.getDrawable());
        assertTrue(screen.isRecycled());
    }

    @Test public void missingSnapshotsKeepTheExistingPanelPlaceholder() {
        ImageView preview = mountPreview();
        assertNull(preview.getDrawable());
        assertEquals(theme.panel, ((ColorDrawable) preview.getBackground()).getColor());
        pages.clear();
        screens.clear();
        assertDraws(preview);
    }

    private ImageView mountPreview() {
        ImageView preview = (ImageView) DetailReturnPreviewFactory.create(
                owner.get(), theme, "feed", pages, screens);
        root.addView(preview, new FrameLayout.LayoutParams(30, 30));
        layoutActivity(owner.get());
        assertTrue(preview.isAttachedToWindow());
        return preview;
    }

    private Bitmap capture(TransitionSnapshotStore store, int color) {
        View source = new View(owner.get());
        source.layout(0, 0, 30, 30);
        store.capture("feed", 2, source, color, null);
        return store.get("feed");
    }

    private static Bitmap bitmap(ImageView preview) {
        return ((BitmapDrawable) preview.getDrawable()).getBitmap();
    }

    private static void assertDraws(ImageView preview) {
        Bitmap frame = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888);
        preview.draw(new Canvas(frame));
        frame.recycle();
    }
}
