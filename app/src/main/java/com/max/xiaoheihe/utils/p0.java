package com.max.xiaoheihe.utils;

import android.net.Uri;

import com.meituan.robust.ChangeQuickRedirect;

import java.util.Map;

public final class p0 {
    public static ChangeQuickRedirect changeQuickRedirect;

    private p0() {}

    public static String F() { return "os_version"; }
    public static String G() { return "build"; }
    public static String H() { return "channel"; }
    public static String I() { return "imei"; }
    public static String J() { return "device_info"; }
    public static String K() { return "netmode"; }
    public static String L() { return "nonce"; }
    public static String M() { return "pkey"; }
    public static String N() { return "hkey"; }
    public static String O() { return "_rnd"; }
    public static String P() { return "_time"; }
    public static String Q() { return "time_zone"; }
    public static String R() { return "os_type"; }
    public static String S() { return "version"; }
    public static String T() { return "dw"; }
    public static String V() { return "x_app"; }
    public static String W() { return "x_client_type"; }
    public static String X() { return "x_os_type"; }

    public static String h0() { return "heybox"; }

    public static String U(String url, String name) {
        if (url == null || url.isEmpty() || name == null || name.isEmpty()) return null;
        try {
            return Uri.parse(url).getQueryParameter(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String e(String url) {
        return url;
    }

    public static String f(String url, Map<String, String> params) {
        if (url == null || url.isEmpty() || params == null || params.isEmpty()) return url;
        try {
            Uri.Builder builder = Uri.parse(url).buildUpon();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry == null || entry.getKey() == null || entry.getKey().isEmpty()) continue;
                builder.appendQueryParameter(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
            return builder.build().toString();
        } catch (Throwable ignored) {
            return url;
        }
    }
}
