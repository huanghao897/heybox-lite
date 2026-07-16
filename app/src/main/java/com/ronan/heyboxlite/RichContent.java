package com.ronan.heyboxlite;

import android.text.Html;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RichContent {
    static final class Block {
        static final int TEXT = 0;
        static final int IMAGE = 1;
        static final int HEADING = 2;
        static final int CAPTION = 3;
        static final int QUOTE = 4;

        final boolean image;
        final int kind;
        final String value;

        private Block(int kind, String value) {
            this.kind = kind;
            this.image = kind == IMAGE;
            this.value = value;
        }
    }

    private static final Pattern IMAGE = Pattern.compile("(?is)<img\\b[^>]*>");
    private static final Pattern HEADING_TAG = Pattern.compile(
            "(?is)<(h[1-6]|figcaption)\\b[^>]*>(.*?)</\\1>");
    private static final Pattern BLOCKQUOTE_TAG = Pattern.compile(
            "(?is)<blockquote\\b[^>]*>(.*?)</blockquote>");
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
            "commentText", "comment_text", "text", "content", "html", "value", "caption",
            "title", "desc", "description"
    };
    private static final String[] IMAGE_KEYS = {
            "url", "src", "original", "origin", "origin_url", "original_url",
            "image", "image_url", "img", "img_url", "pic", "pic_url",
            "cover", "cover_url", "thumb", "thumbnail", "large", "large_url",
            "images", "pictures"
    };

    private RichContent() {}

    static String diagnostics(JSONObject source, JSONArray fallbackImages) {
        StringBuilder out = new StringBuilder();
        out.append("RichContent diagnostics\n");
        if (source == null) {
            out.append("source: null\n");
            out.append("fallbackImages: ").append(length(fallbackImages)).append('\n');
            return out.toString();
        }
        boolean articleMode = source.optInt("use_concept_type", -1) == 0
                || source.optBoolean("is_article", false);
        out.append("articleMode: ").append(articleMode).append('\n');
        out.append("use_concept_type: ").append(source.optInt("use_concept_type", -1)).append('\n');
        out.append("is_article: ").append(source.opt("is_article")).append('\n');
        out.append("link_type: ").append(source.opt("link_type")).append('\n');
        out.append("content_type: ").append(source.opt("content_type")).append('\n');
        out.append("fallbackImages: ").append(length(fallbackImages)).append('\n');
        appendFallbackImages(out, fallbackImages);
        appendSourceKeys(out, source);

        out.append("\nbody candidates:\n");
        for (String key : DETAIL_BODY_KEYS) {
            if (!source.has(key)) continue;
            Object raw = source.opt(key);
            ParseResult candidate = parseDetailBody(raw, articleMode);
            out.append("- ").append(key)
                    .append(" type=").append(typeName(raw))
                    .append(" rawLen=").append(rawLength(raw))
                    .append(" blocks=").append(candidate.blocks.size())
                    .append(" textBlocks=").append(textCount(candidate.blocks))
                    .append(" images=").append(imageCount(candidate.blocks))
                    .append(" readable=").append(readableLength(candidate.blocks))
                    .append(" score=").append(bodyScore(candidate, key, articleMode))
                    .append('\n');
            if (raw instanceof String) {
                out.append("  rawPreview: ").append(brief((String) raw, 180)).append('\n');
            }
            appendBlocks(out, candidate.blocks, 18, "  ");
        }

        List<Block> finalBlocks = parse(source, fallbackImages);
        out.append("\nfinal blocks:\n")
                .append("blocks=").append(finalBlocks.size())
                .append(" textBlocks=").append(textCount(finalBlocks))
                .append(" images=").append(imageCount(finalBlocks))
                .append(" readable=").append(readableLength(finalBlocks))
                .append(" trailingImageRun=").append(trailingImageRun(finalBlocks))
                .append('\n');
        appendBlocks(out, finalBlocks, 100, "  ");
        return out.toString();
    }

    static List<Block> parse(JSONObject source, JSONArray fallbackImages) {
        ParseResult result = new ParseResult();
        if (source == null) {
            addFallbackImages(result.blocks, result.imageUrls, fallbackImages);
            return result.blocks;
        }

        boolean articleMode = source.optInt("use_concept_type", -1) == 0
                || source.optBoolean("is_article", false);
        ParseResult bestReadable = null;
        int bestScore = Integer.MIN_VALUE;
        for (String key : DETAIL_BODY_KEYS) {
            ParseResult candidate = parseDetailBody(source.opt(key), articleMode);
            if (hasReadableBody(candidate)) {
                if (articleMode && "text".equalsIgnoreCase(key)) {
                    return candidate.blocks;
                }
                int score = bodyScore(candidate, key, articleMode);
                if (score > bestScore) {
                    bestReadable = candidate;
                    bestScore = score;
                }
                continue;
            }
            if (result.blocks.isEmpty() && candidate != null && !candidate.blocks.isEmpty()) {
                result = candidate;
            }
        }
        if (bestReadable != null) {
            addFallbackImagesIfNeeded(bestReadable, fallbackImages, !articleMode);
            return bestReadable.blocks;
        }

        ParseResult fallbackText = new ParseResult();
        for (String key : DETAIL_TEXT_FALLBACK_KEYS) {
            addAny(fallbackText.blocks, fallbackText.imageUrls, source.opt(key));
            if (readableLength(fallbackText.blocks) > 0) break;
        }
        if (readableLength(fallbackText.blocks) > 0) {
            mergeBlocks(fallbackText, result);
            addFallbackImagesIfNeeded(fallbackText, fallbackImages, !articleMode);
            return fallbackText.blocks;
        }

        addFallbackImagesIfNeeded(result, fallbackImages, true);
        return result.blocks;
    }

    private static int bodyScore(ParseResult result, String key, boolean articleMode) {
        int score = Math.min(readableLength(result.blocks), 8000);
        score += result.blocks.size() * 30;
        score += textCount(result.blocks) * 120;
        score += imageCount(result.blocks) * 2500;
        String name = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (name.contains("rich") || name.contains("attr")
                || name.contains("html") || name.contains("body")
                || name.contains("article") || name.contains("list")
                || name.contains("content_v2")) {
            score += 900;
        }
        if (articleMode && "text".equals(name) && imageCount(result.blocks) > 0) {
            score += 700;
        }
        if (imageCount(result.blocks) == 0 && ("text".equals(name)
                || "description".equals(name) || "summary".equals(name))) {
            score -= 500;
        }
        return score;
    }

    private static int textCount(List<Block> blocks) {
        if (blocks == null) return 0;
        int count = 0;
        for (Block block : blocks) if (!block.image) count++;
        return count;
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
            addCaption(result.blocks, imageCaption(item));
            return;
        }
        String text = firstText(item);
        boolean hasText = !text.isEmpty();
        if (hasText && isCaptionType(type)) {
            addCaption(result.blocks, text);
            return;
        }
        if (hasText && isHeadingType(type)) {
            addHeading(result.blocks, text);
            return;
        }
        if (hasText && isQuoteType(type)) {
            addQuote(result.blocks, text);
            return;
        }
        if (!text.isEmpty()) {
            addArticleHtml(result.blocks, result.imageUrls, text);
        }
        if (hasText && isSelfContainedTextType(type)) return;
        Object insert = item.opt("insert");
        if (insert != null) addInsert(result.blocks, result.imageUrls, insert);
        for (String key : CHILD_KEYS) {
            if (hasText && isMetadataChildKey(key)) continue;
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
                                                  JSONArray fallbackImages,
                                                  boolean allowWithText) {
        if (imageCount(result.blocks) == 0) {
            if (!allowWithText && hasReadableText(result.blocks)) return;
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
        result.blocks.add(new Block(Block.TEXT, value));
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
            addAny(blocks, imageUrls, item.opt(key));
        }
    }

    private static boolean isSelfContainedTextType(String type) {
        if (type == null) return false;
        String value = type.toLowerCase(Locale.ROOT);
        return value.equals("html") || value.equals("text")
                || value.equals("rich_text") || value.equals("richtext")
                || value.equals("paragraph") || value.equals("p");
    }

    private static boolean isMetadataChildKey(String key) {
        if (key == null) return false;
        String value = key.toLowerCase(Locale.ROOT).replace('_', '-');
        return value.equals("attrs") || value.equals("models") || value.equals("model")
                || value.equals("content-attrs") || value.equals("content-attr")
                || value.equals("raw-content") || value.equals("rawcontent");
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

    private static boolean isHeadingType(String type) {
        if (type == null || type.isEmpty()) return false;
        return type.matches("h[1-6]") || type.contains("header")
                || type.contains("heading");
    }

    private static boolean isCaptionType(String type) {
        if (type == null || type.isEmpty()) return false;
        return "h4".equals(type) || type.contains("caption");
    }

    private static boolean isQuoteType(String type) {
        return type != null && type.contains("quote");
    }

    private static String imageCaption(JSONObject item) {
        String caption = first(item.optString("caption"), item.optString("desc"),
                item.optString("description"), item.optString("title"));
        if (looksImageUrl(caption)) return "";
        return caption;
    }

    private static boolean hasReadableContent(JSONObject item) {
        String text = firstText(item);
        return text != null && text.replaceAll("\\s+", "").length() > 0;
    }

    static String firstText(JSONObject item) {
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
            if (imageCount(blocks) == imagesBefore) {
                addLooseImages(blocks, imageUrls, source);
            }
            return true;
        }
        if (looksStructured(source)) {
            addReadableFragment(blocks, imageUrls, source);
            if (imageCount(blocks) == imagesBefore) {
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

    static String commentText(String... sources) {
        String best = "";
        int bestScore = -1;
        for (String source : sources) {
            if (source == null || source.isEmpty()) continue;
            String value = inlineText(source);
            int score = inlineScore(value);
            if (score > bestScore) {
                best = value;
                bestScore = score;
            }
        }
        return best;
    }

    private static String inlineText(String source) {
        List<Block> blocks = parse(source, null);
        StringBuilder value = new StringBuilder();
        for (Block block : blocks) {
            if (block.image || block.value.isEmpty()) continue;
            if (value.length() > 0) value.append(' ');
            value.append(block.value);
        }
        return value.toString()
                .replace('\u00a0', ' ')
                .replaceAll("[ \\t]*\\n[ \\t]*", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static int inlineScore(String value) {
        if (value == null || value.isEmpty()) return 0;
        return value.replaceAll("\\s+", "").length();
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
            addArticleSegment(blocks, html.substring(start, matcher.start()));
            addImage(blocks, imageUrls, imageFromTag(matcher.group()));
            addCaption(blocks, captionFromTag(matcher.group()));
            start = matcher.end();
        }
        addArticleSegment(blocks, html.substring(start));
    }

    /** 先拆 blockquote（引用块），再拆 h1-h6 / figcaption，保留正文层级。 */
    private static void addArticleSegment(List<Block> blocks, String html) {
        if (html == null || html.isEmpty()) return;
        Matcher matcher = BLOCKQUOTE_TAG.matcher(html);
        int start = 0;
        while (matcher.find()) {
            addHeadingSegment(blocks, html.substring(start, matcher.start()));
            addQuote(blocks, matcher.group(1));
            start = matcher.end();
        }
        addHeadingSegment(blocks, html.substring(start));
    }

    private static void addHeadingSegment(List<Block> blocks, String html) {
        if (html == null || html.isEmpty()) return;
        Matcher matcher = HEADING_TAG.matcher(html);
        int start = 0;
        while (matcher.find()) {
            addArticleText(blocks, html.substring(start, matcher.start()));
            String tag = matcher.group(1).toLowerCase(Locale.ROOT);
            // 官方编辑器：h2/h3 是标题，h4 是图注（hb_editor.css 里 h4 = 12px 灰色居中）
            if (tag.startsWith("f") || "h4".equals(tag)) {
                addCaption(blocks, matcher.group(2));
            } else {
                addHeading(blocks, matcher.group(2));
            }
            start = matcher.end();
        }
        addArticleText(blocks, html.substring(start));
    }

    /** 引用块：保留内部换行，剥掉其余标签，渲染端显示为左竖线灰字。 */
    private static void addQuote(List<Block> blocks, String html) {
        if (html == null || html.isEmpty()) return;
        String value = html
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)<li\\b[^>]*>", "\n- ")
                .replaceAll("(?is)</(?:p|div|li|h[1-6])>", "\n")
                .replaceAll("(?is)<[^>]+>", "");
        value = decodeHtml(value)
                .replace('\u00a0', ' ')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (value.isEmpty() || isStructuredNoise(value)) return;
        blocks.add(new Block(Block.QUOTE, value));
    }

    /** 图片标签上直接携带的图注（编辑器写入的描述属性）。 */
    private static String captionFromTag(String tag) {
        if (tag == null || tag.isEmpty()) return "";
        String attrs = tag.replaceFirst("(?is)^\\s*<img\\b", "")
                .replaceFirst("(?is)/?>\\s*$", "");
        return first(attribute(attrs, "desc"),
                attribute(attrs, "data-desc"),
                attribute(attrs, "data-caption"),
                attribute(attrs, "caption"));
    }

    private static String imageFromTag(String tag) {
        if (tag == null || tag.isEmpty()) return "";
        String attrs = tag.replaceFirst("(?is)^\\s*<img\\b", "")
                .replaceFirst("(?is)/?>\\s*$", "");
        String found = first(attribute(attrs, "data-original"),
                attribute(attrs, "data-src"),
                attribute(attrs, "data-url"),
                attribute(attrs, "data-image"),
                attribute(attrs, "data-image-url"),
                attribute(attrs, "data-original-src"),
                attribute(attrs, "origin-url"),
                attribute(attrs, "original-url"),
                attribute(attrs, "origin-src"),
                attribute(attrs, "original-src"),
                attribute(attrs, "src"),
                attribute(attrs, "url"));
        if (!found.isEmpty()) return found;
        Matcher attr = ATTRIBUTE.matcher(attrs);
        while (attr.find()) {
            String name = attr.group(1).toLowerCase(Locale.ROOT).replace('_', '-');
            if (name.contains("url") || name.contains("src")
                    || name.contains("image") || name.contains("original")) {
                String value = decodeHtml(attr.group(3)).replace("\\/", "/");
                if (looksImageUrl(value)) return value;
            }
        }
        return "";
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
            String label = first(attribute(attrs, "alt"),
                    attribute(attrs, "data-name"),
                    attribute(attrs, "data-code"),
                    attribute(attrs, "data-key"),
                    attribute(attrs, "data-emoji-name"),
                    attribute(attrs, "data-emoji"),
                    attribute(attrs, "title"),
                    attribute(attrs, "name"));
            String light = first(urls.light, attribute(attrs, "src"),
                    attribute(attrs, "data-src"), attribute(attrs, "data-original"),
                    imageFromTag("<img " + attrs + ">"));
            if (!looksEmojiUrl(light) && !looksEmojiUrl(urls.dark)
                    && !attrs.toLowerCase(Locale.ROOT).contains("emoji")) {
                continue;
            }
            if (label.isEmpty()) label = emojiLabelFromUrl(light);
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
                || value.contains("icon-url")
                || value.contains("cube_")
                || value.contains("heygirl_");
    }

    private static String emojiLabelFromUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        String value = decodeHtml(url).replace("\\/", "/");
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int hash = value.indexOf('#');
        if (hash >= 0) value = value.substring(0, hash);
        int slash = value.lastIndexOf('/');
        String name = slash >= 0 ? value.substring(slash + 1) : value;
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.matches("(?i)(cube|heygirl)[-_].+") ? name : "";
    }

    private static boolean looksImageUrl(String url) {
        if (url == null) return false;
        String value = url.toLowerCase(Locale.ROOT);
        return value.startsWith("http://") || value.startsWith("https://")
                || value.startsWith("//")
                || value.contains("imgheybox")
                || value.contains("image")
                || value.contains(".jpg")
                || value.contains(".jpeg")
                || value.contains(".png")
                || value.contains(".webp")
                || value.contains(".gif");
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
        blocks.add(new Block(Block.TEXT, value));
    }

    /** 文章小标题（h1-h6 / 富文本 header 类型），渲染端会加粗放大显示。 */
    private static void addHeading(List<Block> blocks, String value) {
        String clean = cleanInlineText(value);
        if (clean.isEmpty() || clean.length() > 64 || isStructuredNoise(clean)) return;
        blocks.add(new Block(Block.HEADING, clean));
    }

    /** 图片图注，渲染端以灰色小字居中显示在图片下方。官方图注可以很长（几百字的翻译），不能按长度丢弃。 */
    private static void addCaption(List<Block> blocks, String value) {
        String clean = cleanInlineText(value);
        if (clean.isEmpty() || isStructuredNoise(clean)) return;
        if (looksImageUrl(clean)) return;
        // 异常超长的当正文段落兜底，正常长图注保留图注样式
        blocks.add(new Block(clean.length() > 800 ? Block.TEXT : Block.CAPTION, clean));
    }

    private static String cleanInlineText(String value) {
        if (value == null || value.isEmpty()) return "";
        String stripped = value.replaceAll("(?is)<[^>]+>", " ");
        return decodeHtml(stripped)
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
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
        if (value.startsWith("//")) value = "https:" + value;
        if (value.regionMatches(true, 0, "http:", 0, 5)) value = "https:" + value.substring(5);
        if (value.regionMatches(true, 0, "imgheybox", 0, 9)) value = "https://" + value;
        if (!value.startsWith("https://")) return;
        if (imageUrls.add(imageKey(value))) blocks.add(new Block(Block.IMAGE, value));
    }

    private static String imageKey(String value) {
        if (value == null) return "";
        String key = value.trim().replace("\\/", "/");
        if (key.startsWith("//")) key = "https:" + key;
        if (key.regionMatches(true, 0, "http:", 0, 5)) key = "https:" + key.substring(5);
        int query = key.indexOf('?');
        if (query >= 0) key = key.substring(0, query);
        int hash = key.indexOf('#');
        if (hash >= 0) key = key.substring(0, hash);
        key = key.replaceFirst("(?i)^https://imgheybox\\d*\\.", "https://imgheybox.");
        return key.toLowerCase(Locale.ROOT);
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

    private static int length(JSONArray array) {
        return array == null ? 0 : array.length();
    }

    private static void appendFallbackImages(StringBuilder out, JSONArray fallbackImages) {
        if (fallbackImages == null || fallbackImages.length() == 0) return;
        int count = Math.min(fallbackImages.length(), 40);
        for (int i = 0; i < count; i++) {
            Object value = fallbackImages.opt(i);
            String url = value instanceof JSONObject
                    ? firstImage((JSONObject) value) : fallbackImages.optString(i);
            out.append("  fallback[").append(i).append("] ")
                    .append(brief(imageKey(url), 180)).append('\n');
        }
        if (fallbackImages.length() > count) {
            out.append("  ... ").append(fallbackImages.length() - count)
                    .append(" more fallback images\n");
        }
    }

    private static void appendSourceKeys(StringBuilder out, JSONObject source) {
        out.append("sourceKeys:\n");
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = source.opt(key);
            out.append("  ").append(key)
                    .append(" type=").append(typeName(value))
                    .append(" len=").append(rawLength(value));
            if (value instanceof String) {
                out.append(" preview=").append(brief((String) value, 80));
            } else if (value instanceof JSONArray) {
                out.append(" count=").append(((JSONArray) value).length());
            } else if (value instanceof JSONObject) {
                out.append(" keys=").append(((JSONObject) value).length());
            }
            out.append('\n');
        }
    }

    private static void appendBlocks(StringBuilder out, List<Block> blocks, int limit,
                                     String prefix) {
        if (blocks == null || blocks.isEmpty()) return;
        int count = Math.min(blocks.size(), limit);
        for (int i = 0; i < count; i++) {
            Block block = blocks.get(i);
            out.append(prefix).append('[').append(i).append("] ");
            if (block.image) {
                out.append("IMG key=").append(brief(imageKey(block.value), 180))
                        .append(" url=").append(brief(block.value, 220));
            } else {
                String label = block.kind == Block.HEADING ? "HEAD"
                        : block.kind == Block.CAPTION ? "CAP"
                        : block.kind == Block.QUOTE ? "QUOTE" : "TXT";
                out.append(label).append(" len=")
                        .append(block.value == null ? 0 : block.value.length())
                        .append(" value=").append(brief(block.value, 220));
            }
            out.append('\n');
        }
        if (blocks.size() > count) {
            out.append(prefix).append("... ").append(blocks.size() - count)
                    .append(" more blocks\n");
        }
    }

    private static int trailingImageRun(List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) return 0;
        int count = 0;
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if (!blocks.get(i).image) break;
            count++;
        }
        return count;
    }

    private static String typeName(Object value) {
        if (value == null) return "null";
        if (value == JSONObject.NULL) return "json-null";
        if (value instanceof JSONArray) return "array";
        if (value instanceof JSONObject) return "object";
        if (value instanceof String) return "string";
        return value.getClass().getSimpleName();
    }

    private static int rawLength(Object value) {
        if (value == null || value == JSONObject.NULL) return 0;
        if (value instanceof String) return ((String) value).length();
        if (value instanceof JSONArray) return ((JSONArray) value).length();
        if (value instanceof JSONObject) return ((JSONObject) value).length();
        return String.valueOf(value).length();
    }

    private static String brief(String value, int max) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ')
                .replaceAll("\\s+", " ").trim();
        if (clean.length() <= max) return clean;
        return clean.substring(0, Math.max(0, max)) + "...";
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
