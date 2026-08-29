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
    private static final Pattern ANCHOR = Pattern.compile(
            "(?is)<a\\b([^>]*)>(.*?)</a\\s*>");
    private static final Pattern CONTENT_FRAGMENT = Pattern.compile(
            "(?is)(?:<a\\s*)?data-link-type\\s*=\\s*(['\"])(game|text)\\1"
                    + "[^>]{0,512}>(.*?)(?:</a\\s*>|$)");

    static final class Link {
        final int start;
        final int end;

        Link(int start, int end) {
            this.start = start;
            this.end = end;
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

    static String normalizeAnchors(String source) {
        if (source == null || source.isEmpty()) return "";
        Matcher matcher = ANCHOR.matcher(source);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String attributes = matcher.group(1);
            String label = matcher.group(2);
            if (RichLinkClassifier.isGameLink(attributes)) {
                matcher.appendReplacement(output, Matcher.quoteReplacement(
                        String.valueOf(START) + label + END));
            } else if (RichLinkClassifier.isTextLink(attributes)) {
                matcher.appendReplacement(output, Matcher.quoteReplacement(label));
            }
        }
        matcher.appendTail(output);
        return normalizeFragments(output.toString());
    }

    private static String normalizeFragments(String source) {
        Matcher matcher = CONTENT_FRAGMENT.matcher(source);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String label = matcher.group(3);
            String replacement = "game".equalsIgnoreCase(matcher.group(2))
                    ? String.valueOf(START) + label + END : label;
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    static Parsed parse(String source) {
        String value = source == null ? "" : source;
        StringBuilder display = new StringBuilder(value);
        List<Link> links = new ArrayList<>();
        int searchFrom = 0;
        while (searchFrom < display.length()) {
            int start = display.indexOf(String.valueOf(START), searchFrom);
            if (start < 0) break;
            int end = display.indexOf(String.valueOf(END), start + 1);
            if (end < 0) {
                display.setCharAt(start, ZERO_WIDTH_SPACE);
                searchFrom = start + 1;
                continue;
            }
            display.setCharAt(start, OBJECT);
            display.setCharAt(end, ZERO_WIDTH_SPACE);
            links.add(new Link(start, end + 1));
            searchFrom = end + 1;
        }
        return new Parsed(display.toString(), Collections.unmodifiableList(links));
    }

    static String plainText(String source) {
        if (source == null || source.isEmpty()) return "";
        return source.replace(String.valueOf(START), "")
                .replace(String.valueOf(END), "")
                .replace(String.valueOf(ZERO_WIDTH_SPACE), "");
    }
}
