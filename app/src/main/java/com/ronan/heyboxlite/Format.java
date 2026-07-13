package com.ronan.heyboxlite;

import android.graphics.Color;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 无状态的展示格式化：时长、缓存/离线体积、点赞数缩写、颜色十六进制等。
 * 不依赖 Activity/视图，集中放置便于复用与单测。
 */
final class Format {
    private Format() {}

    static String colorHex(int color) {
        return String.format(Locale.US, "#%02X%02X%02X",
                Integer.valueOf(Color.red(color)), Integer.valueOf(Color.green(color)), Integer.valueOf(Color.blue(color)));
    }

    static String compactDecimal(double value) {
        String text = String.format(Locale.US, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    static String readingDuration(long milliseconds) {
        if (milliseconds <= 0L) return "0 分钟";
        long minutes = milliseconds / 60_000L;
        if (minutes == 0L) return "<1 分钟";
        long hours = minutes / 60L;
        long remainder = minutes % 60L;
        if (hours == 0L) return minutes + " 分钟";
        return remainder == 0L ? hours + " 小时" : hours + " 小时 " + remainder + " 分钟";
    }

    static String cacheMb(long bytes) {
        return String.format(Locale.US, "%.1f MB", Float.valueOf(Math.max(0L, bytes) / 1048576.0f));
    }

    static String offlineSize(long bytes) {
        if (bytes < 1024L * 1024L) {
            return Math.max(1L, bytes / 1024L) + " KB";
        }
        return cacheMb(bytes);
    }

    static String offlineTime(long millis) {
        if (millis <= 0L) return "未知";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    static String commentLikeCount(int count) {
        if (count < 1_000) return String.valueOf(count);
        if (count < 10_000) return compactDecimal(count / 1_000.0d) + "K";
        if (count < 100_000_000) return compactDecimal(count / 10_000.0d) + "万";
        return compactDecimal(count / 100_000_000.0d) + "亿";
    }
}
