package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class FeedItem {
    private static final String[] LIKE_KEYS = {
            "link_award_num", "like_num", "award_num", "award_count", "up_num", "up"
    };
    private static final String[] TOPIC_ARRAY_KEYS = {
            "topics", "topic_list", "tags", "content_tags", "list_content_tags",
            "hashtags", "act_hashtags"
    };
    private static final String[] TOPIC_VALUE_KEYS = {
            "topic", "tag", "topic_name", "tag_name", "category", "post_tag",
            "extra_tag", "link_extra_tag"
    };
    private static final String[] NESTED_TOPIC_KEYS = {"link", "link_content"};
    private static final String[] ARTICLE_NESTED_KEYS = {
            "link", "link_content", "link_info", "basic_info"
    };

    final String id;
    final String hsrc;
    final String title;
    final String description;
    final String author;
    final String authorId;
    final String authorAvatar;
    final String topicName;
    final String image;
    final String[] images;
    final long createdAt;
    final int comments;
    final int clicks;
    int likes;
    final boolean article;
    final boolean video;
    final List<VideoData> videos;
    final boolean pinned;
    boolean liked;
    boolean following;
    boolean followPending;

    private FeedItem(String id, String title, String description, String author,
                     String authorId, String authorAvatar,
                     String topicName, String image, long createdAt, int comments, int clicks, int likes,
                     boolean article, boolean video, List<VideoData> videos, boolean liked,
                     boolean pinned, boolean following, String hsrc, String[] images) {
        this.id = id;
        this.hsrc = hsrc;
        this.title = title;
        this.description = description;
        this.author = author;
        this.authorId = authorId == null ? "" : authorId;
        this.authorAvatar = authorAvatar == null ? "" : authorAvatar;
        this.topicName = topicName == null ? "" : topicName;
        this.image = image;
        this.images = images == null ? new String[0] : images;
        this.createdAt = createdAt;
        this.comments = comments;
        this.clicks = clicks;
        this.likes = likes;
        this.article = article;
        this.video = video;
        this.videos = videos == null || videos.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(videos));
        this.pinned = pinned;
        this.liked = liked;
        this.following = following;
    }

    static FeedItem from(JSONObject json) {
        JSONObject user = json.optJSONObject("user");
        JSONArray thumbs = json.optJSONArray("thumbs");
        JSONArray imageArray = json.optJSONArray("imgs");
        String image = firstImage(thumbs);
        if (image.isEmpty()) image = firstImage(imageArray);
        if (image.isEmpty()) image = json.optString("image");
        if (image.isEmpty()) image = json.optString("thumb");
        List<VideoData> videos = VideoData.from(json);
        if (image.isEmpty() && !videos.isEmpty()) image = videos.get(0).cover;
        String[] detailImages = images(imageArray, thumbs, image,
                json.optString("image"), json.optString("thumb"));
        String title = first(json.optString("title"), json.optString("subject"),
                json.optString("name"));
        if (title.isEmpty()) title = json.optString("content");
        String description = first(json.optString("description"), json.optString("summary"),
                json.optString("brief"), json.optString("text"), json.optString("content"));
        String author = user == null ? "" : user.optString("username",
                user.optString("nickname", user.optString("name")));
        if (author.isEmpty()) {
            author = first(json.optString("author_name"), json.optString("username"),
                    json.optString("nickname"), json.optString("author"));
        }
        String authorId = first(userId(user), userId(json));
        String authorAvatar = user == null ? "" : user.optString("avatar", user.optString("avartar"));
        String topicName = topicName(json);
        return new FeedItem(
                json.optString("linkid", json.optString("link_id")),
                title,
                description,
                author,
                authorId,
                authorAvatar,
                topicName,
                image,
                normalizeTime(firstLong(json, "create_time", "created_at", "publish_time",
                        "post_time", "time")),
                firstInt(json, "comment_num", "comment_count", "reply_num",
                        "reply_count", "comments"),
                firstInt(json, "click", "click_num", "read_num", "view_num", "views"),
                firstInt(json, LIKE_KEYS),
                isArticleJson(json),
                !videos.isEmpty(),
                videos,
                json.optBoolean("is_award", json.optBoolean("liked",
                        json.optBoolean("is_liked", json.optInt("has_award") == 1))),
                pinned(json),
                FollowStatus.follows(json, user),
                hsrc(json),
                detailImages
        );
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("linkid", id);
            json.put("h_src", hsrc);
            json.put("title", title);
            json.put("description", description);
            json.put("image", image);
            if (createdAt > 0L) json.put("create_time", createdAt);
            if (images.length > 0) {
                JSONArray values = new JSONArray();
                for (String value : images) values.put(value);
                json.put("imgs", values);
            } else if (!image.isEmpty()) {
                JSONArray values = new JSONArray();
                values.put(image);
                json.put("imgs", values);
            }
            json.put("comment_num", comments);
            json.put("click", clicks);
            json.put("link_award_num", likes);
            json.put("is_article", article ? 1 : 0);
            json.put("has_video", video ? 1 : 0);
            if (!videos.isEmpty()) {
                VideoData firstVideo = videos.get(0);
                if (!firstVideo.url.isEmpty()) json.put("video_url", firstVideo.url);
                if (!firstVideo.cover.isEmpty()) json.put("video_thumb", firstVideo.cover);
                if (!firstVideo.title.isEmpty()) json.put("video_title", firstVideo.title);
            }
            json.put("is_top", pinned);
            json.put("is_liked", liked);
            JSONObject user = new JSONObject();
            user.put("username", author);
            user.put("userid", authorId);
            user.put("avatar", authorAvatar);
            user.put("is_follow", following ? 1 : 0);
            user.put("is_following", following);
            json.put("user", user);
            if (!topicName.isEmpty()) {
                JSONArray topics = new JSONArray();
                JSONObject topic = new JSONObject();
                topic.put("name", topicName);
                topics.put(topic);
                json.put("topics", topics);
            }
        } catch (JSONException ignored) {
        }
        return json;
    }

    static boolean isArticleJson(JSONObject json) {
        return articleValue(json, new HashSet<JSONObject>()) == Boolean.TRUE;
    }

    private static Boolean articleValue(JSONObject json, Set<JSONObject> visited) {
        if (json == null || !visited.add(json)) return null;

        Boolean explicit = booleanValue(json, "is_article");
        int contentType = json.optInt("content_type", Integer.MIN_VALUE);
        String type = firstValue(json, "link_type", "content_type", "type");

        // Feed wrappers can carry a generic false marker while their nested
        // link is an article. Check positive official markers before negatives.
        if (Boolean.TRUE.equals(explicit)
                || contentType == -1 || contentType == 101 || contentType == 103
                || isArticleType(type) || hasArticlePayload(json)) {
            return Boolean.TRUE;
        }

        Boolean nestedResult = null;
        for (String key : ARTICLE_NESTED_KEYS) {
            JSONObject nested = nestedObject(json, key);
            Boolean value = articleValue(nested, visited);
            if (Boolean.TRUE.equals(value)) return Boolean.TRUE;
            if (value != null) nestedResult = value;
        }

        if (explicit != null) return explicit;
        if (contentType == 102 || isPostType(type)) return Boolean.FALSE;
        // Older official feed responses use 0 for articles and 1 for posts.
        Boolean conceptType = booleanValue(json, "use_concept_type");
        if (conceptType != null) return !conceptType;
        return nestedResult;
    }

    private static JSONObject nestedObject(JSONObject json, String key) {
        Object value = json == null ? null : json.opt(key);
        if (value instanceof JSONObject) return (JSONObject) value;
        if (!(value instanceof String)) return null;
        String text = ((String) value).trim();
        if (!text.startsWith("{")) return null;
        try {
            return new JSONObject(text);
        } catch (JSONException ignored) {
            return null;
        }
    }

    private static Boolean booleanValue(JSONObject json, String key) {
        if (json == null || !json.has(key)) return null;
        Object value = json.opt(key);
        if (value == null || value == JSONObject.NULL) return null;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) {
            int number = ((Number) value).intValue();
            if (number == 0) return false;
            if (number == 1) return true;
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;
        if ("0".equals(text) || "false".equalsIgnoreCase(text)
                || isPostType(text)) return false;
        if ("1".equals(text) || "true".equalsIgnoreCase(text)
                || isArticleType(text)) return true;
        return null;
    }

    private static String firstValue(JSONObject json, String... keys) {
        for (String key : keys) {
            Object value = json.opt(key);
            if (value == null || value == JSONObject.NULL) continue;
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    private static boolean isArticleType(String value) {
        return "article".equalsIgnoreCase(value)
                || "news".equalsIgnoreCase(value)
                || "投稿".equals(value)
                || "文章".equals(value);
    }

    private static boolean isPostType(String value) {
        return "post".equalsIgnoreCase(value)
                || "thread".equalsIgnoreCase(value)
                || "帖子".equals(value)
                || "动态".equals(value);
    }

    private static boolean hasArticlePayload(JSONObject json) {
        return hasValue(json, "news_content") || hasValue(json, "article_info");
    }

    private static boolean hasValue(JSONObject json, String key) {
        Object value = json.opt(key);
        if (value == null || value == JSONObject.NULL) return false;
        if (value instanceof JSONObject) return ((JSONObject) value).length() > 0;
        if (value instanceof JSONArray) return ((JSONArray) value).length() > 0;
        return !String.valueOf(value).trim().isEmpty();
    }

    private static String hsrc(JSONObject json) {
        String value = json.optString("h_src", json.optString("hsrc"));
        if (!value.isEmpty()) return value;
        String shareUrl = json.optString("share_url");
        int index = shareUrl.indexOf("h_src=");
        if (index < 0) return "";
        int start = index + "h_src=".length();
        int end = shareUrl.indexOf('&', start);
        value = shareUrl.substring(start, end < 0 ? shareUrl.length() : end);
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (IllegalArgumentException | UnsupportedEncodingException ignored) {
            return value;
        }
    }

    private static String firstImage(JSONArray array) {
        if (array == null || array.length() == 0) return "";
        for (int i = 0; i < array.length(); i++) {
            String value = imageValue(array.opt(i));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static boolean pinned(JSONObject json) {
        return json.optBoolean("is_top", json.optBoolean("top",
                json.optBoolean("is_sticky", json.optInt("sticky") == 1)))
                || json.optInt("is_top", 0) == 1
                || json.optInt("is_sticky", 0) == 1;
    }

    private static String userId(JSONObject json) {
        if (json == null) return "";
        return first(json.optString("userid"), json.optString("user_id"),
                json.optString("heybox_id"), json.optString("heyboxid"),
                json.optString("uid"), json.optString("account_id"), json.optString("id"));
    }

    static String topicName(JSONObject json) {
        List<String> names = topicNames(json);
        return names.isEmpty() ? "" : names.get(0);
    }

    static List<String> topicNames(JSONObject json) {
        List<String> names = new ArrayList<>();
        if (json == null) return names;
        for (String key : TOPIC_ARRAY_KEYS) {
            addTopicValues(names, json.optJSONArray(key));
        }
        for (String key : TOPIC_VALUE_KEYS) {
            addTopicValue(names, json.opt(key));
        }
        addUiKitLabels(names, json.opt("link_extra_tag_v2"), 0);
        for (String key : NESTED_TOPIC_KEYS) {
            JSONObject nested = json.optJSONObject(key);
            if (nested != null && nested != json) {
                addUnique(names, topicNames(nested));
            }
        }
        return names;
    }

    private static void addUiKitLabels(List<String> names, Object value, int depth) {
        if (depth > 3 || value == null || value == JSONObject.NULL) return;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int index = 0; index < array.length(); index++) {
                addUiKitLabels(names, array.opt(index), depth + 1);
            }
            return;
        }
        if (!(value instanceof JSONObject)) return;
        JSONObject object = (JSONObject) value;
        addTopicValue(names, object);
        addUiKitLabels(names, object.opt("children"), depth + 1);
    }

    private static void addUnique(List<String> names, List<String> candidates) {
        for (String value : candidates) {
            if (!names.contains(value)) names.add(value);
        }
    }

    private static void addTopicValues(List<String> names, JSONArray values) {
        if (values == null) return;
        for (int i = 0; i < values.length(); i++) {
            addTopicValue(names, values.opt(i));
        }
    }

    private static void addTopicValue(List<String> names, Object value) {
        String label = "";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            label = first(object.optString("name"), object.optString("title"),
                    object.optString("topic_name"), object.optString("tag_name"),
                    object.optString("label"), object.optString("text"));
        } else if (value instanceof String) {
            label = ((String) value).trim();
        }
        if (!isReadableLabel(label) || names.contains(label)) return;
        names.add(label);
    }

    private static boolean isReadableLabel(String value) {
        if (value == null) return false;
        String clean = value.trim();
        if (clean.isEmpty() || clean.length() > 32 || clean.matches("\\d+")) return false;
        return !clean.startsWith("http://")
                && !clean.startsWith("https://")
                && !clean.startsWith("{")
                && !clean.startsWith("[");
    }

    private static String[] images(JSONArray primary, JSONArray secondary, String... extra) {
        List<String> values = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addImages(values, seen, primary);
        addImages(values, seen, secondary);
        if (extra != null) {
            for (String value : extra) addImage(values, seen, value);
        }
        return values.toArray(new String[values.size()]);
    }

    private static void addImages(List<String> values, Set<String> seen, JSONArray array) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            addImage(values, seen, imageValue(array.opt(i)));
        }
    }

    private static void addImage(List<String> values, Set<String> seen, String value) {
        if (value == null) return;
        String clean = value.trim();
        if (clean.isEmpty() || !seen.add(clean)) return;
        values.add(clean);
    }

    private static String imageValue(Object raw) {
        if (raw == null || raw == JSONObject.NULL) return "";
        if (raw instanceof JSONObject) {
            JSONObject object = (JSONObject) raw;
            return first(object.optString("url"), object.optString("src"),
                    object.optString("original"), object.optString("origin"),
                    object.optString("image"), object.optString("image_url"),
                    object.optString("thumb"), object.optString("thumbnail"));
        }
        return String.valueOf(raw);
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }

    private static int firstInt(JSONObject json, String... keys) {
        for (String key : keys) {
            if (!json.has(key)) continue;
            int value = json.optInt(key, Integer.MIN_VALUE);
            if (value != Integer.MIN_VALUE) return value;
        }
        return 0;
    }

    private static long firstLong(JSONObject json, String... keys) {
        for (String key : keys) {
            if (json.has(key)) return json.optLong(key, 0L);
        }
        return 0L;
    }

    private static long normalizeTime(long value) {
        return value > 100000000000L ? value / 1000L : value;
    }
}
