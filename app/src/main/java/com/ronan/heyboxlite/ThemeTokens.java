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

        int safePrimary = primarySeed == 0 ? Color.rgb(42, 116, 181) : primarySeed;
        int safeSecondary = secondarySeed == 0 ? Color.rgb(78, 132, 190) : secondarySeed;
        primary = dark
                ? blend(safePrimary, Color.WHITE, 0.42f)
                : blend(safePrimary, Color.BLACK, 0.08f);
        secondary = dark
                ? blend(safeSecondary, Color.WHITE, 0.32f)
                : blend(safeSecondary, Color.BLACK, 0.10f);
        tertiary = dark ? Color.rgb(255, 184, 139) : Color.rgb(142, 75, 31);

        if (dark) {
            background = Color.rgb(15, 15, 18);
            surface = Color.rgb(18, 18, 22);
            surfaceContainerLow = Color.rgb(24, 24, 29);
            surfaceContainer = Color.rgb(29, 29, 35);
            surfaceContainerHigh = Color.rgb(36, 36, 43);
            surfaceContainerHighest = Color.rgb(45, 45, 53);
            text = Color.rgb(235, 231, 237);
            muted = Color.rgb(201, 196, 205);
            subtle = Color.rgb(148, 143, 153);
            outline = Color.rgb(148, 143, 153);
            outlineVariant = Color.rgb(72, 69, 78);
            error = Color.rgb(255, 180, 171);
            success = Color.rgb(126, 219, 172);
            warning = Color.rgb(245, 196, 111);
            scrim = Color.argb(184, 0, 0, 0);
        } else {
            background = Color.rgb(248, 247, 251);
            surface = Color.rgb(255, 251, 255);
            surfaceContainerLow = Color.rgb(247, 243, 248);
            surfaceContainer = Color.rgb(241, 238, 243);
            surfaceContainerHigh = Color.rgb(235, 232, 237);
            surfaceContainerHighest = Color.rgb(229, 226, 231);
            text = Color.rgb(29, 27, 32);
            muted = Color.rgb(73, 69, 79);
            subtle = Color.rgb(121, 116, 126);
            outline = Color.rgb(121, 116, 126);
            outlineVariant = Color.rgb(201, 196, 208);
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
