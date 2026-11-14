package com.ronan.heyboxlite;

import static com.ronan.heyboxlite.RichContent.firstImage;
import static com.ronan.heyboxlite.RichContent.firstText;
import static com.ronan.heyboxlite.RichContent.imageCount;
import static com.ronan.heyboxlite.RichContentHtmlParser.addCaption;
import static com.ronan.heyboxlite.RichContentHtmlParser.addHeading;
import static com.ronan.heyboxlite.RichContentHtmlParser.addHtml;
import static com.ronan.heyboxlite.RichContentHtmlParser.addImage;
import static com.ronan.heyboxlite.RichContentHtmlParser.addQuote;
import static com.ronan.heyboxlite.RichContentHtmlParser.stripStructuredNoise;

import com.ronan.heyboxlite.RichContent.Block;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Traverses structured rich-text containers and mixed JSON/text streams. */
final class RichContentJsonParser {
    static final int MAX_JSON_DEPTH = 32;
    static final String[] CHILD_KEYS = {
            "ops", "blocks", "children", "child", "items", "list", "nodes",
            "paragraphs", "spans", "elements", "attrs", "models", "model",
            "content", "contents", "data", "content_attrs", "contentAttrs",
            "content_attr", "contentAttr", "raw_content", "rawContent", "games"
    };
    private static final Pattern JSON_IMAGE = Pattern.compile(
            "(?is)\"(?:url|src|original|origin_url|original_url|image|image_url|img|img_url|pic|pic_url|cover|cover_url)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private RichContentJsonParser() {}

    static List<Block> parseText(String source, JSONArray fallbackImages) {
        List<Block> blocks = new ArrayList<>();
        Set<String> imageUrls = new HashSet<>();
        String jsonSource = RichTransportDecoder.decodeJson(source);
        if (!addStructured(blocks, imageUrls, jsonSource)) {
            source = RichTransportDecoder.decode(source);
            source = RichInlineMarkup.normalize(source);
            if (!addStructured(blocks, imageUrls, source)) {
                addHtml(blocks, imageUrls, source);
            }
        }

        addFallbackImages(blocks, imageUrls, fallbackImages);
        return blocks;
    }

    static void addFallbackImages(List<Block> blocks, Set<String> imageUrls,
                                  JSONArray fallbackImages) {
        if (fallbackImages == null) return;
        for (int i = 0; i < fallbackImages.length(); i++) {
            Object value = fallbackImages.opt(i);
            if (value instanceof JSONObject) {
                addImage(blocks, imageUrls, firstImage((JSONObject) value));
            } else {
                addImage(blocks, imageUrls, fallbackImages.optString(i));
            }
        }
    }

    static boolean addStructured(List<Block> blocks, Set<String> imageUrls, String source) {
        return addStructured(blocks, imageUrls, source, 0);
    }

    private static boolean addStructured(List<Block> blocks, Set<String> imageUrls,
                                         String source, int depth) {
        if (depth > MAX_JSON_DEPTH) return true;
        String value = source == null ? "" : source.trim();
        if (value.isEmpty()) return true;
        int arrayStart = value.indexOf('[');
        int arrayEnd = value.lastIndexOf(']');
        if (arrayStart > 0 && arrayEnd > arrayStart
                && RichInlineMarkup.isLikelyJsonArray(value.substring(arrayStart, arrayEnd + 1))) {
            String prefix = value.substring(0, arrayStart).trim();
            try {
                JSONArray array = new JSONArray(value.substring(arrayStart, arrayEnd + 1));
                addReadableFragment(blocks, imageUrls, prefix);
                addArray(blocks, imageUrls, array, depth + 1);
                String suffix = value.substring(arrayEnd + 1).trim();
                addReadableFragment(blocks, imageUrls, suffix);
                return true;
            } catch (JSONException ignored) {
            }
        }
        if (RichInlineMarkup.isLikelyJsonArray(value)) {
            try {
                addArray(blocks, imageUrls, new JSONArray(value), depth + 1);
                return true;
            } catch (JSONException ignored) {
            }
        }
        return addObjectStream(blocks, imageUrls, value, depth + 1);
    }

    private static void addArray(List<Block> blocks, Set<String> imageUrls, JSONArray content) {
        addArray(blocks, imageUrls, content, 0);
    }

    private static void addArray(List<Block> blocks, Set<String> imageUrls, JSONArray content,
                                 int depth) {
        if (depth > MAX_JSON_DEPTH) return;
        for (int i = 0; i < content.length(); i++) {
            addAny(blocks, imageUrls, content.opt(i), depth + 1);
        }
    }

    private static void addObject(List<Block> blocks, Set<String> imageUrls, JSONObject item) {
        addObject(blocks, imageUrls, item, 0);
    }

    private static void addObject(List<Block> blocks, Set<String> imageUrls, JSONObject item,
                                  int depth) {
        if (depth > MAX_JSON_DEPTH) return;
        String type = item.optString("type").toLowerCase(Locale.ROOT);
        if (RichGameCardParser.isGameNode(item)) {
            blocks.add(RichGameCardParser.block(item));
            return;
        }
        Object insert = item.opt("insert");
        if (insert != null) {
            addInsert(blocks, imageUrls, insert, depth + 1);
        }

        if (type.contains("video")) return;
        String image = firstImage(item);
        if (!image.isEmpty() && (isImageType(type) || !hasReadableContent(item))) {
            addImage(blocks, imageUrls, image);
            if (isImageType(type)) addCaption(blocks, imageCaption(item));
        }

        if (!isImageType(type)) {
            String text = firstText(item);
            if (!text.isEmpty()) {
                if (isCaptionType(type)) {
                    addCaption(blocks, text);
                    return;
                }
                if (isHeadingType(type)) {
                    addHeading(blocks, text);
                    return;
                }
                if (isQuoteType(type)) {
                    addQuote(blocks, text);
                    return;
                }
                addHtml(blocks, imageUrls, text);
                if (isSelfContainedTextType(type)) return;
            }
        }

        for (String key : CHILD_KEYS) {
            if (hasReadableContent(item) && isMetadataChildKey(key)) continue;
            if ("content".equals(key) && item.opt(key) instanceof String) continue;
            addAny(blocks, imageUrls, item.opt(key), depth + 1);
        }
    }

    static boolean isSelfContainedTextType(String type) {
        if (type == null) return false;
        String value = type.toLowerCase(Locale.ROOT);
        return value.equals("html") || value.equals("text")
                || value.equals("rich_text") || value.equals("richtext")
                || value.equals("paragraph") || value.equals("p");
    }

    static boolean isMetadataChildKey(String key) {
        if (key == null) return false;
        String value = key.toLowerCase(Locale.ROOT).replace('_', '-');
        return value.equals("attrs") || value.equals("models") || value.equals("model")
                || value.equals("content-attrs") || value.equals("content-attr")
                || value.equals("raw-content") || value.equals("rawcontent");
    }

    static void addAny(List<Block> blocks, Set<String> imageUrls, Object raw) {
        addAny(blocks, imageUrls, raw, 0);
    }

    private static void addAny(List<Block> blocks, Set<String> imageUrls, Object raw,
                               int depth) {
        if (depth > MAX_JSON_DEPTH) return;
        if (raw == null || raw == JSONObject.NULL) return;
        if (raw instanceof JSONObject) {
            addObject(blocks, imageUrls, (JSONObject) raw, depth + 1);
        } else if (raw instanceof JSONArray) {
            addArray(blocks, imageUrls, (JSONArray) raw, depth + 1);
        } else if (raw instanceof String) {
            String value = RichTransportDecoder.decode((String) raw);
            value = RichInlineMarkup.normalize(value);
            if (!addStructured(blocks, imageUrls, value, depth + 1)) {
                addHtml(blocks, imageUrls, value);
            }
        }
    }

    private static void addInsert(List<Block> blocks, Set<String> imageUrls, Object insert) {
        addInsert(blocks, imageUrls, insert, 0);
    }

    static void addInsert(List<Block> blocks, Set<String> imageUrls, Object insert, int depth) {
        if (depth > MAX_JSON_DEPTH) return;
        if (insert instanceof String) {
            addHtml(blocks, imageUrls, (String) insert);
        } else if (insert instanceof JSONObject) {
            JSONObject object = (JSONObject) insert;
            if (RichGameCardParser.isGameContainer(object)
                    || RichGameCardParser.isGameNode(object)) {
                addObject(blocks, imageUrls, object, depth + 1);
                return;
            }
            String image = firstImage(object);
            if (!image.isEmpty()) {
                addImage(blocks, imageUrls, image);
            } else {
                addObject(blocks, imageUrls, object, depth + 1);
            }
        } else if (insert instanceof JSONArray) {
            addArray(blocks, imageUrls, (JSONArray) insert, depth + 1);
        }
    }

    static boolean isImageType(String type) {
        return type.contains("img") || type.contains("image")
                || type.contains("picture") || type.contains("photo");
    }

    static boolean isHeadingType(String type) {
        if (type == null || type.isEmpty()) return false;
        return type.matches("h[1-6]") || type.contains("header")
                || type.contains("heading");
    }

    static boolean isCaptionType(String type) {
        if (type == null || type.isEmpty()) return false;
        return "h4".equals(type) || type.contains("caption");
    }

    static boolean isQuoteType(String type) {
        return type != null && type.contains("quote");
    }

    static String imageCaption(JSONObject item) {
        String caption = first(item.optString("caption"), item.optString("desc"),
                item.optString("description"), item.optString("title"));
        if (RichInlineMarkup.looksImageUrl(caption)) return "";
        return caption;
    }

    static String detailImage(JSONObject item) {
        String image = firstImage(item);
        if (!image.isEmpty()) return image;
        return first(item.optString("url"), item.optString("src"),
                item.optString("original"), item.optString("text"));
    }

    private static boolean hasReadableContent(JSONObject item) {
        String text = firstText(item);
        return text != null && text.replaceAll("\\s+", "").length() > 0;
    }

    private static boolean addObjectStream(List<Block> blocks, Set<String> imageUrls,
                                           String source, int nestingDepth) {
        if (nestingDepth > MAX_JSON_DEPTH) return true;
        boolean found = false;
        int imagesBefore = imageCount(blocks);
        int start = -1;
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        int lastEnd = 0;
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
                continue;
            }
            if (current == '"') {
                quoted = true;
            } else if (current == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (current == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    try {
                        JSONObject object = new JSONObject(source.substring(start, i + 1));
                        String fragment = source.substring(lastEnd, start).trim();
                        addReadableFragment(blocks, imageUrls, fragment);
                        addObject(blocks, imageUrls, object, nestingDepth + 1);
                        found = true;
                        lastEnd = i + 1;
                    } catch (JSONException ignored) {
                    }
                    start = -1;
                }
            }
        }
        if (found) {
            String suffix = source.substring(lastEnd).trim();
            addReadableFragment(blocks, imageUrls, suffix);
            if (imageCount(blocks) == imagesBefore
                    && !RichContentSupport.hasGameCards(blocks)) {
                addLooseImages(blocks, imageUrls, source);
            }
            return true;
        }
        if (looksStructured(source)) {
            addReadableFragment(blocks, imageUrls, source);
            if (imageCount(blocks) == imagesBefore
                    && !RichContentSupport.hasGameCards(blocks)) {
                addLooseImages(blocks, imageUrls, source);
            }
            return true;
        }
        return false;
    }

    private static void addReadableFragment(List<Block> blocks, Set<String> imageUrls,
                                            String source) {
        if (source == null) return;
        String value = stripStructuredNoise(source).trim();
        if (value.isEmpty()) return;
        int marker = structuredMarker(value);
        if (marker > 0) {
            String readable = value.substring(0, marker);
            if (hasReadableText(readable)) addHtml(blocks, imageUrls, readable);
        } else if (marker < 0) {
            addHtml(blocks, imageUrls, value);
        }
    }

    private static int structuredMarker(String value) {
        int marker = -1;
        String[] candidates = {
                "\",\"type\"", "\"type\":", "\"url\":", "\"height\":",
                "\"width\":", "},{", "[{"
        };
        for (String candidate : candidates) {
            int index = value.indexOf(candidate);
            if (index >= 0 && (marker < 0 || index < marker)) marker = index;
        }
        int object = value.indexOf('{');
        if (object >= 0 && (marker < 0 || object < marker)) marker = object;
        return marker;
    }

    private static boolean hasReadableText(String value) {
        return value != null && value.replaceAll("[\\s\"',:;{}\\[\\]<>/\\\\]+", "")
                .length() > 0;
    }

    private static void addLooseImages(List<Block> blocks, Set<String> imageUrls, String source) {
        Matcher matcher = JSON_IMAGE.matcher(source);
        while (matcher.find()) {
            addImage(blocks, imageUrls, decodeJsonString(matcher.group(1)));
        }
    }

    private static String decodeJsonString(String value) {
        try {
            return new JSONArray("[\"" + value + "\"]").optString(0);
        } catch (JSONException ignored) {
            return value;
        }
    }

    static boolean looksStructured(String value) {
        if (value == null) return false;
        String clean = value.trim();
        return clean.startsWith("{") || RichInlineMarkup.isLikelyJsonArray(clean)
                || clean.contains("\",\"type\"") || clean.contains("\"url\":");
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }
}
