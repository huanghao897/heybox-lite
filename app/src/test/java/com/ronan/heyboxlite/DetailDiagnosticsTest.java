package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class DetailDiagnosticsTest {
    @Test
    public void compactText_normalizesWhitespaceAndAppliesLimit() {
        assertEquals("a b c...", DetailDiagnostics.compactText(" a\n b   cdef ", 5));
        assertEquals("", DetailDiagnostics.compactText(null, 20));
        assertEquals("...", DetailDiagnostics.compactText("content", -1));
    }

    @Test
    public void build_includesDetailAndCommentMetadata() throws Exception {
        JSONObject link = new JSONObject()
                .put("linkid", "post-1")
                .put("title", "测试标题")
                .put("imgs", new JSONArray().put("https://img.example/1.jpg"));
        JSONObject comment = new JSONObject()
                .put("commentid", "comment-1")
                .put("text", "[cube_惊讶]评论文字")
                .put("imgs", new JSONArray().put(new JSONObject()
                        .put("url", "https://img.example/comment.gif")
                        .put("mimetype", "image/gif")));
        JSONArray comments = new JSONArray().put(new JSONObject()
                .put("comment", new JSONArray().put(comment)));

        String result = DetailDiagnostics.build(
                "detail", "post-1", true,
                new JSONObject().put("result", new JSONObject()),
                FeedItem.from(link), link, null, comments,
                RichContent.parse(link, null));

        assertTrue(result.contains("detail screen: detail"));
        assertTrue(result.contains("currentLinkId: post-1"));
        assertTrue(result.contains("fallbackTitle: 测试标题"));
        assertTrue(result.contains("parsed blocks:"));
        assertTrue(result.contains("[0] id=comment-1"));
        assertTrue(result.contains("parsed=[cube_惊讶]评论文字"));
        assertTrue(result.contains("animated=true"));
        assertFalse(result.contains("groups: null"));
    }
}
