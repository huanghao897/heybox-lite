package com.ronan.heyboxlite;

import android.graphics.Color;

final class ThemePalette {
    private static final String[] NAMES = {
            "蓝色", "红色", "粉色", "紫色", "绿色", "青色",
            "橙色", "黄色", "灰色", "深蓝", "黑金", "薄荷绿"
    };
    private static final int[][] COLORS = {
            {0xFF2479B8, 0xFF73B8E6}, {0xFFC33A3A, 0xFFEF7777},
            {0xFFD85C91, 0xFFF0A4C1}, {0xFF7652B5, 0xFFB79ADF},
            {0xFF278A57, 0xFF71C798}, {0xFF168B91, 0xFF6BC7CB},
            {0xFFD36B24, 0xFFF0A064}, {0xFFC39A16, 0xFFF0CF68},
            {0xFF878896, 0xFFAEB4BA}, {0xFF173F76, 0xFF5D8BC4},
            {0xFF171717, 0xFFD0A83E}, {0xFF318B73, 0xFF88D8BF}
    };

    private ThemePalette() {}

    static int count() {
        return NAMES.length;
    }

    static String name(int index) {
        return NAMES[index];
    }

    static int primary(int index) {
        return COLORS[index][0];
    }

    static int secondary(int index) {
        return COLORS[index][1];
    }

    static boolean isDefault(SessionStore session) {
        return session.primaryColor().isEmpty() && session.secondaryColor().isEmpty();
    }

    static int currentPrimary(SessionStore session) {
        return savedColor(session.primaryColor(), session.darkMode() ? Color.WHITE : Color.BLACK);
    }

    static int currentSecondary(SessionStore session) {
        int fallback = session.darkMode()
                ? Color.rgb(196, 198, 201) : Color.rgb(87, 91, 96);
        return savedColor(session.secondaryColor(), fallback);
    }

    static int indexOf(int primary, int secondary) {
        for (int index = 0; index < COLORS.length; index++) {
            if (COLORS[index][0] == primary && COLORS[index][1] == secondary) return index;
        }
        return -1;
    }

    private static int savedColor(String value, int fallback) {
        if (value == null || value.isEmpty()) return fallback;
        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException error) {
            return fallback;
        }
    }
}
