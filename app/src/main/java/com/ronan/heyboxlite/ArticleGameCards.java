package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Extracts the game list that the official article renderer keeps beside text. */
final class ArticleGameCards {
    // The web renderer receives complete GameObj values in `game`; older
    // detail responses use game_details.
    private static final String[] DETAIL_KEYS = {"game", "game_details", "gameDetails"};
    private static final String[] PRELOAD_KEYS = {
            "_community_post_preload", "communityPostPreload", "community_post_preload",
            "post_content_section", "postContentSection", "content_section", "contentSection",
            "content", "contentModelList", "content_model_list",
            "game_cards", "gameCards",
            "preload_web_json_content", "preloadWebJsonContent",
            "_article_web_view", "web_content", "webContent"
    };

    private ArticleGameCards() {
    }

    static List<RichContent.Block> merge(JSONObject source,
                                         List<RichContent.Block> original) {
        if (source == null || original == null) return original;
        List<GameEntry> entries = entries(source);
        if (entries.isEmpty()) return original;

        List<RichContent.Block> result = new ArrayList<>(original);
        Set<String> existing = new HashSet<>();
        for (RichContent.Block block : result) {
            if (RichGameCardParser.isCard(block) && !block.value.trim().isEmpty()) {
                existing.add(block.value.trim());
            }
        }
        for (GameEntry entry : entries) {
            String id = entry.appId.trim();
            if (id.isEmpty() || !existing.add(id)) continue;
            RichContent.Block card = RichContent.Block.gameCard(entry.object, id);
            int position = findTitlePosition(result, entry.name);
            if (position < 0) {
                result.add(card);
            } else {
                // Several cards can follow one heading. Always append after the
                // cards already inserted there, otherwise the source order is reversed.
                int insertAt = position + 1;
                while (insertAt < result.size()
                        && RichGameCardParser.isCard(result.get(insertAt))) {
                    insertAt++;
                }
                result.add(insertAt, card);
            }
        }
        return result;
    }

    static int count(JSONObject source) {
        return entries(source).size();
    }

    static String fieldSummary(JSONObject source) {
        if (source == null) return "missing";
        StringBuilder summary = new StringBuilder();
        appendShape(summary, "game", source.opt("game"));
        appendShape(summary, "game_details", source.opt("game_details"));
        appendShape(summary, "preload", source.opt("_community_post_preload"));
        appendShape(summary, "web", source.opt("_article_web_view"));
        return summary.toString();
    }

    private static void appendShape(StringBuilder summary, String key, Object raw) {
        if (summary.length() > 0) summary.append(' ');
        summary.append(key).append('=');
        if (raw == null || raw == JSONObject.NULL) {
            summary.append("missing");
        } else if (raw instanceof JSONArray) {
            summary.append("array:").append(((JSONArray) raw).length());
        } else if (raw instanceof JSONObject) {
            summary.append("object:").append(((JSONObject) raw).length());
        } else if (raw instanceof String) {
            Object parsed = parseStructured((String) raw);
            if (parsed instanceof JSONArray) {
                summary.append("jsonArray:").append(((JSONArray) parsed).length());
            } else if (parsed instanceof JSONObject) {
                summary.append("jsonObject:").append(((JSONObject) parsed).length());
            } else {
                summary.append("text:").append(((String) raw).length());
            }
        } else {
            summary.append(raw.getClass().getSimpleName());
        }
    }

    static boolean shouldLoadWeb(JSONObject body, FeedItem item) {
        JSONObject result = body == null ? null : body.optJSONObject("result");
        JSONObject link = result == null ? null : result.optJSONObject("link");
        if (link == null) return false;
        // A store descriptor must not suppress loading the real article tree.
        // Typed card nodes are authoritative; loose metadata is not.
        List<GameEntry> cards = entries(link);
        boolean complete = !cards.isEmpty();
        for (GameEntry entry : cards) {
            if (!RichGameCardParser.hasRenderableData(entry.object)) {
                complete = false;
                break;
            }
        }
        if (complete) return false;
        return (item != null && item.article) || hasHtmlContent(link);
    }

    static boolean hasHtmlContent(JSONObject source) {
        return hasHtmlContent(source == null ? null : source.opt("text"), 0)
                || hasHtmlContent(source == null ? null : source.opt("article_content"), 0)
                || hasHtmlContent(source == null ? null : source.opt("content"), 0);
    }

    private static boolean hasHtmlContent(Object raw, int depth) {
        if (raw == null || raw == JSONObject.NULL || depth > 12) return false;
        if (raw instanceof String) {
            Object parsed = parseStructured((String) raw);
            return parsed != null && hasHtmlContent(parsed, depth + 1);
        }
        if (raw instanceof JSONArray) {
            JSONArray array = (JSONArray) raw;
            for (int i = 0; i < array.length(); i++) {
                if (hasHtmlContent(array.opt(i), depth + 1)) return true;
            }
            return false;
        }
        if (!(raw instanceof JSONObject)) return false;
        JSONObject object = (JSONObject) raw;
        String type = object.optString("type", "").trim().toLowerCase(Locale.ROOT);
        if ("html".equals(type) || "web_html".equals(type)) return true;
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            if (hasHtmlContent(object.opt(keys.next()), depth + 1)) return true;
        }
        return false;
    }

    private static List<GameEntry> entries(JSONObject source) {
        List<GameEntry> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collectDetails(source, result, seen);
        collectTextGames(source.opt("text"), result, seen, 0);
        for (String key : PRELOAD_KEYS) {
            Object raw = source.opt(key);
            if ("game_cards".equals(key)
                    || "gameCards".equals(key)) {
                collectGamePayload(raw, result, seen, 0, true);
            } else {
                collectContentNodes(raw, result, seen, 0);
            }
        }
        return result;
    }

    private static void collectContentNodes(Object raw, List<GameEntry> result,
                                            Set<String> seen, int depth) {
        if (raw == null || raw == JSONObject.NULL || depth > 16) return;
        if (raw instanceof String) {
            String value = RichTransportDecoder.decode((String) raw);
            Object parsed = parseStructured(value);
            if (parsed != null) collectContentNodes(parsed, result, seen, depth + 1);
            collectEmbeddedGameObjects(value, result, seen, depth + 1);
            collectMarkupGameReferences(value, result, seen);
            return;
        }
        if (raw instanceof JSONArray) {
            JSONArray array = (JSONArray) raw;
            for (int i = 0; i < array.length(); i++) {
                collectContentNodes(array.opt(i), result, seen, depth + 1);
            }
            return;
        }
        if (!(raw instanceof JSONObject)) return;
        JSONObject object = (JSONObject) raw;
        if (RichGameCardParser.isGameContainer(object)) {
            collectGamePayload(RichGameCardParser.gamePayload(object), result, seen,
                    depth + 1, true);
            return;
        }
        if (RichGameCardParser.isGameNode(object)) {
            addDetail(result, seen, object, true);
            return;
        }
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object child = object.opt(key);
            if (child instanceof JSONObject || child instanceof JSONArray
                    || child instanceof String) {
                if (isExplicitGameKey(key)) {
                    collectGamePayload(child, result, seen, depth + 1,
                            "game".equals(key));
                } else {
                    collectContentNodes(child, result, seen, depth + 1);
                }
            }
        }
    }

    /** Parses only the payload of an explicit web game component. */
    private static void collectGamePayload(Object raw, List<GameEntry> result,
                                           Set<String> seen, int depth,
                                           boolean allowIdOnly) {
        if (raw == null || raw == JSONObject.NULL || depth > 16) return;
        if (raw instanceof String) {
            Object parsed = parseStructured((String) raw);
            if (parsed != null) collectGamePayload(parsed, result, seen, depth + 1,
                    allowIdOnly);
            return;
        }
        if (raw instanceof JSONArray) {
            JSONArray array = (JSONArray) raw;
            for (int i = 0; i < array.length(); i++) {
                collectGamePayload(array.opt(i), result, seen, depth + 1, allowIdOnly);
            }
            return;
        }
        if (!(raw instanceof JSONObject)) return;
        JSONObject object = (JSONObject) raw;
        String id = RichGameCardParser.appId(object);
        if (!id.isEmpty()) {
            // cpt=game is a web component, but its payload is still expected to
            // contain the complete GameObj. Only an explicit `game` field or a
            // game_cards list may authorize an ID-only fetch.
            addDetail(result, seen, object, allowIdOnly);
            return;
        }
        Object baseInfos = object.opt("base_infos");
        if (baseInfos != null && baseInfos != JSONObject.NULL) {
            collectGamePayload(baseInfos, result, seen, depth + 1, allowIdOnly);
        }
        JSONObject nestedResult = object.optJSONObject("result");
        if (nestedResult != null) {
            collectGamePayload(nestedResult, result, seen, depth + 1, allowIdOnly);
        }
        Object baseInfo = object.opt("base_info");
        if (baseInfo != null && baseInfo != JSONObject.NULL) {
            collectGamePayload(baseInfo, result, seen, depth + 1, allowIdOnly);
        }
        Object data = object.opt("data");
        if (data != null && data != JSONObject.NULL) {
            collectGamePayload(data, result, seen, depth + 1, allowIdOnly);
        }
    }

    private static void collectTextGames(Object raw, List<GameEntry> result,
                                         Set<String> seen, int depth) {
        if (raw == null || raw == JSONObject.NULL || depth > 12) return;
        if (raw instanceof String) {
            Object parsed = parseStructured((String) raw);
            if (parsed != null) collectTextGames(parsed, result, seen, depth + 1);
            collectMarkupGameReferences((String) raw, result, seen);
            return;
        }
        if (raw instanceof JSONArray) {
            JSONArray array = (JSONArray) raw;
            for (int i = 0; i < array.length(); i++) {
                collectTextGames(array.opt(i), result, seen, depth + 1);
            }
            return;
        }
        if (!(raw instanceof JSONObject)) return;
        JSONObject object = (JSONObject) raw;
        if (RichGameCardParser.isGameContainer(object)) {
            collectGamePayload(RichGameCardParser.gamePayload(object), result, seen,
                    depth + 1, true);
            return;
        }
        if (RichGameCardParser.isGameNode(object)) {
            addDetail(result, seen, object, true);
            return;
        }
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if ("games".equals(key) || "app_info".equals(key)) continue;
            Object child = object.opt(key);
            if (child instanceof JSONObject || child instanceof JSONArray
                    || child instanceof String) {
                collectTextGames(child, result, seen, depth + 1);
            }
        }
    }

    /**
     * The web endpoint sometimes returns an HTML wrapper around escaped JSON.
     * Only consume fragments that still carry the official explicit game marker;
     * ordinary heybox:// game links remain inline text and must not become cards.
     */
    private static void collectEmbeddedGameObjects(String raw, List<GameEntry> result,
                                                   Set<String> seen, int depth) {
        if (raw == null || raw.isEmpty() || depth > 16) return;
        for (JSONObject object : ArticleGameMarkupScanner.scan(raw)) {
            if (RichGameCardParser.isGameContainer(object)) {
                collectGamePayload(RichGameCardParser.gamePayload(object), result, seen,
                        depth + 1, true);
            } else if (RichGameCardParser.isGameNode(object)) {
                addDetail(result, seen, object, true);
            }
        }
    }

    private static void collectMarkupGameReferences(String raw, List<GameEntry> result,
                                                    Set<String> seen) {
        for (ArticleGameMarkupScanner.GameReference reference
                : ArticleGameMarkupScanner.scanGameReferences(raw)) {
            if (reference.appId.isEmpty() || !seen.add(reference.appId)) continue;
            JSONObject marker = new JSONObject();
            try {
                marker.put("type", "game_card");
                marker.put("appid", reference.appId);
                if (!reference.name.isEmpty()) marker.put("name", reference.name);
            } catch (JSONException ignored) {
                continue;
            }
            result.add(new GameEntry(reference.appId, reference.name, marker));
        }
    }

    private static void collectDetails(JSONObject source, List<GameEntry> result,
                                       Set<String> seen) {
        for (String key : DETAIL_KEYS) {
            Object raw = source.opt(key);
            if (raw == null || raw == JSONObject.NULL) continue;
            JSONArray array = asArray(raw);
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    addDetail(result, seen, array.opt(i), "game".equals(key));
                }
            } else if (raw instanceof JSONObject) {
                addDetail(result, seen, raw, "game".equals(key));
            }
        }
        JSONObject body = source.optJSONObject("body");
        if (body != null && body != source) collectDetails(body, result, seen);
    }

    private static boolean isExplicitGameKey(String key) {
        return "game".equals(key) || "game_details".equals(key)
                || "gameDetails".equals(key);
    }

    private static void addDetail(List<GameEntry> result, Set<String> seen, Object raw,
                                  boolean allowIdOnly) {
        JSONObject object = objectValue(raw);
        if (object == null) return;
        JSONObject nested = object.optJSONObject("app_info");
        JSONObject value = nested == null ? object : mergeObject(nested, object);
        String id = RichGameCardParser.appId(value);
        if (id.isEmpty() || RichGameCardParser.isPlatformDescriptor(value)
                || (!allowIdOnly && !RichGameCardParser.hasRenderableData(value))
                || !seen.add(id)) return;
        GameCardData data = GameCardData.fromEmbedded(value, id);
        String name = data == null ? first(value, "name", "title", "game_name") : data.name;
        result.add(new GameEntry(id, name, value));
    }

    private static int findTitlePosition(List<RichContent.Block> blocks, String name) {
        String needle = comparable(name);
        if (needle.length() < 3) return -1;
        for (int i = 0; i < blocks.size(); i++) {
            RichContent.Block block = blocks.get(i);
            if (block == null || block.image || RichGameCardParser.isCard(block)) continue;
            if (comparable(block.value).contains(needle)) return i;
        }
        return -1;
    }

    private static String comparable(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}《》【】（）()“”‘’]", "");
    }

    /**
     * The official response may carry JSON as an escaped string. The normal
     * content parser tries both forms; the card scanner must do the same or it
     * will incorrectly decide that the article has no web components.
     */
    private static Object parseStructured(String raw) {
        if (raw == null) return null;
        String[] candidates = {RichTransportDecoder.decodeJson(raw),
                RichTransportDecoder.decode(raw), raw};
        Set<String> visited = new HashSet<>();
        for (String candidate : candidates) {
            String value = candidate == null ? "" : candidate.trim();
            for (int layer = 0; layer < 3 && !value.isEmpty(); layer++) {
                if (!visited.add(value)) break;
                try {
                    if (value.startsWith("[")) return new JSONArray(value);
                    if (value.startsWith("{")) return new JSONObject(value);
                    if (value.startsWith("\"")) {
                        Object unwrapped = new org.json.JSONTokener(value).nextValue();
                        if (unwrapped instanceof String) {
                            value = ((String) unwrapped).trim();
                            continue;
                        }
                    }
                } catch (Exception ignored) {
                    // Try the next transport representation before treating it as HTML.
                }
                break;
            }
        }
        return null;
    }

    private static JSONArray asArray(Object raw) {
        if (raw instanceof JSONArray) return (JSONArray) raw;
        if (!(raw instanceof String)) return null;
        Object parsed = parseStructured((String) raw);
        return parsed instanceof JSONArray ? (JSONArray) parsed : null;
    }

    private static JSONObject objectValue(Object raw) {
        if (raw instanceof JSONObject) return (JSONObject) raw;
        if (!(raw instanceof String)) return null;
        String value = ((String) raw).trim();
        if (!value.startsWith("{")) return null;
        try {
            return new JSONObject(value);
        } catch (JSONException ignored) {
            return null;
        }
    }

    private static JSONObject mergeObject(JSONObject nested, JSONObject outer) {
        JSONObject result = new JSONObject();
        try {
            java.util.Iterator<String> keys = outer.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                result.put(key, outer.get(key));
            }
            keys = nested.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                result.put(key, nested.get(key));
            }
        } catch (JSONException ignored) {
            return nested;
        }
        return result;
    }

    private static String first(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static final class GameEntry {
        final String appId;
        final String name;
        final JSONObject object;

        GameEntry(String appId, String name, JSONObject object) {
            this.appId = appId;
            this.name = name;
            this.object = object;
        }
    }
}
