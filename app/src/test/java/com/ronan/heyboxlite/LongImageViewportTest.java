package com.ronan.heyboxlite;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LongImageViewportTest {
    private static final String URL = "https://long-image.invalid/fixture.png";
    @Rule public TemporaryFolder files = new TemporaryFolder();
    private File previousDirectory;
    private Context previousContext;
    private final List<LongImageTileView> views = new ArrayList<>();

    @Before public void prepareCompressedLongImage() throws Exception {
        previousDirectory = (File) field(ImageDiskCache.class, "directory").get(null);
        previousContext = (Context) field(ImageLoader.class, "appContext").get(null);
        ImageLoader.init(ApplicationProvider.getApplicationContext());
        field(ImageDiskCache.class, "directory").set(null, files.newFolder("cache"));
        Bitmap input = Bitmap.createBitmap(640, 12000, Bitmap.Config.ARGB_8888);
        new Canvas(input).drawColor(Color.GREEN);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        assertTrue(input.compress(Bitmap.CompressFormat.PNG, 100, encoded));
        input.recycle();
        assertTrue(encoded.size() < ImageLoader.MAX_DECODE_BYTES);
        ImageDiskCache.write(URL, encoded.toByteArray());
    }

    @After public void releaseTilesAndRestoreCache() throws Exception {
        for (LongImageTileView view : views) view.close();
        shadowOf(Looper.getMainLooper()).idle();
        field(ImageDiskCache.class, "directory").set(null, previousDirectory);
        field(ImageLoader.class, "appContext").set(null, previousContext);
    }

    @Test public void scrollingRecyclesOffscreenTilesWithoutDecodingTheEntireLongImage() throws Exception {
        ImageTileBudget budget = new ImageTileBudget(ImageDecodePolicy.tileBytes(128L * 1024 * 1024));
        AtomicInteger ready = new AtomicInteger();
        LongImageTileView view = view(budget, ready);
        view.viewport(0, 320);
        settle(view);
        assertEquals(1, ready.get());
        assertTrue(view.residentCount() > 0 && view.residentCount() <= 5);
        assertTrue(budget.used() > 0 && budget.peak() <= budget.limit);
        List<Bitmap> previous = bitmaps(view);
        view.viewport(3500, 320);
        settle(view);
        assertTrue(previous.stream().allMatch(Bitmap::isRecycled));
        assertTrue(view.residentCount() > 0 && view.residentCount() <= 5);
        assertTrue(budget.used() <= budget.limit);
        view.close();
        assertEquals(0, budget.used());
    }

    @Test public void sharedBudgetIncludesPendingDeliveriesAndPauseReleasesAllReservations() throws Exception {
        ImageTileBudget budget = new ImageTileBudget(64 * 1024);
        LongImageTileView first = view(budget, new AtomicInteger());
        LongImageTileView second = view(budget, new AtomicInteger());
        first.viewport(0, 320);
        second.viewport(0, 320);
        assertTrue(first.pendingTile() && second.pendingTile());
        assertTrue(budget.used() > 0 && budget.used() <= budget.limit);
        settle(first);
        settle(second);
        assertTrue(budget.peak() <= budget.limit);
        first.setActive(false);
        second.setActive(false);
        assertEquals(0, first.residentCount());
        assertEquals(0, second.residentCount());
        assertEquals(0, budget.used());
        first.setActive(true);
        first.viewport(1000, 320);
        settle(first);
        assertTrue(first.residentCount() > 0);
        first.close();
        assertEquals(0, budget.used());
    }

    @Test public void closeDuringDecodeReleasesPendingPixelsAndCannotPublishNewTiles() throws Exception {
        ImageTileBudget budget = new ImageTileBudget(64 * 1024);
        AtomicInteger ready = new AtomicInteger();
        LongImageTileView view = view(budget, ready);
        view.viewport(0, 320);
        view.close();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (budget.used() != 0 && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(1L);
        }
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, budget.used());
        assertEquals(0, ready.get());
        assertEquals(0, view.residentCount());
    }

    @Test public void composeTileReplacementReleasesPixelsWithoutClosingPageSource() throws Exception {
        ImageTileBudget budget = new ImageTileBudget(256 * 1024);
        LongImageTileView view = view(budget, new AtomicInteger());
        LongImageSource source = (LongImageSource) field(LongImageTileView.class, "source").get(view);
        view.viewport(0, 320);
        settle(view);
        view.release();
        assertEquals(0, budget.used());
        Bitmap region = source.decode(new android.graphics.Rect(0, 0, 640, 320), 2);
        assertNotNull(region);
        region.recycle();
        source.close();
        assertNull(source.decode(new android.graphics.Rect(0, 0, 640, 320), 2));
    }

    @Test public void viewportReceivedBeforeLayoutIsAppliedAfterSizeArrives() throws Exception {
        LongImageSource source = LongImageSource.open(URL, files.getRoot());
        assertNotNull(source);
        AtomicInteger ready = new AtomicInteger();
        LongImageTileView view = new LongImageTileView(ApplicationProvider.getApplicationContext(),
                source, 240, ready::incrementAndGet, () -> fail("Valid fixture must decode"));
        views.add(view);
        view.viewport(0, 320);
        assertFalse(view.pendingTile());
        view.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        settle(view);
        assertEquals(1, ready.get());
        assertTrue(view.residentCount() > 0);
    }

    @Test public void repeatedDecodeFailureNotifiesViewerAndReleasesItsHiddenLongPage() throws Exception {
        ImageTileBudget budget = new ImageTileBudget(64 * 1024);
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger ready = new AtomicInteger();
        LongImageTileView view = view(budget, ready, failed::incrementAndGet);
        ((LongImageSource) field(LongImageTileView.class, "source").get(view)).close();
        view.viewport(0, 320);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (failed.get() == 0 && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
            Thread.sleep(1L);
        }
        assertEquals(1, failed.get());
        assertEquals(0, ready.get());
        assertEquals(0, budget.used());
        assertFalse(view.pendingTile());
        view.viewport(1000, 320);
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));
        assertEquals(1, failed.get());
    }

    private LongImageTileView view(ImageTileBudget budget, AtomicInteger ready) {
        return view(budget, ready, () -> fail("Valid fixture must decode"));
    }

    private LongImageTileView view(ImageTileBudget budget, AtomicInteger ready, Runnable failed) {
        LongImageSource source = LongImageSource.open(URL, files.getRoot());
        assertNotNull(source);
        LongImageTileView view = new LongImageTileView(ApplicationProvider.getApplicationContext(),
                source, 640, ready::incrementAndGet, failed, budget);
        view.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        views.add(view);
        return view;
    }

    private static void settle(LongImageTileView view) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (view.pendingTile() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(1L);
        }
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse("Tile decoding must settle within its bounded viewport", view.pendingTile());
    }

    private static List<Bitmap> bitmaps(LongImageTileView view) throws Exception {
        Map<?, ?> tiles = (Map<?, ?>) field(LongImageTileView.class, "tiles").get(view);
        List<Bitmap> result = new ArrayList<>();
        for (Object tile : tiles.values()) result.add((Bitmap) field(tile.getClass(), "bitmap").get(tile));
        return result;
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
