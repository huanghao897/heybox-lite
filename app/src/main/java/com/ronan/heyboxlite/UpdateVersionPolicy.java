package com.ronan.heyboxlite;

import org.json.JSONObject;

/** Pure version and release-metadata rules shared by the update checker and JVM tests. */
final class UpdateVersionPolicy {
    private UpdateVersionPolicy() {}

    static boolean isUpdateAvailable(JSONObject payload, String latest,
                                     String currentVersion, int currentCode) {
        int latestCode = firstPositiveInt(payload, "versionCode", "version_code",
                "latestVersionCode", "latest_version_code");
        if (latestCode > currentCode) return true;
        if (latestCode <= 0 && compare(latest, normalize(currentVersion)) > 0) return true;
        if (payload != null && payload.has("hasUpdate")) {
            return payload.optBoolean("hasUpdate", false);
        }
        if (payload != null && payload.has("updateAvailable")) {
            return payload.optBoolean("updateAvailable", false);
        }
        return false;
    }

    static String latestVersion(JSONObject payload) {
        if (payload == null) return "0";
        return normalize(firstNonEmpty(payload.optString("versionName"),
                payload.optString("version_name"), payload.optString("latestVersion"),
                payload.optString("latest_version"), payload.optString("version"),
                payload.optString("tag_name")));
    }

    static int firstPositiveInt(JSONObject payload, String... keys) {
        if (payload == null || keys == null) return 0;
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            int value = payload.optInt(key, 0);
            if (value > 0) return value;
        }
        return 0;
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
}
