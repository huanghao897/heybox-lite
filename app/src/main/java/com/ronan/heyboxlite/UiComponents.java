package com.ronan.heyboxlite;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
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
        GradientDrawable drawable = round(context, tokens.softAccent(), 18, scale);
        drawable.setStroke(Math.max(1, dp(context, 1, scale)),
                ThemeTokens.blend(tokens.hairline, tokens.accent, tokens.dark ? 0.45f : 0.24f));
        return drawable;
    }

    /** 石墨单色图标芯片：深灰底 + 极淡描边，图标用前景色（全屏唯一彩色留给主题色）。 */
    static GradientDrawable monoChip(Context context, ThemeTokens tokens, float scale) {
        int fill = ThemeTokens.blend(tokens.panel, tokens.text, tokens.dark ? 0.06f : 0.05f);
        GradientDrawable drawable = round(context, fill, 8, scale);
        drawable.setStroke(Math.max(1, dp(context, 1, scale)),
                ThemeTokens.blend(fill, tokens.text, 0.05f));
        return drawable;
    }

    /** 设置分组卡：无描边，靠底色与卡面的色阶分层。 */
    static GradientDrawable groupCard(Context context, ThemeTokens tokens, float scale) {
        return round(context, tokens.panel, 16, scale);
    }

    static GradientDrawable dock(Context context, ThemeTokens tokens, float scale) {
        GradientDrawable drawable = round(context, tokens.panelElevated, 21, scale);
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

    static GradientDrawable outlinedTextField(Context context, ThemeTokens tokens, float scale) {
        GradientDrawable drawable = round(context, Color.TRANSPARENT, 8, scale);
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
        if (view == null || Motions.off()
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) return;
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
