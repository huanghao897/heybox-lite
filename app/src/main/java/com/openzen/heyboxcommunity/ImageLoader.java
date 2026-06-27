package com.openzen.heyboxcommunity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ImageLoader {
    interface Callback {
        void onLoaded(Bitmap bitmap);
    }

    interface IntoCallback {
        void onComplete(boolean success);
    }

    interface IntoBitmapCallback {
        void onComplete(boolean success, Bitmap bitmap);
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
    private static final WeakHashMap<ImageView, ValueAnimator> REVEAL_ANIMATORS = new WeakHashMap<>();

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
        view.setClipBounds(null);
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
        if (view != null) {
            clearReveal(view);
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
