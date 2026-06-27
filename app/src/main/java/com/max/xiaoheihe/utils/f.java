package com.max.xiaoheihe.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import com.meituan.robust.ChangeQuickRedirect;

import java.util.List;

public final class f {
    public static ChangeQuickRedirect changeQuickRedirect;
    private static String deviceId = "";
    private static String appVersion = "1.3.379";
    private static String buildCode = "1055";

    private f() {}

    public static String B0() {
        return appVersion == null || appVersion.isEmpty() ? "1.3.379" : appVersion;
    }

    public static String buildCode() {
        return buildCode == null || buildCode.isEmpty() ? "1055" : buildCode;
    }

    public static String x0() {
        return "heybox_oppo";
    }

    public static String Y() {
        if (deviceId != null && !deviceId.trim().isEmpty()) return deviceId.trim();
        Context context = com.max.xiaoheihe.app.HeyBoxApplication.P();
        if (context == null) return "";
        try {
            String value = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID);
            return value == null ? "" : value;
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String g0(Context context) {
        return context == null ? "" : context.getPackageName();
    }

    public static void initDeviceId(String value) {
        deviceId = value == null ? "" : value;
    }

    public static void initAppInfo(String version, String build) {
        if (version != null && !version.trim().isEmpty()) appVersion = version.trim();
        if (build != null && !build.trim().isEmpty()) buildCode = build.trim();
        com.max.xiaoheihe.a.a(appVersion, buildCode);
    }

    public static String C(Context context, String key) {
        return "";
    }

    public static String q() {
        return Build.MODEL == null ? "" : Build.MODEL;
    }

    public static String f() {
        return "";
    }

    public static String f(String value) {
        return value == null ? "" : value;
    }

    public static String f(int start, int end) {
        return "";
    }

    public static String f(Context context) {
        return g0(context);
    }

    public static String f(Context context, String key) {
        return C(context, key);
    }

    public static void e(Context context, String key) {}

    public static Intent e0(Context context, String value) {
        return new Intent();
    }

    public static String h0(Context context, String path) {
        return "";
    }

    public static String f(Context context, String a, boolean b) {
        return a == null ? "" : a;
    }

    public static String f(Context context, String a, String b, String c) {
        return "";
    }

    public static String f(Object object, String a, String b, String c) {
        return "";
    }

    public static List f(List list) {
        return list;
    }
}
