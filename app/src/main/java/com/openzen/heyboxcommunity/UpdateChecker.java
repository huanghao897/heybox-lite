package com.openzen.heyboxcommunity;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class UpdateChecker {
    interface Callback {
        void onResult(Result result);
        void onError(String message);
    }

    static final class Result {
        final boolean updateAvailable;
        final String version;
        final String releaseUrl;
        final String downloadUrl;

        Result(boolean updateAvailable, String version, String releaseUrl, String downloadUrl) {
            this.updateAvailable = updateAvailable;
            this.version = version;
            this.releaseUrl = releaseUrl;
            this.downloadUrl = downloadUrl;
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateChecker() {}

    static void check(String currentVersion, Callback callback) {
        EXECUTOR.execute(() -> request(currentVersion, callback));
    }

    private static void request(String currentVersion, Callback callback) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://api.github.com/repos/huanghao897/heybox-lite/releases/latest");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "heybox-Lite/" + currentVersion);
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String text = read(stream);
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("GitHub HTTP " + status);
            }
            JSONObject release = new JSONObject(text);
            String tag = release.optString("tag_name");
            String latest = normalize(tag);
            String releaseUrl = release.optString("html_url");
            String downloadUrl = "";
            JSONArray assets = release.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null) continue;
                    String name = asset.optString("name").toLowerCase();
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url");
                        break;
                    }
                }
            }
            Result result = new Result(compare(latest, normalize(currentVersion)) > 0,
                    latest, releaseUrl, downloadUrl);
            MAIN.post(() -> callback.onResult(result));
        } catch (Exception error) {
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            MAIN.post(() -> callback.onError(message));
        } finally {
            if (connection != null) connection.disconnect();
        }
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

    private static String normalize(String version) {
        if (version == null) return "0";
        String value = version.trim();
        if (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);
        int dash = value.indexOf('-');
        return dash < 0 ? value : value.substring(0, dash);
    }

    private static int compare(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int count = Math.max(a.length, b.length);
        for (int i = 0; i < count; i++) {
            int av = i < a.length ? number(a[i]) : 0;
            int bv = i < b.length ? number(b[i]) : 0;
            if (av != bv) return av < bv ? -1 : 1;
        }
        return 0;
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
