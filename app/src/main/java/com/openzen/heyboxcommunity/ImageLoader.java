package com.openzen.heyboxcommunity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
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

    private static final int CACHE_KB = 8 * 1024;
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
        String url = thumbnailUrl(sourceUrl, targetPx);
        view.setTag(url);
        view.setImageDrawable(null);
        if (url.isEmpty()) return;

        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }

        EXECUTOR.execute(() -> {
            Bitmap bitmap = download(url, targetPx);
            if (bitmap != null) MAIN.post(() -> {
                if (url.equals(view.getTag())) showLoaded(view, bitmap);
            });
        });
    }

    static void intoOriginal(ImageView view, String sourceUrl, int targetPx) {
        String url = originalUrl(sourceUrl);
        loadInto(view, url, targetPx);
    }

    private static void loadInto(ImageView view, String url, int targetPx) {
        view.setTag(url);
        view.setImageDrawable(null);
        if (url.isEmpty()) return;
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        EXECUTOR.execute(() -> {
            Bitmap bitmap = download(url, targetPx);
            if (bitmap != null) MAIN.post(() -> {
                if (url.equals(view.getTag())) showLoaded(view, bitmap);
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
        if (url.isEmpty()) return;
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            MAIN.post(() -> callback.onLoaded(cached));
            return;
        }
        EXECUTOR.execute(() -> {
            Bitmap bitmap = download(url, targetPx);
            if (bitmap != null) MAIN.post(() -> callback.onLoaded(bitmap));
        });
    }

    static void loadOriginal(String sourceUrl, int targetPx, Callback callback) {
        String url = originalUrl(sourceUrl);
        if (url.isEmpty()) return;
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            MAIN.post(() -> callback.onLoaded(cached));
            return;
        }
        EXECUTOR.execute(() -> {
            Bitmap bitmap = download(url, targetPx);
            if (bitmap != null) MAIN.post(() -> callback.onLoaded(bitmap));
        });
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
            byte[] bytes;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream output = new ByteArrayOutputStream(48 * 1024)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                bytes = output.toByteArray();
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inSampleSize = sampleSize(bounds, targetPx);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (bitmap != null) CACHE.put(url, bitmap);
            return bitmap;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static int sampleSize(BitmapFactory.Options bounds, int target) {
        int sample = 1;
        while (bounds.outWidth / sample > target * 2) {
            sample *= 2;
        }
        while ((long) (bounds.outWidth / sample) * (bounds.outHeight / sample) > 12_000_000L) {
            sample *= 2;
        }
        return sample;
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
        if (value.regionMatches(true, 0, "http:", 0, 5)) value = "https:" + value.substring(5);
        return value;
    }
}
