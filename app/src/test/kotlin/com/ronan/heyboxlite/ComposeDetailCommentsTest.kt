package com.ronan.heyboxlite

import android.app.Application
import com.ronan.heyboxlite.ComposeCommentActionsTestFixtures.Fixture
import com.ronan.heyboxlite.ComposeCommentActionsTestFixtures.comment
import com.ronan.heyboxlite.ComposeCommentActionsTestFixtures.replyPage
import com.ronan.heyboxlite.ComposeCommentActionsTestFixtures.thread
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ComposeDetailCommentsTest {
    @Test fun progressiveRefreshRetainsLoadedRepliesAndPaginationWithoutDuplicatingServerRows() {
        val f = Fixture(comments = listOf(thread(comment("root"), comment("server-reply"))))
        f.actions.replies(f.findComment("root"))
        f.callbacks.replies.single().callback.onSuccess(
            replyPage("last-page", false, comment("loaded-1"), comment("loaded-2")))
        val pagination = f.detail.replyStates
        val incoming = thread(comment("root").put("text", "Updated root"),
            comment("server-reply").put("text", "Updated server reply"),
            comment("loaded-1").put("text", "Updated overlapping reply"), comment("server-new"))
            .put("group_marker", "fresh metadata")

        repeat(2) { f.navigation.showDetailDocument(document(f, listOf(incoming))) }

        assertEquals(listOf("root", "server-reply", "loaded-1", "server-new", "loaded-2"), f.rowIds())
        assertEquals("Updated root", f.findComment("root").getString("text"))
        assertEquals("Updated server reply", f.findComment("server-reply").getString("text"))
        assertEquals("Updated overlapping reply", f.findComment("loaded-1").getString("text"))
        assertEquals("fresh metadata", f.detail.comments.single().getString("group_marker"))
        assertEquals(pagination, f.detail.replyStates)
        f.actions.replies(f.findComment("root"))
        assertEquals(1, f.callbacks.replies.size)
    }

    @Test fun progressiveRefreshKeepsPendingAndSuccessfulRootAndReplyLikes() {
        val f = Fixture(comments = listOf(thread(comment("root", likes = 4), comment("reply", likes = 2))))
        f.actions.like(f.findComment("root"))
        f.actions.like(f.findComment("reply"))
        val rootRequest = f.callbacks.likes.first()
        val replyRequest = f.callbacks.likes.last()
        val incoming = thread(comment("root", likes = 4), comment("reply", likes = 2))

        f.navigation.showDetailDocument(document(f, listOf(incoming)))

        assertEquals(setOf("root", "reply"), f.detail.pendingLikes)
        assertLiked(f.findComment("root"), true, 5)
        assertLiked(f.findComment("reply"), true, 3)
        rootRequest.callback.onSuccess(JSONObject())
        f.navigation.showDetailDocument(document(f, listOf(incoming)))
        assertEquals(setOf("reply"), f.detail.pendingLikes)
        assertLiked(f.findComment("root"), true, 5)
        assertLiked(f.findComment("reply"), true, 3)
        replyRequest.callback.onSuccess(JSONObject())
        f.navigation.showDetailDocument(document(f, listOf(incoming)))
        assertTrue(f.detail.pendingLikes.isEmpty())
        assertLiked(f.findComment("root"), true, 5)
        assertLiked(f.findComment("reply"), true, 3)
        assertTrue(f.messages.isEmpty())
    }

    @Test fun successfulUnlikeIsNotResurrectedByAnOlderServerSnapshot() {
        val f = Fixture(comments = listOf(comment("root", liked = true, likes = 1)))
        f.actions.like(f.findComment("root"))
        assertFalse(f.callbacks.likes.single().liked)
        f.callbacks.likes.single().callback.onSuccess(JSONObject())

        f.navigation.showDetailDocument(document(f, listOf(comment("root", liked = true, likes = 9))))

        assertLiked(f.findComment("root"), false, 0)
        assertEquals(ComposeCommentLikeState(false, 0), f.detail.likeOverrides["root"])
        assertTrue(f.detail.pendingLikes.isEmpty())
    }

    @Test fun failedLikeRollsBackAfterRefreshAndItsRollbackSurvivesTheNextRefresh() {
        val f = Fixture(comments = listOf(thread(comment("root", likes = 4))))
        f.actions.like(f.findComment("root"))
        val incoming = listOf(thread(comment("root", likes = 4)))
        f.navigation.showDetailDocument(document(f, incoming))

        f.callbacks.likes.single().callback.onError("offline")
        f.navigation.showDetailDocument(document(f, incoming))

        assertLiked(f.findComment("root"), false, 4)
        assertEquals(ComposeCommentLikeState(false, 4), f.detail.likeOverrides["root"])
        assertTrue(f.detail.pendingLikes.isEmpty())
        assertTrue(f.messages.single().contains("offline"))
    }

    @Test fun finalEmptyCommentsDoNotFabricateRootsFromLoadedRepliesOrLikeOverrides() {
        val f = Fixture()
        f.actions.replies(f.findComment("root"))
        f.callbacks.replies.single().callback.onSuccess(replyPage("done", false, comment("loaded")))
        f.actions.like(f.findComment("root"))
        f.callbacks.likes.single().callback.onSuccess(JSONObject())
        f.actions.like(f.findComment("loaded"))
        assertTrue(f.detail.replyStates.isNotEmpty())
        assertTrue(f.detail.likeOverrides.isNotEmpty())
        assertTrue(f.detail.pendingLikes.isNotEmpty())

        f.navigation.showDetailDocument(document(f, emptyList()))

        assertTrue("A final empty response is authoritative, even with local interaction state",
            f.detail.comments.isEmpty())
    }

    @Test fun finalRefreshDoesNotAppendAnOldRootMissingFromTheServerResponse() {
        val f = Fixture()
        f.actions.replies(f.findComment("root"))
        f.callbacks.replies.single().callback.onSuccess(replyPage("done", false, comment("loaded")))

        f.navigation.showDetailDocument(document(f, listOf(comment("different-root"))))

        assertEquals(listOf("different-root"), f.rowIds())
        assertEquals(1, f.detail.comments.size)
    }

    @Test fun emptyPreviewPreservesExistingCommentContentRepliesMetadataAndPendingLikes() {
        val root = comment("root", likes = 4).put("text", "Existing root content")
        val f = Fixture(comments = listOf(thread(root).put("group_marker", "existing metadata")))
        f.actions.replies(root)
        f.callbacks.replies.single().callback.onSuccess(
            replyPage("next", true, comment("loaded").put("text", "Existing reply content")))
        f.actions.like(f.findComment("root"))
        val before = f.detail

        f.navigation.showDetailDocument(document(f, emptyList(), preview = true))

        assertEquals(listOf("root", "loaded"), f.rowIds())
        assertEquals("Existing root content", f.findComment("root").getString("text"))
        assertEquals("Existing reply content", f.findComment("loaded").getString("text"))
        assertEquals("existing metadata", f.detail.comments.single().getString("group_marker"))
        assertEquals(before.replyStates, f.detail.replyStates)
        assertEquals(before.pendingLikes, f.detail.pendingLikes)
        assertEquals(before.likeOverrides, f.detail.likeOverrides)
        assertLiked(f.findComment("root"), true, 5)
    }

    @Test fun refreshDoesNotKeepOldServerRepliesWhenNoReplyPageWasRequested() {
        val f = Fixture(comments = listOf(thread(comment("root"), comment("old-server-reply"))))
        assertTrue(f.detail.replyStates.isEmpty())

        f.navigation.showDetailDocument(document(f, listOf(thread(comment("root"), comment("fresh-reply")))))

        assertEquals(listOf("root", "fresh-reply"), f.rowIds())
    }

    @Test fun mergeAndLikeUpdatesDoNotMutateIncomingOrPreviousJson() {
        val oldGroup = thread(comment("root", likes = 4), comment("loaded"))
            .put("metadata", JSONObject().put("value", "old"))
        val bare = comment("bare", liked = true, likes = 2)
        val f = Fixture(comments = listOf(oldGroup, bare))
        val oldJson = f.detail.comments.map(JSONObject::toString)
        val original = f.detail.copy(replyStates = mapOf("root" to ComposeReplyLoadState(cursor = "next")))

        val liked = composeUpdateCommentLike(original, "root", true, 5)
        val updated = composeUpdateCommentLike(liked, "bare", false, -3)

        assertEquals(oldJson, original.comments.map(JSONObject::toString))
        assertLiked(original.comments.first().getJSONArray("comment").getJSONObject(0), false, 4)
        assertLiked(bare, true, 2)
        assertLiked(updated.comments.last(), false, 0)
        val incoming = listOf(thread(comment("root", likes = 4), comment("fresh"))
            .put("metadata", JSONObject().put("value", "new")), comment("bare", liked = true, likes = 2))
        val incomingJson = incoming.map(JSONObject::toString)
        val previousJson = updated.comments.map(JSONObject::toString)

        val merged = composeMergeDetailComments(incoming, updated, preview = false)
        composeMergeDetailComments(emptyList(), updated, preview = true)

        assertEquals(incomingJson, incoming.map(JSONObject::toString))
        assertEquals(previousJson, updated.comments.map(JSONObject::toString))
        assertEquals(oldJson, original.comments.map(JSONObject::toString))
        assertEquals(listOf("root", "fresh", "loaded"), rowIds(merged.first()))
        assertLiked(merged.first().getJSONArray("comment").getJSONObject(0), true, 5)
        assertLiked(merged.last().getJSONArray("comment").getJSONObject(0), false, 0)
        assertEquals("new", merged.first().getJSONObject("metadata").getString("value"))
    }

    private fun document(f: Fixture, comments: List<JSONObject>, preview: Boolean = false) =
        ComposeDetailDocument(JSONObject().put("_progressive_preview", preview), f.detail.item,
            emptyList(), emptyList(), comments, "", "", "")

    private fun rowIds(group: JSONObject): List<String> {
        val rows = group.getJSONArray("comment")
        return (0 until rows.length()).map { CommentData.commentId(rows.getJSONObject(it)) }
    }

    private fun assertLiked(comment: JSONObject, liked: Boolean, count: Int) {
        assertEquals(liked, CommentData.commentLiked(comment))
        assertEquals(count, CommentData.commentLikes(comment))
    }
}
