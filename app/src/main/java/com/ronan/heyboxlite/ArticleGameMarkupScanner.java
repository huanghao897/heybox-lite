package com.ronan.heyboxlite;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds explicitly marked game objects embedded in the web HTML payload. */
final class ArticleGameMarkupScanner {
    private static final Pattern GAME_ELEMENT = Pattern.compile(
            "<([a-z][a-z0-9:-]*)\\b(?=[^>]*\\bdata-gameid\\s*=\\s*[\\\"']([0-9]+)[\\\"'])[^>]*>.*?</\\1\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern GAME_OPEN_TAG = Pattern.compile(
            "<([a-z][a-z0-9:-]*)\\b[^>]*\\bdata-gameid\\s*=\\s*[\\\"']([0-9]+)[\\\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    static final Pattern GAME_TOKEN = Pattern.compile("\\u0001heybox-game-([0-9]+)\\u0001");

    private ArticleGameMarkupScanner() {
    }

    static List<JSONObject> scan(String raw) {
        List<JSONObject> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        String decoded = decodeMarkup(RichTransportDecoder.decode(raw));
        collect(decoded, result);
        String unescaped = decodeMarkup(decoded.replace("\\\\\"", "\""));
        if (!unescaped.equals(decoded)) collect(unescaped, result);
        return result;
    }

    /** Extracts the official data-gameid links used by article HTML. */
    static List<GameReference> scanGameReferences(String raw) {
        List<GameReference> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        String value = decodeMarkup(RichTransportDecoder.decode(raw));
        Set<String> seen = new HashSet<>();
        for (GameMarker marker : scanGameMarkers(value)) {
            if (seen.add(marker.appId)) {
                result.add(new GameReference(marker.appId, marker.name));
            }
        }
        return result;
    }

    /** Returns card placeholders in source order. Only the official data-gameid marker is valid. */
    static List<GameMarker> scanGameMarkers(String raw) {
        List<GameMarker> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        String value = decodeMarkup(RichTransportDecoder.decode(raw));
        Set<Integer> consumed = new HashSet<>();
        Matcher elements = GAME_ELEMENT.matcher(value);
        while (elements.find()) {
            String id = elements.group(2);
            result.add(new GameMarker(elements.start(), elements.end(), id,
                    visibleText(elements.group(0))));
            consumed.add(elements.start());
        }
        Matcher openings = GAME_OPEN_TAG.matcher(value);
        while (openings.find()) {
            if (consumed.contains(openings.start())) continue;
            String id = openings.group(2);
            result.add(new GameMarker(openings.start(), openings.end(), id, ""));
        }
        result.sort((left, right) -> Integer.compare(left.start, right.start));
        return result;
    }

    /** Replaces card elements with an inert token before the normal HTML parser strips tags. */
    static String replaceGameMarkers(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        String value = decodeMarkup(RichTransportDecoder.decode(raw));
        List<GameMarker> markers = scanGameMarkers(value);
        if (markers.isEmpty()) return value;
        StringBuilder output = new StringBuilder(value.length());
        int cursor = 0;
        for (GameMarker marker : markers) {
            if (marker.start < cursor) continue;
            output.append(value, cursor, marker.start)
                    .append('\u0001').append("heybox-game-").append(marker.appId)
                    .append('\u0001');
            cursor = marker.end;
        }
        return output.append(value, cursor, value.length()).toString();
    }

    static List<String> splitGameTokens(String value) {
        List<String> parts = new ArrayList<>();
        if (value == null || value.isEmpty()) return parts;
        Matcher matcher = GAME_TOKEN.matcher(value);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) parts.add(value.substring(cursor, matcher.start()));
            parts.add(matcher.group());
            cursor = matcher.end();
        }
        if (cursor < value.length()) parts.add(value.substring(cursor));
        if (parts.isEmpty()) parts.add(value);
        return parts;
    }

    static boolean isGameToken(String value) {
        return value != null && GAME_TOKEN.matcher(value).matches();
    }

    static String gameTokenId(String value) {
        if (value == null) return "";
        Matcher matcher = GAME_TOKEN.matcher(value);
        return matcher.matches() ? matcher.group(1) : "";
    }

    static RichContent.Block block(String appId) {
        JSONObject marker = new JSONObject();
        try {
            marker.put("type", "game_card");
            marker.put("appid", appId);
        } catch (JSONException ignored) {
        }
        return RichContent.Block.gameCard(marker, appId);
    }

    static final class GameMarker {
        final int start;
        final int end;
        final String appId;
        final String name;

        GameMarker(int start, int end, String appId, String name) {
            this.start = start;
            this.end = end;
            this.appId = appId == null ? "" : appId.trim();
            this.name = name == null ? "" : name.trim();
        }

    }

    static final class GameReference {
        final String appId;
        final String name;

        GameReference(String appId, String name) {
            this.appId = appId == null ? "" : appId.trim();
            this.name = name == null ? "" : name.trim();
        }
    }

    private static void collect(String value, List<JSONObject> result) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        String[] markers = {"\"cpt\"", "\\\"cpt\\\"", "&quot;cpt&quot;",
                "'cpt'", "cpt:", "\"type\"", "\\\"type\\\"",
                "&quot;type&quot;", "'type'", "type:"};
        for (String marker : markers) {
            int from = 0;
            while (from < lower.length()) {
                int markerAt = lower.indexOf(marker, from);
                if (markerAt < 0) break;
                int start = value.lastIndexOf('{', markerAt);
                String objectText = start < 0 ? null : balancedObject(value, start);
                if (objectText != null) {
                    try {
                        result.add(new JSONObject(objectText));
                    } catch (JSONException ignored) {
                        // The marker may belong to unrelated page data.
                    }
                }
                from = markerAt + marker.length();
            }
        }
    }

    private static String balancedObject(String value, int start) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            char current = value.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
                continue;
            }
            if (current == '"') quoted = true;
            else if (current == '{') depth++;
            else if (current == '}' && --depth == 0) {
                return value.substring(start, i + 1);
            }
        }
        return null;
    }

    private static String decodeMarkup(String value) {
        String result = value;
        for (int pass = 0; pass < 2; pass++) {
            result = result.replace("&quot;", "\"")
                    .replace("&#34;", "\"")
                    .replace("&#x22;", "\"")
                    .replace("&#123;", "{")
                    .replace("&#x7b;", "{")
                    .replace("&#125;", "}")
                    .replace("&#x7d;", "}")
                    .replace("&#91;", "[")
                    .replace("&#x5b;", "[")
                    .replace("&#93;", "]")
                    .replace("&#x5d;", "]")
                    .replace("&#x2F;", "/")
                    .replace("&#47;", "/")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&amp;", "&");
        }
        return result;
    }

    private static String visibleText(String value) {
        if (value == null) return "";
        String text = value.replaceAll("(?is)<[^>]+>", " ");
        text = decodeMarkup(text).replaceAll("\\s+", " ").trim();
        return text.toLowerCase(Locale.ROOT).startsWith("game:")
                ? text.substring(5).trim() : text;
    }
}
