package com.ronan.heyboxlite;

final class PhoneNumber {
    private PhoneNumber() {}

    static String normalizeChineseMobile(String value) {
        if (value == null) return "";
        String clean = value.trim().replace(" ", "").replace("-", "");
        if (clean.startsWith("+86")) clean = clean.substring(3);
        else if (clean.startsWith("86") && clean.length() == 13) clean = clean.substring(2);
        return clean.matches("1[3-9][0-9]{9}") ? "+86" + clean : "";
    }

    static boolean isVerificationCode(String value) {
        return value != null && value.trim().matches("[0-9]{4,8}");
    }
}
