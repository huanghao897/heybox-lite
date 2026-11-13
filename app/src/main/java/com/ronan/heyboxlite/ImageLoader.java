package com.ronan.heyboxlite;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

final class ImageLoader {
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

    private static final long MAX_HEAP_BYTES = Runtime.getRuntime().maxMemory();
    private static final int CACHE_KB = ImageMemoryBudget.memoryCacheKb(MAX_HEAP_BYTES);
    static final int MAX_DECODE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_BITMAP_PIXELS = ImageMemoryBudget.decodePixels(MAX_HEAP_BYTES);
    private static final int MAX_BITMAP_SIDE = 2400;
    private static final Object SMALL_DECODE_LOCK = new Object();
    private static final Object LARGE_DECODE_LOCK = new Object();
    private static Context appContext;
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(CACHE_KB) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
    private static final LruCache<String, byte[]> GIF_BYTES =
            new LruCache<String, byte[]>(ImageMemoryBudget.gifCacheKb(MAX_HEAP_BYTES)) {
                @Override protected int sizeOf(String key, byte[] value) {
                    return Math.max(1, value.length / 1024);
                }
            };
    private static final ExecutorService SMALL_EXECUTOR = new ImageTaskExecutor(
            ImageMemoryBudget.decodeThreadCount(MAX_HEAP_BYTES), 32);
    private static final ExecutorService LARGE_EXECUTOR = new ImageTaskExecutor(1, 12);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicInteger PERFORMANCE_SAMPLES = new AtomicInteger();
    private static volatile Logger performanceLogger;

    static synchronized void init(Context context) {
        if (context == null) return;
        appContext = context.getApplicationContext();
        ImageDiskCache.init(appContext);
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
        if (view == null) return;
        init(view.getContext());
        ImageViewBinding current = ImageViewBinding.current(view);
        if (current != null && url.equals(current.key) && view.getDrawable() != null) {
            Bitmap cached = CACHE.get(url);
            if (callback != null) callback.onComplete(true, cached);
            return;
        }
        ImageViewBinding binding = ImageViewBinding.create(view, url, animate);
        if (url.isEmpty()) {
            if (callback != null) callback.onComplete(false, null);
            return;
        }
        Bitmap cached = CACHE.get(url);
        if (cached == null) cached = CACHE.get(cacheKey);
        if (cached != null) {
            CACHE.put(url, cached);
            ImageRevealAnimation.clear(view);
            view.setImageBitmap(cached);
            view.setAlpha(1f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            if (callback != null) callback.onComplete(true, cached);
            return;
        }
        if (clearBefore) view.setImageDrawable(null);
        binding.startBitmap(url, fallbackUrl, safeTarget(targetPx), callback);
    }

    static ImageLoadRequest requestInto(String url, String fallbackUrl, int targetPx, Callback callback) {
        return ImageLoadRequest.submit(SMALL_EXECUTOR, MAIN, () -> {
            Bitmap downloaded = download(url, safeTarget(targetPx));
            if (!ImageWorkToken.stopped() && downloaded == null
                    && fallbackUrl != null && !fallbackUrl.isEmpty()
                    && !fallbackUrl.equals(url)) {
                downloaded = download(fallbackUrl, safeTarget(targetPx));
            }
            return downloaded;
        }, callback);
    }

    static void load(String sourceUrl, int targetPx, Callback callback) {
        loadCancellable(sourceUrl, targetPx, callback);
    }

    static Bitmap cachedThumbnail(String sourceUrl, int targetPx) {
        return CACHE.get(thumbnailUrl(sourceUrl, targetPx));
    }

    static ImageLoadRequest loadCancellable(String sourceUrl, int targetPx, Callback callback) {
        return requestBitmap(thumbnailUrl(sourceUrl, targetPx), targetPx, SMALL_EXECUTOR, callback);
    }

    private static ImageLoadRequest requestBitmap(String url, int targetPx,
                                                  ExecutorService executor, Callback callback) {
        Bitmap cached = CACHE.get(url);
        if (url.isEmpty() || cached != null) return ImageLoadRequest.deliver(MAIN, cached, callback);
        return ImageLoadRequest.submit(executor, MAIN, () -> download(url, safeTarget(targetPx)), callback);
    }

    static void prefetchOffline(Context context, List<String> sourceUrls, int targetPx,
                                PrefetchCallback callback) {
        init(context);
        List<String> urls = uniqueUrls(sourceUrls);
        ImageLoadRequest.work(LARGE_EXECUTOR, MAIN, () -> {
            long bytes = 0L;
            for (String source : urls) {
                if (ImageWorkToken.stopped()) break;
                String url = thumbnailUrl(source, targetPx);
                if (url.isEmpty()) continue;
                download(url, safeTarget(targetPx));
                File file = ImageDiskCache.exactFile(url);
                if (file != null && file.isFile()) bytes += file.length();
            }
            return bytes;
        }, bytes -> { if (callback != null) callback.onComplete(bytes == null ? 0L : bytes); },
                ignored -> {});
    }

    static long offlineBytes(List<String> sourceUrls) {
        return ImageDiskCache.bytes(sourceUrls);
    }

    static void pruneOffline(Context context, long maxAgeMs) {
        init(context);
        ImageDiskCache.prune(maxAgeMs);
    }

    static void loadOriginal(String sourceUrl, int targetPx, Callback callback) {
        loadOriginalCancellable(sourceUrl, targetPx, callback);
    }

    static ImageLoadRequest loadOriginalCancellable(String sourceUrl, int targetPx, Callback callback) {
        return requestBitmap(originalUrl(sourceUrl), Math.min(targetPx, MAX_BITMAP_SIDE),
                LARGE_EXECUTOR, callback);
    }

    static Bitmap cachedExactOriginal(String sourceUrl) {
        String url = originalUrl(sourceUrl);
        return url.isEmpty() ? null : CACHE.get(exactOriginalCacheKey(url));
    }

    /** Alpha-sensitive media must not reuse a JPEG fallback cached under the ordinary URL. */
    static ImageLoadRequest loadExactOriginalCancellable(String sourceUrl, int targetPx,
                                                        Callback callback) {
        String url = originalUrl(sourceUrl);
        Bitmap cached = cachedExactOriginal(url);
        if (url.isEmpty() || cached != null) return ImageLoadRequest.deliver(MAIN, cached, callback);
        return ImageLoadRequest.submit(SMALL_EXECUTOR, MAIN,
                () -> downloadExactOriginal(url, safeTarget(targetPx)), callback);
    }

    private static String exactOriginalCacheKey(String url) {
        return "exact-original:" + url;
    }

    private static Bitmap downloadExactOriginal(String url, int targetPx) {
        return download(url, targetPx, exactOriginalCacheKey(url), false);
    }

    static ImageLoadRequest openLongImage(String sourceUrl,
                                           ImageLoadRequest.Result<LongImageSource> callback) {
        String url = originalUrl(sourceUrl);
        File directory = appContext == null ? null : appContext.getCacheDir();
        return ImageLoadRequest.work(LARGE_EXECUTOR, MAIN,
                () -> url.isEmpty() ? null : LongImageSource.open(url, directory),
                callback, source -> { if (source != null) source.close(); });
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
        ImageViewBinding binding = ImageViewBinding.create(view, "gif:" + url, false);
        binding.startGif(url, callback);
    }

    static ImageLoadRequest requestGif(String url, ImageLoadRequest.Result<Drawable> callback) {
        return ImageLoadRequest.work(LARGE_EXECUTOR, MAIN, () -> {
            if (ImageWorkToken.stopped()) return null;
            byte[] bytes = GIF_BYTES.get(url);
            if (bytes == null && !ImageWorkToken.stopped()) {
                bytes = ImageDiskCache.read(ImageDiskCache.exactFile(url), GifSupport.MAX_GIF_BYTES);
            }
            if (bytes == null && !ImageWorkToken.stopped() && isNetworkConnected()) {
                bytes = ImageSourceIO.download(url, GifSupport.MAX_GIF_BYTES);
                if (bytes != null && !ImageWorkToken.stopped()) ImageDiskCache.write(url, bytes);
            }
            if (ImageWorkToken.stopped()) return null;
            if (bytes != null) GIF_BYTES.put(url, bytes);
            return GifSupport.decode(bytes);
        }, callback, drawable -> {
            if (drawable != null) GifSupport.setRunning(drawable, false);
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
        ImageLoadRequest.work(LARGE_EXECUTOR, MAIN, () -> headContentLength(url),
                bytes -> callback.onSize(bytes == null ? -1L : bytes), ignored -> {});
    }

    private static long headContentLength(String url) {
        if (ImageWorkToken.stopped()) return -1L;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            ImageWorkToken.abortWith(connection::disconnect);
            if (ImageWorkToken.stopped()) return -1L;
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setUseCaches(true);
            HeaderProvider.applyPublic(connection);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                logFailure("head", url, status, null);
                return -1L;
            }
            String length = connection.getHeaderField("Content-Length");
            if (length == null) return -1L;
            return Long.parseLong(length.trim());
        } catch (Exception error) {
            logFailure("head", url, 0, error);
            return -1L;
        } finally {
            ImageWorkToken.abortWith(null);
            if (connection != null) connection.disconnect();
        }
    }


    static void cancel(ImageView view) {
        if (view != null) {
            ImageViewBinding.cancel(view);
            ImageRevealAnimation.clear(view);
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
        GIF_BYTES.evictAll();
        EmojiRenderer.clear();
    }

    static int cacheSizeKb() {
        return CACHE.size();
    }

    private static Bitmap download(String url, int targetPx) {
        return download(url, targetPx, url, true);
    }

    private static Bitmap download(String url, int targetPx, String cacheKey,
                                   boolean allowVariantFallback) {
        if (ImageWorkToken.stopped()) return null;
        Bitmap cached = CACHE.get(cacheKey);
        if (cached != null) return cached;
        byte[] exact = ImageDiskCache.read(
                ImageDiskCache.exactFile(url), MAX_DECODE_BYTES);
        if (ImageWorkToken.stopped()) return null;
        Bitmap stored = decode(exact, targetPx);
        if (stored != null) {
            CACHE.put(cacheKey, stored);
            return stored;
        }
        if (!isNetworkConnected()) return offlineFallback(url, targetPx, cacheKey, allowVariantFallback);
        byte[] bytes = ImageSourceIO.download(url, MAX_DECODE_BYTES);
        if (ImageWorkToken.stopped()) return null;
        if (bytes == null) return offlineFallback(url, targetPx, cacheKey, allowVariantFallback);
        ImageDiskCache.write(url, bytes);
        if (ImageWorkToken.stopped()) return null;
        Bitmap bitmap = decode(bytes, targetPx);
        if (bitmap != null) CACHE.put(cacheKey, bitmap);
        return bitmap;
    }

    private static Bitmap offlineFallback(String url, int targetPx, String cacheKey,
                                          boolean allowVariantFallback) {
        if (!allowVariantFallback || ImageWorkToken.stopped()) return null;
        Bitmap fallback = decode(ImageDiskCache.read(
                ImageDiskCache.fallbackFile(url), MAX_DECODE_BYTES), targetPx);
        if (fallback != null) CACHE.put(cacheKey, fallback);
        return fallback;
    }

    static boolean isNetworkConnected() {
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
        if (ImageWorkToken.stopped() || bytes == null || bytes.length == 0) return null;
        long startedAt = SystemClock.elapsedRealtime();
        synchronized (decodeLock(bytes, targetPx)) {
            if (ImageWorkToken.stopped()) return null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                BitmapFactory.Options options = decodeOptions(bounds, targetPx);
                if (ImageWorkToken.stopped()) return null;
                try {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(
                            bytes, 0, bytes.length, options);
                    logDecode(bytes.length, targetPx, bounds, options, bitmap, startedAt);
                    return bitmap;
                } catch (OutOfMemoryError firstFailure) {
                    CACHE.evictAll();
                    if (ImageWorkToken.stopped()) return null;
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

    private static void logFailure(String stage, String url, int status, Throwable error) {
        String host = "unknown";
        try {
            String parsed = new URL(url).getHost();
            if (parsed != null && !parsed.isEmpty()) host = parsed;
        } catch (Exception ignored) {
        }
        logPerformance("image failure stage=" + stage + " host=" + host
                + " status=" + status + " error="
                + (error == null ? "none" : error.getClass().getSimpleName()));
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

    private static Object decodeLock(byte[] bytes, int targetPx) {
        return targetPx > 1200 || (bytes != null && bytes.length > 2 * 1024 * 1024)
                ? LARGE_DECODE_LOCK : SMALL_DECODE_LOCK;
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
        return ImageMemoryBudget.decodeTargetPx(MAX_HEAP_BYTES, target);
    }

    private static String thumbnailUrl(String source, int targetPx) {
        String original = originalUrl(source);
        if (original.isEmpty()) return "";
        int requested = safeTarget(targetPx);
        return original + "?imageMogr2/auto-orient/ignore-error/1/thumbnail/"
                + requested + "x/format/jpg";
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
