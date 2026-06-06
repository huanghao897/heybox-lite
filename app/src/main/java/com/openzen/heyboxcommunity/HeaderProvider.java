package com.openzen.heyboxcommunity;

import android.os.Build;

import java.net.HttpURLConnection;

final class HeaderProvider {
    private HeaderProvider() {}

    static void apply(HttpURLConnection connection, SessionStore session) {
        applyPublic(connection);
        String cookie = session.getCookie();
        if (!cookie.isEmpty()) connection.setRequestProperty(SecureStrings.cookieHeader(), cookie);
    }

    static void applyPublic(HttpURLConnection connection) {
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Referer", EndpointProvider.baseUrl().replace("api.", "www.") + "/");
        connection.setRequestProperty("User-Agent", userAgent());
    }

    private static String userAgent() {
        String release = safe(Build.VERSION.RELEASE, "10");
        String model = safe(Build.MODEL, "Android");
        return "Mozilla/5.0 (Linux; Android " + release + "; " + model
                + ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36";
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.isEmpty()) return fallback;
        return value.replace(";", "").replace("(", "").replace(")", "");
    }
}
