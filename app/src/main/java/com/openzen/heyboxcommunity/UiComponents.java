package com.openzen.heyboxcommunity;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

final class UiComponents {
    private UiComponents() {}

    static GradientDrawable card(Context context, ThemeTokens tokens, float scale) {
        GradientDrawable drawable = round(context, tokens.panel, 16, scale);
        drawable.setStroke(Math.max(1, dp(context, 1, scale)), tokens.glassStroke);
        return drawable;
    }

    static GradientDrawable softPill(Context context, ThemeTokens tokens, float scale) {
        GradientDrawable drawable = round(context, tokens.softAccent(), 14, scale);
        drawable.setStroke(Math.max(1, dp(context, 1, scale)),
                ThemeTokens.blend(tokens.hairline, tokens.accent, tokens.dark ? 0.45f : 0.24f));
        return drawable;
    }

    static GradientDrawable iconChip(Context context, int color, float scale) {
        return round(context, color, 8, scale);
    }

    static GradientDrawable dock(Context context, ThemeTokens tokens, float scale) {
        GradientDrawable drawable = round(context, tokens.panelElevated, 16, scale);
        drawable.setStroke(Math.max(1, dp(context, 1, scale)), tokens.glassStroke);
        return drawable;
    }

    static GradientDrawable primaryButton(Context context, ThemeTokens tokens, float scale) {
        return round(context, tokens.primary, 12, scale);
    }

    static GradientDrawable ghostButton(Context context, ThemeTokens tokens, float scale) {
        GradientDrawable drawable = round(context, tokens.panelElevated, 12, scale);
        drawable.setStroke(Math.max(1, dp(context, 1, scale)), tokens.hairline);
        return drawable;
    }

    static GradientDrawable round(Context context, int color, int radius, float scale) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius, scale));
        return drawable;
    }

    static void press(View view) {
        if (view == null || Motions.off()) return;
        view.animate().cancel();
        view.animate().scaleX(0.975f).scaleY(0.975f)
                .setDuration(MotionSpec.PRESS_IN_MS)
                .setInterpolator(MotionSpec.EASE_OUT)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f)
                        .setDuration(MotionSpec.PRESS_OUT_MS)
                        .setInterpolator(Motions.full() ? MotionSpec.SPRING : MotionSpec.EASE_OUT)
                        .start())
                .start();
    }

    static TextView label(Context context, String value, float sp, int color, float textScale) {
        TextView view = new TextView(context);
        view.setText(value == null ? "" : value);
        view.setTextSize(sp * textScale);
        view.setTextColor(color);
        Compat.setLetterSpacing(view, 0);
        return view;
    }

    static int dp(Context context, int value, float scale) {
        return Math.round(value * context.getResources().getDisplayMetrics().density * scale);
    }
}
