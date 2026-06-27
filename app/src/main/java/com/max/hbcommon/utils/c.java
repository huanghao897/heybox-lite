package com.max.hbcommon.utils;

public final class c {
    private c() {}

    public static boolean u(String value) {
        return value == null || value.length() == 0;
    }

    public static boolean e(String value) {
        return u(value);
    }

    public static boolean x(String value) {
        return value != null && ("1".equals(value) || "true".equalsIgnoreCase(value));
    }
}
