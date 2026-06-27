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
    final int onPrimary;

    private ThemeTokens(boolean dark, int primary, int secondary) {
        this.dark = dark;
        this.primary = primary;
        this.secondary = secondary;
        if (dark) {
            background = Color.rgb(12, 13, 14);
            panel = Color.rgb(25, 26, 28);
            panelElevated = Color.rgb(32, 33, 35);
            text = Color.rgb(244, 245, 246);
            muted = Color.rgb(158, 162, 166);
            subtle = Color.rgb(104, 108, 112);
            hairline = Color.rgb(48, 50, 53);
        } else {
            background = Color.rgb(245, 246, 247);
            panel = Color.rgb(255, 255, 255);
            panelElevated = Color.rgb(250, 251, 252);
            text = Color.rgb(24, 25, 27);
            muted = Color.rgb(96, 101, 106);
            subtle = Color.rgb(154, 158, 162);
            hairline = Color.rgb(224, 226, 228);
        }
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
        return blend(panel, secondary, dark ? 0.22f : 0.10f);
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
        float keep = 1f - amount;
        return Color.rgb(
                Math.round(Color.red(base) * keep + Color.red(overlay) * amount),
                Math.round(Color.green(base) * keep + Color.green(overlay) * amount),
                Math.round(Color.blue(base) * keep + Color.blue(overlay) * amount));
    }
}
