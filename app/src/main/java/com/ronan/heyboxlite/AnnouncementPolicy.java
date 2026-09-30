package com.ronan.heyboxlite;

import android.content.Context;
import android.content.SharedPreferences;

/** Suppression is an explicit user choice, independent of the old read marker. */
final class AnnouncementPolicy {
    private final SharedPreferences preferences;

    AnnouncementPolicy(Context context) {
        preferences = context.getSharedPreferences("announcement_dismissals", Context.MODE_PRIVATE);
    }

    boolean suppressed(String id) {
        return id != null && !id.isEmpty() && preferences.getBoolean(id, false);
    }

    void suppress(String id) {
        if (id != null && !id.isEmpty()) preferences.edit().putBoolean(id, true).apply();
    }

    static boolean active(boolean enabled, long start, long end, long now) {
        return enabled && (start == 0 || now >= start) && (end == 0 || now < end);
    }
}
