package com.max.xiaoheihe.utils;

import android.content.Context;

import com.max.xiaoheihe.bean.account.User;
import com.meituan.robust.ChangeQuickRedirect;

public final class m0 {
    public static ChangeQuickRedirect changeQuickRedirect;
    private static String userId = "-1";
    private static String pkey = "";

    private m0() {}

    public static void init(String id, String key) {
        userId = id == null || id.isEmpty() ? "-1" : id;
        pkey = key == null ? "" : key;
    }

    public static boolean s() {
        return userId != null && !userId.isEmpty() && !"-1".equals(userId);
    }

    public static String j() {
        return userId == null || userId.isEmpty() ? "-1" : userId;
    }

    public static boolean e(Context context) {
        return s();
    }

    public static User o() {
        return new User(pkey, j());
    }
}
