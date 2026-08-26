package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class VideoData {
    private static final String[] VIDEO_SOURCE_KEYS = {
            "video_url", "videoUrl", "play_url", "playUrl", "video_info"
    };
    private static final String[] VIDEO_COVER_KEYS = {
            "video_thumb", "videoThumb", "video_cover", "videoCover", "poster",
            "video_poster", "video_poster_url"
    };

    final String url;
    final String cover;
    final String title;

    private VideoData(String url, String cover, String title) {
        this.url = normalizeUrl(url);
        this.cover = normalizeUrl(cover);
        this.title = title == null ? "" : title.trim();
    }

    static VideoData create(String url, String cover, String title) {
        return new VideoData(url, cover, title);
    }

    static List<VideoData> from(JSONObject source) {
        List<VideoData> videos = new ArrayList<>();
        collect(source, videos, new HashSet<JSONObject>(), new HashSet<String>(), 0);
        return videos;
    }

    boolean playable() {
        return !this.url.isEmpty();
    }

    VideoData withFallbackTitle(String fallbackTitle) {
        if (!this.title.isEmpty() || fallbackTitle == null || fallbackTitle.trim().isEmpty()) {
            return this;
        }
        return new VideoData(this.url, this.cover, fallbackTitle);
    }

    private static void collect(Object value, List<VideoData> output,
                                Set<JSONObject> visited, Set<String> seen, int depth) {
        if (value == null || value == JSONObject.NULL || depth > 16
                || output.size() >= 4) return;
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (!visited.add(object)) return;
            if (isVideoNode(object)) {
                addCandidate(output, seen, object);
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object child = object.opt(key);
                if (child instanceof JSONObject || child instanceof JSONArray) {
                    collect(child, output, visited, seen, depth + 1);
                } else if (child instanceof String) {
                    collectJsonString((String) child, output, visited, seen, depth + 1);
                }
            }
            return;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                collect(array.opt(index), output, visited, seen, depth + 1);
                if (output.size() >= 4) return;
            }
        }
    }

    private static void collectJsonString(String value, List<VideoData> output,
                                          Set<JSONObject> visited, Set<String> seen,
                                          int depth) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || (!text.startsWith("{") && !text.startsWith("["))) return;
        try {
            collect(text.startsWith("[") ? new JSONArray(text) : new JSONObject(text),
                    output, visited, seen, depth);
        } catch (JSONException ignored) {
        }
    }

    private static void addCandidate(List<VideoData> output, Set<String> seen,
                                     JSONObject object) {
        String rawUrl = firstUrl(object, VIDEO_SOURCE_KEYS);
        if (rawUrl.isEmpty()) rawUrl = genericVideoUrl(object);
        String fallbackUrl = firstUrl(object, "url", "src", "source");
        String cover = firstUrl(object, VIDEO_COVER_KEYS);
        if (cover.isEmpty()) cover = coverUrl(object.opt("video_info"));
        boolean typedVideoNode = isVideoType(object.optString("type", ""));
        if (rawUrl.isEmpty() && isLikelyVideoUrl(fallbackUrl)) {
            rawUrl = fallbackUrl;
        }
        if (cover.isEmpty() && typedVideoNode && isLikelyImageUrl(fallbackUrl)) {
            cover = fallbackUrl;
        }
        if (rawUrl.isEmpty() && cover.isEmpty()) return;

        String key = rawUrl.isEmpty() ? cover : rawUrl;
        if (!seen.add(key)) return;
        output.add(new VideoData(rawUrl, cover,
                first(object.optString("title"), object.optString("name"))));
    }

    private static boolean isVideoNode(JSONObject object) {
        if (isVideoType(object.optString("type", ""))) return true;
        if (hasVideoSource(object)) return true;
        if (!firstUrl(object, VIDEO_COVER_KEYS).isEmpty()) return true;
        return isVideoFlag(object) && object.has("video_info");
    }

    private static boolean isVideoType(String value) {
        String type = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "video".equals(type) || type.endsWith("_video")
                || type.endsWith("-video") || type.contains("video_item");
    }

    private static boolean isVideoFlag(JSONObject object) {
        return flagValue(object.opt("has_video")) || flagValue(object.opt("is_video"));
    }

    private static boolean flagValue(Object value) {
        if (value == null || value == JSONObject.NULL) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() == 1;
        String text = String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    private static boolean hasVideoSource(JSONObject object) {
        String direct = firstUrl(object, VIDEO_SOURCE_KEYS);
        if (!direct.isEmpty() || !genericVideoUrl(object).isEmpty()) return true;
        return isLikelyVideoUrl(firstUrl(object, "url", "src", "source"));
    }

    private static String genericVideoUrl(JSONObject object) {
        String value = firstUrl(object, "media_url", "mediaUrl", "source_url", "src_url");
        return isLikelyVideoUrl(value) ? value : "";
    }

    private static String firstUrl(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = urlValue(object.opt(key));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String urlValue(Object value) {
        if (value instanceof String) return ((String) value).trim();
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                String result = urlValue(array.opt(index));
                if (!result.isEmpty()) return result;
            }
            return "";
        }
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            return firstUrl(object, "video_url", "videoUrl", "play_url", "playUrl",
                    "video_urls", "option_urls", "media_url", "mediaUrl", "url", "src",
                    "original", "origin", "source");
        }
        return "";
    }

    private static String coverUrl(Object value) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                String result = coverUrl(array.opt(index));
                if (!result.isEmpty()) return result;
            }
            return "";
        }
        if (!(value instanceof JSONObject)) return "";
        JSONObject object = (JSONObject) value;
        return firstUrl(object, VIDEO_COVER_KEYS);
    }

    private static boolean isLikelyVideoUrl(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp4") || lower.contains(".mp4?")
                || lower.endsWith(".m3u8") || lower.contains(".m3u8?")
                || lower.endsWith(".webm") || lower.contains("/video/")
                || lower.contains("video_url");
    }

    private static boolean isLikelyImageUrl(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.contains(".jpg?")
                || lower.endsWith(".jpeg") || lower.contains(".jpeg?")
                || lower.endsWith(".png") || lower.contains(".png?")
                || lower.endsWith(".webp") || lower.contains("imagemogr")
                || lower.contains("imgheybox");
    }

    private static String normalizeUrl(String value) {
        if (value == null) return "";
        String result = value.trim().replace("\\/", "/");
        if (result.startsWith("//")) return "https:" + result;
        if (result.regionMatches(true, 0, "http://", 0, 7)) {
            return "https://" + result.substring(7);
        }
        return result;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }
}
