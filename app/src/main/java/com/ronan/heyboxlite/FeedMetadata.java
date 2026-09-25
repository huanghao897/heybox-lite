package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Official content identity and community metadata, separate from display copy. */
final class FeedMetadata {
    private static final String[] NESTED_KEYS = {
            "link", "link_content", "news_content", "link_info", "basic_info"
    };
    private static final String[] TAG_KEYS = {
            "tags", "hashtags", "act_hashtags", "content_tags", "list_content_tags"
    };

    static final class Section {
        static final Section EMPTY = new Section("", "", "");
        final String id;
        final String name;
        final String icon;

        Section(String id, String name, String icon) {
            this.id = id;
            this.name = name;
            this.icon = icon;
        }
    }

    private FeedMetadata() {}

    static boolean isArticle(JSONObject source) {
        Boolean linkStyle = articleLinkStyle(source, 0);
        if (linkStyle != null) return linkStyle;
        Boolean explicit = articleFlag(source, 0);
        return explicit != null ? explicit : articleStyle(source, 0);
    }

    private static Boolean articleLinkStyle(JSONObject source, int depth) {
        if (source == null || depth > 4) return null;
        // LinkStyle and the official feed DTO mapper use link_style, not
        // content_type (which selects a card layout).
        switch (source.optInt("link_style", -1)) {
            case 101: return true;
            case 100: case 102: case 200: case 300: case 301: case 400: return false;
            default: break;
        }
        for (String key : NESTED_KEYS) {
            Boolean style = articleLinkStyle(object(source.opt(key)), depth + 1);
            if (style != null) return style;
        }
        return null;
    }

    private static Boolean articleFlag(JSONObject source, int depth) {
        if (source == null || depth > 4) return null;
        Boolean own = flag(source.opt("is_article"));
        if (Boolean.TRUE.equals(own)) return true;
        for (String key : NESTED_KEYS) {
            Boolean child = articleFlag(object(source.opt(key)), depth + 1);
            if (Boolean.TRUE.equals(child)) return true;
            if (own == null) own = child;
        }
        return own;
    }

    private static boolean articleStyle(JSONObject source, int depth) {
        if (source == null || depth > 4) return false;
        // LinkStyleKt: link_tag=1 opens WEB_ARTICLE_LINK. use_concept_type
        // describes presentation, not article identity.
        if (source.optInt("link_tag", -1) == 1) return true;
        int tag = source.optInt("link_tag", -1);
        if (tag == 27 || tag == 28) return false;
        // BBSLinkObj news card constants and FeedsFlowItemDtoDeserializer.
        switch (source.optInt("content_type", Integer.MIN_VALUE)) {
            case 0: case 1: case 2: case 11: case 14: case 15: case 16:
            case 21: case 33: case 35: case 36: case 37: case 38: case 39:
            case 43: case 50: case 54: case 101: case 103:
                return true;
            default:
                break;
        }
        for (String key : NESTED_KEYS) {
            if (articleStyle(object(source.opt(key)), depth + 1)) return true;
        }
        return false;
    }

    static Section section(JSONObject source) {
        return section(source, 0);
    }

    private static Section section(JSONObject source, int depth) {
        if (source == null || depth > 4) return Section.EMPTY;
        JSONArray topics = source.optJSONArray("topics");
        if (topics == null) topics = source.optJSONArray("topic_list");
        if (topics != null) {
            for (int i = 0; i < topics.length(); i++) {
                Section section = topic(topics.opt(i));
                if (!section.name.isEmpty()) return section;
            }
        }
        Section section = topic(source.opt("topic"));
        if (!section.name.isEmpty()) return section;
        String name = text(source, "topic_name");
        if (readable(name)) {
            return new Section(text(source, "topic_id"), name,
                    text(source, "topic_icon", "pic_url"));
        }
        for (String key : new String[]{"content_tags", "list_content_tags"}) {
            JSONArray values = source.optJSONArray(key);
            if (values == null) continue;
            for (int i = 0; i < values.length(); i++) {
                JSONObject tag = values.optJSONObject(i);
                if (isCommunityTag(tag)) {
                    section = topic(tag);
                    if (!section.name.isEmpty()) return section;
                }
            }
        }
        for (String key : NESTED_KEYS) {
            section = section(object(source.opt(key)), depth + 1);
            if (!section.name.isEmpty()) return section;
        }
        // Some feed cards omit topics but retain the official community
        // feedback option. Its topic_name is not a hashtag or free-form label.
        JSONArray feedback = source.optJSONArray("feedback");
        if (feedback != null) {
            for (int i = 0; i < feedback.length(); i++) {
                JSONObject group = feedback.optJSONObject(i);
                JSONArray options = group == null ? null : group.optJSONArray("options");
                if (options == null) continue;
                for (int j = 0; j < options.length(); j++) {
                    JSONObject option = options.optJSONObject(j);
                    if (option != null && !text(option, "topic_id").isEmpty()) {
                        name = text(option, "topic_name");
                        if (readable(name)) {
                            return new Section(text(option, "topic_id"), name, "");
                        }
                    }
                }
            }
        }
        return Section.EMPTY;
    }

    static List<String> tags(JSONObject source) {
        List<String> result = new ArrayList<>();
        collectTags(source, result, 0);
        result.remove(section(source).name);
        return result;
    }

    private static void collectTags(JSONObject source, List<String> result, int depth) {
        if (source == null || depth > 4) return;
        for (String key : TAG_KEYS) {
            JSONArray array = source.optJSONArray(key);
            if (array == null) continue;
            for (int i = 0; i < array.length(); i++) {
                Object value = array.opt(i);
                if (value instanceof JSONObject && isCommunityTag((JSONObject) value)) continue;
                addLabel(result, value);
            }
        }
        for (String key : new String[]{"tag", "tag_name", "post_tag", "extra_tag",
                "link_extra_tag"}) {
            addLabel(result, source.opt(key));
        }
        collectUiLabels(source.opt("link_extra_tag_v2"), result, 0);
        for (String key : NESTED_KEYS) {
            collectTags(object(source.opt(key)), result, depth + 1);
        }
        JSONArray topics = source.optJSONArray("topics");
        if (topics != null) {
            for (int i = 0; i < topics.length(); i++) result.remove(topic(topics.opt(i)).name);
        }
        result.remove(section(source).name);
    }

    private static boolean isCommunityTag(JSONObject tag) {
        if (tag == null) return false;
        if (!text(tag, "topic_id").isEmpty()) return true;
        String protocol = text(tag, "protocol");
        if (!protocol.startsWith("heybox://")) return false;
        JSONObject route = object(protocol.substring("heybox://".length()));
        return route != null && "/bbs/topic".equals(text(route, "path"));
    }

    private static Section topic(Object value) {
        JSONObject object = object(value);
        String name = object == null ? value instanceof String ? ((String) value).trim() : ""
                : text(object, "name", "title", "topic_name", "text");
        if (!readable(name)) return Section.EMPTY;
        return new Section(text(object, "topic_id"), name,
                text(object, "pic_url", "icon", "img_url", "appicon"));
    }

    private static void collectUiLabels(Object value, List<String> names, int depth) {
        if (value == null || depth > 4) return;
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) collectUiLabels(array.opt(i), names, depth + 1);
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (!isCommunityTag(object)) addLabel(names, object);
            collectUiLabels(object.opt("children"), names, depth + 1);
        }
    }

    private static void addLabel(List<String> names, Object value) {
        String label = value instanceof JSONObject
                ? text((JSONObject) value, "name", "title", "tag_name", "label", "text")
                : value instanceof String ? ((String) value).trim() : "";
        if (readable(label) && !names.contains(label)) names.add(label);
    }

    private static boolean readable(String value) {
        return !value.isEmpty() && value.length() <= 80 && !"null".equals(value)
                && !value.matches("\\d+") && !value.startsWith("http")
                && !value.startsWith("{") && !value.startsWith("[");
    }

    private static String text(JSONObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            Object value = object.opt(key);
            if (!(value instanceof String) && !(value instanceof Number)) continue;
            String text = String.valueOf(value).trim();
            if (!text.isEmpty() && !"null".equals(text)) return text;
        }
        return "";
    }

    private static Boolean flag(Object value) {
        if (Boolean.TRUE.equals(value) || "1".equals(String.valueOf(value))
                || "true".equalsIgnoreCase(String.valueOf(value))) return true;
        if (Boolean.FALSE.equals(value) || "0".equals(String.valueOf(value))
                || "false".equalsIgnoreCase(String.valueOf(value))) return false;
        return null;
    }

    private static JSONObject object(Object value) {
        if (value instanceof JSONObject) return (JSONObject) value;
        if (!(value instanceof String) || !((String) value).trim().startsWith("{")) return null;
        try {
            return new JSONObject((String) value);
        } catch (JSONException ignored) {
            return null;
        }
    }
}
