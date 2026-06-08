package com.openzen.heyboxcommunity;

import android.text.Html;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RichContent {
    static final class Block {
        final boolean image;
        final String value;

        private Block(boolean image, String value) {
            this.image = image;
            this.value = value;
        }
    }

    private static final Pattern IMAGE = Pattern.compile(
            "(?is)<img\\b[^>]*?(?:data-original|data-src|src)\\s*=\\s*(['\"])(.*?)\\1[^>]*>");
    private static final Pattern INLINE_EMOJI = Pattern.compile(
            "(?is)<a\\b([^>]*(?:icon[-_]?url|icon[-_]?dark[-_]?url)[^>]*)>(.*?)</a>");
    private static final Pattern INLINE_IMAGE_EMOJI = Pattern.compile("(?is)<img\\b([^>]*)>");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "(?is)([a-z0-9_-]+)\\s*=\\s*(['\"])(.*?)\\2");
    private static final Pattern JSON_IMAGE = Pattern.compile(
            "(?is)\"(?:url|src|original|origin_url|original_url|image|image_url|img|img_url|pic|pic_url|cover|cover_url)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final String[] DETAIL_BODY_KEYS = {
            "text", "article_text", "articleText",
            "article_content", "articleContent", "content_v2", "contentV2",
            "content_list", "contentList", "rich_text", "richText",
            "content_html", "contentHtml", "body", "body_text", "bodyText",
            "raw_content", "rawContent", "raw_html", "rawHtml",
            "content_attrs", "contentAttrs", "content_attr", "contentAttr",
            "hb_rich_texts", "hbRichTexts", "rich_texts", "richTexts",
            "article_rich_texts", "articleRichTexts"
    };
    private static final String[] DETAIL_TEXT_FALLBACK_KEYS = {
            "description", "summary", "intro", "content"
    };
    private static final String[] CHILD_KEYS = {
            "ops", "blocks", "children", "child", "items", "list", "nodes",
            "paragraphs", "spans", "elements", "attrs", "models", "model",
            "content", "contents", "data", "content_attrs", "contentAttrs",
            "content_attr", "contentAttr", "raw_content", "rawContent"
    };
    private static final String[] TEXT_KEYS = {
            "text", "content", "html", "value", "caption",
            "title", "desc", "description"
    };
    private static final String[] IMAGE_KEYS = {
            "url", "src", "original", "origin", "origin_url", "original_url",
            "image", "image_url", "img", "img_url", "pic", "pic_url",
            "cover", "cover_url", "thumb", "thumbnail", "large", "large_url",
            "images", "pictures"
    };

    private RichContent() {}

    static List<Block> parse(JSONObject source, JSONArray fallbackImages) {
        ParseResult result = new ParseResult();
        if (source == null) {
            addFallbackImages(result.blocks, result.imageUrls, fallbackImages);
            return result.blocks;
        }

        boolean articleMode = source.optInt("use_concept_type", -1) == 0;
        for (String key : DETAIL_BODY_KEYS) {
            ParseResult candidate = parseDetailBody(source.opt(key), articleMode);
            if (hasReadableBody(candidate)) {
                addFallbackImagesIfNeeded(candidate, fallbackImages);
                return candidate.blocks;
            }
            if (result.blocks.isEmpty() && candidate != null && !candidate.blocks.isEmpty()) {
                result = candidate;
            }
        }

        ParseResult fallbackText = new ParseResult();
        for (String key : DETAIL_TEXT_FALLBACK_KEYS) {
            addAny(fallbackText.blocks, fallbackText.imageUrls, source.opt(key));
            if (readableLength(fallbackText.blocks) > 0) break;
        }
        if (readableLength(fallbackText.blocks) > 0) {
            mergeBlocks(fallbackText, result);
            addFallbackImages(fallbackText.blocks, fallbackText.imageUrls, fallbackImages);
            return fallbackText.blocks;
        }

        addFallbackImages(result.blocks, result.imageUrls, fallbackImages);
        return result.blocks;
    }

    private static ParseResult parseDetailBody(Object value, boolean articleMode) {
        ParseResult result = new ParseResult();
        if (value == null || value == JSONObject.NULL) return result;
        if (value instanceof String) {
            String raw = (String) value;
            String jsonText = decodeJsonTransport(raw).trim();
            if (jsonText.isEmpty()) return result;
            try {
                addDetailArray(result, new JSONArray(jsonText), articleMode);
                return result;
            } catch (Exception ignored) {
            }
            try {
                addDetailObject(result, new JSONObject(jsonText), articleMode);
                return result;
            } catch (Exception ignored) {
            }
            if (addStructured(result.blocks, result.imageUrls, jsonText)
                    && !result.blocks.isEmpty()) {
                return result;
            }
            String text = decodeTransport(raw).trim();
            if (text.isEmpty()) return result;
            if (addStructured(result.blocks, result.imageUrls, text)
                    && !result.blocks.isEmpty()) {
                return result;
            }
            addArticleHtml(result.blocks, result.imageUrls, text);
            return result;
        }
        if (value instanceof JSONArray) {
            addDetailArray(result, (JSONArray) value, articleMode);
        } else if (value instanceof JSONObject) {
            addDetailObject(result, (JSONObject) value, articleMode);
        }
        return result;
    }

    private static void addDetailArray(ParseResult result, JSONArray array,
                                       boolean articleMode) {
        for (int i = 0; i < array.length(); i++) {
            Object raw = array.opt(i);
            if (raw instanceof JSONObject) {
                addDetailObject(result, (JSONObject) raw, articleMode);
            } else if (raw instanceof JSONArray) {
                addDetailArray(result, (JSONArray) raw, articleMode);
            } else if (raw instanceof String) {
                addArticleHtml(result.blocks, result.imageUrls, (String) raw);
            }
        }
    }

    private static void addDetailObject(ParseResult result, JSONObject item,
                                        boolean articleMode) {
        String type = item.optString("type").toLowerCase(Locale.ROOT);
        if (isImageType(type) || type.contains("video")) {
            addImage(result.blocks, result.imageUrls, detailImage(item));
            return;
        }
        String text = firstText(item);
        if (!text.isEmpty()) {
            addArticleHtml(result.blocks, result.imageUrls, text);
        }
        if (articleMode && "html".equals(type)) return;
        Object insert = item.opt("insert");
        if (insert != null) addInsert(result.blocks, result.imageUrls, insert);
        for (String key : CHILD_KEYS) {
            if (item.opt(key) instanceof String) continue;
            Object child = item.opt(key);
            if (child instanceof JSONObject) {
                addDetailObject(result, (JSONObject) child, articleMode);
            } else if (child instanceof JSONArray) {
                addDetailArray(result, (JSONArray) child, articleMode);
            }
        }
    }

    private static boolean hasReadableBody(ParseResult result) {
        return result != null && hasReadableText(result.blocks);
    }

    static boolean hasReadableText(List<Block> blocks) {
        return readableLength(blocks) >= 4;
    }

    private static void addFallbackImagesIfNeeded(ParseResult result,
                                                  JSONArray fallbackImages) {
        if (imageCount(result.blocks) == 0) {
            addFallbackImages(result.blocks, result.imageUrls, fallbackImages);
        }
    }

    private static void mergeBlocks(ParseResult target, ParseResult extra) {
        if (extra == null) return;
        for (Block block : extra.blocks) {
            if (block.image) addImage(target.blocks, target.imageUrls, block.value);
            else addTextBlock(target, block.value);
        }
    }

    private static void addTextBlock(ParseResult result, String value) {
        if (value == null || value.trim().isEmpty()) return;
        for (Block block : result.blocks) {
            if (!block.image && value.equals(block.value)) return;
        }
        result.blocks.add(new Block(false, value));
    }

    private static int imageCount(List<Block> blocks) {
        if (blocks == null) return 0;
        int count = 0;
        for (Block block : blocks) if (block.image) count++;
        return count;
    }

    private static int readableLength(List<Block> blocks) {
        if (blocks == null) return 0;
        int length = 0;
        for (Block block : blocks) {
            if (!block.image) length += block.value.replaceAll("\\s+", "").length();
        }
        return length;
    }

    static List<Block> parse(String source, JSONArray fallbackImages) {
        List<Block> blocks = new ArrayList<>();
        Set<String> imageUrls = new HashSet<>();
        String jsonSource = decodeJsonTransport(source);
        if (!addStructured(blocks, imageUrls, jsonSource)) {
            source = decodeTransport(source);
            source = normalizeInlineEmojis(source);
            if (!addStructured(blocks, imageUrls, source)) {
                addHtml(blocks, imageUrls, source);
            }
        }

        addFallbackImages(blocks, imageUrls, fallbackImages);
        return blocks;
    }

    private static void addFallbackImages(List<Block> blocks, Set<String> imageUrls,
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

    private static boolean addStructured(List<Block> blocks, Set<String> imageUrls,
                                         String source) {
        String value = source == null ? "" : source.trim();
        if (value.isEmpty()) return true;
        int arrayStart = value.indexOf('[');
        int arrayEnd = value.lastIndexOf(']');
        if (arrayStart > 0 && arrayEnd > arrayStart) {
            String prefix = value.substring(0, arrayStart).trim();
            try {
                JSONArray array = new JSONArray(value.substring(arrayStart, arrayEnd + 1));
                addReadableFragment(blocks, imageUrls, prefix);
                addArray(blocks, imageUrls, array);
                String suffix = value.substring(arrayEnd + 1).trim();
                addReadableFragment(blocks, imageUrls, suffix);
                return true;
            } catch (Exception ignored) {
            }
        }
        try {
            addArray(blocks, imageUrls, new JSONArray(value));
            return true;
        } catch (Exception ignored) {
        }
        return addObjectStream(blocks, imageUrls, value);
    }

    private static void addArray(List<Block> blocks, Set<String> imageUrls, JSONArray content) {
        for (int i = 0; i < content.length(); i++) {
            addAny(blocks, imageUrls, content.opt(i));
        }
    }

    private static void addObject(List<Block> blocks, Set<String> imageUrls, JSONObject item) {
        Object insert = item.opt("insert");
        if (insert != null) {
            addInsert(blocks, imageUrls, insert);
        }

        String type = item.optString("type").toLowerCase(Locale.ROOT);
        String image = firstImage(item);
        if (!image.isEmpty() && (isImageType(type) || !hasReadableContent(item))) {
            addImage(blocks, imageUrls, image);
        }

        if (!isImageType(type)) {
            String text = firstText(item);
            if (!text.isEmpty()) addHtml(blocks, imageUrls, text);
        }

        for (String key : CHILD_KEYS) {
            if ("content".equals(key) && item.opt(key) instanceof String) continue;
            addAny(blocks, imageUrls, item.opt(key));
        }
    }

    private static void addAny(List<Block> blocks, Set<String> imageUrls, Object raw) {
        if (raw == null || raw == JSONObject.NULL) return;
        if (raw instanceof JSONObject) {
            addObject(blocks, imageUrls, (JSONObject) raw);
        } else if (raw instanceof JSONArray) {
            addArray(blocks, imageUrls, (JSONArray) raw);
        } else if (raw instanceof String) {
            String value = decodeTransport((String) raw);
            value = normalizeInlineEmojis(value);
            if (!addStructured(blocks, imageUrls, value)) {
                addHtml(blocks, imageUrls, value);
            }
        }
    }

    private static void addInsert(List<Block> blocks, Set<String> imageUrls, Object insert) {
        if (insert instanceof String) {
            addHtml(blocks, imageUrls, (String) insert);
        } else if (insert instanceof JSONObject) {
            JSONObject object = (JSONObject) insert;
            String image = firstImage(object);
            if (!image.isEmpty()) {
                addImage(blocks, imageUrls, image);
            } else {
                addObject(blocks, imageUrls, object);
            }
        } else if (insert instanceof JSONArray) {
            addArray(blocks, imageUrls, (JSONArray) insert);
        }
    }

    private static boolean isImageType(String type) {
        return type.contains("img") || type.contains("image")
                || type.contains("picture") || type.contains("photo");
    }

    private static boolean hasReadableContent(JSONObject item) {
        String text = firstText(item);
        return text != null && text.replaceAll("\\s+", "").length() > 0;
    }

    private static String firstText(JSONObject item) {
        for (String key : TEXT_KEYS) {
            Object value = item.opt(key);
            if (value instanceof String && !((String) value).isEmpty()) return (String) value;
        }
        return "";
    }

    private static String firstImage(JSONObject item) {
        for (String key : IMAGE_KEYS) {
            Object value = item.opt(key);
            String found = imageValue(value);
            if (!found.isEmpty()) return found;
        }
        return "";
    }

    private static String detailImage(JSONObject item) {
        String image = firstImage(item);
        if (!image.isEmpty()) return image;
        return first(item.optString("url"), item.optString("src"),
                item.optString("original"), item.optString("text"));
    }

    private static String imageValue(Object value) {
        if (value instanceof String) return (String) value;
        if (value instanceof JSONObject) return firstImage((JSONObject) value);
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String found = imageValue(array.opt(i));
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }

    private static boolean addObjectStream(List<Block> blocks, Set<String> imageUrls,
                                           String source) {
        boolean found = false;
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
                        addObject(blocks, imageUrls, object);
                        found = true;
                        lastEnd = i + 1;
                    } catch (Exception ignored) {
                    }
                    start = -1;
                }
            }
        }
        if (found) {
            String suffix = source.substring(lastEnd).trim();
            addReadableFragment(blocks, imageUrls, suffix);
            addLooseImages(blocks, imageUrls, source);
            return true;
        }
        if (looksStructured(source)) {
            addReadableFragment(blocks, imageUrls, source);
            addLooseImages(blocks, imageUrls, source);
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

    private static void addLooseImages(List<Block> blocks, Set<String> imageUrls,
                                       String source) {
        Matcher matcher = JSON_IMAGE.matcher(source);
        while (matcher.find()) {
            addImage(blocks, imageUrls, decodeJsonString(matcher.group(1)));
        }
    }

    private static String decodeJsonString(String value) {
        try {
            return new JSONArray("[\"" + value + "\"]").optString(0);
        } catch (Exception ignored) {
            return value;
        }
    }

    private static boolean looksStructured(String value) {
        if (value == null) return false;
        String clean = value.trim();
        return clean.startsWith("{") || clean.startsWith("[")
                || clean.contains("\",\"type\"") || clean.contains("\"url\":");
    }

    static String plainText(String source) {
        List<Block> blocks = parse(source, null);
        StringBuilder value = new StringBuilder();
        for (Block block : blocks) {
            if (block.image || block.value.isEmpty()) continue;
            if (value.length() > 0) value.append('\n');
            value.append(block.value);
        }
        return value.toString().trim();
    }

    private static void addHtml(List<Block> blocks, Set<String> imageUrls, String html) {
        addArticleHtml(blocks, imageUrls, html);
    }

    private static void addArticleHtml(List<Block> blocks, Set<String> imageUrls,
                                       String html) {
        if (html == null || html.isEmpty()) return;
        html = normalizeInlineEmojis(html);
        Matcher matcher = IMAGE.matcher(html);
        int start = 0;
        while (matcher.find()) {
            addArticleText(blocks, html.substring(start, matcher.start()));
            addImage(blocks, imageUrls, decodeHtml(matcher.group(2)));
            start = matcher.end();
        }
        addArticleText(blocks, html.substring(start));
    }

    private static void addArticleText(List<Block> blocks, String html) {
        if (html == null || html.isEmpty()) return;
        String value = html
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)<li\\b[^>]*>", "\n- ")
                .replaceAll("(?is)</(?:p|div|section|article|h[1-6]|blockquote|li|ul|ol|figure|figcaption)>", "\n")
                .replaceAll("(?is)<(?:p|div|section|article|h[1-6]|blockquote|ul|ol|figure|figcaption)\\b[^>]*>", "\n");
        addText(blocks, value);
    }

    private static String normalizeInlineEmojis(String html) {
        html = decodeAttributeEntities(html);
        Matcher matcher = INLINE_EMOJI.matcher(html);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String attrs = matcher.group(1);
            String label = decodeHtml(matcher.group(2)).trim();
            if (label.isEmpty()) label = "表情";
            EmojiUrls urls = extractEmojiUrls(attrs);
            if (urls.hasUrl()) {
                String token = token(label);
                EmojiStore.register(token, urls.light, urls.dark);
                matcher.appendReplacement(output, Matcher.quoteReplacement(token));
            }
        }
        matcher.appendTail(output);
        return normalizeInlineImageEmojis(output.toString());
    }

    private static String normalizeInlineImageEmojis(String html) {
        Matcher matcher = INLINE_IMAGE_EMOJI.matcher(html);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String attrs = matcher.group(1);
            EmojiUrls urls = extractEmojiUrls(attrs);
            String label = first(attribute(attrs, "alt"), attribute(attrs, "data-name"),
                    attribute(attrs, "title"), attribute(attrs, "name"));
            String light = first(urls.light, attribute(attrs, "src"),
                    attribute(attrs, "data-src"), attribute(attrs, "data-original"));
            if (!looksEmojiUrl(light) && !looksEmojiUrl(urls.dark)
                    && !attrs.toLowerCase(Locale.ROOT).contains("emoji")) {
                continue;
            }
            if (label.isEmpty()) label = "表情";
            String token = token(decodeHtml(label));
            EmojiStore.register(token, light, urls.dark);
            matcher.appendReplacement(output, Matcher.quoteReplacement(token));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static EmojiUrls extractEmojiUrls(String attrs) {
        String light = "";
        String dark = "";
        Matcher attr = ATTRIBUTE.matcher(attrs);
        while (attr.find()) {
            String name = attr.group(1).toLowerCase(Locale.ROOT).replace('_', '-');
            String url = decodeHtml(attr.group(3)).replace("\\/", "/");
            if (name.contains("dark") && name.contains("url")) {
                dark = url;
            } else if ((name.contains("icon") && name.contains("url"))
                    || "src".equals(name) || "data-src".equals(name)
                    || "data-original".equals(name)) {
                light = url;
            }
        }
        return new EmojiUrls(light, dark);
    }

    private static String attribute(String attrs, String expected) {
        Matcher attr = ATTRIBUTE.matcher(attrs);
        String target = expected.toLowerCase(Locale.ROOT);
        while (attr.find()) {
            String name = attr.group(1).toLowerCase(Locale.ROOT).replace('_', '-');
            if (target.equals(name)) return decodeHtml(attr.group(3)).replace("\\/", "/");
        }
        return "";
    }

    private static String token(String label) {
        String clean = label == null ? "" : label.replaceAll("[\\[\\]\\s]+", "");
        return "[" + (clean.isEmpty() ? "表情" : clean) + "]";
    }

    private static boolean looksEmojiUrl(String url) {
        if (url == null) return false;
        String value = url.toLowerCase(Locale.ROOT);
        return value.contains("/emoji/") || value.contains("emoji")
                || value.contains("icon-url");
    }

    private static String decodeAttributeEntities(String value) {
        if (value == null) return "";
        String decoded = value;
        for (int i = 0; i < 2; i++) {
            String next = decoded
                    .replace("&lt;", "<")
                    .replace("&LT;", "<")
                    .replace("&#60;", "<")
                    .replace("&#x3c;", "<")
                    .replace("&#X3C;", "<")
                    .replace("&gt;", ">")
                    .replace("&GT;", ">")
                    .replace("&#62;", ">")
                    .replace("&#x3e;", ">")
                    .replace("&#X3E;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#34;", "\"")
                    .replace("&#x22;", "\"")
                    .replace("&#X22;", "\"")
                    .replace("&apos;", "'")
                    .replace("&#39;", "'")
                    .replace("&amp;", "&")
                    .replace("&AMP;", "&");
            if (next.equals(decoded)) break;
            decoded = next;
        }
        return decoded;
    }

    private static void addText(List<Block> blocks, String html) {
        String value = decodeHtml(html)
                .replace('\u00a0', ' ')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        value = stripStructuredNoise(value);
        if (isStructuredNoise(value)) return;
        if (value.isEmpty()) return;
        for (Block block : blocks) {
            if (!block.image && value.equals(block.value)) return;
        }
        blocks.add(new Block(false, value));
    }

    private static String stripStructuredNoise(String source) {
        String value = source == null ? "" : source.trim();
        if (value.isEmpty()) return "";
        int marker = noiseMarker(value);
        if (marker == 0) return "";
        if (marker > 0) {
            value = value.substring(0, marker).trim();
            value = value.replaceAll("[\\s,，;；:：\"'“”]+$", "").trim();
        }
        return value;
    }

    private static int noiseMarker(String value) {
        int marker = -1;
        String[] candidates = {
                "\",\"type\"", "\"type\":", "\"url\":", "\"height\":",
                "\"width\":", "{\"height\"", "{\"width\"", "{\"url\"",
                "{\"type\"", "},{", "[{", "imageMogr2/", "imageMogr/"
        };
        for (String candidate : candidates) {
            int index = value.indexOf(candidate);
            if (index >= 0 && (marker < 0 || index < marker)) marker = index;
        }
        return marker;
    }

    private static boolean isStructuredNoise(String value) {
        if (value == null) return true;
        String clean = value.trim();
        if (clean.isEmpty()) return true;
        String compact = clean.replaceAll("\\s+", "");
        return compact.length() <= 1
                || compact.startsWith("{") || compact.startsWith("[")
                || compact.startsWith("},") || compact.startsWith("\",")
                || compact.startsWith("imageMogr") || compact.startsWith("?imageMogr")
                || compact.contains("\"type\":\"img\"")
                || compact.contains("\"url\":\"")
                || compact.contains("\"height\":\"")
                || compact.contains("\"width\":\"")
                || (compact.startsWith("https://") && compact.contains("imgheybox")
                && compact.contains("/thumb."));
    }

    private static void addImage(List<Block> blocks, Set<String> imageUrls, String url) {
        if (url == null) return;
        String value = decodeHtml(url).replace("\\/", "/").trim();
        if (value.regionMatches(true, 0, "http:", 0, 5)) value = "https:" + value.substring(5);
        if (!value.startsWith("https://")) return;
        if (imageUrls.add(value)) blocks.add(new Block(true, value));
    }

    private static String decodeHtml(String value) {
        if (value == null || value.isEmpty()) return "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString();
        }
        return legacyFromHtml(value);
    }

    @SuppressWarnings("deprecation")
    private static String legacyFromHtml(String value) {
        return Html.fromHtml(value).toString();
    }

    private static String decodeTransport(String value) {
        if (value == null || value.isEmpty()) return "";
        String decoded = value
                .replace("\\u003c", "<")
                .replace("\\u003C", "<")
                .replace("\\u003e", ">")
                .replace("\\u003E", ">")
                .replace("\\u0026", "&")
                .replace("\\\"", "\"")
                .replace("\\/", "/");
        for (int i = 0; i < 2; i++) {
            String lower = decoded.toLowerCase(Locale.ROOT);
            if (!lower.contains("%3c") && !lower.contains("%5b")
                    && !lower.contains("%7b")) break;
            try {
                String next = URLDecoder.decode(decoded.replace("+", "%2B"),
                        "UTF-8");
                if (next.equals(decoded)) break;
                decoded = next;
            } catch (Exception ignored) {
                break;
            }
        }
        return decoded;
    }

    private static String decodeJsonTransport(String value) {
        if (value == null || value.isEmpty()) return "";
        String decoded = value
                .replace("\\u003c", "<")
                .replace("\\u003C", "<")
                .replace("\\u003e", ">")
                .replace("\\u003E", ">")
                .replace("\\u0026", "&")
                .replace("\\/", "/");
        for (int i = 0; i < 2; i++) {
            String lower = decoded.toLowerCase(Locale.ROOT);
            if (!lower.contains("%3c") && !lower.contains("%5b")
                    && !lower.contains("%7b")) break;
            try {
                String next = URLDecoder.decode(decoded.replace("+", "%2B"),
                        "UTF-8");
                if (next.equals(decoded)) break;
                decoded = next;
            } catch (Exception ignored) {
                break;
            }
        }
        return decoded;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }

    private static final class EmojiUrls {
        final String light;
        final String dark;

        EmojiUrls(String light, String dark) {
            this.light = light == null ? "" : light;
            this.dark = dark == null ? "" : dark;
        }

        boolean hasUrl() {
            return !light.isEmpty() || !dark.isEmpty();
        }
    }

    private static final class ParseResult {
        final List<Block> blocks = new ArrayList<>();
        final Set<String> imageUrls = new HashSet<>();
    }
}
