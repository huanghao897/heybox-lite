package com.ronan.heyboxlite;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AnnouncementChecker {
    interface Callback {
        void onResult(List<Item> items);
        void onError(String message);
    }

    static final class Item {
        final String id;
        final String title;
        final String content;
        final String level;
        final String updatedAt;
        final boolean enabled;

        Item(String id, String title, String content, String level,
                String updatedAt, boolean enabled) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.level = level;
            this.updatedAt = updatedAt;
            this.enabled = enabled;
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AnnouncementChecker() {}

    static void load(Callback callback) {
        EXECUTOR.execute(() -> request(callback));
    }

    private static void request(Callback callback) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(UpdateChecker.requireTrustedUrl(BuildConfig.ANNOUNCEMENT_API_URL));
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "heybox-Lite/" + BuildConfig.VERSION_NAME);
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String text = read(stream);
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Announcement server HTTP " + status);
            }
            JSONObject payload = new JSONObject(text);
            List<Item> items = parseItems(payload);
            MAIN.post(() -> callback.onResult(items));
        } catch (Exception error) {
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            String lower = message.toLowerCase(Locale.US);
            if (lower.contains("ssl") || lower.contains("handshake")) {
                message = "当前系统 TLS 过旧或网络不兼容。";
            }
            final String finalMessage = message;
            MAIN.post(() -> callback.onError(finalMessage));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static List<Item> parseItems(JSONObject payload) {
        List<Item> items = new ArrayList<>();
        JSONArray array = payload.optJSONArray("items");
        if (array == null) array = payload.optJSONArray("announcements");
        if (array == null) array = payload.optJSONArray("list");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                Item item = parseItem(object, i);
                if (!item.content.isEmpty() || !item.title.isEmpty()) items.add(item);
            }
            return items;
        }
        Item item = parseItem(payload, 0);
        if (!item.content.isEmpty() || !item.title.isEmpty()) items.add(item);
        return items;
    }

    private static Item parseItem(JSONObject object, int index) {
        String title = firstNonEmpty(object.optString("title"),
                object.optString("name"), "公告");
        String content = firstNonEmpty(object.optString("content"),
                object.optString("message"), object.optString("body"));
        String updatedAt = firstNonEmpty(object.optString("updatedAt"),
                object.optString("updated_at"), object.optString("time"));
        String id = firstNonEmpty(object.optString("id"), updatedAt, title + "-" + index);
        String level = firstNonEmpty(object.optString("level"), "normal");
        boolean enabled = object.optBoolean("enabled", true);
        return new Item(id, title, content, level, updatedAt, enabled);
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, "UTF-8"))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0) result.append(buffer, 0, count);
        }
        return result.toString();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }
}
