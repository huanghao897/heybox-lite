package com.openzen.heyboxcommunity;

final class SecureStrings {
    private static final int KEY = 83;

    private SecureStrings() {}

    static String hkey() { return decode(59, 56, 54, 42); }
    static String nonce() { return decode(61, 60, 61, 48, 54); }
    static String time() { return decode(12, 39, 58, 62, 54); }
    static String cookieHeader() { return decode(16, 60, 60, 56, 58, 54); }
    static String setCookieHeader() { return decode(0, 54, 39, 126, 16, 60, 60, 56, 58, 54); }
    static String setCookieHeaderLower() { return decode(32, 54, 39, 126, 48, 60, 60, 56, 58, 54); }
    static String cookieKey() { return decode(48, 60, 60, 56, 58, 54); }
    static String encryptedCookieKey() {
        return decode(48, 60, 60, 56, 58, 54, 12, 54, 61, 48, 33, 42, 35, 39, 54, 55);
    }
    static String preferencesName() {
        return decode(59, 54, 42, 49, 60, 43, 12, 32, 54, 32, 32, 58, 60, 61);
    }
    static String deviceId() { return decode(55, 54, 37, 58, 48, 54, 12, 58, 55); }
    static String userId() { return decode(38, 32, 54, 33, 12, 58, 55); }
    static String userid() { return decode(38, 32, 54, 33, 58, 55); }
    static String heyboxId() { return decode(59, 54, 42, 49, 60, 43, 12, 58, 55); }
    static String keyAlias() {
        return decode(59, 54, 42, 36, 54, 50, 33, 12, 32, 54, 32, 32, 58, 60, 61, 12, 56, 54, 42);
    }

    private static String decode(int... values) {
        char[] result = new char[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (char) (values[i] ^ KEY);
        return new String(result);
    }
}
