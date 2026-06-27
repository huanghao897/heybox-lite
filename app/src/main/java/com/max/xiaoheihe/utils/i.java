package com.max.xiaoheihe.utils;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.max.xiaoheihe.app.HeyBoxApplication;

public final class i {
    public static String c = "";
    private static String token = "";

    private i() {}

    public static void init(String value) {
        token = value == null ? "" : value;
        c = token;
    }

    public static String e() {
        try {
            Context context = HeyBoxApplication.P();
            if (context == null) return "0";
            WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (manager == null) return "0";
            DisplayMetrics metrics = new DisplayMetrics();
            manager.getDefaultDisplay().getMetrics(metrics);
            float density = metrics.density <= 0 ? 1f : metrics.density;
            int widthDp = (int) ((metrics.widthPixels / density) + 0.5f);
            return String.valueOf(Math.max(widthDp, 1));
        } catch (Throwable ignored) {
            return "0";
        }
    }

    public static String f() {
        return token == null ? "" : token;
    }
}
