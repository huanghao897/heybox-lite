package com.ronan.heyboxlite;

import org.json.JSONException;
import org.json.JSONObject;

final class CheckinSharing {
    final boolean available;
    final boolean post;
    final boolean game;
    final boolean review;

    private CheckinSharing(boolean available, boolean post, boolean game, boolean review) {
        this.available = available;
        this.post = post;
        this.game = game;
        this.review = review;
    }

    static CheckinSharing parse(JSONObject task) {
        return new CheckinSharing(task.optBoolean("share_actions_available"),
                task.optBoolean("share_post"), task.optBoolean("share_game"),
                task.optBoolean("share_review"));
    }

    static void putChange(JSONObject body, String action, Boolean enabled) throws JSONException {
        if (action == null && enabled == null) return;
        if (enabled == null || !("share_post".equals(action) || "share_game".equals(action)
                || "share_review".equals(action))) throw new JSONException("invalid share action");
        body.put(action, enabled);
    }
}
