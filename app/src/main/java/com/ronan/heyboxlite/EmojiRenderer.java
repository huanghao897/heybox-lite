package com.ronan.heyboxlite;

import android.graphics.Bitmap;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.LruCache;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EmojiRenderer {
    private static final Pattern TOKEN = Pattern.compile(
            "\\[[^\\]\\s]{1,48}\\]|(?<![A-Za-z0-9_./-])(?:cube|heygirl)_[^\\s\\[\\]<>\"']{1,48}");
    private static final int CACHE_KB = 2 * 1024;
    private static final LruCache<String, Bitmap> BITMAPS =
            new LruCache<String, Bitmap>(CACHE_KB) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value == null ? 0 : Math.max(1, value.getByteCount() / 1024);
                }
            };
    private static final LruCache<String, SpannableString> SPANS = new LruCache<>(96);
    private static final Set<String> LOADING = new HashSet<>();
    private static final Map<String, List<Waiter>> WAITERS = new HashMap<>();
    private static final Map<TextView, Decorator> DECORATORS = new WeakHashMap<>();
    private static final Map<TextView, Binding> BINDINGS = new WeakHashMap<>();
    private static final Map<String, ImageLoadRequest> REQUESTS = new HashMap<>();
    private static final Map<String, Object> LOAD_OWNERS = new HashMap<>();
    private static int generation;

    /** 在每次 setText 前对最终 Spannable 追加额外样式（颜色等），emoji 异步刷新时也会重新应用。 */
    interface Decorator {
        void apply(Spannable span);
        default List<String> imageUrls() { return Collections.emptyList(); }
    }

    private EmojiRenderer() {}

    // Like set/reset and image delivery, cache clearing runs on the main thread.
    static void clear() {
        generation++;
        for (ImageLoadRequest request : REQUESTS.values()) request.cancel();
        REQUESTS.clear();
        LOAD_OWNERS.clear();
        BITMAPS.evictAll();
        SPANS.evictAll();
        LOADING.clear();
        WAITERS.clear();
    }

    static int cacheSizeKb() {
        return BITMAPS.size();
    }

    static boolean hasTokens(String source) {
        return source != null && TOKEN.matcher(source).find();
    }

    static void set(TextView view, String source) {
        set(view, source, false, null);
    }

    static void set(TextView view, String source, boolean darkMode) {
        set(view, source, darkMode, null);
    }

    static void set(TextView view, String source, boolean darkMode, Decorator decorator) {
        release(view);
        if (decorator != null) {
            DECORATORS.put(view, decorator);
        } else {
            DECORATORS.remove(view);
        }
        String value = source == null ? "" : source;
        Binding binding = new Binding(value, darkMode);
        BINDINGS.put(view, binding);
        view.setTag(value);
        render(view, value, darkMode);
        if (!EmojiStore.isLoaded() && hasTokens(value)) {
            WeakReference<TextView> target = new WeakReference<>(view);
            binding.ready = () -> {
                TextView current = target.get();
                if (current != null && binding.owns(current)) render(current, value, darkMode);
            };
            EmojiStore.whenReady(binding.ready);
        }
    }

    static void reset(TextView view) {
        release(view);
        DECORATORS.remove(view);
        view.setTag(null);
        view.setText("");
    }

    private static void release(TextView view) {
        Binding previous = BINDINGS.remove(view);
        if (previous != null && previous.ready != null) EmojiStore.cancelWhenReady(previous.ready);
        java.util.Iterator<Map.Entry<String, List<Waiter>>> entries = WAITERS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, List<Waiter>> entry = entries.next();
            List<Waiter> waiters = entry.getValue();
            java.util.Iterator<Waiter> pending = waiters.iterator();
            while (pending.hasNext()) {
                TextView target = pending.next().view.get();
                if (target == null || target == view) pending.remove();
            }
            if (waiters.isEmpty()) {
                entries.remove();
                String url = entry.getKey();
                LOAD_OWNERS.remove(url);
                LOADING.remove(url);
                ImageLoadRequest request = REQUESTS.remove(url);
                if (request != null) request.cancel();
            }
        }
    }

    private static void render(TextView view, String source, boolean darkMode) {
        String spanKey = (darkMode ? "d:" : "l:")
                + EmojiStore.revision() + ":" + view.getResources().getDisplayMetrics().densityDpi
                + ":" + Float.floatToIntBits(view.getTextSize()) + ":" + source;
        Decorator decorator = DECORATORS.get(view);
        SpannableString cached = decorator == null ? SPANS.get(spanKey) : null;
        if (cached != null) {
            view.setText(new SpannableString(cached));
            return;
        }
        SpannableString styled = new SpannableString(source);
        Matcher matcher = TOKEN.matcher(source);
        boolean hasToken = false;
        boolean unresolved = false;
        while (matcher.find()) {
            hasToken = true;
            String code = EmojiStore.longestResolvableCode(matcher.group(), darkMode);
            String url = EmojiStore.url(code, darkMode);
            if (code.isEmpty() || url.isEmpty()) {
                unresolved = true;
                continue;
            }
            Bitmap bitmap = cachedBitmap(url);
            if (bitmap != null) {
                int height = Math.max(16, Math.round(view.getTextSize() * 1.18f));
                int width = Math.max(16, Math.round(bitmap.getWidth() * height
                        / (float) Math.max(1, bitmap.getHeight())));
                bitmap.setDensity(view.getResources().getDisplayMetrics().densityDpi);
                android.graphics.drawable.BitmapDrawable drawable =
                        new android.graphics.drawable.BitmapDrawable(view.getResources(), bitmap);
                drawable.setBounds(0, 0, width, height);
                styled.setSpan(new CenteredImageSpan(drawable),
                        matcher.start(), matcher.start() + code.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            unresolved = true;
            requestBitmap(url, view, source, darkMode);
        }
        if (decorator != null) {
            for (String url : decorator.imageUrls()) {
                if (!url.isEmpty() && cachedBitmap(url) == null) {
                    requestBitmap(url, view, source, darkMode);
                }
            }
            try {
                decorator.apply(styled);
            } catch (RuntimeException ignored) {
            }
        }
        if (decorator == null && hasToken && !unresolved) SPANS.put(spanKey, styled);
        view.setText(styled);
    }

    static Bitmap cachedBitmap(String url) {
        if (url.isEmpty()) return null;
        Bitmap bitmap = BITMAPS.get(url);
        if (bitmap == null) bitmap = ImageLoader.cachedExactOriginal(url);
        return bitmap != null && !bitmap.isRecycled() ? bitmap : null;
    }

    private static void requestBitmap(String url, TextView view, String source, boolean darkMode) {
        waitFor(url, view, source, darkMode);
        if (!LOADING.add(url)) return;
        int requestGeneration = generation;
        Object owner = new Object();
        LOAD_OWNERS.put(url, owner);
        ImageLoadRequest request = ImageLoader.loadExactOriginalCancellable(url, 96, loaded -> {
            if (requestGeneration != generation || LOAD_OWNERS.get(url) != owner) return;
            LOAD_OWNERS.remove(url);
            if (loaded != null) BITMAPS.put(url, loaded);
            REQUESTS.remove(url);
            LOADING.remove(url);
            notifyWaiters(url, loaded != null);
        });
        REQUESTS.put(url, request);
    }

    private static void waitFor(String cacheKey, TextView view, String source,
                                boolean darkMode) {
        List<Waiter> waiters = WAITERS.get(cacheKey);
        if (waiters == null) {
            waiters = new ArrayList<>();
            WAITERS.put(cacheKey, waiters);
        }
        for (int i = waiters.size() - 1; i >= 0; i--) {
            Waiter waiter = waiters.get(i);
            TextView target = waiter.view.get();
            if (target == null || !waiter.owns(target)) waiters.remove(i);
            else if (target == view) return;
        }
        waiters.add(new Waiter(view, source, darkMode));
    }

    private static void notifyWaiters(String cacheKey, boolean loaded) {
        List<Waiter> waiters = WAITERS.remove(cacheKey);
        if (waiters == null || !loaded) return;
        for (Waiter waiter : waiters) {
            TextView view = waiter.view.get();
            if (view == null) continue;
            if (waiter.owns(view)) render(view, waiter.source, waiter.darkMode);
        }
    }

    private static final class Binding {
        final String source;
        final boolean darkMode;
        Runnable ready;

        Binding(String source, boolean darkMode) {
            this.source = source;
            this.darkMode = darkMode;
        }

        boolean owns(TextView view) {
            return BINDINGS.get(view) == this && source.equals(view.getTag());
        }
    }

    private static final class Waiter {
        final WeakReference<TextView> view;
        final String source;
        final boolean darkMode;
        final Binding binding;

        Waiter(TextView view, String source, boolean darkMode) {
            this.view = new WeakReference<>(view);
            this.source = source;
            this.darkMode = darkMode;
            this.binding = BINDINGS.get(view);
        }

        boolean owns(TextView target) {
            return binding != null && binding.owns(target);
        }
    }
}
