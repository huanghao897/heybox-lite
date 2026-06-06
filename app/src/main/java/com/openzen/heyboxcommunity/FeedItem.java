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
    final int likes;

    private FeedItem(String id, String title, String description, String author,
                     String image, int comments, int likes) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.author = author;
        this.image = image;
        this.comments = comments;
        this.likes = likes;
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
        return new FeedItem(
                json.optString("linkid", json.optString("link_id")),
                title,
                description,
                user == null ? "" : user.optString("username"),
                image,
                json.optInt("comment_num", json.optInt("comment_count")),
                json.optInt("link_award_num",
                        json.optInt("like_num",
                                json.optInt("award_num", json.optInt("up"))))
        );
    }

    private static String first(JSONArray array) {
        return array == null || array.length() == 0 ? "" : array.optString(0);
    }
}
