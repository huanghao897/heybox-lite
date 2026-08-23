package com.ronan.heyboxlite;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

final class OfficialEmojiFallback {
    private static final String TAG = "OfficialEmojiFallback";
    private static final String ASSET_NAME = "official_emoji_fallback.json";
    private static volatile Map<String, String> urls = Collections.emptyMap();

    private OfficialEmojiFallback() {}

    static void load(Context context) {
        if (!urls.isEmpty()) return;
        try (InputStream input = context.getAssets().open(ASSET_NAME)) {
            loadJson(read(input));
        } catch (IOException | JSONException error) {
            Log.w(TAG, "Unable to load bundled emoji catalog", error);
        }
    }

    static void loadJson(String source) throws JSONException {
        JSONObject root = new JSONObject(source);
        String baseUrl = root.getString("base_url");
        JSONObject entries = root.getJSONObject("entries");
        Map<String, String> parsed = new HashMap<>();
        Iterator<String> keys = entries.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            put(parsed, key, baseUrl + entries.getString(key));
        }
        urls = Collections.unmodifiableMap(parsed);
    }

    static String url(String code) {
        String clean = normalize(code);
        String value = urls.get(clean);
        if (value != null) return value;
        int underscore = clean.indexOf('_');
        if (underscore > 0 && underscore < clean.length() - 1) {
            return urls.get(clean.substring(underscore + 1));
        }
        return null;
    }

    private static String normalize(String code) {
        String clean = code == null ? "" : code.trim();
        while (clean.startsWith("[")) clean = clean.substring(1);
        while (clean.endsWith("]")) clean = clean.substring(0, clean.length() - 1);
        return clean;
    }

    private static void put(Map<String, String> target, String key, String url) {
        target.put(key, url);
        int underscore = key.indexOf('_');
        if (underscore > 0 && underscore < key.length() - 1) {
            target.put(key.substring(underscore + 1), url);
        }
    }

    private static String read(InputStream input) throws IOException {
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) value.append(line);
        }
        return value.toString();
    }
}
