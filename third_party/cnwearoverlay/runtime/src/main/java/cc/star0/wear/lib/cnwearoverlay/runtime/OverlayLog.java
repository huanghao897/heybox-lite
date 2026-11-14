package cc.star0.wear.lib.cnwearoverlay.runtime;

import android.util.Log;

/** 通过 adb shell setprop log.tag.CnWearOverlay DEBUG 临时开启，不缓存开关。 */
final class OverlayLog {
    private static final String TAG = "CnWearOverlay";

    private OverlayLog() {}

    static boolean enabled() {
        return Log.isLoggable(TAG, Log.DEBUG);
    }

    static void debug(String message) {
        if (enabled()) {
            Log.d(TAG, message);
        }
    }

    static void failure(String message, Throwable error) {
        if (enabled()) {
            Log.d(TAG, message, error);
        }
    }
}
