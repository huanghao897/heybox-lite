package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

final class DetailDiagnostics {
    private static final int COMMENT_LIMIT = 5;

    private DetailDiagnostics() {
    }

    static String build(String screen, String currentLinkId, boolean playGif,
                        JSONObject body, FeedItem fallback, JSONObject link,
                        JSONArray fallbackImages, JSONArray comments) {
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
        appendLink(out, link, fallbackImages);
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
                                   JSONArray fallbackImages) {
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
        out.append("has imgs: ").append(link.has("imgs"))
                .append(" count=").append(images == null ? 0 : images.length())
                .append('\n');
        out.append(RichContent.diagnostics(link, fallbackImages));
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
                    .append(" url=").append(compactText(image.url, 100))
                    .append(']');
        }
        out.append('\n');
    }
}
