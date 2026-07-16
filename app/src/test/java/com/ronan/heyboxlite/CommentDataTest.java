package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * 评论字段判定的回归网。重点锁死置顶判据（历史上 is_top 误标整列的 bug），
 * 以及 Cy / id / 点赞 / 回复对象等 fallback 取值链，供后续把评论视图抽出时兜底。
 */
public class CommentDataTest {

    // ---- 置顶：只认 top_comment == "1"/"true"，绝不认 is_top ----

    @Test
    public void pinned_topCommentOne() throws Exception {
        assertTrue(CommentData.isPinnedComment(new JSONObject().put("top_comment", "1")));
    }

    @Test
    public void pinned_topCommentTrueAnyCase() throws Exception {
        assertTrue(CommentData.isPinnedComment(new JSONObject().put("top_comment", "true")));
        assertTrue(CommentData.isPinnedComment(new JSONObject().put("top_comment", "TRUE")));
    }

    @Test
    public void pinned_topCommentZeroOrEmpty() throws Exception {
        assertFalse(CommentData.isPinnedComment(new JSONObject().put("top_comment", "0")));
        assertFalse(CommentData.isPinnedComment(new JSONObject().put("top_comment", "")));
    }

    @Test
    public void pinned_isTopMustNotCount() throws Exception {
        // 每个楼主评论都带 is_top，绝不能因此被标置顶
        assertFalse(CommentData.isPinnedComment(new JSONObject().put("is_top", "1")));
    }

    @Test
    public void pinned_nullOrMissing() throws Exception {
        assertFalse(CommentData.isPinnedComment(null));
        assertFalse(CommentData.isPinnedComment(new JSONObject()));
    }

    // ---- 置顶楼组：组自身或首条评论置顶即算 ----

    @Test
    public void pinnedThread_onGroupItself() throws Exception {
        assertTrue(CommentData.isPinnedThread(new JSONObject().put("top_comment", "1")));
    }

    @Test
    public void pinnedThread_onFirstComment() throws Exception {
        JSONObject group = new JSONObject().put("comment",
                new JSONArray().put(new JSONObject().put("top_comment", "1")));
        assertTrue(CommentData.isPinnedThread(group));
    }

    @Test
    public void pinnedThread_notPinned() throws Exception {
        JSONObject group = new JSONObject().put("comment",
                new JSONArray().put(new JSONObject().put("top_comment", "0")));
        assertFalse(CommentData.isPinnedThread(group));
        assertFalse(CommentData.isPinnedThread(null));
    }

    // ---- Cy 评论 ----

    @Test
    public void cy_byFlag() throws Exception {
        assertTrue(CommentData.isCyComment(new JSONObject().put("is_cy", true)));
    }

    @Test
    public void cy_byType() throws Exception {
        assertTrue(CommentData.isCyComment(new JSONObject().put("comment_type", "cy")));
        assertTrue(CommentData.isCyComment(new JSONObject().put("type", "CY")));
    }

    @Test
    public void cy_negative() throws Exception {
        assertFalse(CommentData.isCyComment(new JSONObject().put("is_cy", false)));
        assertFalse(CommentData.isCyComment(new JSONObject()));
        assertFalse(CommentData.isCyComment(null));
    }

    // ---- id / rootId 的 fallback 优先级 ----

    @Test
    public void commentId_priority() throws Exception {
        assertEquals("A", CommentData.commentId(new JSONObject().put("commentid", "A")
                .put("comment_id", "B").put("id", "C")));
        assertEquals("B", CommentData.commentId(new JSONObject().put("comment_id", "B").put("id", "C")));
        assertEquals("C", CommentData.commentId(new JSONObject().put("id", "C")));
        assertEquals("", CommentData.commentId(new JSONObject()));
    }

    @Test
    public void rootId_fallsBackToCommentId() throws Exception {
        assertEquals("R", CommentData.commentRootId(new JSONObject().put("root_id", "R")));
        // 无 root 时退回自身 id
        assertEquals("X", CommentData.commentRootId(new JSONObject().put("id", "X")));
    }

    // ---- 点赞数 / 已赞 / 时间 ----

    @Test
    public void likes_priority() throws Exception {
        assertEquals(5, CommentData.commentLikes(new JSONObject().put("comment_award_num", 5)
                .put("award_num", 3).put("up", 2)));
        assertEquals(3, CommentData.commentLikes(new JSONObject().put("award_num", 3).put("up", 2)));
        assertEquals(2, CommentData.commentLikes(new JSONObject().put("up", 2)));
        assertEquals(0, CommentData.commentLikes(new JSONObject()));
    }

    @Test
    public void liked_variants() throws Exception {
        assertTrue(CommentData.commentLiked(new JSONObject().put("is_support", 1)));
        assertTrue(CommentData.commentLiked(new JSONObject().put("supported", true)));
        assertFalse(CommentData.commentLiked(new JSONObject()));
        assertFalse(CommentData.commentLiked(null));
    }

    @Test
    public void time_fallback() throws Exception {
        assertEquals(1000L, CommentData.commentTime(new JSONObject().put("create_at", 1000)));
        assertEquals(2000L, CommentData.commentTime(new JSONObject().put("create_time", 2000)));
    }

    // ---- 回复对象 / 等级 ----

    @Test
    public void replyTarget_variants() throws Exception {
        assertEquals("u", CommentData.replyTarget(new JSONObject().put("to_user",
                new JSONObject().put("username", "u"))));
        assertEquals("n", CommentData.replyTarget(new JSONObject().put("reply_user",
                new JSONObject().put("nickname", "n"))));
        assertEquals("", CommentData.replyTarget(new JSONObject()));
    }

    @Test
    public void userLevel_prefersLevelInfo() throws Exception {
        assertEquals(30, CommentData.userLevel(new JSONObject().put("level_info",
                new JSONObject().put("level", 30)).put("level", 99)));
        assertEquals(12, CommentData.userLevel(new JSONObject().put("level", 12)));
        assertEquals(0, CommentData.userLevel(null));
    }

    @Test
    public void commentImages_returnsEveryImageAndRemovesDuplicates() throws Exception {
        JSONArray images = new JSONArray()
                .put(new JSONObject().put("url", "https://img/1.jpg"))
                .put(new JSONObject().put("original", "https://img/2.jpg"))
                .put("https://img/1.jpg");
        assertEquals(2, CommentData.commentImages(new JSONObject().put("imgs", images)).size());
        assertEquals("https://img/1.jpg",
                CommentData.commentImages(new JSONObject().put("imgs", images)).get(0).url);
        assertEquals("https://img/2.jpg",
                CommentData.commentImages(new JSONObject().put("imgs", images)).get(1).url);
    }

    @Test
    public void commentImages_acceptsSerializedArray() throws Exception {
        JSONObject comment = new JSONObject().put("imgs",
                "[{\"url\":\"https://img/1.jpg\"},{\"src\":\"https://img/2.jpg\"}]");
        assertEquals(2, CommentData.commentImages(comment).size());
    }

    @Test
    public void commentImages_preservesGifMimeType() throws Exception {
        JSONObject image = new JSONObject()
                .put("url", "https://img/comment.webp")
                .put("mimetype", "image/gif");
        CommentData.CommentImage parsed = CommentData.commentImages(
                new JSONObject().put("imgs", new JSONArray().put(image))).get(0);

        assertEquals("image/gif", parsed.mimeType);
        assertTrue(parsed.animated);
    }

    @Test
    public void richCommentText_prefersCommentTextOverGeneratedImageValue() throws Exception {
        JSONObject segment = new JSONObject()
                .put("text", "[cube_emoji]")
                .put("commentText", "哇，atm发帖了");
        assertEquals("哇，atm发帖了", RichContent.firstText(segment));
    }
}
