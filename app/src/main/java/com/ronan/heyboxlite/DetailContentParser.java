package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

final class DetailContentParser {
    static final class Result {
        final List<RichContent.Block> blocks;
        final boolean useImagePager;

        Result(List<RichContent.Block> blocks, boolean useImagePager) {
            this.blocks = blocks;
            this.useImagePager = useImagePager;
        }
    }

    private static final class CacheEntry {
        final JSONArray fallbackImages;
        final List<RichContent.Block> blocks;

        CacheEntry(JSONArray fallbackImages, List<RichContent.Block> blocks) {
            this.fallbackImages = fallbackImages;
            this.blocks = blocks;
        }
    }

    private final WeakHashMap<JSONObject, CacheEntry> cache = new WeakHashMap<>();

    Result resolve(JSONObject link, String fallback, JSONArray fallbackImages) {
        List<RichContent.Block> blocks = link == null
                ? RichContent.parse(fallback, fallbackImages)
                : parseCached(link, fallbackImages);
        if (!RichContent.hasReadableText(blocks) && fallback != null && !fallback.isEmpty()) {
            List<RichContent.Block> fallbackBlocks =
                    RichContent.parse(fallback, (JSONArray) null);
            if (RichContent.hasReadableText(fallbackBlocks)) {
                List<RichContent.Block> merged = new ArrayList<>(fallbackBlocks);
                merged.addAll(blocks);
                blocks = merged;
            } else if (blocks.isEmpty()) {
                blocks = RichContent.parse(fallback, fallbackImages);
            }
        }
        boolean article = link != null
                && (Json.truthy(link, "use_concept_type")
                || Json.truthy(link, "is_article"));
        return new Result(Collections.unmodifiableList(new ArrayList<>(blocks)), !article);
    }

    private List<RichContent.Block> parseCached(JSONObject link, JSONArray fallbackImages) {
        synchronized (cache) {
            CacheEntry cached = cache.get(link);
            if (cached != null && cached.fallbackImages == fallbackImages) {
                return cached.blocks;
            }
        }
        List<RichContent.Block> parsed = Collections.unmodifiableList(
                new ArrayList<>(RichContent.parse(link, fallbackImages)));
        synchronized (cache) {
            cache.put(link, new CacheEntry(fallbackImages, parsed));
        }
        return parsed;
    }
}
