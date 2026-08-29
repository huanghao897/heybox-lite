package com.ronan.heyboxlite;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

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
        EmojiRenderer.set(view, parsed.text, darkMode, span -> {
            applyGameLinks(view, span, parsed, linkColor);
            if (decorator != null) decorator.apply(span);
        });
    }

    static String plainText(String source) {
        return RichGameLinkMarkup.plainText(source);
    }

    private static void applyGameLinks(TextView view, Spannable span,
                                       RichGameLinkMarkup.Parsed parsed, int color) {
        for (RichGameLinkMarkup.Link link : parsed.links) {
            Drawable icon = Compat.tintedDrawable(
                    view.getContext(), R.drawable.ic_game_link, color);
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
