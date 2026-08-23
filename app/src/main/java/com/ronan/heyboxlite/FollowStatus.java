package com.ronan.heyboxlite;

import org.json.JSONObject;

final class FollowStatus {
    private static final String[] KEYS = {
            "follow_status", "follow_state", "follow_state_v2",
            "is_follow", "is_following", "followed"
    };

    private FollowStatus() {}

    static int resolve(JSONObject primary, JSONObject fallback) {
        int status = value(primary);
        return status >= 0 ? status : value(fallback);
    }

    static boolean follows(JSONObject primary, JSONObject fallback) {
        int status = resolve(primary, fallback);
        return status == 1 || status == 3;
    }

    static int value(JSONObject source) {
        if (source == null) return -1;
        for (String key : KEYS) {
            if (!source.has(key)) continue;
            Object raw = source.opt(key);
            if (raw instanceof Boolean) return (Boolean) raw ? 1 : 0;
            if (raw instanceof Number) return ((Number) raw).intValue();
            String text = String.valueOf(raw).trim();
            if ("true".equalsIgnoreCase(text) || "followed".equalsIgnoreCase(text)
                    || "following".equalsIgnoreCase(text)) return 1;
            if ("mutual".equalsIgnoreCase(text)) return 3;
            if ("false".equalsIgnoreCase(text) || "none".equalsIgnoreCase(text)
                    || "unfollowed".equalsIgnoreCase(text)) return 0;
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }
}
