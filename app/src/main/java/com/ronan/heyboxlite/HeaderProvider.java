package com.ronan.heyboxlite;

import java.net.HttpURLConnection;

final class HeaderProvider {
    private static final String OFFICIAL_REFERER = "http://api.maxjia.com/";

    private HeaderProvider() {}

    static String captureCookie(SessionStore session, ApiClient.HeaderMode mode,
                                boolean includeClientKeys) {
        switch (mode) {
            case OFFICIAL_MOBILE:
                return session.officialMobileCookie(includeClientKeys);
            case OFFICIAL_REQUEST:
                return session.officialBridgeCookie(includeClientKeys);
            case OFFICIAL_MINIMAL:
                return session.officialMinimalCookie(includeClientKeys);
            default:
                return session.getCookie();
        }
    }

    static void apply(HttpURLConnection connection, ApiClient.HeaderMode mode, String cookie) {
        if (mode == ApiClient.HeaderMode.DEFAULT) {
            applyPublic(connection);
        } else if (mode == ApiClient.HeaderMode.MOBILE) {
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            connection.setRequestProperty("User-Agent", mobileUserAgent());
            connection.setRequestProperty("X-Requested-With", "com.max.xiaoheihe");
        } else {
            connection.setRequestProperty("Referer", OFFICIAL_REFERER);
            connection.setRequestProperty("User-Agent", mobileUserAgent());
        }
        applyCookie(connection, cookie);
    }

    private static void applyCookie(HttpURLConnection connection, String cookie) {
        if (cookie != null && !cookie.isEmpty()) {
            connection.setRequestProperty(SecureStrings.cookieHeader(), cookie);
        }
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

    private static String mobileUserAgent() {
        return "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/41.0.2272.118 Safari/537.36 ApiMaxJia/1.0";
    }
}
