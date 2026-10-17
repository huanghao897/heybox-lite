package com.ronan.heyboxlite;

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
    final int link;
    final int glassStroke;
    final int onPrimary;
    /** Text/icon colour that sits on top of {@link #accent}. */
    final int onAccent;

    private ThemeTokens(boolean dark, int primary, int secondary, boolean defaultPalette) {
        this.dark = dark;
        this.primary = primary;
        this.secondary = secondary;
        if (dark) {
            background = Color.rgb(10, 10, 11);
            panel = Color.rgb(22, 22, 24);
            panelElevated = Color.rgb(38, 38, 42);
            text = Color.rgb(245, 245, 247);
            muted = Color.rgb(178, 178, 184);
            subtle = Color.rgb(132, 132, 139);
            hairline = Color.rgb(44, 44, 48);
        } else {
            background = Color.rgb(243, 243, 245);
            panel = Color.rgb(255, 255, 255);
            panelElevated = Color.rgb(234, 235, 239);
            text = Color.rgb(25, 25, 27);
            muted = Color.rgb(92, 92, 98);
            subtle = Color.rgb(126, 126, 132);
            hairline = Color.rgb(226, 226, 230);
        }
        // The untouched default palette used to fall back to grey, which left every
        // highlight, toggle and selected state colourless. Give it one real accent.
        accent = defaultPalette ? brandAccent(dark) : secondary;
        link = dark ? Color.rgb(89, 169, 235) : Color.rgb(0, 103, 173);
        glassStroke = blend(hairline, text, dark ? 0.06f : 0.04f);
        onPrimary = contrast(primary);
        onAccent = contrast(accent);
    }

    static ThemeTokens of(boolean dark, int primary, int secondary) {
        int defaultPrimary = dark ? Color.WHITE : Color.rgb(20, 21, 23);
        int defaultSecondary = dark ? Color.rgb(196, 198, 201) : Color.rgb(87, 91, 96);
        int normalizedPrimary = primary != 0 ? primary : defaultPrimary;
        int normalizedSecondary = secondary != 0 ? secondary : defaultSecondary;
        boolean defaultPalette = normalizedPrimary == defaultPrimary
                && normalizedSecondary == defaultSecondary;
        return new ThemeTokens(dark, normalizedPrimary, normalizedSecondary, defaultPalette);
    }

    /** Warm brand accent used while no palette has been picked in settings. */
    static int brandAccent(boolean dark) {
        return dark ? Color.rgb(255, 122, 69) : Color.rgb(194, 65, 12);
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

    int dockSurface() {
        return dark ? Color.argb(224, 30, 30, 33)
                : Color.argb(232, 248, 248, 250);
    }

    int navSelection() {
        return dark ? Color.argb(36, 255, 255, 255)
                : Color.argb(22, 0, 0, 0);
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
