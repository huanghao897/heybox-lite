package com.ronan.heyboxlite;

import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;

import java.util.Locale;

final class RoundLayoutMetrics {
    private static final float ROUND_ASPECT_RATIO = 1.06f;
    private static final float WATCH_ASPECT_RATIO = 1.40f;

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
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return model.contains("watch")
                && isRoundAspectRatio(metrics.widthPixels, metrics.heightPixels);
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
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return isWatchAspectRatio(metrics.widthPixels, metrics.heightPixels, metrics.density);
    }

    static boolean isRoundAspectRatio(int widthPx, int heightPx) {
        int shortSide = Math.min(widthPx, heightPx);
        int longSide = Math.max(widthPx, heightPx);
        return shortSide > 0 && longSide / (float) shortSide <= ROUND_ASPECT_RATIO;
    }

    static boolean isWatchAspectRatio(int widthPx, int heightPx, float density) {
        int shortSide = Math.min(widthPx, heightPx);
        int longSide = Math.max(widthPx, heightPx);
        if (shortSide <= 0 || longSide <= 0) return false;
        float shortDp = shortSide / Math.max(1.0f, density);
        return shortSide <= 600 && shortDp <= 320.0f
                && longSide / (float) shortSide <= WATCH_ASPECT_RATIO;
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
