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
    private static final ThreadLocal<SimpleDateFormat> MONTH_DAY =
            new ThreadLocal<SimpleDateFormat>() {
                @Override protected SimpleDateFormat initialValue() {
                    return new SimpleDateFormat("MM-dd", Locale.getDefault());
                }
            };
    private static final ThreadLocal<SimpleDateFormat> MONTH_DAY_TIME =
            new ThreadLocal<SimpleDateFormat>() {
                @Override protected SimpleDateFormat initialValue() {
                    return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                }
            };

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
        return MONTH_DAY_TIME.get().format(new Date(millis));
    }

    static String commentLikeCount(int count) {
        if (count < 1_000) return String.valueOf(count);
        if (count < 10_000) return compactDecimal(count / 1_000.0d) + "K";
        if (count < 100_000_000) return compactDecimal(count / 10_000.0d) + "万";
        return compactDecimal(count / 100_000_000.0d) + "亿";
    }

    static String relativeTime(long timestamp) {
        long millis = timestamp > 100_000_000_000L
                ? timestamp : timestamp * 1_000L;
        long diff = Math.max(0L, System.currentTimeMillis() - millis);
        long minute = 60_000L;
        long hour = 60L * minute;
        long day = 24L * hour;
        if (diff < minute) return "刚刚";
        if (diff < hour) return Math.max(1L, diff / minute) + "分钟前";
        if (diff < day) return Math.max(1L, diff / hour) + "小时前";
        return MONTH_DAY.get().format(new Date(millis));
    }

    static String announcementPreview(String value) {
        if (value == null) return "";
        String clean = value.replace('\r', '\n').replace("\n\n", "\n").trim();
        return clean.length() <= 92 ? clean : clean.substring(0, 92) + "...";
    }

    static String announcementTime(String value) {
        if (value == null) return "";
        String clean = value.trim();
        if (clean.isEmpty()) return "";
        if (!clean.matches("\\d+")) {
            return clean.contains("-") || clean.contains("/") || clean.contains(":") ? clean : "";
        }
        try {
            long timestamp = Long.parseLong(clean);
            if (timestamp <= 0) return "";
            if (timestamp < 100_000_000_000L) timestamp *= 1000L;
            return MONTH_DAY_TIME.get().format(new Date(timestamp));
        } catch (NumberFormatException ignored) {
            return "";
        }
    }
}
