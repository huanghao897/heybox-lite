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
    private static final Map<String, String> DARK_URLS = new ConcurrentHashMap<>();
    private static boolean loading;
    private static boolean catalogLoaded;
    private static final List<Runnable> READY = new ArrayList<>();

    private EmojiStore() {}

    static String url(String code) {
        return url(code, false);
    }

    static String url(String code, boolean darkMode) {
        String value = lookup(code, darkMode);
        return value == null ? "" : value;
    }

    static void register(String code, String lightUrl, String darkUrl) {
        if (code == null || code.isEmpty()) return;
        String light = normalizeUrl(lightUrl);
        String dark = normalizeUrl(darkUrl);
        putVariants(code, light, dark);
    }

    static boolean isLoaded() {
        return catalogLoaded;
    }

    static void whenReady(Runnable ready) {
        if (catalogLoaded) ready.run();
        else if (!loading) ready.run();
        else READY.add(ready);
    }

    static void load(ApiClient api, Runnable ready) {
        if (catalogLoaded) {
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
                parse(result == null ? body : result);
                catalogLoaded = !URLS.isEmpty() || !DARK_URLS.isEmpty();
                notifyReady();
            }

            @Override public void onError(String message) {
                loading = false;
                notifyReady();
            }
        });
    }

    private static void notifyReady() {
        List<Runnable> callbacks = new ArrayList<>(READY);
        READY.clear();
        for (Runnable callback : callbacks) callback.run();
    }

    private static void parse(JSONObject root) {
        if (root == null) return;
        Map<String, String> found = new HashMap<>();
        scan(root, "", found, 0);
        URLS.putAll(found);
    }

    private static void parse(JSONArray groups, Map<String, String> found) {
        if (groups == null) return;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group == null) continue;
            String groupCode = first(group.optString("group_code"), group.optString("group_name"));
            JSONArray emojis = group.optJSONArray("emojis");
            if (emojis == null) emojis = group.optJSONArray("emoji_list");
            if (emojis == null) continue;
            parseEmojiArray(emojis, groupCode, found);
        }
    }

    private static void scan(Object node, String inheritedGroup, Map<String, String> found,
                             int depth) {
        if (node == null || depth > 6) return;
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) scan(array.opt(i), inheritedGroup, found, depth + 1);
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject object = (JSONObject) node;
        JSONArray groups = object.optJSONArray("emoji_groups");
        if (groups == null) groups = object.optJSONArray("groups");
        if (groups != null) parse(groups, found);

        String groupCode = first(object.optString("group_code"),
                object.optString("group_name"), inheritedGroup);
        JSONArray emojis = object.optJSONArray("emojis");
        if (emojis == null) emojis = object.optJSONArray("emoji_list");
        if (emojis != null) {
            parseEmojiArray(emojis, groupCode, found);
        } else {
            parseEmojiObject(object, groupCode, found);
        }

        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            Object child = object.opt(keys.next());
            if (child instanceof JSONObject || child instanceof JSONArray) {
                scan(child, groupCode, found, depth + 1);
            }
        }
    }

    private static void parseEmojiArray(JSONArray emojis, String groupCode,
                                        Map<String, String> found) {
        for (int j = 0; j < emojis.length(); j++) {
            JSONObject emoji = emojis.optJSONObject(j);
            if (emoji != null) parseEmojiObject(emoji, groupCode, found);
        }
    }

    private static void parseEmojiObject(JSONObject emoji, String groupCode,
                                         Map<String, String> found) {
        String code = first(emoji.optString("code"), emoji.optString("name"),
                emoji.optString("text"));
        String url = first(emoji.optString("img"), emoji.optString("url"),
                emoji.optString("image"), emoji.optString("icon"),
                emoji.optString("icon_url"));
        String darkUrl = first(emoji.optString("dark_img"),
                emoji.optString("dark_url"), emoji.optString("image_dark"),
                emoji.optString("icon_dark_url"), emoji.optString("icon_dark"));
        if (code.isEmpty() || url.isEmpty()) return;
        String clean = normalizeCode(code);
        String normalUrl = normalizeUrl(url);
        String normalDark = normalizeUrl(darkUrl);
        putVariants(found, clean, normalUrl, normalDark);
        if (!groupCode.isEmpty() && !clean.startsWith(groupCode + "_")) {
            putVariants(found, normalizeCode(groupCode) + "_" + clean, normalUrl, normalDark);
        }
    }

    private static String lookup(String code, boolean darkMode) {
        String clean = normalizeCode(code);
        String fallback = OfficialEmojiFallback.url(clean);
        if (fallback != null && isOfficialToken(clean)) return fallback;
        String direct = directLookup(code, darkMode);
        if (direct != null) return direct;
        String[] variants = variants(clean);
        for (String variant : variants) {
            String value = darkMode ? DARK_URLS.get(variant) : URLS.get(variant);
            if (value != null) return value;
        }
        for (String variant : variants) {
            String value = URLS.get(variant);
            if (value != null) return value;
        }
        return fallback;
    }

    private static boolean isOfficialToken(String clean) {
        if (clean == null) return false;
        return clean.startsWith("cube_") || clean.startsWith("heygirl_")
                || OfficialEmojiFallback.url(clean) != null;
    }

    private static String directLookup(String code, boolean darkMode) {
        if (code == null || code.isEmpty()) return null;
        String value = darkMode ? DARK_URLS.get(code) : URLS.get(code);
        if (value != null) return value;
        value = URLS.get(code);
        if (value != null) return value;
        if (!code.startsWith("[")) {
            String bracketed = "[" + code + "]";
            value = darkMode ? DARK_URLS.get(bracketed) : URLS.get(bracketed);
            if (value != null) return value;
            return URLS.get(bracketed);
        }
        return null;
    }

    private static void putVariants(String code, String lightUrl, String darkUrl) {
        String clean = normalizeCode(code);
        for (String variant : variants(clean)) {
            if (!lightUrl.isEmpty()) URLS.put(variant, lightUrl);
            if (!darkUrl.isEmpty()) DARK_URLS.put(variant, darkUrl);
        }
    }

    private static void putVariants(Map<String, String> found, String code,
                                    String lightUrl, String darkUrl) {
        String clean = normalizeCode(code);
        for (String variant : variants(clean)) {
            if (!lightUrl.isEmpty()) found.put(variant, lightUrl);
            if (!darkUrl.isEmpty()) DARK_URLS.put(variant, darkUrl);
        }
    }

    private static String[] variants(String clean) {
        if (clean == null) clean = "";
        int underscore = clean.indexOf('_');
        if (underscore > 0 && underscore < clean.length() - 1) {
            String suffix = clean.substring(underscore + 1);
            return new String[] {clean, "[" + clean + "]", suffix, "[" + suffix + "]"};
        }
        String cube = "cube_" + clean;
        String heygirl = "heygirl_" + clean;
        return new String[] {
                clean, "[" + clean + "]",
                cube, "[" + cube + "]",
                heygirl, "[" + heygirl + "]"
        };
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }

    private static String normalizeCode(String value) {
        String clean = value == null ? "" : value.trim();
        while (clean.startsWith("[")) clean = clean.substring(1);
        while (clean.endsWith("]")) clean = clean.substring(0, clean.length() - 1);
        return clean;
    }

    private static String normalizeUrl(String value) {
        if (value == null) return "";
        String url = value.replace("\\/", "/").trim();
        if (url.startsWith("//")) url = "https:" + url;
        if (url.regionMatches(true, 0, "http:", 0, 5)) url = "https:" + url.substring(5);
        return url;
    }
}
