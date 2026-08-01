package com.ronan.heyboxlite;

import java.net.HttpURLConnection;

final class HeaderProvider {
    private static final String OFFICIAL_REFERER = "http://api.maxjia.com/";

    private HeaderProvider() {}

    static void apply(HttpURLConnection connection, SessionStore session) {
        applyPublic(connection);
        applyCookie(connection, session.getCookie());
    }

    static void applyMobile(HttpURLConnection connection, SessionStore session) {
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        connection.setRequestProperty("User-Agent", mobileUserAgent());
        connection.setRequestProperty("X-Requested-With", "com.max.xiaoheihe");
        applyCookie(connection, session.getCookie());
    }

    static void applyOfficialMobile(HttpURLConnection connection, SessionStore session,
                                    boolean addClientKey) {
        applyOfficial(connection, session.officialMobileCookie(addClientKey));
    }

    static void applyOfficialRequest(HttpURLConnection connection, SessionStore session,
                                     boolean includeClientKeys) {
        applyOfficial(connection, session.officialBridgeCookie(includeClientKeys));
    }

    static void applyOfficialMinimalRequest(HttpURLConnection connection, SessionStore session,
                                            boolean includeClientKeys) {
        applyOfficial(connection, session.officialMinimalCookie(includeClientKeys));
    }

    static void applyOfficialMobileRawCookie(HttpURLConnection connection, SessionStore session) {
        applyOfficial(connection, session.getCookie());
    }

    private static void applyOfficial(HttpURLConnection connection, String cookie) {
        connection.setRequestProperty("Referer", OFFICIAL_REFERER);
        connection.setRequestProperty("User-Agent", mobileUserAgent());
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
