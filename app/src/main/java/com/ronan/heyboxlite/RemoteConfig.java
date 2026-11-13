package com.ronan.heyboxlite;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RemoteConfig {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Feature> FEATURES = new HashMap<>();
    private static volatile AccessStatus accessStatus = new AccessStatus(false, "");
    private RemoteConfig() {}

    static void load(SessionStore session, Runnable complete) {
        EXECUTOR.execute(() -> {
            boolean loaded = request(session);
            if (loaded && complete != null) MAIN.post(complete);
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

    static AccessStatus accessStatus() {
        return accessStatus;
    }

    private static boolean request(SessionStore session) {
        return request(session, url -> (HttpURLConnection) url.openConnection());
    }

    static boolean request(SessionStore session, ApiClient.ConnectionFactory connections) {
        HttpURLConnection connection = null;
        long startedAt = SystemClock.elapsedRealtime();
        try {
            String endpoint = UpdateChecker.requireTrustedUrl(BuildConfig.CONFIG_API_URL);
            String separator = endpoint.contains("?") ? "&" : "?";
            URL url = new URL(endpoint + separator + "versionCode=" + BuildConfig.VERSION_CODE);
            connection = connections.open(url);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(4000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "heybox-Lite/" + BuildConfig.VERSION_NAME);
            new DeviceAuthorizationStore(session.appContext()).apply(connection, session);
            int status = connection.getResponseCode();
            if (status / 100 != 2) return false;
            JSONObject payload = new JSONObject(MetadataResponse.read(connection, status, startedAt));
            accessStatus = AccessStatus.from(payload);
            JSONObject values = payload.optJSONObject("features");
            if (values == null) return true;
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
            return true;
        } catch (Exception ignored) {
            return false;
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

    private static final class Feature {
        final boolean enabled;
        final String message;

        Feature(boolean enabled, String message) {
            this.enabled = enabled;
            this.message = message;
        }
    }
}
