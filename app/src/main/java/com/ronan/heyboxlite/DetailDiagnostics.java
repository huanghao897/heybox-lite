package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class DetailDiagnostics {
    private static final int COMMENT_LIMIT = 5;

    private DetailDiagnostics() {
    }

    static String build(String screen, String currentLinkId, boolean playGif,
                        JSONObject body, FeedItem fallback, JSONObject link,
                        JSONArray fallbackImages, JSONArray comments,
                        List<RichContent.Block> contentBlocks) {
        return build(screen, currentLinkId, playGif, body, fallback, link,
                fallbackImages, comments, contentBlocks, Collections.emptyList());
    }

    static String build(String screen, String currentLinkId, boolean playGif,
                        JSONObject body, FeedItem fallback, JSONObject link,
                        JSONArray fallbackImages, JSONArray comments,
                        List<RichContent.Block> contentBlocks,
                        List<VideoData> videos) {
        StringBuilder out = new StringBuilder();
        out.append("detail screen: ").append(screen == null ? "" : screen).append('\n');
        out.append("currentLinkId: ")
                .append(currentLinkId == null ? "" : currentLinkId)
                .append('\n');
        out.append("fallbackId: ").append(fallback == null ? "" : fallback.id).append('\n');
        out.append("fallbackTitle: ")
                .append(compactText(fallback == null ? "" : fallback.title, 180))
                .append('\n');
        out.append("fallbackArticle: ").append(fallback != null && fallback.article).append('\n');
        out.append("playGif: ").append(playGif).append('\n');
        appendBodyKeys(out, body);
        appendLink(out, link, fallbackImages, contentBlocks, videos);
        appendComments(out, comments);
        return out.toString();
    }

    static String compactText(String value, int maxLength) {
        String clean = value == null
                ? ""
                : value.replace('\n', ' ')
                        .replace('\r', ' ')
                        .replaceAll("\\s+", " ")
                        .trim();
        int limit = Math.max(0, maxLength);
        return clean.length() <= limit ? clean : clean.substring(0, limit) + "...";
    }

    private static void appendBodyKeys(StringBuilder out, JSONObject body) {
        out.append("bodyKeys: ");
        if (body != null) {
            Iterator<String> keys = body.keys();
            while (keys.hasNext()) {
                out.append(keys.next()).append(' ');
            }
        }
        out.append('\n');
    }

    private static void appendLink(StringBuilder out, JSONObject link,
                                   JSONArray fallbackImages,
                                   List<RichContent.Block> contentBlocks,
                                   List<VideoData> videos) {
        if (link == null) {
            out.append("link: null\n");
            return;
        }
        JSONArray images = link.optJSONArray("imgs");
        out.append("linkid: ")
                .append(link.optString("linkid", link.optString("link_id")))
                .append('\n');
        out.append("title: ").append(compactText(link.optString("title"), 180)).append('\n');
        out.append("use_concept_type: ").append(link.opt("use_concept_type")).append('\n');
        out.append("is_article: ").append(link.opt("is_article")).append('\n');
        out.append("link_type: ").append(link.opt("link_type")).append('\n');
        out.append("content_type: ").append(link.opt("content_type")).append('\n');
        out.append("article game metadata: details=")
                .append(ArticleGameCards.count(link))
                .append(" tagIds=").append(arrayLength(link, "game_tag_appids"))
                .append('\n');
        out.append("article game fields: ")
                .append(ArticleGameCards.fieldSummary(link)).append('\n');
        appendTypedTextSummary(out, link.opt("text"));
        out.append("has imgs: ").append(link.has("imgs"))
                .append(" count=").append(images == null ? 0 : images.length())
                .append('\n');
        int playable = 0;
        int covers = 0;
        if (videos != null) {
            for (VideoData video : videos) {
                if (video.playable()) playable++;
                if (!video.cover.isEmpty()) covers++;
            }
        }
        out.append("video candidates: ").append(videos == null ? 0 : videos.size())
                .append(" playable=").append(playable)
                .append(" covers=").append(covers)
                .append('\n');
        appendParsedContent(out, contentBlocks);
    }

    private static int arrayLength(JSONObject object, String key) {
        JSONArray value = object == null ? null : object.optJSONArray(key);
        return value == null ? 0 : value.length();
    }

    private static void appendTypedTextSummary(StringBuilder out, Object raw) {
        if (raw == null || raw == JSONObject.NULL) {
            out.append("text payload: missing\n");
            return;
        }
        TypeSummary summary = new TypeSummary();
        inspectTypedText(raw, summary, 0);
        out.append("text payload: type=").append(typeName(raw))
                .append(" rawLen=").append(rawLength(raw))
                .append(" objects=").append(summary.objects)
                .append(" typed=").append(summary.typed)
                .append(" game=").append(summary.game)
                .append(" game_card=").append(summary.gameCard)
                .append(" appids=").append(summary.appids)
                .append(" gamesArrays=").append(summary.gamesArrays)
                .append(" gameItems=").append(summary.gameItems)
                .append(" types=").append(summary.types())
                .append('\n');
    }

    private static void inspectTypedText(Object raw, TypeSummary summary, int depth) {
        if (raw == null || raw == JSONObject.NULL || depth > 12
                || summary.objects >= 500) return;
        if (raw instanceof JSONObject) {
            JSONObject object = (JSONObject) raw;
            summary.objects++;
            String type = object.optString("type", "").trim().toLowerCase(Locale.ROOT);
            if (!type.isEmpty()) {
                summary.typed++;
                if ("game".equals(type)) summary.game++;
                if ("game_card".equals(type) || "gamecard".equals(type)) {
                    summary.gameCard++;
                }
            }
            summary.addType(type);
            Object games = object.opt("games");
            if (games instanceof JSONArray) {
                summary.gamesArrays++;
                summary.gameItems += ((JSONArray) games).length();
            }
            if (!object.optString("appid", "").trim().isEmpty()
                    || !object.optString("app_id", "").trim().isEmpty()
                    || !object.optString("appId", "").trim().isEmpty()) {
                summary.appids++;
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) inspectTypedText(object.opt(keys.next()), summary, depth + 1);
        } else if (raw instanceof JSONArray) {
            JSONArray array = (JSONArray) raw;
            for (int i = 0; i < array.length(); i++) {
                inspectTypedText(array.opt(i), summary, depth + 1);
            }
        } else if (raw instanceof String) {
            String value = RichTransportDecoder.decodeJson((String) raw).trim();
            if (value.isEmpty()) return;
            try {
                inspectTypedText(new JSONArray(value), summary, depth + 1);
                return;
            } catch (Exception ignored) {
            }
            try {
                inspectTypedText(new JSONObject(value), summary, depth + 1);
            } catch (Exception ignored) {
                String unescaped = value.replace("\\\"", "\"");
                if (!unescaped.equals(value)) {
                    try {
                        inspectTypedText(new JSONArray(unescaped), summary, depth + 1);
                    } catch (Exception ignoredArray) {
                        try {
                            inspectTypedText(new JSONObject(unescaped), summary, depth + 1);
                        } catch (Exception ignoredObject) {
                            // Keep diagnostics structural and bounded.
                        }
                    }
                }
            }
        }
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static int rawLength(Object value) {
        return value == null ? 0 : String.valueOf(value).length();
    }

    private static final class TypeSummary {
        int objects;
        int typed;
        int game;
        int gameCard;
        int appids;
        int gamesArrays;
        int gameItems;
        final java.util.LinkedHashMap<String, Integer> typeCounts =
                new java.util.LinkedHashMap<>();

        void addType(String type) {
            if (type == null || type.isEmpty()) return;
            Integer count = typeCounts.get(type);
            typeCounts.put(type, count == null ? 1 : count + 1);
        }

        String types() {
            StringBuilder value = new StringBuilder();
            for (java.util.Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
                if (value.length() > 0) value.append(',');
                if (value.length() > 180) {
                    value.append("...");
                    break;
                }
                value.append(entry.getKey()).append(':').append(entry.getValue());
            }
            return value.toString();
        }
    }

    private static void appendParsedContent(StringBuilder out,
                                            List<RichContent.Block> blocks) {
        int textCount = 0;
        int imageCount = 0;
        int gameCount = 0;
        int inlineGameCount = 0;
        int readableLength = 0;
        if (blocks != null) {
            for (RichContent.Block block : blocks) {
                if (block.image) {
                    imageCount++;
                } else if (RichGameCardParser.isCard(block)) {
                    gameCount++;
                } else {
                    for (RichGameLinkMarkup.Link link :
                            RichGameLinkMarkup.parse(block.value).links) {
                        if (link.game != null && link.game.valid()) inlineGameCount++;
                    }
                    textCount++;
                    readableLength += compactText(block.value, Integer.MAX_VALUE).length();
                }
            }
        }
        out.append("parsed blocks: ").append(blocks == null ? 0 : blocks.size())
                .append(" text=").append(textCount)
                .append(" images=").append(imageCount)
                .append(" games=").append(gameCount)
                .append(" inlineGames=").append(inlineGameCount)
                .append(" readable=").append(readableLength)
                .append('\n');
    }

    private static void appendComments(StringBuilder out, JSONArray groups) {
        out.append("\ncomment diagnostics:\n");
        if (groups == null) {
            out.append("groups: null\n");
            return;
        }
        out.append("groups: ").append(groups.length()).append('\n');
        int limit = Math.min(groups.length(), COMMENT_LIMIT);
        for (int i = 0; i < limit; i++) {
            JSONObject group = groups.optJSONObject(i);
            JSONArray thread = group == null ? null : group.optJSONArray("comment");
            JSONObject comment = thread == null ? group : thread.optJSONObject(0);
            if (comment == null) {
                continue;
            }
            appendComment(out, i, comment);
        }
    }

    private static void appendComment(StringBuilder out, int index, JSONObject comment) {
        String parsed = RichContent.commentText(
                comment.optString("text"),
                comment.optString("content"),
                comment.optString("html"),
                comment.optString("description"),
                comment.optString("desc_extra"),
                comment.optString("rich_text"),
                comment.optString("hb_rich_texts"));
        out.append('[').append(index).append("] id=")
                .append(CommentData.commentId(comment))
                .append(" parsed=").append(compactText(parsed, 180)).append('\n');
        out.append("  text=").append(compactText(comment.optString("text"), 180)).append('\n');

        List<CommentData.CommentImage> images = CommentData.commentImages(comment);
        out.append("  images=").append(images.size());
        for (CommentData.CommentImage image : images) {
            out.append(" [mime=").append(image.mimeType)
                    .append(" animated=").append(image.animated)
                    .append(" preview=").append(compactText(image.previewUrl, 100))
                    .append(" original=").append(compactText(image.originalUrl, 100))
                    .append(']');
        }
        out.append('\n');
    }
}
