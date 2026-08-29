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
        return "game".equals(type) || !attribute(attributes, "data-game-id").isEmpty();
    }

    static boolean isTextLink(String attributes) {
        return "text".equals(attribute(attributes, "data-link-type"));
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
