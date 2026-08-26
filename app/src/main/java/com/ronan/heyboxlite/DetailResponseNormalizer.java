package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

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
                JSONArray comments = commentsFrom(response, result, link);
                if (comments != null) result.put("comments", comments);
            }
            return response;
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to normalize detail response", exception);
        }
    }

    static void mergeVideoFallback(JSONObject response, JSONObject fallbackLink) {
        if (response == null || fallbackLink == null) return;
        JSONObject result = response.optJSONObject("result");
        JSONObject link = result == null ? null : result.optJSONObject("link");
        if (link == null) return;

        List<VideoData> detailVideos = VideoData.from(link);
        List<VideoData> fallbackVideos = VideoData.from(fallbackLink);
        if (fallbackVideos.isEmpty()) return;

        boolean needsFallback = detailVideos.isEmpty()
                || (!detailVideos.get(0).playable() && fallbackVideos.get(0).playable())
                || (detailVideos.get(0).cover.isEmpty()
                && !fallbackVideos.get(0).cover.isEmpty());
        if (!needsFallback) return;

        try {
            link.put("has_video", 1);
            mergeMissing(link, fallbackLink, "video_url", "video_thumb", "video_title",
                    "video_info", "video_urls", "option_urls", "duration");
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to merge video fallback", exception);
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

    private static void mergeMissing(JSONObject target, JSONObject source, String... keys)
            throws JSONException {
        for (String key : keys) {
            Object value = source.opt(key);
            if (value == null || value == JSONObject.NULL) continue;
            if (!target.has(key) || target.isNull(key)
                    || String.valueOf(target.opt(key)).trim().isEmpty()) {
                target.put(key, value);
            }
        }
    }

    private static JSONArray commentsFrom(JSONObject response, JSONObject result,
                                          JSONObject link) {
        JSONArray comments = firstArray(result, "comment_list", "comment");
        if (comments != null) return comments;
        comments = nestedComments(result.optJSONObject("comment"));
        if (comments != null) return comments;
        comments = nestedComments(result.optJSONObject("data"));
        if (comments != null) return comments;
        comments = nestedComments(link);
        if (comments != null) return comments;
        return response == null ? null : response.optJSONArray("comments");
    }

    private static JSONArray nestedComments(JSONObject object) {
        if (object == null) return null;
        JSONArray comments = object.optJSONArray("comments");
        if (comments != null) return comments;
        return object.optJSONArray("comment");
    }

    private static JSONArray firstArray(JSONObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            JSONArray value = object.optJSONArray(key);
            if (value != null) return value;
        }
        return null;
    }
}
