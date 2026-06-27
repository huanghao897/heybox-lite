package com.max.xiaoheihe.app;

import android.content.Context;
import android.content.ContextWrapper;

import com.meituan.robust.ChangeQuickRedirect;

public final class HeyBoxApplication extends ContextWrapper {
    public static ChangeQuickRedirect changeQuickRedirect;

    private static HeyBoxApplication instance;

    public static void init(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        instance = new HeyBoxApplication(app);
    }

    public static HeyBoxApplication P() {
        return instance;
    }

    public static HeyBoxApplication O() {
        return instance;
    }

    public static boolean j0() {
        return false;
    }

    private HeyBoxApplication(Context base) {
        super(base);
    }

    public void c(String tag, Object listener) {}
}
