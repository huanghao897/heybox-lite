package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

/** Formats bounded parser diagnostics without adding policy to RichContent. */
final class RichContentDiagnostics {
    private RichContentDiagnostics() {
    }

    static String build(JSONObject source, JSONArray fallbackImages) {
        StringBuilder out = new StringBuilder();
        out.append("RichContent diagnostics\n");
        if (source == null) {
            out.append("source: null\n");
            out.append("fallbackImages: ").append(length(fallbackImages)).append('\n');
            return out.toString();
        }
        boolean articleMode = FeedItem.isArticleJson(source);
        out.append("articleMode: ").append(articleMode).append('\n');
        out.append("use_concept_type: ").append(source.optInt("use_concept_type", -1))
                .append('\n');
        out.append("is_article: ").append(source.opt("is_article")).append('\n');
        out.append("link_type: ").append(source.opt("link_type")).append('\n');
        out.append("content_type: ").append(source.opt("content_type")).append('\n');
        out.append("fallbackImages: ").append(length(fallbackImages)).append('\n');
        appendFallbackImages(out, fallbackImages);
        appendSourceKeys(out, source);

        out.append("\nbody candidates:\n");
        for (String key : RichContent.DETAIL_BODY_KEYS) {
            if (!source.has(key)) continue;
            Object raw = source.opt(key);
            RichContent.ParseResult candidate = RichContent.parseDetailBody(raw, articleMode);
            out.append("- ").append(key)
                    .append(" type=").append(typeName(raw))
                    .append(" rawLen=").append(rawLength(raw))
                    .append(" blocks=").append(candidate.blocks.size())
                    .append(" textBlocks=").append(RichContent.textCount(candidate.blocks))
                    .append(" images=").append(RichContent.imageCount(candidate.blocks))
                    .append(" games=").append(RichContentSupport.gameCount(candidate.blocks))
                    .append(" readable=").append(RichContent.readableLength(candidate.blocks))
                    .append(" score=").append(
                            RichContent.bodyScore(candidate, key, articleMode))
                    .append('\n');
            if (raw instanceof String) {
                out.append("  rawPreview: ").append(brief((String) raw, 180)).append('\n');
            }
            appendBlocks(out, candidate.blocks, 18, "  ");
        }

        List<RichContent.Block> finalBlocks = RichContent.parse(source, fallbackImages);
        out.append("\nfinal blocks:\n")
                .append("blocks=").append(finalBlocks.size())
                .append(" textBlocks=").append(RichContent.textCount(finalBlocks))
                .append(" images=").append(RichContent.imageCount(finalBlocks))
                .append(" games=").append(RichContentSupport.gameCount(finalBlocks))
                .append(" readable=").append(RichContent.readableLength(finalBlocks))
                .append(" trailingImageRun=").append(trailingImageRun(finalBlocks))
                .append('\n');
        appendBlocks(out, finalBlocks, 100, "  ");
        return out.toString();
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
                    ? RichContent.firstImage((JSONObject) value) : fallbackImages.optString(i);
            out.append("  fallback[").append(i).append("] ")
                    .append(brief(RichContent.imageKey(url), 180)).append('\n');
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

    private static void appendBlocks(StringBuilder out, List<RichContent.Block> blocks,
                                     int limit, String prefix) {
        if (blocks == null || blocks.isEmpty()) return;
        int count = Math.min(blocks.size(), limit);
        for (int i = 0; i < count; i++) {
            RichContent.Block block = blocks.get(i);
            out.append(prefix).append('[').append(i).append("] ");
            if (block.image) {
                out.append("IMG key=").append(brief(RichContent.imageKey(block.value), 180))
                        .append(" url=").append(brief(block.value, 220));
            } else if (RichGameCardParser.isCard(block)) {
                out.append("GAME appid=").append(brief(block.value, 80));
            } else {
                String label = block.kind == RichContent.Block.HEADING ? "HEAD"
                        : block.kind == RichContent.Block.CAPTION ? "CAP"
                        : block.kind == RichContent.Block.QUOTE ? "QUOTE" : "TXT";
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

    private static int trailingImageRun(List<RichContent.Block> blocks) {
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
}
