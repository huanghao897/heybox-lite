package com.ronan.heyboxlite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RichGameLinkMarkup {
    private static final char START = '\uE100';
    private static final char END = '\uE101';
    private static final char OBJECT = '\uFFFC';
    private static final char ZERO_WIDTH_SPACE = '\u200B';
    private static final char META = '\uE102';
    private static final char META_FIELD = '\uE103';
    private static final char META_END = '\uE104';
    private static final char TEXT_META = '\uE105';
    private static final Pattern ANCHOR = Pattern.compile(
            "(?is)<a\\b([^>]*)>(.*?)</a\\s*>");
    private static final Pattern CONTENT_FRAGMENT = Pattern.compile(
            "(?is)(?:<a\\s*)?data-link-type\\s*=\\s*(['\"])(game|text)\\1"
                    + "[^>]{0,512}>(.*?)(?:</a\\s*>|$)");
    private static final Pattern HREF_FRAGMENT = Pattern.compile(
            "(?is)(?:<a\\s*)?href\\s*=\\s*(['\"])(.*?)\\1([^>]{0,512})>"
                    + "(.*?)(?:</a\\s*>|$)");

    static final class Link {
        final int start;
        final int end;
        final RichLinkClassifier.GameLinkInfo game;
        final boolean textLink;
        final String href;
        final String lightIcon;
        final String darkIcon;

        Link(int start, int end, MarkerContent content) {
            this.start = start;
            this.end = end;
            this.game = content.game;
            this.textLink = content.textLink;
            this.href = content.href;
            this.lightIcon = content.lightIcon;
            this.darkIcon = content.darkIcon;
        }

        String iconUrl(boolean darkMode) {
            return darkMode && !darkIcon.isEmpty() ? darkIcon : lightIcon;
        }
    }

    static final class Parsed {
        final String text;
        final List<Link> links;

        Parsed(String text, List<Link> links) {
            this.text = text;
            this.links = links;
        }
    }

    private RichGameLinkMarkup() {}

    static boolean hasMarkup(String source) {
        return source != null && source.indexOf(START) >= 0;
    }

    static String normalizeAnchors(String source) {
        if (source == null || source.isEmpty()) return "";
        Matcher matcher = ANCHOR.matcher(source);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String attributes = matcher.group(1);
            String label = matcher.group(2);
            if (RichLinkClassifier.isGameLink(attributes)) {
                matcher.appendReplacement(output, Matcher.quoteReplacement(
                        marker(label, RichLinkClassifier.gameLinkInfo(attributes))));
            } else if (RichLinkClassifier.isTextLink(attributes)) {
                matcher.appendReplacement(output, Matcher.quoteReplacement(textMarker(label, attributes)));
            }
        }
        matcher.appendTail(output);
        return normalizeHrefFragments(normalizeFragments(output.toString()));
    }

    private static String normalizeFragments(String source) {
        Matcher matcher = CONTENT_FRAGMENT.matcher(source);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String label = matcher.group(3);
            String fragment = matcher.group(0);
            int tagEnd = fragment.indexOf('>');
            String attributes = tagEnd >= 0 ? fragment.substring(0, tagEnd) : fragment;
            String replacement = "game".equalsIgnoreCase(matcher.group(2))
                    ? marker(label, RichLinkClassifier.gameLinkInfo(attributes))
                    : textMarker(label, attributes);
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String normalizeHrefFragments(String source) {
        Matcher matcher = HREF_FRAGMENT.matcher(source);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String attributes = "href=" + matcher.group(1) + matcher.group(2)
                    + matcher.group(1) + matcher.group(3);
            if (RichLinkClassifier.isGameLink(attributes)) {
                String replacement = marker(matcher.group(4),
                        RichLinkClassifier.gameLinkInfo(attributes));
                matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
            } else if (RichLinkClassifier.isTextLink(attributes)) {
                matcher.appendReplacement(output, Matcher.quoteReplacement(
                        textMarker(matcher.group(4), attributes)));
            }
        }
        matcher.appendTail(output);
        return output.toString();
    }

    static Parsed parse(String source) {
        String value = source == null ? "" : source;
        StringBuilder display = new StringBuilder(value);
        List<Link> links = new ArrayList<>();
        StringBuilder rendered = new StringBuilder(value.length());
        int cursor = 0;
        while (cursor < display.length()) {
            int start = display.indexOf(String.valueOf(START), cursor);
            if (start < 0) {
                rendered.append(display, cursor, display.length());
                break;
            }
            rendered.append(display, cursor, start);
            int end = display.indexOf(String.valueOf(END), start + 1);
            if (end < 0) {
                rendered.append(display, start, display.length());
                break;
            }
            String marker = display.substring(start + 1, end);
            MarkerContent content = markerContent(marker);
            int linkStart = rendered.length();
            rendered.append(OBJECT).append(content.label);
            links.add(new Link(linkStart, rendered.length(), content));
            cursor = end + 1;
        }
        return new Parsed(rendered.toString(), Collections.unmodifiableList(links));
    }

    static String plainText(String source) {
        if (source == null || source.isEmpty()) return "";
        return parse(source).text
                .replace(String.valueOf(OBJECT), "")
                .replace(String.valueOf(ZERO_WIDTH_SPACE), "");
    }

    private static String marker(String label, RichLinkClassifier.GameLinkInfo game) {
        StringBuilder value = new StringBuilder();
        value.append(START);
        if (game != null && game.valid()) {
            value.append(META)
                    .append(field(game.appId))
                    .append(META_FIELD).append(field(game.gameType))
                    .append(META_FIELD).append(field(game.hsrc))
                    .append(META_FIELD).append(field(game.skuId))
                    .append(META_END);
        }
        value.append(label == null ? "" : label).append(END);
        return value.toString();
    }

    private static String field(String value) {
        if (value == null) return "";
        return value.replace(String.valueOf(META_FIELD), "")
                .replace(String.valueOf(META_END), "")
                .replace(String.valueOf(START), "")
                .replace(String.valueOf(END), "");
    }

    private static String textMarker(String label, String attributes) {
        return new StringBuilder().append(START).append(TEXT_META)
                .append(encodeField(RichLinkClassifier.href(attributes)))
                .append(META_FIELD).append(encodeField(RichLinkClassifier.iconUrl(attributes, false)))
                .append(META_FIELD).append(encodeField(RichLinkClassifier.iconUrl(attributes, true)))
                .append(META_END).append(label == null ? "" : label).append(END).toString();
    }

    private static String encodeField(String value) {
        String clean = field(value);
        StringBuilder encoded = new StringBuilder(clean.length() * 4);
        for (int i = 0; i < clean.length(); i++) {
            String hex = Integer.toHexString(clean.charAt(i));
            for (int pad = hex.length(); pad < 4; pad++) encoded.append('0');
            encoded.append(hex);
        }
        return encoded.toString();
    }

    private static String decodeField(String value) {
        if (value.length() % 4 != 0) return "";
        StringBuilder decoded = new StringBuilder(value.length() / 4);
        try {
            for (int i = 0; i < value.length(); i += 4) {
                decoded.append((char) Integer.parseInt(value.substring(i, i + 4), 16));
            }
        } catch (NumberFormatException malformed) { return ""; }
        return decoded.toString();
    }

    private static MarkerContent markerContent(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == TEXT_META) {
            int end = value.indexOf(META_END);
            if (end >= 0) {
                String[] fields = value.substring(1, end).split(String.valueOf(META_FIELD), -1);
                return new MarkerContent(value.substring(end + 1), fields, true);
            }
        }
        if (value == null || value.isEmpty() || value.charAt(0) != META) {
            return new MarkerContent(value == null ? "" : value, null);
        }
        int end = value.indexOf(META_END);
        if (end < 0) return new MarkerContent(value, null);
        String metadata = value.substring(1, end);
        String[] fields = metadata.split(String.valueOf(META_FIELD), -1);
        RichLinkClassifier.GameLinkInfo game = fields.length >= 1
                ? new RichLinkClassifier.GameLinkInfo(
                fields[0], fields.length > 1 ? fields[1] : "",
                fields.length > 2 ? fields[2] : "",
                fields.length > 3 ? fields[3] : "") : null;
        return new MarkerContent(value.substring(end + 1), game);
    }

    private static final class MarkerContent {
        final String label;
        final RichLinkClassifier.GameLinkInfo game;
        final boolean textLink;
        final String href;
        final String lightIcon;
        final String darkIcon;

        MarkerContent(String label, RichLinkClassifier.GameLinkInfo game) {
            this.label = label;
            this.game = game;
            this.textLink = false;
            this.href = "";
            this.lightIcon = "";
            this.darkIcon = "";
        }

        MarkerContent(String label, String[] fields, boolean textLink) {
            this.label = label;
            this.game = null;
            this.textLink = textLink;
            this.href = decodeField(fields[0]);
            this.lightIcon = fields.length > 1 ? decodeField(fields[1]) : "";
            this.darkIcon = fields.length > 2 ? decodeField(fields[2]) : "";
        }
    }
}
