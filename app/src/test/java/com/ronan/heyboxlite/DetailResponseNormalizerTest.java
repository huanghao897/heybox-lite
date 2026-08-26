package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class DetailResponseNormalizerTest {
    @Test
    public void preservesLegacyFlattenedResponse() throws Exception {
        JSONObject link = new JSONObject()
                .put("link_id", "42")
                .put("title", "Legacy title")
                .put("user", new JSONObject().put("userid", "7"));
        JSONArray comments = new JSONArray().put(new JSONObject().put("comment_id", "9"));
        JSONObject response = response(link, comments);

        JSONObject normalized = DetailResponseNormalizer.normalize(response);

        assertSame(response, normalized);
        assertSame(link, normalized.getJSONObject("result").getJSONObject("link"));
        assertSame(comments, normalized.getJSONObject("result").getJSONArray("comments"));
    }

    @Test
    public void flattensOfficialV2LinkAndCommentsForExistingRenderer() throws Exception {
        JSONObject user = new JSONObject()
                .put("userid", "10086")
                .put("username", "盒友")
                .put("avatar", "https://example.com/avatar.jpg");
        JSONObject nestedLink = new JSONObject()
                .put("body", new JSONObject()
                        .put("link_id", "42")
                        .put("title", "V2 title")
                        .put("text", "V2 body")
                        .put("user", user))
                .put("stats", new JSONObject()
                        .put("comment_num", 1)
                        .put("link_award_num", 8)
                        .put("is_favour", "1"))
                .put("access", new JSONObject().put("disable_comment", false))
                .put("bbs_link_content", new JSONObject().put("news_thumb", "cover"));
        JSONObject rootComment = new JSONObject()
                .put("commentid", "9")
                .put("user", new JSONObject()
                        .put("userid", "20001")
                        .put("avatar", "https://example.com/comment.jpg"));
        JSONArray comments = new JSONArray().put(new JSONObject()
                .put("comment", new JSONArray().put(rootComment)));
        JSONObject response = new JSONObject().put("result", new JSONObject()
                .put("link", nestedLink)
                .put("comment", new JSONObject().put("comments", comments)));

        JSONObject normalized = DetailResponseNormalizer.normalize(response);
        JSONObject result = normalized.getJSONObject("result");
        JSONObject link = result.getJSONObject("link");

        assertEquals("42", link.getString("link_id"));
        assertEquals("V2 title", link.getString("title"));
        assertEquals("V2 body", link.getString("text"));
        assertEquals("10086", link.getJSONObject("user").getString("userid"));
        assertEquals("https://example.com/avatar.jpg",
                link.getJSONObject("user").getString("avatar"));
        assertEquals(8, link.getInt("link_award_num"));
        assertEquals("1", link.getString("is_favour"));
        JSONObject normalizedComment = result.getJSONArray("comments").getJSONObject(0)
                .getJSONArray("comment").getJSONObject(0);
        assertEquals("20001", normalizedComment.getJSONObject("user").getString("userid"));
        assertEquals("https://example.com/comment.jpg",
                normalizedComment.getJSONObject("user").getString("avatar"));
        assertSame(nestedLink.getJSONObject("body"), link.getJSONObject("body"));

        FeedItem item = FeedItem.from(link);
        assertEquals("10086", item.authorId);
        assertEquals("https://example.com/avatar.jpg", item.authorAvatar);
    }

    @Test
    public void movesVideoDetailCommentVariantsIntoRendererField() throws Exception {
        JSONArray comments = new JSONArray().put(new JSONObject()
                .put("comment", new JSONArray().put(new JSONObject()
                        .put("commentid", "video-comment"))));
        JSONObject response = new JSONObject().put("result", new JSONObject()
                .put("link", new JSONObject().put("linkid", "video-1"))
                .put("data", new JSONObject().put("comments", comments)));

        JSONObject normalized = DetailResponseNormalizer.normalize(response);

        assertEquals("video-comment", normalized.getJSONObject("result")
                .getJSONArray("comments").getJSONObject(0)
                .getJSONArray("comment").getJSONObject(0)
                .getString("commentid"));
    }

    @Test
    public void restoresVideoFieldsWhenDetailOmitsThem() throws Exception {
        JSONObject response = new JSONObject().put("result", new JSONObject()
                .put("link", new JSONObject()
                        .put("linkid", "video-1")
                        .put("has_video", 1)));
        JSONObject fallback = new JSONObject()
                .put("has_video", 1)
                .put("video_url", "https://video.example/test.mp4")
                .put("video_thumb", "https://img.example/test.jpg");

        DetailResponseNormalizer.mergeVideoFallback(response, fallback);

        JSONObject link = response.getJSONObject("result").getJSONObject("link");
        assertEquals("https://video.example/test.mp4", link.getString("video_url"));
        assertEquals("https://img.example/test.jpg", link.getString("video_thumb"));
    }

    @Test
    public void restoresVideoCoverWhenDetailOnlyReturnsPlayableUrl() throws Exception {
        JSONObject response = new JSONObject().put("result", new JSONObject()
                .put("link", new JSONObject()
                        .put("linkid", "video-2")
                        .put("has_video", 1)
                        .put("video_url", "https://video.example/test.mp4")));
        JSONObject fallback = new JSONObject()
                .put("has_video", 1)
                .put("video_url", "https://video.example/test.mp4")
                .put("video_thumb", "https://img.example/test.jpg");

        DetailResponseNormalizer.mergeVideoFallback(response, fallback);

        assertEquals("https://img.example/test.jpg", response.getJSONObject("result")
                .getJSONObject("link").getString("video_thumb"));
    }

    private static JSONObject response(JSONObject link, JSONArray comments) throws Exception {
        return new JSONObject().put("result", new JSONObject()
                .put("link", link)
                .put("comments", comments));
    }
}
