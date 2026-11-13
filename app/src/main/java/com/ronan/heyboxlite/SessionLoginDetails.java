package com.ronan.heyboxlite;

import android.content.SharedPreferences;

import org.json.JSONObject;

/** Login metadata is committed only after the caller has accepted its cookie. */
final class SessionLoginDetails {
    private SessionLoginDetails() {}

    static void save(SharedPreferences prefs, JSONObject result, String cookie, long generation) {
        String id = result.optString("heyboxid",
                result.optString(SecureStrings.userid(), result.optString(SecureStrings.heyboxId())));
        JSONObject account = result.optJSONObject("account_detail");
        String name = result.optString("nickname", result.optString("username"));
        String avatar = result.optString("avatar");
        if (account != null) {
            if (id.isEmpty()) id = account.optString(
                    SecureStrings.userid(), account.optString("heyboxid"));
            if (name.isEmpty()) name = account.optString("username", account.optString("nickname"));
            if (avatar.isEmpty()) avatar = account.optString("avatar");
        }
        if (id.isEmpty()) id = SessionCookieCodec.userId(cookie);
        SharedPreferences.Editor editor = prefs.edit()
                .putLong(SessionCookies.GENERATION_KEY, generation + 1L);
        if (!id.isEmpty()) editor.putString(SecureStrings.userId(), id);
        if (!name.isEmpty()) editor.putString("user_name", name);
        if (!avatar.isEmpty()) editor.putString("avatar", avatar);
        editor.apply();
    }
}
