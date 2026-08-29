package com.ronan.heyboxlite;

import java.util.Locale;

final class RichLinkClassifier {
    private RichLinkClassifier() {}

    static boolean isContentLink(String attributes) {
        String value = attributes == null ? "" : attributes.toLowerCase(Locale.ROOT);
        return value.contains("data-link-type") || value.contains("data-game-id");
    }
}
