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
        final List<VideoData> videos;

        Result(List<RichContent.Block> blocks, boolean useImagePager,
               List<VideoData> videos) {
            this.blocks = blocks;
            this.useImagePager = useImagePager;
            this.videos = videos;
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
        return resolve(link, fallback, fallbackImages, null);
    }

    Result resolve(JSONObject link, String fallback, JSONArray fallbackImages,
                   FeedItem fallbackItem) {
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
        boolean article = FeedItem.isArticleJson(link);
        List<VideoData> videos = link == null
                ? Collections.emptyList() : VideoData.from(link);
        if (fallbackItem != null) {
            List<VideoData> fallbackVideos = fallbackItem.videos;
            if (videos.isEmpty() || (!videos.get(0).playable()
                    && !fallbackVideos.isEmpty() && fallbackVideos.get(0).playable())
                    || (!videos.isEmpty() && videos.get(0).cover.isEmpty()
                    && !fallbackVideos.isEmpty() && !fallbackVideos.get(0).cover.isEmpty())) {
                videos = fallbackVideos;
            }
            if (videos.isEmpty() && fallbackItem.video) {
                videos = Collections.singletonList(VideoData.create(
                        "", fallbackItem.image, fallbackItem.title));
            }
        }
        String videoTitle = link == null ? "" : Json.first(link.optString("title"),
                link.optString("subject"), link.optString("name"));
        if (videoTitle.isEmpty() && fallbackItem != null) videoTitle = fallbackItem.title;
        if (!videoTitle.isEmpty() && !videos.isEmpty()) {
            List<VideoData> titled = new ArrayList<>(videos.size());
            for (VideoData video : videos) titled.add(video.withFallbackTitle(videoTitle));
            videos = titled;
        }
        return new Result(Collections.unmodifiableList(new ArrayList<>(blocks)), !article,
                Collections.unmodifiableList(new ArrayList<>(videos)));
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
