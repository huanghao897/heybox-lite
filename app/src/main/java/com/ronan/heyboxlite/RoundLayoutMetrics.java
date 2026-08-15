package com.ronan.heyboxlite;

import android.content.Context;
import android.os.Build;

import java.util.Locale;

final class RoundLayoutMetrics {
    static final float PAGE_HORIZONTAL_RATIO = 0.105f;
    static final float HEADER_HORIZONTAL_RATIO = 0.13f;
    static final float SEARCH_HORIZONTAL_RATIO = 0.075f;
    static final float PAGE_TOP_RATIO = 0.11f;
    static final float SUBPAGE_TOP_RATIO = 0.09f;

    private RoundLayoutMetrics() {
    }

    static boolean isRoundDisplay(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && context.getResources().getConfiguration().isScreenRound()) {
            return true;
        }
        String model = Build.MODEL == null ? "" : Build.MODEL.toLowerCase(Locale.US);
        String compactModel = model.replace(" ", "");
        if (model.contains("watch x2") || compactModel.contains("watchx2")
                || compactModel.contains("owatchx2")) return true;
        return false;
    }

    static boolean isWatchDisplay(Context context) {
        if (context == null) return false;
        if (isRoundDisplay(context)) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && (context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_TYPE_MASK)
                == android.content.res.Configuration.UI_MODE_TYPE_WATCH) {
            return true;
        }
        if (context.getPackageManager().hasSystemFeature("android.hardware.type.watch")) {
            return true;
        }
        String model = Build.MODEL == null ? "" : Build.MODEL.toLowerCase(Locale.US);
        if (model.contains("watch")) return true;
        return false;
    }

    static boolean isRectangularWatchDisplay(Context context) {
        return isWatchDisplay(context) && !isRoundDisplay(context);
    }

    static int componentInset(int extentPx, float targetRatio, int minimumPx) {
        int safeExtent = Math.max(0, extentPx);
        int targetPx = Math.round(safeExtent * Math.max(0.0f, targetRatio));
        return Math.max(Math.max(0, minimumPx), targetPx);
    }

    static int headerInnerInset(int widthPx) {
        return Math.max(0, Math.round(widthPx
                * (HEADER_HORIZONTAL_RATIO - PAGE_HORIZONTAL_RATIO)));
    }
}
