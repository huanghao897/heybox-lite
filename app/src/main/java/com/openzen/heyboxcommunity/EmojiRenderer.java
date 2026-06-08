package com.openzen.heyboxcommunity;

import android.graphics.Bitmap;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EmojiRenderer {
    private static final Pattern TOKEN = Pattern.compile("\\[[^\\]\\s]{1,48}\\]");
    private static final Map<String, Bitmap> BITMAPS = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();
    private static final Map<String, List<Waiter>> WAITERS = new HashMap<>();

    private EmojiRenderer() {}

    static void clear() {
        BITMAPS.clear();
        LOADING.clear();
        WAITERS.clear();
    }

    static int cacheSizeKb() {
        int bytes = 0;
        for (Bitmap bitmap : BITMAPS.values()) {
            if (bitmap != null) bytes += bitmap.getByteCount();
        }
        return Math.max(0, bytes / 1024);
    }

    static void set(TextView view, String source) {
        set(view, source, false);
    }

    static void set(TextView view, String source, boolean darkMode) {
        String value = source == null ? "" : source;
        view.setTag(value);
        render(view, value, darkMode);
        if (!EmojiStore.isLoaded() && TOKEN.matcher(value).find()) {
            EmojiStore.whenReady(() -> {
                if (value.equals(view.getTag())) render(view, value, darkMode);
            });
        }
    }

    private static void render(TextView view, String source, boolean darkMode) {
        SpannableString styled = new SpannableString(source);
        Matcher matcher = TOKEN.matcher(source);
        while (matcher.find()) {
            String code = matcher.group();
            String cacheKey = (darkMode ? "d:" : "l:") + code;
            Bitmap bitmap = BITMAPS.get(cacheKey);
            if (bitmap != null) {
                int height = Math.max(18, Math.round(view.getTextSize() * 1.25f));
                int width = Math.max(18, Math.round(bitmap.getWidth() * height
                        / (float) Math.max(1, bitmap.getHeight())));
                bitmap.setDensity(view.getResources().getDisplayMetrics().densityDpi);
                android.graphics.drawable.BitmapDrawable drawable =
                        new android.graphics.drawable.BitmapDrawable(view.getResources(), bitmap);
                drawable.setBounds(0, 0, width, height);
                styled.setSpan(new ImageSpan(drawable, ImageSpan.ALIGN_BASELINE),
                        matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            String url = EmojiStore.url(code, darkMode);
            if (url.isEmpty()) continue;
            waitFor(cacheKey, view, source, darkMode);
            if (LOADING.add(cacheKey)) {
                ImageLoader.loadOriginal(url, 96, loaded -> {
                    if (loaded != null) BITMAPS.put(cacheKey, loaded);
                    LOADING.remove(cacheKey);
                    notifyWaiters(cacheKey, loaded != null);
                });
            }
        }
        view.setText(styled);
    }

    private static void waitFor(String cacheKey, TextView view, String source,
                                boolean darkMode) {
        List<Waiter> waiters = WAITERS.get(cacheKey);
        if (waiters == null) {
            waiters = new ArrayList<>();
            WAITERS.put(cacheKey, waiters);
        }
        waiters.add(new Waiter(view, source, darkMode));
    }

    private static void notifyWaiters(String cacheKey, boolean loaded) {
        List<Waiter> waiters = WAITERS.remove(cacheKey);
        if (waiters == null || !loaded) return;
        for (Waiter waiter : waiters) {
            TextView view = waiter.view.get();
            if (view == null) continue;
            Object tag = view.getTag();
            if (waiter.source.equals(tag)) render(view, waiter.source, waiter.darkMode);
        }
    }

    private static final class Waiter {
        final WeakReference<TextView> view;
        final String source;
        final boolean darkMode;

        Waiter(TextView view, String source, boolean darkMode) {
            this.view = new WeakReference<>(view);
            this.source = source;
            this.darkMode = darkMode;
        }
    }
}
