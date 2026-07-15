package com.ronan.heyboxlite;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RemoteConfig {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Feature> FEATURES = new HashMap<>();

    private RemoteConfig() {}

    static void load(String userId, Runnable complete) {
        EXECUTOR.execute(() -> {
            request(userId);
            if (complete != null) MAIN.post(complete);
        });
    }

    static String blockedMessage(String actionName) {
        Feature feature;
        synchronized (FEATURES) {
            feature = FEATURES.get(keyForAction(actionName));
        }
        if (feature == null || feature.enabled) return "";
        return feature.message.isEmpty() ? actionName + "暂时不可用" : feature.message;
    }

    private static void request(String userId) {
        HttpURLConnection connection = null;
        try {
            String endpoint = UpdateChecker.requireTrustedUrl(BuildConfig.CONFIG_API_URL);
            String separator = endpoint.contains("?") ? "&" : "?";
            String cleanUserId = userId == null ? "" : userId.trim();
            URL url = new URL(endpoint + separator + "versionCode=" + BuildConfig.VERSION_CODE
                    + (cleanUserId.isEmpty() ? "" : "&userId="
                    + URLEncoder.encode(cleanUserId, "UTF-8")));
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(4000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "heybox-Lite/" + BuildConfig.VERSION_NAME);
            if (connection.getResponseCode() / 100 != 2) return;
            JSONObject payload = new JSONObject(read(connection.getInputStream()));
            JSONObject values = payload.optJSONObject("features");
            if (values == null) return;
            Map<String, Feature> next = new HashMap<>();
            java.util.Iterator<String> keys = values.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject value = values.optJSONObject(key);
                if (value != null) {
                    next.put(key, new Feature(value.optBoolean("enabled", true),
                            value.optString("message", "").trim()));
                }
            }
            synchronized (FEATURES) {
                FEATURES.clear();
                FEATURES.putAll(next);
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String keyForAction(String actionName) {
        if ("点赞".equals(actionName)) return "like";
        if ("收藏".equals(actionName)) return "favorite";
        if ("关注".equals(actionName)) return "follow";
        if ("评论点赞".equals(actionName)) return "comment_like";
        if ("评论".equals(actionName) || "回复评论".equals(actionName)) return "comment";
        return actionName == null ? "" : actionName;
    }

    private static String read(InputStream stream) throws Exception {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) >= 0) text.append(buffer, 0, count);
        }
        return text.toString();
    }

    private static final class Feature {
        final boolean enabled;
        final String message;

        Feature(boolean enabled, String message) {
            this.enabled = enabled;
            this.message = message;
        }
    }
}
