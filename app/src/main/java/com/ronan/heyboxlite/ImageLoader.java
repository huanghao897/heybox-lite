package com.ronan.heyboxlite;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

final class ImageLoader {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    interface Logger {
        void log(String message);
    }

    interface Callback {
        void onLoaded(Bitmap bitmap);
    }

    interface IntoCallback {
        void onComplete(boolean success);
    }

    interface IntoBitmapCallback {
        void onComplete(boolean success, Bitmap bitmap);
    }

    interface PrefetchCallback {
        void onComplete(long bytes);
    }

    interface LongImageCallback {
        boolean cancelled();
        void onStart(int width, int height, int tileCount);
        void onTile(int index, Bitmap bitmap);
        void onComplete(boolean success);
    }

    private static final long MAX_HEAP_BYTES = Runtime.getRuntime().maxMemory();
    private static final int CACHE_KB = memoryCacheKb(MAX_HEAP_BYTES);
    private static final int MAX_DECODE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_BITMAP_PIXELS = 5_000_000;
    private static final int MAX_BITMAP_SIDE = 2400;
    private static final long MAX_OFFLINE_BYTES = 96L * 1024L * 1024L;
    private static final Object DECODE_LOCK = new Object();
    private static Context appContext;
    private static File offlineDir;
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(CACHE_KB) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            decodeThreadCount(MAX_HEAP_BYTES));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<ImageView, ValueAnimator> REVEAL_ANIMATORS = new WeakHashMap<>();
    private static final AtomicInteger PERFORMANCE_SAMPLES = new AtomicInteger();
    private static volatile Logger performanceLogger;

    static synchronized void init(Context context) {
        if (context == null) return;
        appContext = context.getApplicationContext();
        if (offlineDir != null) return;
        offlineDir = new File(appContext.getFilesDir(),
                "offline-cache/images");
        offlineDir.mkdirs();
    }

    static void setLogger(Logger logger) {
        performanceLogger = logger;
    }

    static void into(ImageView view, String sourceUrl, int targetPx) {
        into(view, sourceUrl, targetPx, null);
    }

    static void into(ImageView view, String sourceUrl, int targetPx, IntoCallback callback) {
        String url = thumbnailUrl(sourceUrl, targetPx);
        loadInto(view, url, url, targetPx, true,
                callback == null ? null : (success, bitmap) -> callback.onComplete(success));
    }

    static void intoPlain(ImageView view, String sourceUrl, int targetPx) {
        String url = thumbnailUrl(sourceUrl, targetPx);
        loadInto(view, url, url, targetPx, true, false, null);
    }

    static void intoStable(ImageView view, String sourceUrl, int targetPx) {
        String url = thumbnailUrl(sourceUrl, targetPx);
        loadInto(view, url, url, targetPx, false, false, null);
    }

    static void intoMeasured(ImageView view, String sourceUrl, int targetPx,
                             IntoBitmapCallback callback) {
        String url = thumbnailUrl(sourceUrl, targetPx);
        loadInto(view, url, url, originalUrl(sourceUrl), targetPx, true, true, callback);
    }

    static void intoMeasuredStable(ImageView view, String sourceUrl, int targetPx,
                                   IntoBitmapCallback callback) {
        String url = thumbnailUrl(sourceUrl, targetPx);
        loadInto(view, url, url, originalUrl(sourceUrl), targetPx, false, false, callback);
    }

    static void intoMeasuredRevealStable(ImageView view, String sourceUrl, int targetPx,
                                         IntoBitmapCallback callback) {
        String url = thumbnailUrl(sourceUrl, targetPx);
        loadInto(view, url, url, originalUrl(sourceUrl), targetPx, false, true, callback);
    }

    static void intoOriginalMeasured(ImageView view, String sourceUrl, int targetPx,
                                     IntoBitmapCallback callback) {
        String url = originalUrl(sourceUrl);
        loadInto(view, url, url, Math.min(targetPx, MAX_BITMAP_SIDE), true, false, callback);
    }

    static void intoOriginal(ImageView view, String sourceUrl, int targetPx) {
        String url = originalUrl(sourceUrl);
        loadInto(view, url, url, Math.min(targetPx, MAX_BITMAP_SIDE), false, null);
    }

    private static void loadInto(ImageView view, String cacheKey, String url,
                                 int targetPx, boolean clearBefore, IntoBitmapCallback callback) {
        loadInto(view, cacheKey, url, targetPx, clearBefore, true, callback);
    }

    private static void loadInto(ImageView view, String cacheKey, String url,
                                 int targetPx, boolean clearBefore, boolean animate,
                                 IntoBitmapCallback callback) {
        loadInto(view, cacheKey, url, null, targetPx, clearBefore, animate, callback);
    }

    private static void loadInto(ImageView view, String cacheKey, String url, String fallbackUrl,
                                 int targetPx, boolean clearBefore, boolean animate,
                                 IntoBitmapCallback callback) {
        init(view == null ? null : view.getContext());
        Object current = view.getTag();
        if (url.equals(current) && view.getDrawable() != null) {
            Bitmap cached = CACHE.get(url);
            if (callback != null) callback.onComplete(true, cached);
            return;
        }
        view.setTag(url);
        if (url.isEmpty()) {
            if (callback != null) callback.onComplete(false, null);
            return;
        }
        Bitmap cached = CACHE.get(url);
        if (cached == null) cached = CACHE.get(cacheKey);
        if (cached != null) {
            CACHE.put(url, cached);
            clearReveal(view);
            view.setImageBitmap(cached);
            view.setAlpha(1f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            if (callback != null) callback.onComplete(true, cached);
            return;
        }
        if (clearBefore) view.setImageDrawable(null);
        EXECUTOR.execute(() -> {
            Bitmap downloaded = download(url, safeTarget(targetPx));
            if (downloaded == null && fallbackUrl != null && !fallbackUrl.isEmpty()
                    && !fallbackUrl.equals(url)) {
                downloaded = download(fallbackUrl, safeTarget(targetPx));
            }
            final Bitmap bitmap = downloaded;
            MAIN.post(() -> {
                if (url.equals(view.getTag())) {
                    if (bitmap != null) {
                        if (!cacheKey.isEmpty()) CACHE.put(cacheKey, bitmap);
                        if (animate) showLoaded(view, bitmap);
                        else {
                            view.animate().cancel();
                            clearReveal(view);
                            view.setAlpha(1f);
                            view.setScaleX(1f);
                            view.setScaleY(1f);
                            view.setImageBitmap(bitmap);
                        }
                    }
                    if (callback != null) callback.onComplete(bitmap != null, bitmap);
                }
            });
        });
    }

    private static void showLoaded(ImageView view, Bitmap bitmap) {
        view.animate().cancel();
        clearReveal(view);
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setImageBitmap(bitmap);
        Object marker = view.getTag();
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            view.post(() -> {
                if (marker == view.getTag()) startReveal(view);
            });
            return;
        }
        startReveal(view);
    }

    private static void startReveal(ImageView view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) return;
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) {
            view.setClipBounds(null);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofInt(0, height);
        REVEAL_ANIMATORS.put(view, animator);
        animator.setDuration(260);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            int bottom = (Integer) animation.getAnimatedValue();
            view.setClipBounds(new Rect(0, 0, width, bottom));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (REVEAL_ANIMATORS.get(view) == animation) {
                    REVEAL_ANIMATORS.remove(view);
                    view.setClipBounds(null);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (REVEAL_ANIMATORS.get(view) == animation) {
                    REVEAL_ANIMATORS.remove(view);
                    view.setClipBounds(null);
                }
            }
        });
        view.setClipBounds(new Rect(0, 0, width, 0));
        animator.start();
    }

    private static void clearReveal(ImageView view) {
        ValueAnimator animator = REVEAL_ANIMATORS.remove(view);
        if (animator != null) animator.cancel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            view.setClipBounds(null);
        }
    }

    static void load(String sourceUrl, int targetPx, Callback callback) {
        String url = thumbnailUrl(sourceUrl, targetPx);
        if (url.isEmpty()) {
            MAIN.post(() -> callback.onLoaded(null));
            return;
        }
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            MAIN.post(() -> callback.onLoaded(cached));
            return;
        }
        EXECUTOR.execute(() -> {
            Bitmap bitmap = download(url, safeTarget(targetPx));
            MAIN.post(() -> callback.onLoaded(bitmap));
        });
    }

    static void prefetchOffline(Context context, List<String> sourceUrls, int targetPx,
                                PrefetchCallback callback) {
        init(context);
        List<String> urls = uniqueUrls(sourceUrls);
        EXECUTOR.execute(() -> {
            long bytes = 0L;
            for (String source : urls) {
                String url = thumbnailUrl(source, targetPx);
                if (url.isEmpty()) continue;
                download(url, safeTarget(targetPx));
                File file = exactOfflineFile(url);
                if (file != null && file.isFile()) bytes += file.length();
            }
            long result = bytes;
            if (callback != null) MAIN.post(() -> callback.onComplete(result));
        });
    }

    static long offlineBytes(List<String> sourceUrls) {
        File dir = offlineDir;
        if (dir == null || sourceUrls == null || sourceUrls.isEmpty()) return 0L;
        Set<String> prefixes = new HashSet<>();
        for (String source : sourceUrls) {
            String original = originalUrl(source);
            if (!original.isEmpty()) prefixes.add(hash(original) + "-");
        }
        File[] files = dir.listFiles();
        if (files == null) return 0L;
        long total = 0L;
        for (File file : files) {
            for (String prefix : prefixes) {
                if (file.getName().startsWith(prefix) && file.getName().endsWith(".img")) {
                    total += file.length();
                    break;
                }
            }
        }
        return total;
    }

    static void pruneOffline(Context context, long maxAgeMs) {
        init(context);
        File dir = offlineDir;
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - Math.max(0L, maxAgeMs);
        List<File> kept = new ArrayList<>();
        long total = 0L;
        for (File file : files) {
            if (!file.isFile()) continue;
            if (maxAgeMs > 0L && file.lastModified() < cutoff) {
                file.delete();
                continue;
            }
            kept.add(file);
            total += file.length();
        }
        Collections.sort(kept,
                (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        for (File file : kept) {
            if (total <= MAX_OFFLINE_BYTES) break;
            long length = file.length();
            if (file.delete()) total -= length;
        }
    }

    static void loadOriginal(String sourceUrl, int targetPx, Callback callback) {
        String url = originalUrl(sourceUrl);
        if (url.isEmpty()) {
            MAIN.post(() -> callback.onLoaded(null));
            return;
        }
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            MAIN.post(() -> callback.onLoaded(cached));
            return;
        }
        EXECUTOR.execute(() -> {
            Bitmap bitmap = download(url, safeTarget(Math.min(targetPx, MAX_BITMAP_SIDE)));
            MAIN.post(() -> callback.onLoaded(bitmap));
        });
    }

    static void loadLongImageTiles(String sourceUrl, int targetWidthPx,
                                   LongImageCallback callback) {
        String url = originalUrl(sourceUrl);
        if (url.isEmpty() || callback == null) {
            if (callback != null) MAIN.post(() -> callback.onComplete(false));
            return;
        }
        EXECUTOR.execute(() -> {
            long startedAt = SystemClock.elapsedRealtime();
            byte[] bytes = readOffline(exactOfflineFile(url));
            if (bytes == null && isNetworkConnected()) {
                bytes = downloadBytes(url, MAX_DECODE_BYTES);
                if (bytes != null) writeOffline(url, bytes);
            }
            if (bytes == null || callback.cancelled()) {
                MAIN.post(() -> callback.onComplete(false));
                return;
            }
            BitmapRegionDecoder decoder = null;
            boolean success = false;
            int decodedTiles = 0;
            try {
                decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.length, false);
                int width = decoder.getWidth();
                int height = decoder.getHeight();
                if (width <= 0 || height <= width * 3) {
                    return;
                }
                int target = Math.max(240, targetWidthPx);
                int sample = 1;
                while (width / sample > Math.round(target * 1.25f)) sample *= 2;
                int sourceTileHeight = Math.max(sample,
                        target * 2 * sample);
                int tileCount = Math.max(1,
                        (height + sourceTileHeight - 1) / sourceTileHeight);
                final int startWidth = width;
                final int startHeight = height;
                final int startTileCount = tileCount;
                MAIN.post(() -> callback.onStart(
                        startWidth, startHeight, startTileCount));
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                options.inSampleSize = sample;
                for (int index = 0; index < tileCount; index++) {
                    if (callback.cancelled()) break;
                    int top = index * sourceTileHeight;
                    int bottom = Math.min(height, top + sourceTileHeight);
                    Bitmap tile = decoder.decodeRegion(
                            new Rect(0, top, width, bottom), options);
                    if (tile == null) break;
                    int tileIndex = index;
                    MAIN.post(() -> {
                        if (callback.cancelled()) {
                            tile.recycle();
                        } else {
                            callback.onTile(tileIndex, tile);
                        }
                    });
                    decodedTiles++;
                }
                success = decodedTiles == tileCount && !callback.cancelled();
            } catch (OutOfMemoryError error) {
                CACHE.evictAll();
            } catch (Exception ignored) {
            } finally {
                if (decoder != null) decoder.recycle();
                boolean completed = success;
                int tiles = decodedTiles;
                logPerformance("perf image stage=long-tiles tiles=" + tiles
                        + " totalMs=" + Math.max(0L,
                        SystemClock.elapsedRealtime() - startedAt)
                        + " success=" + completed);
                MAIN.post(() -> callback.onComplete(completed));
            }
        });
    }

    /** 详情页动图：拉原始 GIF 字节，后台解码，成功后替换已显示的静态缩略图。 */
    static void intoGif(ImageView view, String sourceUrl) {
        intoGif(view, sourceUrl, null);
    }

    static void intoGif(ImageView view, String sourceUrl, IntoCallback callback) {
        String url = originalUrl(sourceUrl);
        if (view == null || url.isEmpty()) {
            if (callback != null) MAIN.post(() -> callback.onComplete(false));
            return;
        }
        final String tag = "gif:" + url;
        view.setTag(tag);
        EXECUTOR.execute(() -> {
            byte[] bytes = downloadBytes(url, GifSupport.MAX_GIF_BYTES);
            android.graphics.drawable.Drawable drawable = GifSupport.decode(bytes);
            if (drawable == null) {
                if (callback != null) MAIN.post(() -> callback.onComplete(false));
                return;
            }
            MAIN.post(() -> {
                if (tag.equals(view.getTag())) {
                    GifSupport.apply(view, drawable);
                    if (callback != null) callback.onComplete(true);
                }
            });
        });
    }

    interface SizeCallback {
        void onSize(long bytes);
    }

    /** 探测原图字节大小（HEAD 请求），用于“查看原图（xxxKB）”。失败回调 -1。 */
    static void probeOriginalSize(String sourceUrl, SizeCallback callback) {
        String url = originalUrl(sourceUrl);
        if (url.isEmpty()) {
            MAIN.post(() -> callback.onSize(-1L));
            return;
        }
        EXECUTOR.execute(() -> {
            long size = headContentLength(url);
            MAIN.post(() -> callback.onSize(size));
        });
    }

    private static long headContentLength(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setUseCaches(true);
            HeaderProvider.applyPublic(connection);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return -1L;
            String length = connection.getHeaderField("Content-Length");
            if (length == null) return -1L;
            return Long.parseLong(length.trim());
        } catch (Throwable ignored) {
            return -1L;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static byte[] downloadBytes(String url, int maxBytes) {
        HttpURLConnection connection = null;
        try {
            long networkStartedAt = SystemClock.elapsedRealtime();
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(15000);
            connection.setUseCaches(true);
            HeaderProvider.applyPublic(connection);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return null;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream output = new ByteArrayOutputStream(64 * 1024)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (output.size() + count > maxBytes) return null;
                    output.write(buffer, 0, count);
                }
                byte[] bytes = output.toByteArray();
                logPerformance("perf image stage=raw-download bytes=" + bytes.length
                        + " networkMs=" + Math.max(0L,
                        SystemClock.elapsedRealtime() - networkStartedAt));
                return bytes;
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static void cancel(ImageView view) {
        if (view != null) {
            clearReveal(view);
            GifSupport.setRunning(view.getDrawable(), false);
            view.setTag(null);
        }
    }

    static void cancelTree(View view) {
        if (view == null) return;
        if (view instanceof ImageView) cancel((ImageView) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) cancelTree(group.getChildAt(i));
        }
    }

    static void clear() {
        CACHE.evictAll();
        EmojiRenderer.clear();
    }

    static int cacheSizeKb() {
        return CACHE.size();
    }

    private static Bitmap download(String url, int targetPx) {
        Bitmap cached = CACHE.get(url);
        if (cached != null) return cached;
        byte[] exact = readOffline(exactOfflineFile(url));
        Bitmap stored = decode(exact, targetPx);
        if (stored != null) {
            CACHE.put(url, stored);
            return stored;
        }
        if (!isNetworkConnected()) return offlineFallback(url, targetPx);
        HttpURLConnection connection = null;
        try {
            long networkStartedAt = SystemClock.elapsedRealtime();
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(12000);
            connection.setUseCaches(true);
            HeaderProvider.applyPublic(connection);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return offlineFallback(url, targetPx);
            byte[] bytes;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream output = new ByteArrayOutputStream(48 * 1024)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (output.size() + count > MAX_DECODE_BYTES) {
                        return offlineFallback(url, targetPx);
                    }
                    output.write(buffer, 0, count);
                }
                bytes = output.toByteArray();
            }
            logPerformance("perf image stage=download bytes=" + bytes.length
                    + " networkMs="
                    + Math.max(0L, SystemClock.elapsedRealtime() - networkStartedAt));

            writeOffline(url, bytes);
            Bitmap bitmap = decode(bytes, targetPx);
            if (bitmap != null) CACHE.put(url, bitmap);
            return bitmap;
        } catch (OutOfMemoryError error) {
            CACHE.evictAll();
            return null;
        } catch (Exception ignored) {
            return offlineFallback(url, targetPx);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Bitmap offlineFallback(String url, int targetPx) {
        Bitmap fallback = decode(readOffline(fallbackOfflineFile(url)), targetPx);
        if (fallback != null) CACHE.put(url, fallback);
        return fallback;
    }

    private static boolean isNetworkConnected() {
        Context context = appContext;
        if (context == null) return true;
        try {
            ConnectivityManager manager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception ignored) {
            return true;
        }
    }

    private static Bitmap decode(byte[] bytes, int targetPx) {
        if (bytes == null || bytes.length == 0) return null;
        long startedAt = SystemClock.elapsedRealtime();
        synchronized (DECODE_LOCK) {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                BitmapFactory.Options options = decodeOptions(bounds, targetPx);
                try {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(
                            bytes, 0, bytes.length, options);
                    logDecode(bytes.length, targetPx, bounds, options, bitmap, startedAt);
                    return bitmap;
                } catch (OutOfMemoryError firstFailure) {
                    CACHE.evictAll();
                    options.inSampleSize = Math.min(128,
                            Math.max(2, options.inSampleSize * 2));
                    try {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(
                                bytes, 0, bytes.length, options);
                        logDecode(bytes.length, targetPx, bounds, options, bitmap, startedAt);
                        return bitmap;
                    } catch (OutOfMemoryError ignored) {
                        logDecode(bytes.length, targetPx, bounds, options, null, startedAt);
                        return null;
                    }
                }
            } catch (OutOfMemoryError ignored) {
                CACHE.evictAll();
                logPerformance("perf image stage=decode result=oom bytes=" + bytes.length
                        + " targetPx=" + targetPx);
                return null;
            }
        }
    }

    private static void logDecode(int bytes, int targetPx,
                                  BitmapFactory.Options bounds,
                                  BitmapFactory.Options options,
                                  Bitmap bitmap, long startedAt) {
        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - startedAt);
        if (elapsed < 40L && PERFORMANCE_SAMPLES.incrementAndGet() > 24) return;
        logPerformance("perf image stage=decode bytes=" + bytes
                + " targetPx=" + targetPx
                + " source=" + Math.max(0, bounds.outWidth) + "x"
                + Math.max(0, bounds.outHeight)
                + " sample=" + Math.max(1, options.inSampleSize)
                + " result=" + (bitmap == null ? "failed"
                : bitmap.getWidth() + "x" + bitmap.getHeight())
                + " decodeMs=" + elapsed);
    }

    private static void logPerformance(String message) {
        Logger logger = performanceLogger;
        if (logger != null) logger.log(message);
    }

    private static BitmapFactory.Options decodeOptions(BitmapFactory.Options bounds,
                                                        int targetPx) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = sampleSize(bounds, targetPx);
        options.inPurgeable = true;
        options.inInputShareable = true;
        return options;
    }

    private static int memoryCacheKb(long maxHeapBytes) {
        long maxHeapKb = Math.max(1L, maxHeapBytes / 1024L);
        if (maxHeapBytes <= 128L * 1024L * 1024L) {
            return (int) Math.max(1536L, Math.min(3L * 1024L, maxHeapKb / 16L));
        }
        return (int) Math.max(3L * 1024L, Math.min(8L * 1024L, maxHeapKb / 12L));
    }

    private static int decodeThreadCount(long maxHeapBytes) {
        return maxHeapBytes <= 128L * 1024L * 1024L ? 1 : 2;
    }

    private static List<String> uniqueUrls(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            String url = originalUrl(value);
            if (!url.isEmpty() && seen.add(url)) result.add(url);
        }
        return result;
    }

    private static File exactOfflineFile(String url) {
        File dir = offlineDir;
        String original = originalUrl(url);
        if (dir == null || original.isEmpty()) return null;
        return new File(dir, hash(original) + "-" + hash(url).substring(0, 12) + ".img");
    }

    private static File fallbackOfflineFile(String url) {
        File exact = exactOfflineFile(url);
        if (exact == null || exact.exists()) return exact;
        File dir = offlineDir;
        String original = originalUrl(url);
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null || original.isEmpty()) return null;
        String prefix = hash(original) + "-";
        File best = null;
        for (File file : files) {
            if (!file.getName().startsWith(prefix) || !file.getName().endsWith(".img")) continue;
            if (best == null || file.lastModified() > best.lastModified()) best = file;
        }
        return best;
    }

    private static byte[] readOffline(File file) {
        if (file == null || !file.isFile() || file.length() <= 0L
                || file.length() > MAX_DECODE_BYTES) return null;
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream((int) file.length())) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            file.setLastModified(System.currentTimeMillis());
            return output.toByteArray();
        } catch (OutOfMemoryError error) {
            CACHE.evictAll();
            return null;
        } catch (Exception ignored) {
            file.delete();
            return null;
        }
    }

    private static synchronized void writeOffline(String url, byte[] bytes) {
        File target = exactOfflineFile(url);
        if (target == null || bytes == null || bytes.length == 0) return;
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp, false)) {
            output.write(bytes);
            output.flush();
            if (target.exists()) target.delete();
            if (!temp.renameTo(target)) temp.delete();
        } catch (Exception ignored) {
            temp.delete();
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                out.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ignored) {
            return Integer.toHexString(value.hashCode()) + "000000000000";
        }
    }

    private static int sampleSize(BitmapFactory.Options bounds, int target) {
        int sample = 1;
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return sample;
        target = safeTarget(target);
        while (bounds.outWidth / sample > target * 2) {
            sample *= 2;
        }
        while (bounds.outHeight / sample > MAX_BITMAP_SIDE * 2) {
            sample *= 2;
        }
        while ((long) (bounds.outWidth / sample) * (bounds.outHeight / sample) > MAX_BITMAP_PIXELS) {
            sample *= 2;
        }
        return sample;
    }

    private static int safeTarget(int target) {
        if (target <= 0) return 720;
        return Math.max(96, Math.min(target, MAX_BITMAP_SIDE));
    }

    private static String thumbnailUrl(String source, int targetPx) {
        String original = originalUrl(source);
        if (original.isEmpty()) return "";
        return original + "?imageMogr2/auto-orient/ignore-error/1/thumbnail/"
                + targetPx + "x/format/jpg";
    }

    static String originalUrl(String source) {
        if (source == null || source.isEmpty()) return "";
        int query = source.indexOf('?');
        String value = (query >= 0 ? source.substring(0, query) : source).replace("\\/", "/");
        if (value.startsWith("//")) value = "https:" + value;
        if (value.regionMatches(true, 0, "http:", 0, 5)) value = "https:" + value.substring(5);
        return value.startsWith("https://") ? value : "";
    }
}
