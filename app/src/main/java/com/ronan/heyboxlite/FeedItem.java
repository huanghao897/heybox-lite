package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class FeedItem {
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
    final boolean pinned;
    boolean liked;

    private FeedItem(String id, String title, String description, String author,
                     String authorId, String authorAvatar,
                     String topicName, String image, long createdAt, int comments, int clicks, int likes, boolean article, boolean liked,
                     boolean pinned, String hsrc, String[] images) {
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
        this.pinned = pinned;
        this.liked = liked;
    }

    static FeedItem from(JSONObject json) {
        JSONObject user = json.optJSONObject("user");
        JSONArray thumbs = json.optJSONArray("thumbs");
        JSONArray imageArray = json.optJSONArray("imgs");
        String image = firstImage(thumbs);
        if (image.isEmpty()) image = firstImage(imageArray);
        if (image.isEmpty()) image = json.optString("image");
        if (image.isEmpty()) image = json.optString("thumb");
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
                json.optInt("link_award_num",
                        json.optInt("like_num",
                                json.optInt("award_num",
                                        json.optInt("award_count",
                                                json.optInt("up_num", json.optInt("up")))))),
                isArticle(json),
                json.optBoolean("is_award", json.optBoolean("liked",
                        json.optBoolean("is_liked", json.optInt("has_award") == 1))),
                pinned(json),
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
            json.put("use_concept_type", article ? 0 : 1);
            json.put("is_top", pinned);
            json.put("is_liked", liked);
            JSONObject user = new JSONObject();
            user.put("username", author);
            user.put("userid", authorId);
            user.put("avatar", authorAvatar);
            json.put("user", user);
            if (!topicName.isEmpty()) {
                JSONArray topics = new JSONArray();
                JSONObject topic = new JSONObject();
                topic.put("name", topicName);
                topics.put(topic);
                json.put("topics", topics);
            }
        } catch (Exception ignored) {
        }
        return json;
    }

    private static boolean isArticle(JSONObject json) {
        if (json.has("use_concept_type")) return json.optInt("use_concept_type", 1) == 0;
        if (json.optBoolean("is_article", false)) return true;
        String type = json.optString("link_type",
                json.optString("content_type", json.optString("type")));
        return "article".equalsIgnoreCase(type) || "文章".equals(type);
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
        } catch (Exception ignored) {
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

    private static String topicName(JSONObject json) {
        JSONArray topics = json.optJSONArray("topics");
        if (topics != null && topics.length() > 0) {
            JSONObject topic = topics.optJSONObject(0);
            if (topic != null) return topic.optString("name");
        }
        JSONObject topic = json.optJSONObject("topic");
        if (topic != null) return topic.optString("name");
        return first(json.optString("topic_name"), json.optString("tag_name"),
                json.optString("category"));
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
            if (json.has(key)) return json.optInt(key, 0);
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
