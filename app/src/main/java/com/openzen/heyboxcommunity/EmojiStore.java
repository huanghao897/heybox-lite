package com.openzen.heyboxcommunity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class EmojiStore {
    private static final Map<String, String> URLS = new ConcurrentHashMap<>();
    private static boolean loading;
    private static boolean loaded;
    private static final List<Runnable> READY = new ArrayList<>();

    private EmojiStore() {}

    static String url(String code) {
        String value = URLS.get(code);
        if (value != null) return value;
        if (!code.startsWith("[")) value = URLS.get("[" + code + "]");
        return value == null ? "" : value;
    }

    static boolean isLoaded() {
        return loaded;
    }

    static void whenReady(Runnable ready) {
        if (loaded) ready.run();
        else READY.add(ready);
    }

    static void load(ApiClient api, Runnable ready) {
        if (loaded) {
            ready.run();
            return;
        }
        READY.add(ready);
        if (loading) return;
        loading = true;
        api.get(EndpointProvider.emojis(), Collections.emptyMap(), new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                loading = false;
                JSONObject result = body.optJSONObject("result");
                JSONArray groups = result == null ? null : result.optJSONArray("emoji_groups");
                if (groups == null && result != null) groups = result.optJSONArray("groups");
                parse(groups);
                loaded = !URLS.isEmpty();
                notifyReady();
            }

            @Override public void onError(String message) {
                loading = false;
            }
        });
    }

    private static void notifyReady() {
        List<Runnable> callbacks = new ArrayList<>(READY);
        READY.clear();
        for (Runnable callback : callbacks) callback.run();
    }

    private static void parse(JSONArray groups) {
        if (groups == null) return;
        Map<String, String> found = new HashMap<>();
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group == null) continue;
            String groupCode = group.optString("group_code", group.optString("group_name"));
            JSONArray emojis = group.optJSONArray("emojis");
            if (emojis == null) emojis = group.optJSONArray("emoji_list");
            if (emojis == null) continue;
            for (int j = 0; j < emojis.length(); j++) {
                JSONObject emoji = emojis.optJSONObject(j);
                if (emoji == null) continue;
                String code = first(emoji.optString("code"), emoji.optString("name"));
                String url = first(emoji.optString("img"), emoji.optString("url"),
                        emoji.optString("image"));
                if (!code.isEmpty() && !url.isEmpty()) {
                    found.put(code, url);
                    if (!code.startsWith("[")) found.put("[" + code + "]", url);
                    if (!groupCode.isEmpty()) {
                        found.put("[" + groupCode + "_" + code + "]", url);
                    }
                }
            }
        }
        URLS.putAll(found);
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }
}
