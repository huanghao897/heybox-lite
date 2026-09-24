package com.ronan.heyboxlite;

import android.content.Context;
import android.os.Build;

import java.util.Locale;

import cc.star0.wear.lib.cnwearoverlay.runtime.CnWearOverlay;

final class CrownDeviceProfile {
    private static final float DEFAULT_AXIS_GAIN = 1.0f;
    private static final float XIAOMI_WEAR_AXIS_GAIN = 4.0f;

    private CrownDeviceProfile() {
    }

    static float scrollAxisGain(Context context) {
        return isXiaomiWear(context) ? XIAOMI_WEAR_AXIS_GAIN : DEFAULT_AXIS_GAIN;
    }

    private static boolean isXiaomiWear(Context context) {
        if (context == null || !RoundLayoutMetrics.isWatchDisplay(context)) return false;
        if (CnWearOverlay.isXiaomi()) return true;

        String manufacturer = normalize(Build.MANUFACTURER);
        String brand = normalize(Build.BRAND);
        String model = normalize(Build.MODEL);
        return manufacturer.contains("xiaomi")
                || manufacturer.equals("mi")
                || brand.contains("xiaomi")
                || brand.equals("mi")
                || model.contains("xiaomi")
                || model.contains("miwatch");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US).replace(" ", "");
    }
}
