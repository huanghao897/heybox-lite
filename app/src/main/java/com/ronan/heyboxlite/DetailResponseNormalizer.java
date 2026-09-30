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
            "roll_room_content", "post_content_section", "postContentSection"
    };
    private static final String[] PRELOAD_KEYS = {
            "communityPostPreload", "community_post_preload",
            "post_content_section", "postContentSection",
            "content_section", "contentSection", "preload_web_json_content",
            "preloadWebJsonContent"
    };
    private static final String[] GAME_KEYS = {
            "game", "game_details", "gameDetails", "game_tag_appids", "gameTagAppids",
            "game_info", "gameInfo", "games"
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
                JSONObject body = link.optJSONObject("body");
                for (String section : LINK_SECTIONS) {
                    mergeMissingValues(flattened, link.opt(section));
                    mergeMissingValues(flattened, body.opt(section));
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

    static void mergePreloadedContent(JSONObject response, JSONObject fallback) {
        if (response == null) return;
        JSONObject result = response.optJSONObject("result");
        JSONObject link = result == null ? null : result.optJSONObject("link");
        if (link == null) return;

        Object preload = firstPresent(fallback, PRELOAD_KEYS);
        if (preload == null) preload = firstPresent(result, PRELOAD_KEYS);
        if (preload == null) preload = firstPresent(link, PRELOAD_KEYS);
        if (preload != null && !link.has("_community_post_preload")) {
            try {
                link.put("_community_post_preload", preload);
            } catch (JSONException exception) {
                throw new IllegalStateException("Unable to merge post preload", exception);
            }
        }
        mergeGameFields(link, result);
        mergeGameFields(link, fallback);
    }

    static void mergeArticleWebContent(JSONObject response, JSONObject webResponse) {
        if (response == null || webResponse == null) return;
        JSONObject result = response.optJSONObject("result");
        JSONObject link = result == null ? null : result.optJSONObject("link");
        if (link == null) return;
        try {
            link.put("_article_web_view", webResponse);
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to merge article web content", exception);
        }
    }

    private static Object firstPresent(JSONObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            Object value = object.opt(key);
            if (value != null && value != JSONObject.NULL) return value;
        }
        return null;
    }

    private static void mergeGameFields(JSONObject target, JSONObject source) {
        if (target == null || source == null) return;
        try {
            for (String key : GAME_KEYS) {
                Object value = source.opt(key);
                if (value == null || value == JSONObject.NULL) continue;
                if (!target.has(key) || target.isNull(key)) target.put(key, value);
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("Unable to merge game fields", exception);
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

    private static void mergeMissingValues(JSONObject target, Object source) throws JSONException {
        if (!(source instanceof JSONObject)) return;
        JSONObject object = (JSONObject) source;
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!target.has(key) || target.isNull(key)) {
                target.put(key, object.get(key));
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
