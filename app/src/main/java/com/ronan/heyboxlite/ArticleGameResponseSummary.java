package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, value-safe shape information for diagnosing article web responses. */
final class ArticleGameResponseSummary {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_NODES = 160;
    private static final String[] INTERESTING_KEYS = {
            "cpt", "type", "appid", "app_id", "steam_appid", "data", "game",
            "content", "children", "html", "preload_web_json_content"
    };

    private ArticleGameResponseSummary() {
    }

    static String describe(JSONObject root) {
        if (root == null) return "missing";
        State state = new State();
        inspect(root, "$", 0, state);
        StringBuilder result = new StringBuilder("nodes=")
                .append(state.nodes)
                .append(" objects=").append(state.objects)
                .append(" arrays=").append(state.arrays)
                .append(" strings=").append(state.strings)
                .append(" keys=");
        appendCounts(result, state.keyCounts);
        result.append(" markers=");
        if (state.markers.length() == 0) result.append("none");
        else result.append(state.markers);
        return result.toString();
    }

    private static void inspect(Object raw, String path, int depth, State state) {
        if (raw == null || raw == JSONObject.NULL || depth > MAX_DEPTH
                || state.nodes >= MAX_NODES) return;
        state.nodes++;
        if (raw instanceof JSONObject) {
            state.objects++;
            JSONObject object = (JSONObject) raw;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String lower = key.toLowerCase(java.util.Locale.ROOT);
                for (String interesting : INTERESTING_KEYS) {
                    if (interesting.equals(lower)) {
                        increment(state.keyCounts, interesting);
                        appendMarker(state, interesting, object.opt(key));
                        break;
                    }
                }
                Object child = object.opt(key);
                if (child instanceof JSONObject || child instanceof JSONArray) {
                    inspect(child, path + "." + key, depth + 1, state);
                } else if (child instanceof String) {
                    inspectStructuredString((String) child, path + "." + key,
                            depth + 1, state);
                }
            }
        } else if (raw instanceof JSONArray) {
            state.arrays++;
            JSONArray array = (JSONArray) raw;
            for (int i = 0; i < array.length(); i++) {
                inspect(array.opt(i), path + "[]", depth + 1, state);
            }
        } else if (raw instanceof String) {
            state.strings++;
            inspectStructuredString((String) raw, path, depth + 1, state);
        }
    }

    private static void inspectStructuredString(String raw, String path, int depth,
                                                State state) {
        if (raw == null || depth > MAX_DEPTH) return;
        String value = RichTransportDecoder.decodeJson(raw).trim();
        if (value.startsWith("{") || value.startsWith("[")) {
            try {
                inspect(value.startsWith("[") ? new JSONArray(value) : new JSONObject(value),
                        path, depth, state);
                return;
            } catch (Exception ignored) {
                // The response may be HTML containing an escaped component.
            }
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("cpt") || lower.contains("game_card")
                || lower.contains("steam_appid")) {
            increment(state.keyCounts, "embedded_markers");
        }
    }

    private static void appendMarker(State state, String key, Object value) {
        if (!(value instanceof String) && !(value instanceof Number)) return;
        String token = String.valueOf(value).trim();
        if (token.isEmpty() || token.length() > 32) return;
        if (!"cpt".equals(key) && !"type".equals(key)) return;
        if (state.markers.length() > 0) state.markers.append(',');
        state.markers.append(key).append('=').append(token.replaceAll("[^A-Za-z0-9_.-]", "_"));
    }

    private static void increment(Map<String, Integer> counts, String key) {
        Integer count = counts.get(key);
        counts.put(key, count == null ? 1 : count + 1);
    }

    private static void appendCounts(StringBuilder target, Map<String, Integer> counts) {
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!first) target.append(',');
            first = false;
            target.append(entry.getKey()).append(':').append(entry.getValue());
        }
        if (first) target.append("none");
    }

    private static final class State {
        int nodes;
        int objects;
        int arrays;
        int strings;
        final Map<String, Integer> keyCounts = new LinkedHashMap<>();
        final StringBuilder markers = new StringBuilder();
    }
}
