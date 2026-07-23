package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

final class DetailResponseNormalizer {
    private static final String[] LINK_SECTIONS = {
            "body", "stats", "access", "config", "share_info",
            "bbs_link_content", "game_comment_content", "web_content",
            "roll_room_content"
    };

    private DetailResponseNormalizer() {}

    static JSONObject normalize(JSONObject response) {
        if (response == null) return null;
        JSONObject result = response.optJSONObject("result");
        if (result == null) return response;

        try {
            JSONObject link = result.optJSONObject("link");
            if (link != null && link.optJSONObject("body") != null) {
                JSONObject flattened = copy(link);
                for (String section : LINK_SECTIONS) {
                    mergeMissing(flattened, link.optJSONObject(section));
                }
                result.put("link", flattened);
            }

            if (result.optJSONArray("comments") == null) {
                JSONObject comment = result.optJSONObject("comment");
                JSONArray comments = comment == null ? null : comment.optJSONArray("comments");
                if (comments != null) result.put("comments", comments);
            }
            return response;
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to normalize detail response", exception);
        }
    }

    private static JSONObject copy(JSONObject source) throws JSONException {
        JSONObject target = new JSONObject();
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            target.put(key, source.get(key));
        }
        return target;
    }

    private static void mergeMissing(JSONObject target, JSONObject source) throws JSONException {
        if (source == null) return;
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!target.has(key) || target.isNull(key)) {
                target.put(key, source.get(key));
            }
        }
    }
}
