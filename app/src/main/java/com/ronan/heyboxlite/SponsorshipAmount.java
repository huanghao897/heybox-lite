package com.ronan.heyboxlite;

final class SponsorshipAmount {
    private SponsorshipAmount() {
    }

    static int parseCents(String value, int minimum, int maximum) {
        if (value == null) return -1;
        String input = value.trim();
        if (input.isEmpty()) return -1;

        int point = input.indexOf('.');
        if (point != input.lastIndexOf('.')) return -1;
        String yuan = point < 0 ? input : input.substring(0, point);
        String fraction = point < 0 ? "" : input.substring(point + 1);
        if (yuan.isEmpty() || fraction.length() > 2
                || !digits(yuan) || !digits(fraction)) {
            return -1;
        }

        long whole = 0L;
        for (int index = 0; index < yuan.length(); index++) {
            whole = whole * 10L + yuan.charAt(index) - '0';
            if (whole > maximum / 100L + 1L) return -1;
        }
        int cents = fraction.isEmpty() ? 0 : (fraction.charAt(0) - '0') * 10;
        if (fraction.length() == 2) cents += fraction.charAt(1) - '0';
        long result = whole * 100L + cents;
        return result >= minimum && result <= maximum ? (int) result : -1;
    }

    static boolean validCents(int value) {
        return value >= 1 && value <= 100_000_000;
    }

    static String formatYuan(int cents) {
        int value = Math.max(0, cents);
        int fraction = value % 100;
        return (value / 100) + "." + (fraction < 10 ? "0" : "") + fraction;
    }

    private static boolean digits(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') return false;
        }
        return true;
    }
}
