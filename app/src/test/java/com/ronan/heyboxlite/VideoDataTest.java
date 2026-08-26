package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class VideoDataTest {
    @Test
    public void readsVideoUrlAndCoverFromOfficialContentNode() throws Exception {
        JSONObject link = new JSONObject()
                .put("has_video", 1)
                .put("text", new JSONArray().put(new JSONObject()
                        .put("type", "video")
                        .put("video_url", "http://video.example/test.mp4")
                        .put("url", "https://img.example/cover.jpg")));

        List<VideoData> videos = VideoData.from(link);

        assertEquals(1, videos.size());
        assertEquals("https://video.example/test.mp4", videos.get(0).url);
        assertEquals("https://img.example/cover.jpg", videos.get(0).cover);
        assertTrue(videos.get(0).playable());
    }

    @Test
    public void keepsCoverWhenDetailHasNoPlayableUrl() throws Exception {
        JSONObject link = new JSONObject().put("text", new JSONArray().put(
                new JSONObject().put("type", "video")
                        .put("url", "https://img.example/cover.jpg")));

        List<VideoData> videos = VideoData.from(link);

        assertEquals(1, videos.size());
        assertEquals("", videos.get(0).url);
        assertEquals("https://img.example/cover.jpg", videos.get(0).cover);
    }

    @Test
    public void readsOfficialVideoPostFields() throws Exception {
        JSONObject link = new JSONObject()
                .put("has_video", 1)
                .put("video_url", "https://video.example/gta.mp4")
                .put("video_thumb", "https://img.example/gta.jpg");

        List<VideoData> videos = VideoData.from(link);

        assertEquals(1, videos.size());
        assertEquals("https://video.example/gta.mp4", videos.get(0).url);
        assertEquals("https://img.example/gta.jpg", videos.get(0).cover);
    }

    @Test
    public void ignoresGenericImagesWhenOnlyHasVideoFlagIsPresent() throws Exception {
        JSONObject link = new JSONObject()
                .put("has_video", 1)
                .put("imgs", new JSONArray().put("https://img.example/gta.jpg"));

        List<VideoData> videos = VideoData.from(link);

        assertTrue(videos.isEmpty());
    }

    @Test
    public void ignoresImageFileNodesFromRichContent() throws Exception {
        JSONObject link = new JSONObject()
                .put("text", new JSONArray().put(new JSONObject()
                        .put("type", "image")
                        .put("file", "https://img.example/post.jpg")));

        assertTrue(VideoData.from(link).isEmpty());
    }

    @Test
    public void readsPlayableUrlFromNestedVideoInfo() throws Exception {
        JSONObject link = new JSONObject()
                .put("has_video", 1)
                .put("video_thumb", "https://img.example/gta.jpg")
                .put("video_info", new JSONObject()
                        .put("video_url", "https://video.example/gta.mp4"));

        List<VideoData> videos = VideoData.from(link);

        assertEquals(1, videos.size());
        assertEquals("https://video.example/gta.mp4", videos.get(0).url);
        assertEquals("https://img.example/gta.jpg", videos.get(0).cover);
    }

    @Test
    public void fillsMissingVideoTitleFromPostTitle() {
        VideoData video = VideoData.create("https://video.example/test.mp4", "", "")
                .withFallbackTitle("[cube_ok] 帖子标题");

        assertEquals("[cube_ok] 帖子标题", video.title);
    }
}
