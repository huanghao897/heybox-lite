package com.max.xiaoheihe.utils;

import android.content.Context;

import com.openzen.heyboxcommunity.NativeLibraryLoader;

public final class NDKTools {
    private static boolean loaded;

    private NDKTools() {}

    public static synchronized void load(Context context) {
        if (loaded) return;
        NativeLibraryLoader.load(context, "native-lib");
        loaded = true;
    }

    public static native int checkSignature(Object context);

    public static native synchronized String encode(Object context, String path, String time, String nonce);

    public static native String getrsakey(Object context, String value, String salt);
}
