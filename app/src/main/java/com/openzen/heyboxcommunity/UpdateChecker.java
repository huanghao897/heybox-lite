package com.openzen.heyboxcommunity;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URL;
import java.util.Locale;
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
        final String title;
        final String notes;
        final String releaseUrl;
        final String downloadUrl;

        Result(boolean updateAvailable, String version, String title, String notes,
                String releaseUrl, String downloadUrl) {
            this.updateAvailable = updateAvailable;
            this.version = version;
            this.title = title;
            this.notes = notes;
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
            String endpoint = buildEndpoint(requireTrustedUrl(BuildConfig.UPDATE_API_URL));
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "heybox-Lite/" + currentVersion);
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String text = read(stream);
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Update server HTTP " + status);
            }

            JSONObject payload = new JSONObject(text);
            String latest = latestVersion(payload);
            String title = firstNonEmpty(payload.optString("title"), payload.optString("name"));
            String notes = cleanNotes(notes(payload));
            String releaseUrl = trustedUrlOrEmpty(firstNonEmpty(payload.optString("releaseUrl"),
                    payload.optString("html_url"), BuildConfig.UPDATE_FALLBACK_URL));
            String downloadUrl = trustedUrlOrEmpty(firstNonEmpty(payload.optString("downloadUrl"),
                    payload.optString("apkUrl"), payload.optString("latestApkUrl"),
                    githubAssetUrl(payload)));
            if (releaseUrl.isEmpty()) releaseUrl = requireTrustedUrl(BuildConfig.UPDATE_FALLBACK_URL);

            Result result = new Result(isUpdateAvailable(payload, latest, currentVersion),
                    latest, title, notes, releaseUrl, downloadUrl);
            if (result.updateAvailable && downloadUrl.isEmpty()) {
                throw new IllegalStateException("未找到匹配的 APK 资源，可打开备用地址手动下载。");
            }
            MAIN.post(() -> callback.onResult(result));
        } catch (Exception error) {
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            String lowerMessage = message.toLowerCase(Locale.US);
            if (lowerMessage.contains("ssl")
                    || lowerMessage.contains("handshake")) {
                message = "当前系统 TLS 过旧或网络不兼容，请打开备用地址检查："
                        + BuildConfig.UPDATE_FALLBACK_URL;
            }
            final String finalMessage = message;
            MAIN.post(() -> callback.onError(finalMessage));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String buildEndpoint(String endpoint) throws Exception {
        String separator = endpoint.contains("?") ? "&" : "?";
        return endpoint + separator
                + "versionCode=" + BuildConfig.VERSION_CODE
                + "&currentVersionCode=" + BuildConfig.VERSION_CODE
                + "&versionName=" + URLEncoder.encode(BuildConfig.VERSION_NAME, "UTF-8")
                + "&currentVersion=" + URLEncoder.encode(BuildConfig.VERSION_NAME, "UTF-8");
    }

    private static boolean isUpdateAvailable(JSONObject payload, String latest,
                                             String currentVersion) {
        if (payload.has("hasUpdate")) return payload.optBoolean("hasUpdate", false);
        if (payload.has("updateAvailable")) return payload.optBoolean("updateAvailable", false);
        int latestCode = payload.optInt("versionCode", 0);
        if (latestCode <= 0) latestCode = payload.optInt("version_code", 0);
        if (latestCode > 0) return latestCode > BuildConfig.VERSION_CODE;
        return compare(latest, normalize(currentVersion)) > 0;
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

    private static String latestVersion(JSONObject payload) {
        return normalize(firstNonEmpty(payload.optString("versionName"),
                payload.optString("version"), payload.optString("tag_name")));
    }

    private static String notes(JSONObject payload) {
        String changelogText = payload.optString("changelog");
        if (changelogText != null && !changelogText.trim().isEmpty()
                && !"null".equalsIgnoreCase(changelogText.trim())) {
            return changelogText;
        }
        JSONArray changelog = payload.optJSONArray("changelog");
        if (changelog != null && changelog.length() > 0) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < changelog.length(); i++) {
                String line = changelog.optString(i).trim();
                if (line.isEmpty()) continue;
                if (builder.length() > 0) builder.append('\n');
                builder.append(line);
            }
            if (builder.length() > 0) return builder.toString();
        }
        return firstNonEmpty(payload.optString("notes"), payload.optString("body"),
                payload.optString("content"), payload.optString("description"));
    }

    private static String githubAssetUrl(JSONObject payload) {
        JSONArray assets = payload.optJSONArray("assets");
        if (assets == null) return "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name").toLowerCase(Locale.US);
            if (name.endsWith(".apk")) {
                return asset.optString("browser_download_url");
            }
        }
        return "";
    }

    private static String cleanNotes(String notes) {
        if (notes == null) return "";
        String value = notes.replace("\r\n", "\n").replace('\r', '\n').trim();
        while (value.contains("\n\n\n")) value = value.replace("\n\n\n", "\n\n");
        return value;
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

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    static String trustedUrlOrEmpty(String value) {
        try {
            return requireTrustedUrl(value);
        } catch (Exception ignored) {
            return "";
        }
    }

    static String requireTrustedUrl(String value) {
        if (value == null) throw new IllegalArgumentException("empty url");
        String clean = value.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("empty url");
        try {
            URI uri = new URI(clean);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null) {
                throw new IllegalArgumentException("untrusted update url");
            }
            String normalizedHost = host.toLowerCase(Locale.US);
            if ("https".equalsIgnoreCase(scheme) && isAllowedUpdateHost(normalizedHost)) {
                return clean;
            }
            if ("http".equalsIgnoreCase(scheme) && isAllowedCleartextUpdateHost(normalizedHost)) {
                return clean;
            }
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception ignored) {
            // Fall through to the common error below.
        }
        throw new IllegalArgumentException("untrusted update url");
    }

    private static boolean isAllowedUpdateHost(String host) {
        return "github.com".equals(host)
                || "api.github.com".equals(host)
                || "raw.githubusercontent.com".equals(host)
                || "objects.githubusercontent.com".equals(host)
                || "github-releases.githubusercontent.com".equals(host)
                || "8.138.134.236".equals(host)
                || "103.236.54.97".equals(host);
    }

    private static boolean isAllowedCleartextUpdateHost(String host) {
        return "8.138.134.236".equals(host)
                || "103.236.54.97".equals(host);
    }
}
