package com.nmmedit.protect;

import android.content.Context;

import com.openzen.heyboxcommunity.NativeLibraryLoader;

public final class NativeUtil {
    private static boolean loaded;

    private NativeUtil() {}

    public static synchronized void load(Context context) {
        if (loaded) return;
        if (NativeLibraryLoader.tryLoadFromNativeLibDir(context, "ailab")) {
            loaded = true;
            return;
        }
        NativeLibraryLoader.load(context, "ailab");
        loaded = true;
    }

    public static native void classesInit0(int value);
    public static native void classes2Init0(int value);
    public static native void classes3Init0(int value);
    public static native void classes4Init0(int value);
    public static native void classes5Init0(int value);
    public static native void classes6Init0(int value);
    public static native void classes7Init0(int value);
    public static native void classes8Init0(int value);
    public static native void classes9Init0(int value);
    public static native void classes10Init0(int value);
    public static native void classes11Init0(int value);
    public static native void classes12Init0(int value);
    public static native void classes13Init0(int value);
    public static native void classes14Init0(int value);
    public static native void classes15Init0(int value);
    public static native void classes16Init0(int value);
    public static native void classes17Init0(int value);
}
