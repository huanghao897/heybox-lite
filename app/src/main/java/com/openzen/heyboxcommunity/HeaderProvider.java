package com.openzen.heyboxcommunity;

import java.net.HttpURLConnection;

final class HeaderProvider {
    private HeaderProvider() {}

    static void apply(HttpURLConnection connection, SessionStore session) {
        applyPublic(connection);
        String cookie = session.getCookie();
        if (!cookie.isEmpty()) connection.setRequestProperty(SecureStrings.cookieHeader(), cookie);
    }

    static void applyPublic(HttpURLConnection connection) {
        String webBase = EndpointProvider.baseUrl().replace("api.", "www.");
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        connection.setRequestProperty("Origin", webBase);
        connection.setRequestProperty("Referer", webBase + "/");
        connection.setRequestProperty("User-Agent", userAgent());
    }

    private static String userAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0";
    }
}
