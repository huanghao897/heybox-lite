package com.ronan.heyboxlite;

import android.content.Context;
import android.content.SharedPreferences;

final class DailyNoticeThrottle {
    private static final String PREFERENCES = "heybox_notice_throttle";
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

    private DailyNoticeThrottle() {}

    static boolean claim(Context context, String notice) {
        if (context == null || notice == null || notice.isEmpty()) return false;
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        long day = System.currentTimeMillis() / DAY_MS;
        if (preferences.getLong(notice, -1L) == day) return false;
        preferences.edit().putLong(notice, day).apply();
        return true;
    }
}
