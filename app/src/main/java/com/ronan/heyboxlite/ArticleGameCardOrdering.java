package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Keeps article game cards in the order encoded by the web article markup. */
final class ArticleGameCardOrdering {
    private ArticleGameCardOrdering() {
    }

    static void merge(JSONObject source, List<RichContent.Block> result,
                      Set<String> existing) {
        List<RichContent.Block> webBlocks = orderedWebBlocks(source);
        if (webBlocks.isEmpty()) return;
        mergeBlocks(result, webBlocks, existing);
    }

    static void mergeBlocks(List<RichContent.Block> result,
                            List<RichContent.Block> webBlocks,
                            Set<String> existing) {
        if (result == null || webBlocks == null || existing == null) return;
        for (int index = 0; index < webBlocks.size(); index++) {
            RichContent.Block card = webBlocks.get(index);
            if (!RichGameCardParser.isCard(card)) continue;
            String id = card.value == null ? "" : card.value.trim();
            if (id.isEmpty() || !existing.add(id)) continue;
            int position = findRelativePosition(result, webBlocks, index);
            result.add(Math.max(0, Math.min(position, result.size())), card);
        }
    }

    private static List<RichContent.Block> orderedWebBlocks(JSONObject source) {
        if (source == null) return Collections.emptyList();
        List<String> candidates = new ArrayList<>();
        collectHtmlCandidates(source.opt("_article_web_view"), candidates, 0);
        collectHtmlCandidates(source.opt("web_content"), candidates, 0);
        collectHtmlCandidates(source.opt("webContent"), candidates, 0);
        List<RichContent.Block> best = Collections.emptyList();
        int bestScore = 0;
        for (String candidate : candidates) {
            List<RichContent.Block> parsed = RichContent.parse(candidate, null);
            int gameCount = RichContentSupport.gameCount(parsed);
            if (gameCount == 0) continue;
            int readableLength = 0;
            for (RichContent.Block block : parsed) {
                if (RichContentSupport.isReadableBlock(block)) {
                    readableLength += block.value == null ? 0 : block.value.length();
                }
            }
            int score = gameCount * 100000 + Math.min(readableLength, 50000);
            if (score > bestScore) {
                best = parsed;
                bestScore = score;
            }
        }
        return best;
    }

    private static void collectHtmlCandidates(Object raw, List<String> candidates,
                                               int depth) {
        if (raw == null || raw == JSONObject.NULL || depth > 16) return;
        if (raw instanceof String) {
            String value = RichTransportDecoder.decode((String) raw);
            Object structured = ArticleGameCards.parseStructured(value);
            if (structured != null) {
                collectHtmlCandidates(structured, candidates, depth + 1);
            } else if (!ArticleGameMarkupScanner.scanGameMarkers(value).isEmpty()) {
                candidates.add(value);
            }
            return;
        }
        if (raw instanceof JSONArray) {
            JSONArray array = (JSONArray) raw;
            for (int i = 0; i < array.length(); i++) {
                collectHtmlCandidates(array.opt(i), candidates, depth + 1);
            }
            return;
        }
        if (!(raw instanceof JSONObject)) return;
        JSONObject object = (JSONObject) raw;
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            collectHtmlCandidates(object.opt(keys.next()), candidates, depth + 1);
        }
    }

    private static int findRelativePosition(List<RichContent.Block> target,
                                             List<RichContent.Block> source, int cardIndex) {
        for (int index = cardIndex - 1; index >= 0; index--) {
            RichContent.Block anchor = source.get(index);
            if (!RichContentSupport.isReadableBlock(anchor)
                    || anchor.value == null || anchor.value.trim().isEmpty()) continue;
            int targetIndex = findReadablePosition(target, anchor.value, 0);
            if (targetIndex >= 0) return afterCards(target, targetIndex);
        }
        for (int index = cardIndex + 1; index < source.size(); index++) {
            RichContent.Block anchor = source.get(index);
            if (!RichContentSupport.isReadableBlock(anchor)
                    || anchor.value == null || anchor.value.trim().isEmpty()) continue;
            int targetIndex = findReadablePosition(target, anchor.value, 0);
            if (targetIndex >= 0) return targetIndex;
        }
        return target.size();
    }

    private static int afterCards(List<RichContent.Block> blocks, int position) {
        int insertAt = position + 1;
        while (insertAt < blocks.size()
                && RichGameCardParser.isCard(blocks.get(insertAt))) {
            insertAt++;
        }
        return insertAt;
    }

    private static int findReadablePosition(List<RichContent.Block> blocks,
                                            String value, int start) {
        String needle = comparable(value);
        if (needle.length() < 3) return -1;
        for (int index = Math.max(0, start); index < blocks.size(); index++) {
            RichContent.Block block = blocks.get(index);
            if (!RichContentSupport.isReadableBlock(block)) continue;
            String candidate = comparable(block.value);
            if (candidate.contains(needle) || needle.contains(candidate)) return index;
        }
        return -1;
    }

    private static String comparable(String value) {
        if (value == null) return "";
        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}《》【】（）()“”‘’]", "");
    }
}
