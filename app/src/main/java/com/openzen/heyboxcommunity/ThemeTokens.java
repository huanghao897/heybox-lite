package com.openzen.heyboxcommunity;

import android.graphics.Color;

final class ThemeTokens {
    final boolean dark;
    final int primary;
    final int secondary;
    final int background;
    final int panel;
    final int panelElevated;
    final int text;
    final int muted;
    final int subtle;
    final int hairline;
    final int accent;
    final int glassStroke;
    final int onPrimary;

    private ThemeTokens(boolean dark, int primary, int secondary) {
        this.dark = dark;
        this.primary = primary;
        this.secondary = secondary;
        if (dark) {
            background = Color.rgb(11, 12, 14);
            panel = Color.rgb(23, 25, 29);
            panelElevated = Color.rgb(29, 33, 39);
            text = Color.rgb(243, 245, 247);
            muted = Color.rgb(155, 164, 174);
            subtle = Color.rgb(104, 108, 112);
            hairline = Color.rgb(43, 48, 56);
        } else {
            background = Color.rgb(245, 246, 247);
            panel = Color.rgb(255, 255, 255);
            panelElevated = Color.rgb(250, 251, 252);
            text = Color.rgb(24, 25, 27);
            muted = Color.rgb(96, 101, 106);
            subtle = Color.rgb(154, 158, 162);
            hairline = Color.rgb(224, 226, 228);
        }
        accent = secondary;
        glassStroke = blend(hairline, text, dark ? 0.12f : 0.06f);
        onPrimary = contrast(primary);
    }

    static ThemeTokens of(boolean dark, int primary, int secondary) {
        int normalizedPrimary = dark ? Color.WHITE : Color.rgb(20, 21, 23);
        int normalizedSecondary = dark ? Color.rgb(196, 198, 201) : Color.rgb(87, 91, 96);
        if (primary != 0) normalizedPrimary = primary;
        if (secondary != 0) normalizedSecondary = secondary;
        return new ThemeTokens(dark, normalizedPrimary, normalizedSecondary);
    }

    int softAccent() {
        return blend(panel, accent, dark ? 0.18f : 0.12f);
    }

    int faintAccent() {
        return blend(panel, accent, dark ? 0.08f : 0.05f);
    }

    int pressedSurface() {
        return blend(panel, text, dark ? 0.09f : 0.04f);
    }

    static int contrast(int color) {
        int luminance = (Color.red(color) * 299 + Color.green(color) * 587
                + Color.blue(color) * 114) / 1000;
        return luminance >= 150 ? Color.BLACK : Color.WHITE;
    }

    static int blend(int base, int overlay, float amount) {
        float value = Math.max(0f, Math.min(1f, amount));
        float keep = 1f - value;
        return Color.rgb(
                Math.round(Color.red(base) * keep + Color.red(overlay) * value),
                Math.round(Color.green(base) * keep + Color.green(overlay) * value),
                Math.round(Color.blue(base) * keep + Color.blue(overlay) * value));
    }
}
