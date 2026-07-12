package com.openzen.heyboxcommunity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * 通用 JSON / 字符串取值助手：按多个候选键取真值 / 首个非空值 / 首个存在的字段，
 * 以及在嵌套结构里深度查找。全部无状态，从 MainActivity 抽出供各处复用。
 */
final class Json {
    private Json() {}

    /** 依次检查候选键，命中即按 1/true/yes 判真、0/2/false/no 判假；都不确定则继续下一个键。 */
    static boolean truthy(JSONObject source, String... keys) {
        if (source == null) {
            return false;
        }
        for (String key : keys) {
            if (source.has(key)) {
                Object value = source.opt(key);
                if (value instanceof Boolean) {
                    return ((Boolean) value).booleanValue();
                }
                if (value instanceof Number) {
                    return ((Number) value).intValue() == 1;
                }
                String text = String.valueOf(value).trim();
                if ("1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)) {
                    return true;
                }
                if ("0".equals(text) || "2".equals(text) || "false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text)) {
                    return false;
                }
            }
        }
        return false;
    }

    static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    static int firstInt(JSONObject source, int fallback, String... keys) {
        if (source == null) {
            return fallback;
        }
        for (String key : keys) {
            if (source.has(key)) {
                return source.optInt(key, fallback);
            }
        }
        return fallback;
    }

    static JSONObject firstObject(JSONObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            JSONObject found = object.optJSONObject(key);
            if (found != null) return found;
        }
        return null;
    }

    static JSONArray firstArray(JSONObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            JSONArray array = object.optJSONArray(key);
            if (array != null) return array;
            JSONObject nested = object.optJSONObject(key);
            if (nested != null) {
                JSONArray nestedArray = firstArray(nested, "links", "list", "events", "moments", "data");
                if (nestedArray != null) return nestedArray;
            }
        }
        return null;
    }

    static int findInt(JSONObject source, String[] keys, int depth) {
        int value;
        if (source == null || depth > 4) {
            return 0;
        }
        for (String key : keys) {
            if (source.has(key) && (value = source.optInt(key, 0)) > 0) {
                return value;
            }
        }
        Iterator<String> names = source.keys();
        while (names.hasNext()) {
            Object value2 = source.opt(names.next());
            if (value2 instanceof JSONObject) {
                int found = findInt((JSONObject) value2, keys, depth + 1);
                if (found > 0) {
                    return found;
                }
            } else if (value2 instanceof JSONArray) {
                JSONArray array = (JSONArray) value2;
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    int found2 = findInt(item, keys, depth + 1);
                    if (found2 > 0) {
                        return found2;
                    }
                }
            }
        }
        return 0;
    }

    static JSONObject findObject(JSONObject source, int depth, String... keys) {
        JSONObject found;
        if (source == null || depth > 4) {
            return null;
        }
        for (String key : keys) {
            JSONObject value = source.optJSONObject(key);
            if (value != null) {
                return value;
            }
        }
        Iterator<String> names = source.keys();
        while (names.hasNext()) {
            Object child = source.opt(names.next());
            if ((child instanceof JSONObject) && (found = findObject((JSONObject) child, depth + 1, keys)) != null) {
                return found;
            }
        }
        return null;
    }
}
