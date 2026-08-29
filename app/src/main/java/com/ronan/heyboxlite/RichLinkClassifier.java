package com.ronan.heyboxlite;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RichLinkClassifier {
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "(?is)([a-z0-9_-]+)\\s*=\\s*(['\"])(.*?)\\2");

    private RichLinkClassifier() {}

    static boolean isContentLink(String attributes) {
        return isGameLink(attributes) || isTextLink(attributes);
    }

    static boolean isGameLink(String attributes) {
        String type = attribute(attributes, "data-link-type");
        if ("game".equals(type) || !attribute(attributes, "data-game-id").isEmpty()) {
            return true;
        }
        return isGameHref(attribute(attributes, "href"))
                || isGameHref(attribute(attributes, "data-href"));
    }

    static boolean isTextLink(String attributes) {
        return "text".equals(attribute(attributes, "data-link-type"));
    }

    static boolean isGameHref(String href) {
        if (href == null || href.isEmpty()) return false;
        String value = href.trim().toLowerCase(Locale.ROOT);
        if (value.contains("opengamedetail") || value.contains("open_game_detail")) {
            return true;
        }
        boolean heyboxLink = value.startsWith("heybox://")
                || value.startsWith("xhh://");
        return heyboxLink && value.contains("app_id") && value.contains("game_type");
    }

    private static String attribute(String attributes, String expected) {
        Matcher matcher = ATTRIBUTE.matcher(attributes == null ? "" : attributes);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT).replace('_', '-');
            if (expected.equals(name)) return matcher.group(3).trim().toLowerCase(Locale.ROOT);
        }
        return "";
    }
}
