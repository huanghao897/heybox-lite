package com.openzen.heyboxcommunity;

import org.json.JSONArray;
import org.json.JSONObject;

final class FeedItem {
    final String id;
    final String title;
    final String description;
    final String author;
    final String image;
    final int comments;
    int likes;
    final boolean article;
    boolean liked;

    private FeedItem(String id, String title, String description, String author,
                     String image, int comments, int likes, boolean article, boolean liked) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.author = author;
        this.image = image;
        this.comments = comments;
        this.likes = likes;
        this.article = article;
        this.liked = liked;
    }

    static FeedItem from(JSONObject json) {
        JSONObject user = json.optJSONObject("user");
        JSONArray thumbs = json.optJSONArray("thumbs");
        JSONArray images = json.optJSONArray("imgs");
        String image = first(thumbs);
        if (image.isEmpty()) image = first(images);
        if (image.isEmpty()) image = json.optString("image");
        if (image.isEmpty()) image = json.optString("thumb");
        String title = json.optString("title");
        if (title.isEmpty()) title = json.optString("content");
        String description = json.optString("description");
        if (description.isEmpty()) description = json.optString("text");
        String author = user == null ? "" : user.optString("username",
                user.optString("nickname", user.optString("name")));
        if (author.isEmpty()) {
            author = first(json.optString("author_name"), json.optString("username"),
                    json.optString("nickname"), json.optString("author"));
        }
        return new FeedItem(
                json.optString("linkid", json.optString("link_id")),
                title,
                description,
                author,
                image,
                firstInt(json, "comment_num", "comment_count", "reply_num",
                        "reply_count", "comments"),
                json.optInt("link_award_num",
                        json.optInt("like_num",
                                json.optInt("award_num",
                                        json.optInt("award_count",
                                                json.optInt("up_num", json.optInt("up")))))),
                isArticle(json),
                json.optBoolean("is_award", json.optBoolean("liked",
                        json.optBoolean("is_liked", json.optInt("has_award") == 1)))
        );
    }

    private static boolean isArticle(JSONObject json) {
        if (json.has("use_concept_type")) return json.optInt("use_concept_type", 1) == 0;
        if (json.optBoolean("is_article", false)) return true;
        String type = json.optString("link_type",
                json.optString("content_type", json.optString("type")));
        return "article".equalsIgnoreCase(type) || "文章".equals(type);
    }

    private static String first(JSONArray array) {
        return array == null || array.length() == 0 ? "" : array.optString(0);
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
}
