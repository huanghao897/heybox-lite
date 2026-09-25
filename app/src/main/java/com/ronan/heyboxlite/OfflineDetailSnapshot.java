package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/** Copies only retained comments, without serializing the entire online response first. */
final class OfflineDetailSnapshot {
    static final int COMMENT_LIMIT = 10;

    private OfflineDetailSnapshot() {}

    static JSONObject copy(JSONObject body) throws JSONException {
        return object(body, 0, false);
    }

    private static JSONObject object(JSONObject source, int depth, boolean result)
            throws JSONException {
        if (depth > 64) throw new JSONException("Offline content is too deeply nested");
        JSONObject target = new JSONObject();
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = source.opt(key);
            if (depth == 0 && "result".equals(key) && value instanceof JSONObject) {
                target.put(key, object((JSONObject) value, depth + 1, true));
            } else if (result && "comments".equals(key) && value instanceof JSONArray) {
                target.put(key, array((JSONArray) value, depth + 1, COMMENT_LIMIT));
            } else {
                target.put(key, copyValue(value, depth + 1));
            }
        }
        return target;
    }

    private static JSONArray array(JSONArray source, int depth, int limit) throws JSONException {
        if (depth > 64) throw new JSONException("Offline content is too deeply nested");
        JSONArray target = new JSONArray();
        for (int i = 0; i < Math.min(limit, source.length()); i++) {
            target.put(copyValue(source.opt(i), depth + 1));
        }
        return target;
    }

    private static Object copyValue(Object value, int depth) throws JSONException {
        if (value instanceof JSONObject) return object((JSONObject) value, depth, false);
        if (value instanceof JSONArray) return array((JSONArray) value, depth, Integer.MAX_VALUE);
        return value;
    }
}
