package com.ronan.heyboxlite;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.LruCache;

import androidx.test.core.app.ApplicationProvider;

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
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowNetworkInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ImageLoaderExactOriginalTest {
    private static final String SOURCE = "https://emoji-cache.invalid/emoji.png";
    private static final String VARIANT = SOURCE + "?imageMogr2/thumbnail/96x/format/jpg";

    @Rule public TemporaryFolder files = new TemporaryFolder();
    private File previousDirectory;
    private Context previousContext;
    private LruCache<String, Bitmap> memory;

    @Before public void useIsolatedOfflineCache() throws Exception {
        previousDirectory = (File) field(ImageDiskCache.class, "directory").get(null);
        previousContext = (Context) field(ImageLoader.class, "appContext").get(null);
        Context context = ApplicationProvider.getApplicationContext();
        ImageLoader.init(context);
        field(ImageDiskCache.class, "directory").set(null, files.newFolder("images"));
        memory = imageMemory();
        memory.evictAll();
        ConnectivityManager connectivity = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(connectivity).setActiveNetworkInfo(ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.DISCONNECTED, ConnectivityManager.TYPE_WIFI,
                0, true, false));
    }

    @After public void restoreCacheState() throws Exception {
        if (memory != null) memory.evictAll();
        field(ImageDiskCache.class, "directory").set(null, previousDirectory);
        field(ImageLoader.class, "appContext").set(null, previousContext);
    }

    @Test public void exactOriginalDoesNotReadSiblingJpegButOrdinaryFallbackStillDoes() throws Exception {
        ImageDiskCache.write(VARIANT, imageBytes(Bitmap.CompressFormat.JPEG));
        assertTrue("The written variant must be available after its output stream closes",
                ImageDiskCache.exactFile(VARIANT).isFile());
        assertFalse(ImageDiskCache.exactFile(SOURCE).exists());
        assertEquals(ImageDiskCache.exactFile(VARIANT), ImageDiskCache.fallbackFile(SOURCE));

        assertNull(downloadExact());
        assertNull(ImageLoader.cachedExactOriginal(SOURCE));

        Bitmap ordinary = downloadOrdinary();
        assertNotNull(ordinary);
        assertEquals(255, Color.alpha(ordinary.getPixel(0, 0)));
        assertSame(ordinary, memory.get(SOURCE));
        assertTrue("Strict lookup must not remove the ordinary offline variant",
                ImageDiskCache.exactFile(VARIANT).isFile());
    }

    @Test public void legacyPollutedOriginalKeyCannotLeakIntoExactCacheAndAMissCanRetry() throws Exception {
        ImageDiskCache.write(VARIANT, imageBytes(Bitmap.CompressFormat.JPEG));
        Bitmap polluted = downloadOrdinary();
        assertNotNull(polluted);
        assertSame(polluted, memory.get(SOURCE));

        assertNull(ImageLoader.cachedExactOriginal(SOURCE));
        assertNull(downloadExact());

        ImageDiskCache.write(SOURCE, imageBytes(Bitmap.CompressFormat.PNG));
        Bitmap original = downloadExact();
        assertNotNull(original);
        assertNotSame(polluted, original);
        assertTrue(original.hasAlpha());
        assertEquals(0, Color.alpha(original.getPixel(0, 0)));
        assertEquals("Opaque white parts of the original artwork must stay intact",
                Color.WHITE, original.getPixel(16, 16));
        assertSame(original, ImageLoader.cachedExactOriginal(SOURCE));
        assertSame(original, ImageLoader.cachedExactOriginal(SOURCE + "?ignored-query=1"));
        assertSame("Ordinary callers keep their existing cache entry", polluted, memory.get(SOURCE));
    }

    @Test public void corruptExactFileDoesNotEnableJpegVariantFallback() throws Exception {
        ImageDiskCache.write(VARIANT, imageBytes(Bitmap.CompressFormat.JPEG));
        ImageDiskCache.write(SOURCE, new byte[] {1, 2, 3, 4});

        assertNull(downloadExact());
        assertNull(ImageLoader.cachedExactOriginal(SOURCE));
    }

    @Test public void exactMemoryHitUsesMainDeliveryAndSupportsCancellation() throws Exception {
        ImageDiskCache.write(SOURCE, imageBytes(Bitmap.CompressFormat.PNG));
        Bitmap original = downloadExact();
        assertNotNull(original);
        AtomicInteger delivered = new AtomicInteger();
        ImageLoader.loadExactOriginalCancellable(SOURCE, 96, value -> {
            assertSame(original, value);
            delivered.incrementAndGet();
        });
        ImageLoadRequest cancelled = ImageLoader.loadExactOriginalCancellable(
                SOURCE, 96, value -> fail("Cancelled exact-image callback"));
        cancelled.cancel();

        assertEquals(0, delivered.get());
        ShadowLooper.shadowMainLooper().idle();
        assertEquals(1, delivered.get());
    }

    @Test public void invalidSourceReturnsNullWithoutUsingADiskVariant() {
        assertNull(ImageLoader.cachedExactOriginal(""));
        assertNull(ImageLoader.cachedExactOriginal("file:///emoji.png"));
        AtomicInteger delivered = new AtomicInteger();
        ImageLoader.loadExactOriginalCancellable("file:///emoji.png", 96, value -> {
            assertNull(value);
            delivered.incrementAndGet();
        });

        ShadowLooper.shadowMainLooper().idle();
        assertEquals(1, delivered.get());
    }

    private static Bitmap downloadExact() throws Exception {
        Method method = ImageLoader.class.getDeclaredMethod("downloadExactOriginal", String.class, int.class);
        method.setAccessible(true);
        return (Bitmap) method.invoke(null, SOURCE, 96);
    }

    private static Bitmap downloadOrdinary() throws Exception {
        Method method = ImageLoader.class.getDeclaredMethod("download", String.class, int.class);
        method.setAccessible(true);
        return (Bitmap) method.invoke(null, SOURCE, 96);
    }

    private static byte[] imageBytes(Bitmap.CompressFormat format) {
        Bitmap source = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(source);
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        canvas.drawRect(8f, 8f, 24f, 24f, paint);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(source.compress(format, 100, output));
        source.recycle();
        return output.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static LruCache<String, Bitmap> imageMemory() throws Exception {
        return (LruCache<String, Bitmap>) field(ImageLoader.class, "CACHE").get(null);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
