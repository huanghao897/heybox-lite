package com.ronan.heyboxlite;

import static com.ronan.heyboxlite.RichContent.DETAIL_BODY_KEYS;
import static com.ronan.heyboxlite.RichContent.hasReadableText;
import static com.ronan.heyboxlite.RichContent.imageCount;
import static com.ronan.heyboxlite.RichContent.readableLength;
import static com.ronan.heyboxlite.RichContent.textCount;
import static com.ronan.heyboxlite.RichContentHtmlParser.addArticleHtml;
import static com.ronan.heyboxlite.RichContentHtmlParser.addCaption;
import static com.ronan.heyboxlite.RichContentHtmlParser.addHeading;
import static com.ronan.heyboxlite.RichContentHtmlParser.addImage;
import static com.ronan.heyboxlite.RichContentHtmlParser.addQuote;
import static com.ronan.heyboxlite.RichContentJsonParser.CHILD_KEYS;
import static com.ronan.heyboxlite.RichContentJsonParser.MAX_JSON_DEPTH;
import static com.ronan.heyboxlite.RichContentJsonParser.addAny;
import static com.ronan.heyboxlite.RichContentJsonParser.addFallbackImages;
import static com.ronan.heyboxlite.RichContentJsonParser.addInsert;
import static com.ronan.heyboxlite.RichContentJsonParser.addStructured;
import static com.ronan.heyboxlite.RichContentJsonParser.detailImage;
import static com.ronan.heyboxlite.RichContentJsonParser.imageCaption;
import static com.ronan.heyboxlite.RichContentJsonParser.isCaptionType;
import static com.ronan.heyboxlite.RichContentJsonParser.isHeadingType;
import static com.ronan.heyboxlite.RichContentJsonParser.isImageType;
import static com.ronan.heyboxlite.RichContentJsonParser.isMetadataChildKey;
import static com.ronan.heyboxlite.RichContentJsonParser.isQuoteType;
import static com.ronan.heyboxlite.RichContentJsonParser.isSelfContainedTextType;

import com.ronan.heyboxlite.RichContent.Block;
import com.ronan.heyboxlite.RichContent.ParseResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/** Selects detail body candidates and preserves article-specific fallback rules. */
final class RichContentBodyParser {
    private static final String[] DETAIL_TEXT_FALLBACK_KEYS = {
            "description", "summary", "intro", "content"
    };

    private RichContentBodyParser() {}

    static List<Block> parse(JSONObject source, JSONArray fallbackImages) {
        ParseResult result = new ParseResult();
        if (source == null) {
            addFallbackImages(result.blocks, result.imageUrls, fallbackImages);
            return result.blocks;
        }

        boolean articleMode = FeedItem.isArticleJson(source);
        ParseResult bestReadable = null;
        int bestScore = Integer.MIN_VALUE;
        for (String key : DETAIL_BODY_KEYS) {
            ParseResult candidate = parseDetailBody(source.opt(key), articleMode);
            if (hasReadableBody(candidate)) {
                if (articleMode && "text".equalsIgnoreCase(key)) {
                    return candidate.blocks;
                }
                int score = bodyScore(candidate, key, articleMode);
                if (RichContentSupport.hasGameCards(candidate.blocks)) score += 10000;
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

    static int bodyScore(ParseResult result, String key, boolean articleMode) {
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

    static ParseResult parseDetailBody(Object value, boolean articleMode) {
        ParseResult result = new ParseResult();
        if (value == null || value == JSONObject.NULL) return result;
        if (value instanceof String) {
            String raw = (String) value;
            String jsonText = RichTransportDecoder.decodeJson(raw).trim();
            if (jsonText.isEmpty()) return result;
            if (addJsonContainer(result, jsonText, articleMode)) return result;
            if (addStructured(result.blocks, result.imageUrls, jsonText)
                    && !result.blocks.isEmpty()) {
                return result;
            }
            String text = RichTransportDecoder.decode(raw).trim();
            if (text.isEmpty()) return result;
            if (addStructured(result.blocks, result.imageUrls, text)
                    && !result.blocks.isEmpty()) {
                return result;
            }
            addArticleHtml(result.blocks, result.imageUrls, text);
            return result;
        }
        if (value instanceof JSONArray) {
            addDetailArray(result, (JSONArray) value, articleMode, 0);
        } else if (value instanceof JSONObject) {
            addDetailObject(result, (JSONObject) value, articleMode, 0);
        }
        return result;
    }

    private static boolean addJsonContainer(ParseResult result, String value,
                                            boolean articleMode) {
        String candidate = value;
        for (int pass = 0; pass < 2; pass++) {
            try {
                if (RichInlineMarkup.isLikelyJsonArray(candidate)) {
                    addDetailArray(result, new JSONArray(candidate), articleMode, 0);
                    return true;
                }
                if (candidate.startsWith("{")) {
                    addDetailObject(result, new JSONObject(candidate), articleMode, 0);
                    return true;
                }
            } catch (JSONException ignored) {
            }
            candidate = candidate.replace("\\\"", "\"");
        }
        return false;
    }

    private static void addDetailArray(ParseResult result, JSONArray array,
                                       boolean articleMode, int depth) {
        if (depth > MAX_JSON_DEPTH) return;
        for (int i = 0; i < array.length(); i++) {
            Object raw = array.opt(i);
            if (raw instanceof JSONObject) {
                addDetailObject(result, (JSONObject) raw, articleMode, depth + 1);
            } else if (raw instanceof JSONArray) {
                addDetailArray(result, (JSONArray) raw, articleMode, depth + 1);
            } else if (raw instanceof String) {
                addArticleHtml(result.blocks, result.imageUrls, (String) raw);
            }
        }
    }

    private static void addDetailObject(ParseResult result, JSONObject item,
                                        boolean articleMode, int depth) {
        if (depth > MAX_JSON_DEPTH) return;
        String type = item.optString("type").toLowerCase(Locale.ROOT);
        if (RichGameCardParser.isGameNode(item)) {
            result.blocks.add(RichGameCardParser.block(item));
            return;
        }
        if (isImageType(type)) {
            addImage(result.blocks, result.imageUrls, detailImage(item));
            addCaption(result.blocks, imageCaption(item));
            return;
        }
        if (type.contains("video")) return;
        String text = RichContent.firstText(item);
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
            String nested = RichTransportDecoder.decodeJson(text).trim();
            if ((nested.startsWith("[") || nested.startsWith("{")) && addJsonContainer(result, nested, articleMode)) return;
            addArticleHtml(result.blocks, result.imageUrls, text);
        }
        if (hasText && isSelfContainedTextType(type)) return;
        Object insert = item.opt("insert");
        if (insert != null) addInsert(result.blocks, result.imageUrls, insert, depth + 1);
        for (String key : CHILD_KEYS) {
            if (hasText && isMetadataChildKey(key)) continue;
            if (item.opt(key) instanceof String) continue;
            Object child = item.opt(key);
            if (child instanceof JSONObject) {
                addDetailObject(result, (JSONObject) child, articleMode, depth + 1);
            } else if (child instanceof JSONArray) {
                addDetailArray(result, (JSONArray) child, articleMode, depth + 1);
            }
        }
    }

    private static boolean hasReadableBody(ParseResult result) {
        return result != null && (hasReadableText(result.blocks)
                || RichContentSupport.hasGameCards(result.blocks));
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
            else if (RichGameCardParser.isCard(block)) target.blocks.add(block);
            else addTextBlock(target, block.value);
        }
    }

    private static void addTextBlock(ParseResult result, String value) {
        if (value == null || value.trim().isEmpty()) return;
        for (Block block : result.blocks) {
            if (!block.image && !RichGameCardParser.isCard(block)
                    && value.equals(block.value)) return;
        }
        result.blocks.add(new Block(Block.TEXT, value));
    }
}
