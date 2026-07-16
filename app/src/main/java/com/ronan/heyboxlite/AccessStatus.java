package com.ronan.heyboxlite;

import org.json.JSONObject;

final class AccessStatus {
    final boolean banned;
    final String message;

    AccessStatus(boolean banned, String message) {
        this.banned = banned;
        this.message = message == null ? "" : message.trim();
    }

    static AccessStatus from(JSONObject payload) {
        if (payload == null) return new AccessStatus(false, "");
        return new AccessStatus(payload.optBoolean("banned", false),
                Json.first(payload.optString("banMessage"), payload.optString("message")));
    }
}
