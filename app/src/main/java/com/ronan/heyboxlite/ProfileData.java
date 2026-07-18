package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;

final class ProfileData {
    private ProfileData() {}

    static JSONObject user(JSONObject body) {
        if (body == null) return null;
        JSONObject result = body.optJSONObject("result");
        if (result != null) {
            JSONObject account = result.optJSONObject("account_detail");
            if (account != null) return account;
            JSONObject nestedUser = result.optJSONObject("user");
            if (nestedUser != null) return nestedUser;
            JSONObject profile = result.optJSONObject("profile");
            if (profile != null) return profile;
        }
        return body.optJSONObject("user");
    }

    static JSONArray posts(JSONObject body) {
        if (body == null) return null;
        JSONArray posts = Json.firstArray(body, "post_links", "links", "list", "events",
                "moments", "data");
        if (posts != null) return posts;
        return Json.firstArray(body.optJSONObject("result"), "post_links", "links", "list",
                "events", "moments", "data");
    }

    static int followCount(JSONObject user) {
        return metric(user, "follow_num", "following_num", "attention_num", "follow_count",
                "following_count", "follow");
    }

    static int fanCount(JSONObject user) {
        return metric(user, "fan_num", "fans_num", "follower_num", "fan_count",
                "follower_count", "fans", "fan");
    }

    static int likeCount(JSONObject user) {
        JSONObject bbs = user == null ? null : user.optJSONObject("bbs_info");
        String[] officialKeys = {"be_favoured_num", "awarded_num", "received_award_num"};
        int value = firstMetric(user, officialKeys);
        if (value >= 0) return value;
        value = firstMetric(bbs, officialKeys);
        if (value >= 0) return value;
        String[] fallbackKeys = {"award_num", "award_count", "up_num", "like_num",
                "liked_num", "praise_num", "praise_count", "link_award_num", "award"};
        value = firstMetric(user, fallbackKeys);
        if (value >= 0) return value;
        value = firstMetric(bbs, fallbackKeys);
        if (value >= 0) return value;
        value = achievementMetric(user, fallbackKeys);
        if (value >= 0) return value;
        return Math.max(0, achievementMetric(bbs, fallbackKeys));
    }

    private static int metric(JSONObject user, String... keys) {
        int value = firstMetric(user, keys);
        if (value >= 0) return value;
        JSONObject bbs = user == null ? null : user.optJSONObject("bbs_info");
        value = firstMetric(bbs, keys);
        if (value >= 0) return value;
        value = achievementMetric(user, keys);
        if (value >= 0) return value;
        value = achievementMetric(bbs, keys);
        return Math.max(0, value);
    }

    private static int firstMetric(JSONObject object, String... keys) {
        if (object == null) return -1;
        for (String key : keys) {
            if (object.has(key)) return Math.max(0, object.optInt(key, 0));
        }
        return -1;
    }

    private static int achievementMetric(JSONObject object, String... keys) {
        JSONArray values = object == null ? null : object.optJSONArray("achieve");
        if (values == null) values = object == null ? null : object.optJSONArray("achievements");
        if (values == null) return -1;
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item == null) continue;
            String key = item.optString("key");
            for (String expected : keys) {
                if (expected.equalsIgnoreCase(key)) return Math.max(0, item.optInt("value", 0));
            }
        }
        return -1;
    }
}
