package com.openzen.heyboxcommunity;

import android.graphics.Bitmap;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.widget.TextView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EmojiRenderer {
    private static final Pattern TOKEN = Pattern.compile("\\[[^\\]\\s]+_[^\\]]+\\]");
    private static final Map<String, Bitmap> BITMAPS = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();

    private EmojiRenderer() {}

    static void clear() {
        BITMAPS.clear();
        LOADING.clear();
    }

    static void set(TextView view, String source) {
        String value = source == null ? "" : source;
        view.setTag(value);
        render(view, value);
        if (!EmojiStore.isLoaded() && TOKEN.matcher(value).find()) {
            EmojiStore.whenReady(() -> {
                if (value.equals(view.getTag())) render(view, value);
            });
        }
    }

    private static void render(TextView view, String source) {
        SpannableString styled = new SpannableString(source);
        Matcher matcher = TOKEN.matcher(source);
        while (matcher.find()) {
            String code = matcher.group();
            Bitmap bitmap = BITMAPS.get(code);
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
            String url = EmojiStore.url(code);
            if (url.isEmpty() || !LOADING.add(code)) continue;
            ImageLoader.loadOriginal(url, 96, loaded -> {
                BITMAPS.put(code, loaded);
                LOADING.remove(code);
                Object tag = view.getTag();
                if (source.equals(tag)) render(view, source);
            });
        }
        view.setText(styled);
    }
}
