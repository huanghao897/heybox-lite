package com.ronan.heyboxlite;

import static com.ronan.heyboxlite.RichContentJsonParser.MAX_JSON_DEPTH;
import static com.ronan.heyboxlite.RichContentJsonParser.addFallbackImages;
import static com.ronan.heyboxlite.RichContentJsonParser.looksStructured;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Stable rich-content entry points and shared block models. */
final class RichContent {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    static final class Block {
        static final int TEXT = 0;
        static final int IMAGE = 1;
        static final int HEADING = 2;
        static final int CAPTION = 3;
        static final int QUOTE = 4;
        static final int GAME_CARD = 5;

        final boolean image;
        final int kind;
        final String value;
        final JSONObject gameObject;

        Block(int kind, String value) {
            this(kind, value, null);
        }

        private Block(int kind, String value, JSONObject gameObject) {
            this.kind = kind;
            this.image = kind == IMAGE;
            this.value = value;
            this.gameObject = gameObject;
        }

        static Block gameCard(JSONObject object, String appId) {
            return new Block(GAME_CARD, appId == null ? "" : appId, object);
        }
        static Block text(String value) { return new Block(TEXT, value == null ? "" : value); }
    }

    static final String[] DETAIL_BODY_KEYS = {
            "text", "article_text", "articleText",
            "article_content", "articleContent", "content_v2", "contentV2",
            "content_list", "contentList", "rich_text", "richText",
            "content_html", "contentHtml", "body", "body_text", "bodyText",
            "raw_content", "rawContent", "raw_html", "rawHtml",
            "content_attrs", "contentAttrs", "content_attr", "contentAttr",
            "hb_rich_texts", "hbRichTexts", "rich_texts", "richTexts",
            "article_rich_texts", "articleRichTexts"
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
        return RichContentDiagnostics.build(source, fallbackImages);
    }

    static List<Block> parse(JSONObject source, JSONArray fallbackImages) {
        return RichContentRecovery.parseObject(source, fallbackImages);
    }

    static List<Block> parseInternal(JSONObject source, JSONArray fallbackImages) {
        return RichContentBodyParser.parse(source, fallbackImages);
    }

    static int bodyScore(ParseResult result, String key, boolean articleMode) {
        return RichContentBodyParser.bodyScore(result, key, articleMode);
    }

    static int textCount(List<Block> blocks) {
        if (blocks == null) return 0;
        int count = 0;
        for (Block block : blocks) if (RichContentSupport.isReadableBlock(block)) count++;
        return count;
    }

    static ParseResult parseDetailBody(Object value, boolean articleMode) {
        return RichContentBodyParser.parseDetailBody(value, articleMode);
    }

    static boolean hasReadableText(List<Block> blocks) {
        return readableLength(blocks) >= 4;
    }

    static boolean hasGameCards(List<Block> blocks) {
        return RichContentSupport.hasGameCards(blocks);
    }

    static int imageCount(List<Block> blocks) {
        if (blocks == null) return 0;
        int count = 0;
        for (Block block : blocks) if (block.image) count++;
        return count;
    }

    static int readableLength(List<Block> blocks) {
        if (blocks == null) return 0;
        int length = 0;
        for (Block block : blocks) {
            if (RichContentSupport.isReadableBlock(block)) {
                length += WHITESPACE.matcher(block.value).replaceAll("").length();
            }
        }
        return length;
    }

    static List<Block> parse(String source, JSONArray fallbackImages) {
        return RichContentRecovery.parseText(source, fallbackImages);
    }

    static List<Block> parseTextInternal(String source, JSONArray fallbackImages) {
        return RichContentJsonParser.parseText(source, fallbackImages);
    }

    static List<Block> fallbackBlocks(JSONArray fallbackImages) {
        List<Block> blocks = new ArrayList<>();
        addFallbackImages(blocks, new HashSet<>(), fallbackImages);
        return blocks;
    }

    static String firstText(JSONObject item) {
        for (String key : TEXT_KEYS) {
            Object value = item.opt(key);
            if (value instanceof String && !((String) value).isEmpty()) return (String) value;
        }
        return "";
    }

    static String firstImage(JSONObject item) {
        return firstImage(item, 0);
    }

    static String firstImage(JSONObject item, int depth) {
        if (item == null || depth > MAX_JSON_DEPTH) return "";
        for (String key : IMAGE_KEYS) {
            Object value = item.opt(key);
            String found = imageValue(value, depth + 1);
            if (!found.isEmpty()) return found;
        }
        return "";
    }

    private static String imageValue(Object value, int depth) {
        if (depth > MAX_JSON_DEPTH) return "";
        if (value instanceof String) return (String) value;
        if (value instanceof JSONObject) return firstImage((JSONObject) value, depth + 1);
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                String found = imageValue(array.opt(i), depth + 1);
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }

    static String plainText(String source) {
        List<Block> blocks = parse(source, null);
        StringBuilder value = new StringBuilder();
        for (Block block : blocks) {
            if (!RichContentSupport.isReadableBlock(block) || block.value.isEmpty()) continue;
            if (value.length() > 0) value.append('\n');
            value.append(block.value);
        }
        return RichGameLinkMarkup.plainText(value.toString().trim());
    }

    static String commentText(String... sources) {
        String best = "";
        int bestScore = -1;
        for (String source : sources) {
            if (source == null || source.isEmpty()) continue;
            String value = inlineText(source);
            int score = inlineScore(value);
            if (score > bestScore || (score == bestScore && RichGameLinkMarkup.hasMarkup(value))) {
                best = value;
                bestScore = score;
            }
        }
        return best;
    }

    private static String inlineText(String source) {
        String decoded = RichGameLinkMarkup.normalizeAnchors(
                RichInlineMarkup.decodeAttributeEntities(RichTransportDecoder.decode(source)));
        if (decoded.indexOf('<') < 0 && !looksStructured(decoded)) {
            return normalizeInlineText(decoded);
        }
        List<Block> blocks = parse(source, null);
        StringBuilder value = new StringBuilder();
        for (Block block : blocks) {
            if (!RichContentSupport.isReadableBlock(block) || block.value.isEmpty()) continue;
            if (value.length() > 0) value.append(' ');
            value.append(block.value);
        }
        return normalizeInlineText(value.toString());
    }

    private static String normalizeInlineText(String value) {
        return value
                .replace('\u00a0', ' ')
                .replaceAll("[ \\t]*\\n[ \\t]*", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static int inlineScore(String value) {
        if (value == null || value.isEmpty()) return 0;
        return RichGameLinkMarkup.plainText(value).replaceAll("\\s+", "").length();
    }

    static String imageKey(String value) {
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

    static final class ParseResult {
        final List<Block> blocks = new ArrayList<>();
        final Set<String> imageUrls = new HashSet<>();
    }
}
