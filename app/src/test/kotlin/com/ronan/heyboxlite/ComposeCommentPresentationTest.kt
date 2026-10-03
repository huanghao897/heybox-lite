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
class ComposeCommentPresentationTest {
    @Test fun previewMatchesTheNativeFiveReplyLimitAndOrdersRepliesChronologically() {
        val root = comment("root", 1).put("child_num", 12)
        val replies = JSONArray().put(root)
        for (index in 7 downTo 1) replies.put(comment("reply-$index", index.toLong() * 10))
        val thread = ComposeCommentThreadData.from(JSONObject().put("comment", replies))
        assertSame(root, thread.root)
        assertEquals(5, thread.initialVisibleCount)
        assertEquals(12, thread.expected)
        assertEquals((1..7).map { "reply-$it" }, thread.replies.map { CommentData.commentId(it) })
        assertEquals("展开 5 条回复", composeCommentExpansionLabel(thread.expected, 5, true))
        assertEquals("展开 2 条回复", composeCommentExpansionLabel(7, 5, false))
    }

    @Test fun groupCyUsesNativeInheritanceWithoutAnnotatingThePayload() {
        val root = comment("root", 1)
        val group = JSONObject().put("is_cy", 1).put("comment", JSONArray().put(root))
        val before = group.toString()
        assertTrue(ComposeCommentThreadData.from(group).cy)
        assertEquals(before, group.toString())
        assertFalse(root.has("_group_is_cy"))
        root.put("is_cy", 0)
        assertFalse(ComposeCommentThreadData.from(group).cy)
        root.put("is_cy", 1)
        assertTrue(ComposeCommentThreadData.from(group).cy)
    }

    @Test fun uniqueCommentKeysDoNotChangeWhenSortOrderChanges() {
        val root = comment("first", 1).put("comment_award_num", 1)
        val second = comment("second", 20).put("comment_award_num", 20)
        val comments = listOf(root, second)
        val hot = composeOrderedComments(comments, false)
        val latest = composeOrderedComments(comments, true)
        assertEquals(setOf("comment-first", "comment-second"), composeCommentKeys(hot).toSet())
        assertEquals(composeCommentKeys(hot).toSet(), composeCommentKeys(latest).toSet())
        assertSame(root, comments[0])
    }

    @Test fun missingAndDuplicatedIdsStillHaveDistinctLazyKeys() {
        val keys = composeCommentKeys(listOf(JSONObject(), JSONObject(), comment("same", 0), comment("same", 0)))
        assertEquals(4, keys.toSet().size)
    }

    @Test fun commentTextKeepsOfficialEmojiAndTheTextOnBothSidesOfGameLinks() {
        val source = "Before [cube_惊讶] <a data-link-type=\"game\" data-game-id=\"42\">Game</a> after"
        val value = composeCommentText(JSONObject().put("text", source))
        val parsed = RichGameLinkMarkup.parse(value)
        assertEquals(1, parsed.links.size)
        assertEquals("42", parsed.links.single().game.appId)
        assertTrue(parsed.text.startsWith("Before [cube_惊讶] "))
        assertTrue(parsed.text.endsWith("Game after"))
        assertEquals("Before [cube_惊讶] Game after", RichInlineRenderer.plainText(value))
    }

    @Test fun userAliasesMatchTheNativeControllerWithoutConfusingSameNamedAuthors() {
        val item = FeedItem.from(JSONObject().put("linkid", "post").put("user",
            JSONObject().put("userid", "author-id").put("username", "Author")))
        for (key in listOf("userid", "user_id", "heybox_id", "heyboxid", "uid", "account_id", "id")) {
            val user = JSONObject().put(key, "author-id")
            assertEquals("author-id", composeCommentUserId(user))
            assertTrue(composeCommentIsPostAuthor(item, user, "Author"))
            user.put(key, "other-id")
            assertFalse(composeCommentIsPostAuthor(item, user, "Author"))
        }
    }

    @Test fun rootRepliesSuppressTheirTargetButSiblingRepliesRetainIt() {
        val reply = comment("reply", 10).put("replyid", "root")
            .put("replyuser", JSONObject().put("username", "Root author"))
        assertEquals("", CommentData.replyTarget(reply, "root"))
        reply.put("replyid", "sibling")
            .put("replyuser", JSONObject().put("username", "Sibling author"))
        assertEquals("Sibling author", CommentData.replyTarget(reply, "root"))
    }

    private fun comment(id: String, time: Long) = JSONObject().put("commentid", id).put("create_at", time)
}
