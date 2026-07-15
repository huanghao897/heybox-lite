package com.ronan.heyboxlite;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class SavedPostParser {
    private SavedPostParser() {
    }

    static String favoriteTabSummary(JSONObject body) {
        if (body == null) return "no body";
        JSONObject result = body.optJSONObject("result");
        JSONArray tabs = result == null ? null : result.optJSONArray("tab_list");
        return "status=" + body.optString("status") + ", tabs=" + (tabs == null ? -1 : tabs.length())
                + ", msg=" + Json.first(body.optString("msg"), body.optString("message"));
    }

    static String favoriteFolderSummary(JSONObject body) {
        if (body == null) return "no body";
        JSONObject result = body.optJSONObject("result");
        JSONObject source = result == null ? body : result;
        JSONArray folders = source.optJSONArray("folders");
        int count = folders == null ? -1 : folders.length();
        int posts = source.optInt("favour_post_num",
                source.optInt("favor_post_num", source.optInt("favorite_post_num", -1)));
        return "status=" + body.optString("status") + ", msg="
                + Json.first(body.optString("msg"), body.optString("message"))
                + ", folders=" + count + ", favour_post_num=" + posts;
    }

    static JSONArray findFavoriteFolders(JSONObject body) {
        JSONObject result = body == null ? null : body.optJSONObject("result");
        JSONArray folders = findFolderArray(result, 0);
        return folders == null ? findFolderArray(body, 0) : folders;
    }

    static JSONObject firstFavoriteFolder(JSONArray folders) {
        if (folders == null) return null;
        JSONObject fallback = null;
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = unwrapFolder(folders.optJSONObject(i));
            if (folder == null) continue;
            if (fallback == null) fallback = folder;
            if (!favoriteFolderId(folder).isEmpty()) return folder;
        }
        return fallback;
    }

    static String favoriteFolderId(JSONObject folder) {
        return folder == null ? "" : Json.first(folder.optString("folder_id"),
                folder.optString("folderid"), folder.optString("fav_folder_id"),
                folder.optString("collect_folder_id"), folder.optString("collection_id"),
                folder.optString("id"), folder.optString("fid"));
    }

    static JSONArray findLinks(JSONObject result) {
        if (result == null) return null;
        String[] keys = {"links", "list", "moments", "history_visit", "visits", "history",
                "data", "items", "rows", "records", "favorites", "collects", "link_list", "links_list"};
        for (String key : keys) {
            JSONArray links = result.optJSONArray(key);
            if (links != null) return links;
        }
        return findNestedLinkArray(result, 0);
    }

    static JSONObject savedFeedValue(JSONObject wrapper) {
        JSONObject value = unwrapSavedItem(wrapper);
        if (value == null) return null;
        try {
            JSONObject merged = new JSONObject(value.toString());
            copyFirstInt(wrapper, merged, "link_award_num", "link_award_num", "like_num",
                    "award_num", "award_count", "like_count", "liked_num", "praise_num",
                    "praise_count", "total_award_num", "award", "awards", "up_num", "up");
            copyFirstInt(wrapper, merged, "comment_num", "comment_num", "comment_count",
                    "reply_num", "reply_count", "comments_count", "total_comment_num",
                    "comment", "comments");
            if (merged.optJSONObject("user") == null) {
                JSONObject user = Json.findObject(wrapper, 0, "user", "author", "account");
                if (user != null) {
                    merged.put("user", user);
                } else {
                    String author = findString(wrapper, 0, "author_name", "username", "nickname", "author");
                    if (!author.isEmpty()) {
                        JSONObject fallback = new JSONObject();
                        fallback.put("username", author);
                        merged.put("user", fallback);
                    }
                }
            }
            return merged;
        } catch (Exception ignored) {
            return value;
        }
    }

    static List<FeedItem> feedItems(JSONObject body) {
        List<FeedItem> items = new ArrayList<>();
        JSONObject result = body == null ? null : body.optJSONObject("result");
        JSONArray links = findLinks(result == null ? body : result);
        if (links != null) {
            for (int i = 0; i < links.length(); i++) {
                JSONObject value = savedFeedValue(links.optJSONObject(i));
                if (value != null) items.add(FeedItem.from(value));
            }
        }
        return items.isEmpty() ? FeedCollection.parse(body) : items;
    }

    private static JSONArray findFolderArray(Object node, int depth) {
        if (node == null || depth > 5) return null;
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            return looksLikeFolderArray(array) ? array : null;
        }
        if (!(node instanceof JSONObject)) return null;
        JSONObject object = (JSONObject) node;
        String[] keys = {"folders", "folder_list", "fav_folders", "favorite_folders",
                "collect_folders", "collections", "list", "items", "data"};
        for (String key : keys) {
            JSONArray array = object.optJSONArray(key);
            if (array != null && looksLikeFolderArray(array)) return array;
        }
        Iterator<String> names = object.keys();
        while (names.hasNext()) {
            JSONArray found = findFolderArray(object.opt(names.next()), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean looksLikeFolderArray(JSONArray array) {
        if (array == null || array.length() == 0) return false;
        for (int i = 0; i < Math.min(6, array.length()); i++) {
            JSONObject folder = unwrapFolder(array.optJSONObject(i));
            if (folder == null) continue;
            if (!favoriteFolderId(folder).isEmpty()) return true;
            if (!Json.first(folder.optString("folder_name"), folder.optString("name"),
                    folder.optString("title")).isEmpty()) return true;
        }
        return false;
    }

    private static JSONObject unwrapFolder(JSONObject item) {
        JSONObject current = item;
        String[] keys = {"folder", "folder_info", "fav_folder", "collect_folder",
                "collection", "data", "item"};
        for (int depth = 0; depth < 4 && current != null; depth++) {
            if (!favoriteFolderId(current).isEmpty()) return current;
            JSONObject next = null;
            for (String key : keys) {
                next = current.optJSONObject(key);
                if (next != null) break;
            }
            if (next == current) break;
            current = next;
        }
        return current;
    }

    private static JSONArray findNestedLinkArray(Object node, int depth) {
        if (node == null || depth > 5) return null;
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            return looksLikeLinkArray(array) ? array : null;
        }
        if (!(node instanceof JSONObject)) return null;
        JSONObject object = (JSONObject) node;
        Iterator<String> names = object.keys();
        while (names.hasNext()) {
            JSONArray found = findNestedLinkArray(object.opt(names.next()), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean looksLikeLinkArray(JSONArray array) {
        if (array == null || array.length() == 0) return false;
        for (int i = 0; i < Math.min(8, array.length()); i++) {
            JSONObject item = array.optJSONObject(i);
            JSONObject value = item == null ? null : savedFeedValue(item);
            if (value != null && !value.optString("linkid", value.optString("link_id")).isEmpty()) return true;
        }
        return false;
    }

    private static JSONObject unwrapSavedItem(JSONObject item) {
        JSONObject current = item;
        String[] keys = {"link", "link_info", "link_detail", "moment", "post", "favorite",
                "fav", "record", "target", "source", "obj", "content", "data", "item"};
        for (int depth = 0; depth < 4 && current != null; depth++) {
            if (!current.optString("linkid", current.optString("link_id")).isEmpty()) return current;
            JSONObject next = null;
            for (String key : keys) {
                next = current.optJSONObject(key);
                if (next != null) break;
            }
            if (next == null || next == current) break;
            current = next;
        }
        return current;
    }

    private static void copyFirstInt(JSONObject source, JSONObject target, String targetKey, String... keys) {
        int value = Json.findInt(source, keys, 0);
        if (target.optInt(targetKey, 0) > 0 || value <= 0) return;
        try {
            target.put(targetKey, value);
        } catch (Exception ignored) {
        }
    }

    private static String findString(JSONObject source, int depth, String... keys) {
        if (source == null || depth > 4) return "";
        for (String key : keys) {
            String value = source.optString(key);
            if (!value.isEmpty() && !value.startsWith("{")) return value;
        }
        Iterator<String> names = source.keys();
        while (names.hasNext()) {
            Object child = source.opt(names.next());
            if (!(child instanceof JSONObject)) continue;
            String found = findString((JSONObject) child, depth + 1, keys);
            if (!found.isEmpty()) return found;
        }
        return "";
    }
}
