package com.ronan.heyboxlite;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

final class RichInlineRenderer {
    private RichInlineRenderer() {}

    static void set(TextView view, String source, boolean darkMode, int linkColor) {
        set(view, source, darkMode, linkColor, null);
    }

    static void set(TextView view, String source, boolean darkMode, int linkColor,
                    EmojiRenderer.Decorator decorator) {
        RichGameLinkMarkup.Parsed parsed = RichGameLinkMarkup.parse(source);
        if (parsed.links.isEmpty()) {
            EmojiRenderer.set(view, parsed.text, darkMode, decorator);
            return;
        }
        WeakReference<TextView> target = new WeakReference<>(view);
        EmojiRenderer.set(view, parsed.text, darkMode, new EmojiRenderer.Decorator() {
            @Override public void apply(Spannable span) {
                TextView current = target.get();
                if (current == null) return;
                applyLinks(current, span, parsed, darkMode, linkColor);
                if (decorator != null) decorator.apply(span);
            }

            @Override public List<String> imageUrls() {
                List<String> urls = new ArrayList<>();
                for (RichGameLinkMarkup.Link link : parsed.links) {
                    if (link.textLink) urls.add(link.iconUrl(darkMode));
                }
                return urls;
            }
        });
    }

    static String plainText(String source) {
        return RichGameLinkMarkup.plainText(source);
    }

    private static void applyLinks(TextView view, Spannable span,
                                   RichGameLinkMarkup.Parsed parsed, boolean darkMode, int color) {
        for (RichGameLinkMarkup.Link link : parsed.links) {
            Bitmap bitmap = link.textLink ? EmojiRenderer.cachedBitmap(link.iconUrl(darkMode)) : null;
            Drawable icon = bitmap == null ? Compat.tintedDrawable(view.getContext(),
                    link.textLink ? R.drawable.il_reading : R.drawable.ic_game_link, color)
                    : new BitmapDrawable(view.getResources(), bitmap);
            if (icon != null) {
                int height = Math.max(dp(view, 13), Math.round(view.getTextSize() * 0.92f));
                int iconWidth = icon.getIntrinsicHeight() <= 0 ? height
                        : Math.max(1, Math.round(height * icon.getIntrinsicWidth()
                        / (float) icon.getIntrinsicHeight()));
                int gap = dp(view, 3);
                InsetDrawable padded = new InsetDrawable(icon, 0, 0, gap, 0);
                padded.setBounds(0, 0, iconWidth + gap, height);
                span.setSpan(new CenteredImageSpan(padded), link.start, link.start + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            span.setSpan(new ForegroundColorSpan(color), link.start, link.end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static int dp(TextView view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
