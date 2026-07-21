package com.ronan.heyboxlite;

import org.json.JSONObject;

final class PhoneLoginResponse {
    final JSONObject user;
    final String userId;
    final String pkey;
    final String userName;
    final String avatar;

    private PhoneLoginResponse(JSONObject user, String userId, String pkey,
                               String userName, String avatar) {
        this.user = user;
        this.userId = userId;
        this.pkey = pkey;
        this.userName = userName;
        this.avatar = avatar;
    }

    static PhoneLoginResponse parse(JSONObject body) {
        JSONObject user = body == null ? null : body.optJSONObject("result");
        if (user == null) {
            throw new IllegalArgumentException("\u767b\u5f55\u54cd\u5e94\u7f3a\u5c11\u8d26\u53f7\u4fe1\u606f");
        }
        JSONObject account = user.optJSONObject("account_detail");
        String userId = first(account == null ? "" : account.optString("userid"),
                user.optString("userid"), user.optString("heyboxid"),
                user.optString("heybox_id"));
        String pkey = user.optString("pkey").trim();
        if (userId.isEmpty() || pkey.isEmpty()) {
            throw new IllegalArgumentException("\u767b\u5f55\u54cd\u5e94\u7f3a\u5c11 pkey \u6216\u8d26\u53f7 ID");
        }
        String userName = first(account == null ? "" : account.optString("username"),
                account == null ? "" : account.optString("nickname"),
                user.optString("username"), user.optString("nickname"));
        String avatar = first(account == null ? "" : account.optString("avatar"),
                account == null ? "" : account.optString("avartar"),
                user.optString("avatar"), user.optString("avartar"));
        return new PhoneLoginResponse(user, userId, pkey, userName, avatar);
    }

    static int retryAfterSeconds(JSONObject body) {
        JSONObject result = body == null ? null : body.optJSONObject("result");
        int seconds = result == null ? 0 : result.optInt("remain_time", 0);
        return Math.max(30, Math.min(seconds > 0 ? seconds : 60, 180));
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }
}
