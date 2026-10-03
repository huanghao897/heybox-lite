package com.ronan.heyboxlite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses and normalizes HeyBox cookies without owning session persistence. */
final class SessionCookieCodec {
    private SessionCookieCodec() {
    }

    static Map<String, String> parse(String cookie) {
        Map<String, String> values = new LinkedHashMap<>();
        if (cookie == null || cookie.isEmpty()) return values;
        String[] parts = cookie.split(";");
        for (String part : parts) {
            int equals = part.indexOf('=');
            if (equals <= 0) continue;
            String name = part.substring(0, equals).trim();
            String value = part.substring(equals + 1).trim();
            if (!name.isEmpty() && !value.isEmpty()) values.put(name, value);
        }
        return values;
    }

    static String normalize(String cookie) {
        Map<String, String> values = parse(cookie);
        normalizeAuth(values);
        return join(values);
    }

    static String value(String cookie, String key) {
        if (cookie == null || cookie.isEmpty() || key == null || key.isEmpty()) return "";
        String value = parse(cookie).get(key);
        return value == null ? "" : value;
    }

    static String userId(String cookie) {
        Map<String, String> values = parse(cookie);
        String value = first(values, SecureStrings.userHeyboxId(),
                SecureStrings.xHeyboxId(), "user_" + SecureStrings.heyboxId());
        if (value.isEmpty()) {
            value = first(values, SecureStrings.heyboxId(), SecureStrings.userid(),
                    SecureStrings.userId(), "heyboxid");
        }
        return value;
    }

    static String first(Map<String, String> values, String... keys) {
        if (values == null || keys == null) return "";
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            String value = values.get(key);
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }

    static String join(Map<String, String> values) {
        StringBuilder merged = new StringBuilder();
        if (values == null) return "";
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty()
                    || entry.getValue() == null || entry.getValue().isEmpty()) continue;
            if (merged.length() > 0) merged.append("; ");
            merged.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return merged.toString();
    }

    static void normalizeAuth(Map<String, String> values) {
        if (values == null || values.isEmpty()) return;
        String pkey = first(values, pkeyKey(), SecureStrings.userPkey(), SecureStrings.xPkey());
        putAliases(values, pkey, pkeyKey(), SecureStrings.userPkey(), SecureStrings.xPkey());

        String id = first(values, SecureStrings.userHeyboxId(), SecureStrings.xHeyboxId(),
                SecureStrings.heyboxId(), SecureStrings.userid(), SecureStrings.userId(),
                "heyboxid");
        putAliases(values, id, SecureStrings.userHeyboxId(), SecureStrings.xHeyboxId(),
                SecureStrings.heyboxId());
    }

    static String keysForLog(String cookie) {
        Map<String, String> values = parse(cookie);
        StringBuilder result = new StringBuilder();
        for (String key : values.keySet()) {
            if (result.length() > 0) result.append(',');
            result.append(key);
        }
        return result.length() == 0 ? "none" : result.toString();
    }

    static String authKeysForLog(String cookie) {
        Map<String, String> values = parse(cookie);
        StringBuilder result = new StringBuilder();
        appendKey(result, values, SecureStrings.userPkey());
        appendKey(result, values, SecureStrings.xPkey());
        appendKey(result, values, SecureStrings.userHeyboxId());
        appendKey(result, values, SecureStrings.xHeyboxId());
        appendKey(result, values, SecureStrings.xXhhTokenId());
        appendKey(result, values, SecureStrings.heyboxId());
        appendKey(result, values, SecureStrings.userid());
        return result.length() == 0 ? "none" : result.toString();
    }

    static void appendPart(List<String> parts, String key, String value) {
        if (parts == null || key == null || key.isEmpty()
                || value == null || value.isEmpty()) return;
        parts.add(key + "=" + value);
    }

    static void appendRawPart(List<String> parts, String value) {
        if (parts == null || value == null) return;
        String clean = value.trim();
        if (!clean.isEmpty()) parts.add(clean);
    }

    static void appendRest(List<String> parts, Map<String, String> values) {
        if (parts == null || values == null || values.isEmpty()) return;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isEmpty() || value == null || value.isEmpty()) continue;
            if (isAuthKey(key)) continue;
            appendPart(parts, key, value);
        }
    }

    static String joinParts(List<String> parts) {
        StringBuilder cookie = new StringBuilder();
        if (parts == null) return "";
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            if (cookie.length() > 0) cookie.append(';');
            cookie.append(part.trim());
        }
        return cookie.toString();
    }

    private static void appendKey(StringBuilder result, Map<String, String> values, String key) {
        if (values == null || key == null || key.isEmpty() || !values.containsKey(key)) return;
        if (result.length() > 0) result.append(',');
        result.append(key);
    }

    static boolean isAuthKey(String key) {
        return pkeyKey().equals(key)
                || SecureStrings.userPkey().equals(key)
                || SecureStrings.xPkey().equals(key)
                || SecureStrings.userHeyboxId().equals(key)
                || SecureStrings.xHeyboxId().equals(key)
                || SecureStrings.xXhhTokenId().equals(key)
                || SecureStrings.heyboxId().equals(key)
                || SecureStrings.userid().equals(key)
                || SecureStrings.userId().equals(key)
                || "heyboxid".equals(key)
                || ("user_" + SecureStrings.heyboxId()).equals(key);
    }

    private static void putAliases(Map<String, String> values, String value, String... keys) {
        if (value == null || value.isEmpty()) return;
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            String existing = values.get(key);
            if (existing == null || existing.isEmpty()) values.put(key, value);
        }
    }

    private static String pkeyKey() {
        return com.max.xiaoheihe.utils.p0.M();
    }
}
