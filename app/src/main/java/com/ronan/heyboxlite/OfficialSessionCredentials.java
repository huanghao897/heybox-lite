package com.ronan.heyboxlite;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

/** Builds the official-shaped request parameters and cookie variants. */
final class OfficialSessionCredentials {
    private final SessionStore session;
    private final Context context;

    OfficialSessionCredentials(SessionStore session, Context context) {
        this.session = session;
        this.context = context;
    }

    Map<String, String> commonParams() {
        Map<String, String> result = new HashMap<>();
        String id = session.userId();
        result.put("os_type", "web");
        result.put("app", "heybox");
        result.put("client_type", "web");
        result.put("version", "999.0.4");
        result.put("web_version", "2.5");
        result.put("x_client_type", "web");
        result.put("x_app", "heybox_website");
        result.put(SecureStrings.heyboxId(), id);
        if (!id.isEmpty()) result.put(SecureStrings.userid(), id);
        result.put("x_os_type", "Windows");
        result.put("device_info", "Edge");
        result.put(SecureStrings.deviceId(), session.deviceIdentifier());
        return result;
    }

    Map<String, String> mobileCommonParams() {
        Map<String, String> result = officialMobileParams(true);
        result.put("client_type", "mobile");
        return result;
    }

    Map<String, String> officialMobileParams(boolean includeDeviceParams) {
        Map<String, String> result = new LinkedHashMap<>();
        String id = session.userId();
        String safeId = id.isEmpty() ? "-1" : id;
        result.put(SecureStrings.heyboxId(), safeId);
        if (!id.isEmpty()) {
            result.put(SecureStrings.userid(), id);
            result.put(SecureStrings.userId(), id);
        }
        if (!includeDeviceParams) return result;
        String release = Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE;
        String model = Build.MODEL == null ? "" : Build.MODEL;
        result.put("app", "heybox");
        String androidId = androidDeviceIdentifier();
        result.put(SecureStrings.deviceId(), androidId);
        result.put("imei", androidId);
        result.put("device_info", model.trim());
        result.put("os_type", "Android");
        result.put("os_version", release.trim());
        result.put("x_os_type", "Android");
        result.put("x_client_type", "mobile");
        result.put("x_app", "heybox");
        result.put("version", com.max.xiaoheihe.utils.f.B0());
        result.put("build", com.max.xiaoheihe.utils.f.buildCode());
        result.put("time_zone", TimeZone.getDefault().getID());
        result.put(SecureStrings.time(), String.valueOf(System.currentTimeMillis() / 1000L));
        result.put("channel", "heybox");
        return result;
    }

    String officialMobileCookie(boolean addClientKey) {
        return buildOfficialCookie(addClientKey, true, false);
    }

    String officialRequestCookie(boolean includeClientKeys) {
        return buildOfficialCookie(includeClientKeys, false, true);
    }

    String officialMinimalCookie(boolean includeClientKeys) {
        return buildOfficialCookie(includeClientKeys, false, false);
    }

    String officialBridgeCookie(boolean includeClientKeys) {
        try {
            String raw = session.getCookie();
            Map<String, String> values = SessionCookieCodec.parse(raw);
            com.max.xiaoheihe.utils.m0.init(session.userId(), officialPkey(values));
            com.max.xiaoheihe.utils.i.init(SessionCookieCodec.first(values,
                    SecureStrings.xXhhTokenId()));
            okhttp3.a0 request = new okhttp3.a0.a()
                    .a(SecureStrings.cookieHeader(), raw)
                    .b();
            String cookie = new com.max.xiaoheihe.router.serviceimpl.k()
                    .a(includeClientKeys, request);
            return cookie == null || cookie.trim().isEmpty()
                    ? officialRequestCookie(includeClientKeys) : cookie.trim();
        } catch (Throwable ignored) {
            return officialRequestCookie(includeClientKeys);
        }
    }

    String officialPkey() {
        return officialPkey(SessionCookieCodec.parse(session.getCookie()));
    }

    String officialXhhToken() {
        Map<String, String> values = SessionCookieCodec.parse(session.getCookie());
        return SessionCookieCodec.first(values, SecureStrings.xXhhTokenId());
    }

    String officialMobileCookieKeysForLog(boolean addClientKey) {
        return SessionCookieCodec.keysForLog(officialMobileCookie(addClientKey));
    }

    String officialRequestCookieKeysForLog(boolean includeClientKeys) {
        return SessionCookieCodec.keysForLog(officialRequestCookie(includeClientKeys));
    }

    String officialMinimalCookieKeysForLog(boolean includeClientKeys) {
        return SessionCookieCodec.keysForLog(officialMinimalCookie(includeClientKeys));
    }

    String officialBridgeCookieKeysForLog(boolean includeClientKeys) {
        return SessionCookieCodec.keysForLog(officialBridgeCookie(includeClientKeys));
    }

    private String buildOfficialCookie(boolean includeClientKeys,
                                       boolean includeRest, boolean includeRaw) {
        String raw = session.getCookie();
        Map<String, String> values = SessionCookieCodec.parse(raw);
        java.util.List<String> parts = new java.util.ArrayList<>();
        String pkey = officialPkey(values);
        SessionCookieCodec.appendPart(parts, com.max.xiaoheihe.utils.p0.M(), pkey);
        if (includeClientKeys) SessionCookieCodec.appendPart(parts, SecureStrings.xPkey(), pkey);
        SessionCookieCodec.appendPart(parts, SecureStrings.xXhhTokenId(),
                values.get(SecureStrings.xXhhTokenId()));
        if (includeRest) SessionCookieCodec.appendRest(parts, values);
        if (includeRaw) SessionCookieCodec.appendRawPart(parts, raw);
        if (includeClientKeys) {
            String id = SessionCookieCodec.first(values, SecureStrings.xHeyboxId(),
                    SecureStrings.userHeyboxId(), SecureStrings.heyboxId(),
                    SecureStrings.userid(), SecureStrings.userId(), "heyboxid");
            SessionCookieCodec.appendPart(parts, SecureStrings.xHeyboxId(), id);
        }
        return SessionCookieCodec.joinParts(parts);
    }

    private String officialPkey(Map<String, String> values) {
        return SessionCookieCodec.first(values, com.max.xiaoheihe.utils.p0.M(),
                SecureStrings.userPkey(), SecureStrings.xPkey());
    }

    private String androidDeviceIdentifier() {
        String imported = session.deviceIdentifier();
        if (imported != null && !imported.trim().isEmpty()) return imported.trim();
        try {
            String value = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            if (value != null && !value.trim().isEmpty()) return value.trim();
        } catch (Throwable ignored) {
        }
        return session.deviceIdentifier();
    }
}
