package com.ronan.heyboxlite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

final class FeedCollection {
    private static final int MAX_JSON_DEPTH = 7;

    private FeedCollection() {
    }

    static List<FeedItem> parse(Object node) {
        List<FeedItem> items = new ArrayList<>();
        collect(node, items, new HashSet<>(), 0);
        return items;
    }

    static List<FeedItem> filter(List<FeedItem> items, List<String> blockedKeywords) {
        List<FeedItem> filtered = new ArrayList<>();
        if (items == null) return filtered;
        for (FeedItem item : items) {
            if (item != null && !isBlocked(item, blockedKeywords)) filtered.add(item);
        }
        return filtered;
    }

    static void appendUnique(List<FeedItem> target, List<FeedItem> incoming) {
        Set<String> ids = new HashSet<>();
        for (FeedItem item : target) {
            if (item != null) ids.add(item.id);
        }
        if (incoming == null) return;
        for (FeedItem item : incoming) {
            if (item != null && ids.add(item.id)) target.add(item);
        }
    }

    static boolean isBlocked(FeedItem item, List<String> keywords) {
        if (item == null || keywords == null || keywords.isEmpty()) return false;
        String haystack = (item.title + "\n" + item.description + "\n" + item.author)
                .toLowerCase(Locale.US);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && haystack.contains(keyword)) return true;
        }
        return false;
    }

    private static void collect(Object node, List<FeedItem> output, Set<String> ids, int depth) {
        if (node == null || depth > MAX_JSON_DEPTH) return;
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) collect(array.opt(i), output, ids, depth + 1);
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject object = (JSONObject) node;
        String id = object.optString("linkid", object.optString("link_id"));
        String title = object.optString("title");
        boolean hasContent = !title.isEmpty() || object.has("text") || object.has("description")
                || object.has("content");
        if (!id.isEmpty() && hasContent) {
            if (ids.add(id)) output.add(FeedItem.from(object));
            return;
        }
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) collect(object.opt(keys.next()), output, ids, depth + 1);
    }
}
