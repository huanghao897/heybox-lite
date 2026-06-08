package com.openzen.heyboxcommunity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ImageLoader {
    interface Callback {
        void onLoaded(Bitmap bitmap);
    }

    interface IntoCallback {
        void onComplete(boolean success);
    }

    private static final int CACHE_KB = 8 * 1024;
    private static final int MAX_DECODE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_BITMAP_PIXELS = 5_000_000;
    private static final int MAX_BITMAP_SIDE = 2400;
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(CACHE_KB) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void into(ImageView view, String sourceUrl, int targetPx) {
        into(view, sourceUrl, targetPx, null);
    }

    static void into(ImageView view, String sourceUrl, int targetPx, IntoCallback callback) {
        String url = thumbnailUrl(sourceUrl, targetPx);
        loadInto(view, url, url, targetPx, true, callback);
    }

    static void intoOriginal(ImageView view, String sourceUrl, int targetPx) {
        String url = originalUrl(sourceUrl);
        loadInto(view, url, url, Math.min(targetPx, MAX_BITMAP_SIDE), false, null);
    }

    private static void loadInto(ImageView view, String cacheKey, String url,
                                 int targetPx, boolean clearBefore, IntoCallback callback) {
        Object current = view.getTag();
        if (url.equals(current) && view.getDrawable() != null) {
            if (callback != null) callback.onComplete(true);
            return;
        }
        view.setTag(url);
        if (url.isEmpty()) {
            if (callback != null) callback.onComplete(false);
            return;
        }
        Bitmap cached = CACHE.get(url);
        if (cached == null) cached = CACHE.get(cacheKey);
        if (cached != null) {
            CACHE.put(url, cached);
            view.setImageBitmap(cached);
            view.setAlpha(1f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            if (callback != null) callback.onComplete(true);
            return;
        }
        if (clearBefore && view.getDrawable() == null) view.setImageDrawable(null);
        EXECUTOR.execute(() -> {
            Bitmap bitmap = download(url, safeTarget(targetPx));
            MAIN.post(() -> {
                if (url.equals(view.getTag())) {
                    if (bitmap != null) {
                        if (!cacheKey.isEmpty()) CACHE.put(cacheKey, bitmap);
                        showLoaded(view, bitmap);
                    }
                    if (callback != null) callback.onComplete(bitmap != null);
                }
            });
        });
    }

    private static void showLoaded(ImageView view, Bitmap bitmap) {
        view.animate().cancel();
        view.setAlpha(0f);
        view.setScaleX(0.985f);
        view.setScaleY(0.985f);
        view.setImageBitmap(bitmap);
        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180)
                .start();
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

    static void cancel(ImageView view) {
        if (view != null) view.setTag(null);
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
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(12000);
            connection.setUseCaches(true);
            HeaderProvider.applyPublic(connection);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return null;
            byte[] bytes;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream output = new ByteArrayOutputStream(48 * 1024)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (output.size() + count > MAX_DECODE_BYTES) return null;
                    output.write(buffer, 0, count);
                }
                bytes = output.toByteArray();
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inSampleSize = sampleSize(bounds, targetPx);
            options.inPurgeable = true;
            options.inInputShareable = true;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (bitmap != null) CACHE.put(url, bitmap);
            return bitmap;
        } catch (OutOfMemoryError error) {
            CACHE.evictAll();
            return null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
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
