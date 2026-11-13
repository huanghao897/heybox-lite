package com.ronan.heyboxlite;

import org.json.JSONObject;

/** Distinguishes an author's identity from a post's generic id. */
final class AuthorIdentity {
    private static final String[] USER_KEYS = {
            "userid", "user_id", "heybox_id", "heyboxid", "uid", "account_id"
    };

    private AuthorIdentity() { }

    static String authorId(JSONObject post) {
        return authorId(post, post == null ? null : post.optJSONObject("user"));
    }

    static String authorId(JSONObject post, JSONObject user) {
        String nested = userId(user);
        if (nested.isEmpty() && post != null) nested = userId(post.optJSONObject("user"));
        // Only a known user object may use the generic id alias.
        return nested.isEmpty() ? explicitUserId(post) : nested;
    }

    static String userId(JSONObject user) {
        String explicit = explicitUserId(user);
        return explicit.isEmpty() ? value(user, "id") : explicit;
    }

    private static String explicitUserId(JSONObject source) {
        for (String key : USER_KEYS) {
            String id = value(source, key);
            if (!id.isEmpty()) return id;
        }
        return "";
    }

    private static String value(JSONObject source, String key) {
        if (source == null || source.isNull(key)) return "";
        Object raw = source.opt(key);
        return raw instanceof String || raw instanceof Number ? String.valueOf(raw).trim() : "";
    }
}
