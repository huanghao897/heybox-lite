package com.ronan.heyboxlite;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Locale;

final class RichTransportDecoder {
    private RichTransportDecoder() {}

    static String decode(String value) {
        return decodeEscapes(value, true);
    }

    static String decodeJson(String value) {
        return decodeEscapes(value, false);
    }

    private static String decodeEscapes(String value, boolean unescapeQuotes) {
        if (value == null || value.isEmpty()) return "";
        String decoded = value
                .replace("\\u003c", "<")
                .replace("\\u003C", "<")
                .replace("\\u003e", ">")
                .replace("\\u003E", ">")
                .replace("\\u0026", "&")
                .replace("\\/", "/");
        if (unescapeQuotes) decoded = decoded.replace("\\\"", "\"");
        for (int i = 0; i < 2; i++) {
            String lower = decoded.toLowerCase(Locale.ROOT);
            if (containsMarkup(lower)) break;
            if (!lower.contains("%3c") && !lower.contains("%5b")
                    && !lower.contains("%7b")) break;
            try {
                String next = URLDecoder.decode(decoded.replace("+", "%2B"), "UTF-8");
                if (next.equals(decoded)) break;
                decoded = next;
            } catch (IllegalArgumentException | UnsupportedEncodingException ignored) {
                break;
            }
        }
        return decoded;
    }

    private static boolean containsMarkup(String lower) {
        return lower.indexOf('<') >= 0 || lower.contains("&lt;")
                || lower.contains("&#60;") || lower.contains("&#x3c;");
    }
}
