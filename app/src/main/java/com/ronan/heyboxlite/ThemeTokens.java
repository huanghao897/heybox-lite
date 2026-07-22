package com.ronan.heyboxlite;

import android.graphics.Color;

/** Compact Material 3 color roles used by the native View UI. */
final class ThemeTokens {
    final boolean dark;
    final int primary;
    final int onPrimary;
    final int primaryContainer;
    final int onPrimaryContainer;
    final int secondary;
    final int tertiary;
    final int background;
    final int surface;
    final int surfaceContainerLow;
    final int surfaceContainer;
    final int surfaceContainerHigh;
    final int surfaceContainerHighest;
    final int text;
    final int muted;
    final int subtle;
    final int outline;
    final int outlineVariant;
    final int hairline;
    final int panel;
    final int panelElevated;
    final int accent;
    final int glassStroke;
    final int error;
    final int success;
    final int warning;
    final int scrim;

    private ThemeTokens(boolean dark, int primarySeed, int secondarySeed) {
        this.dark = dark;

        int safePrimary = primarySeed == 0 ? Color.rgb(86, 87, 92) : primarySeed;
        int safeSecondary = secondarySeed == 0 ? Color.rgb(145, 145, 152) : secondarySeed;
        primary = dark
                ? blend(safePrimary, Color.WHITE, 0.42f)
                : blend(safePrimary, Color.BLACK, 0.08f);
        secondary = dark
                ? blend(safeSecondary, Color.WHITE, 0.32f)
                : blend(safeSecondary, Color.BLACK, 0.10f);
        tertiary = dark ? Color.rgb(194, 194, 202) : Color.rgb(78, 78, 86);

        if (dark) {
            background = Color.rgb(12, 12, 14);
            surface = Color.rgb(16, 16, 18);
            surfaceContainerLow = Color.rgb(20, 20, 23);
            surfaceContainer = Color.rgb(25, 25, 28);
            surfaceContainerHigh = Color.rgb(31, 31, 35);
            surfaceContainerHighest = Color.rgb(39, 39, 44);
            text = Color.rgb(240, 240, 243);
            muted = Color.rgb(190, 190, 197);
            subtle = Color.rgb(139, 139, 148);
            outline = Color.rgb(139, 139, 148);
            outlineVariant = Color.rgb(66, 66, 73);
            error = Color.rgb(255, 180, 171);
            success = Color.rgb(126, 219, 172);
            warning = Color.rgb(245, 196, 111);
            scrim = Color.argb(184, 0, 0, 0);
        } else {
            background = Color.rgb(247, 247, 249);
            surface = Color.rgb(253, 253, 254);
            surfaceContainerLow = Color.rgb(244, 244, 246);
            surfaceContainer = Color.rgb(238, 238, 241);
            surfaceContainerHigh = Color.rgb(232, 232, 235);
            surfaceContainerHighest = Color.rgb(225, 225, 229);
            text = Color.rgb(28, 28, 31);
            muted = Color.rgb(75, 75, 82);
            subtle = Color.rgb(119, 119, 128);
            outline = Color.rgb(119, 119, 128);
            outlineVariant = Color.rgb(198, 198, 204);
            error = Color.rgb(186, 26, 26);
            success = Color.rgb(28, 111, 72);
            warning = Color.rgb(126, 85, 0);
            scrim = Color.argb(122, 0, 0, 0);
        }

        primaryContainer = blend(surfaceContainerHigh, primary, dark ? 0.28f : 0.18f);
        onPrimaryContainer = dark
                ? blend(text, primary, 0.10f)
                : blend(text, primary, 0.22f);
        onPrimary = contrast(primary);

        panel = surfaceContainer;
        panelElevated = surfaceContainerHigh;
        hairline = blend(outlineVariant, background, dark ? 0.34f : 0.18f);
        accent = primary;
        glassStroke = blend(outlineVariant, text, dark ? 0.05f : 0.02f);
    }

    static ThemeTokens of(boolean dark, int primary, int secondary) {
        return new ThemeTokens(dark, primary, secondary);
    }

    int softAccent() {
        return primaryContainer;
    }

    int faintAccent() {
        return blend(panel, accent, dark ? 0.12f : 0.08f);
    }

    int pressedSurface() {
        return blend(panel, text, dark ? 0.11f : 0.06f);
    }

    static int contrast(int color) {
        int luminance = (Color.red(color) * 299 + Color.green(color) * 587
                + Color.blue(color) * 114) / 1000;
        return luminance >= 150 ? Color.BLACK : Color.WHITE;
    }

    static int blend(int base, int overlay, float amount) {
        float value = Math.max(0f, Math.min(1f, amount));
        float keep = 1f - value;
        return Color.argb(
                Math.round(Color.alpha(base) * keep + Color.alpha(overlay) * value),
                Math.round(Color.red(base) * keep + Color.red(overlay) * value),
                Math.round(Color.green(base) * keep + Color.green(overlay) * value),
                Math.round(Color.blue(base) * keep + Color.blue(overlay) * value));
    }
}
