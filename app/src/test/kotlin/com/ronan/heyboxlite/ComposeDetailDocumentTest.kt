package com.ronan.heyboxlite

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ComposeDetailDocumentTest {
    @Test fun sparseDetailsRetainListIdentityButRespectExplicitUnlikesAndUnfavorites() {
        val fallback = FeedItem.from(JSONObject().put("linkid", "one").put("title", "title")
            .put("description", "summary").put("is_article", 1).put("is_liked", true)
            .put("is_favour", true).put("link_award_num", 100)
            .put("user", JSONObject().put("userid", "42").put("username", "author").put("avatar", "image")))
        val source = body(JSONObject().put("linkid", "one").put("is_award_link", 0)
            .put("is_fav", 0).put("like_num", 50).put("text", "full body"))
        val result = ComposeDetailDocument.parse(source, fallback, false)
        assertEquals("one", result.item.id)
        assertEquals("title", result.item.title)
        assertEquals("42", result.item.authorId)
        assertEquals("image", result.item.authorAvatar)
        assertTrue(result.item.article)
        assertFalse(result.item.liked)
        assertFalse(result.item.favorited)
        assertEquals(50, result.item.likes)
        assertEquals("full body", result.content.single().value)
    }

    @Test fun linkClassificationAndUserAliasesOverrideFallbackFields() {
        val fallback = FeedItem.from(JSONObject().put("linkid", "one").put("is_article", 0)
            .put("user", JSONObject().put("userid", "old").put("username", "old")))
        val link = JSONObject().put("linkid", "one").put("link_tag", 1)
            .put("share_url", "https://example.test/link?h_src=shared%2Bsource&x=1")
            .put("user", JSONObject().put("user_id", "new").put("nickname", "new name"))
        val result = ComposeDetailDocument.parse(body(link), fallback, false)
        assertTrue(result.item.article)
        assertEquals("new", result.item.authorId)
        assertEquals("new name", result.item.author)
        assertEquals("shared+source", result.hsrc)
    }

    @Test fun authCodeFallsBackThroughLinkResultAndRootWithoutChangingThePayload() {
        val link = JSONObject().put("linkid", "one").put("text", "text")
        val source = body(link).put("auth_code", "root-code")
        val original = source.toString()
        val fallback = FeedItem.from(link)
        assertEquals("root-code", ComposeDetailDocument.parse(source, fallback, false).authCode)
        assertEquals(original, source.toString())
        source.getJSONObject("result").put("auth_code", "result-code")
        assertEquals("result-code", ComposeDetailDocument.parse(source, fallback, false).authCode)
        link.put("auth_code", "link-code")
        assertEquals("link-code", ComposeDetailDocument.parse(source, fallback, false).authCode)
    }

    @Test fun commentGroupsKeepOfficialMetadataAndDoNotCreateNativeUi() {
        val comment = JSONObject().put("commentid", "root").put("text", "text")
            .put("child_num", 5).put("top_comment", 1)
        val source = body(JSONObject().put("linkid", "one"))
        source.getJSONObject("result").put("comments", JSONArray().put(
            JSONObject().put("comment", JSONArray().put(comment))))
        val result = ComposeDetailDocument.parse(source, FeedItem.from(JSONObject().put("linkid", "one")), true)
        assertEquals("root", ComposeCommentThreadData.from(result.comments.single()).rootId)
        assertEquals(5, ComposeCommentThreadData.from(result.comments.single()).expected)
        assertTrue(CommentData.isPinnedThread(result.comments.single()))
    }

    private fun body(link: JSONObject) = JSONObject().put("result", JSONObject().put("link", link))
}
