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
class ComposeCommentActionsTest {
    @Test fun likePublishesAnOptimisticSnapshotAndAllowsOnlyOnePendingRequestPerComment() {
        val root = comment("root", likes = 4)
        val reply = comment("reply", likes = 2)
        val group = thread(root, reply)
        val f = Fixture(comments = listOf(group))
        val before = f.detail

        f.actions.like(root)
        val request = f.callbacks.likes.single()
        assertEquals("root", request.id)
        assertTrue(request.liked)
        assertNotSame(before, f.detail)
        assertNotSame(group, f.detail.comments.single())
        assertEquals(4, CommentData.commentLikes(root))
        assertFalse(CommentData.commentLiked(root))
        assertEquals(5, CommentData.commentLikes(f.findComment("root")))
        assertTrue(CommentData.commentLiked(f.findComment("root")))
        assertSame(reply, f.findComment("reply"))
        assertEquals(setOf("root"), f.detail.pendingLikes)
        repeat(3) { f.actions.like(f.findComment("root")) }
        assertEquals(1, f.callbacks.likes.size)

        f.actions.like(reply)
        assertEquals(2, f.callbacks.likes.size)
        assertEquals(setOf("root", "reply"), f.detail.pendingLikes)
        request.callback.onSuccess(JSONObject())
        assertEquals(setOf("reply"), f.detail.pendingLikes)
        assertTrue(CommentData.commentLiked(f.findComment("root")))
        assertEquals(5, CommentData.commentLikes(f.findComment("root")))
        f.callbacks.likes.last().callback.onSuccess(JSONObject())
        assertTrue(f.detail.pendingLikes.isEmpty())
        assertTrue(f.messages.isEmpty())
    }

    @Test fun failedUnlikeRestoresOnlyItsCommentAndKeepsOtherRequestsPending() {
        val root = comment("root", liked = true, likes = 0)
        val reply = comment("reply", likes = 7)
        val f = Fixture(comments = listOf(thread(root, reply)))
        f.actions.like(root)
        val unlike = f.callbacks.likes.single()
        assertFalse(unlike.liked)
        assertFalse(CommentData.commentLiked(f.findComment("root")))
        assertEquals(0, CommentData.commentLikes(f.findComment("root")))
        f.actions.like(reply)

        unlike.callback.onError("offline")

        assertTrue(CommentData.commentLiked(f.findComment("root")))
        assertEquals(0, CommentData.commentLikes(f.findComment("root")))
        assertTrue(CommentData.commentLiked(f.findComment("reply")))
        assertEquals(8, CommentData.commentLikes(f.findComment("reply")))
        assertEquals(setOf("reply"), f.detail.pendingLikes)
        assertTrue(f.messages.single().contains("offline"))
        f.actions.like(f.findComment("root"))
        assertEquals(3, f.callbacks.likes.size)
        assertFalse(f.callbacks.likes.last().liked)
    }

    @Test fun staleLikeCallbacksCannotRollBackOrUnlockAFreshEntryForTheSamePostAndComment() {
        val f = Fixture(comments = listOf(comment("root", likes = 3)))
        f.actions.like(f.findComment("root"))
        val old = f.callbacks.likes.single()
        val oldKey = f.detail.entryKey
        f.enter("post", listOf(comment("root", likes = 20)))
        assertNotEquals(oldKey, f.detail.entryKey)
        f.actions.like(f.findComment("root"))
        val current = f.detail

        old.callback.onError("stale failure")
        old.callback.onSuccess(JSONObject())

        assertSame(current, f.detail)
        assertEquals(setOf("root"), f.detail.pendingLikes)
        assertEquals(21, CommentData.commentLikes(f.findComment("root")))
        assertTrue(f.messages.isEmpty())
        f.callbacks.likes.last().callback.onSuccess(JSONObject())
        assertTrue(f.detail.pendingLikes.isEmpty())
    }

    @Test fun likeCallbacksFinishTheSavedDetailParentWhileItsAuthorPageIsOpen() {
        for (success in listOf(true, false)) {
            val f = Fixture(comments = listOf(comment("root", likes = 4)))
            f.actions.like(f.findComment("root"))
            val request = f.callbacks.likes.single()
            val key = f.detail.entryKey
            f.navigation.navigate("user_space?user=author")

            if (success) request.callback.onSuccess(JSONObject()) else request.callback.onError("offline")

            assertEquals("user_space", f.navigation.route.value)
            assertTrue(f.navigation.restoreSavedDetailIfNeeded("detail"))
            assertEquals(key, f.detail.entryKey)
            assertTrue("Returning from the author must not leave a permanent pending like",
                f.detail.pendingLikes.isEmpty())
            assertEquals(success, CommentData.commentLiked(f.findComment("root")))
            assertEquals(if (success) 5 else 4, CommentData.commentLikes(f.findComment("root")))
            f.actions.like(f.findComment("root"))
            assertEquals(2, f.callbacks.likes.size)
        }
    }

    @Test fun repliesCaptureCursorAppendDeduplicatedSnapshotsAndStopWhenNoMore() {
        val root = comment("root").put("child_num", 9)
        val cached = comment("cached")
        val originalGroup = thread(root, cached).put("group_marker", "retained")
        val sibling = comment("other-root")
        val f = Fixture(comments = listOf(originalGroup, sibling))
        f.actions.replies(root)
        val first = f.callbacks.replies.single()
        assertEquals("root", first.id)
        assertEquals("", first.cursor)
        assertTrue(f.detail.replyStates.getValue("root").loading)
        f.actions.replies(root)
        assertEquals(1, f.callbacks.replies.size)

        first.callback.onSuccess(replyPage("cursor-1", true, root, cached,
            thread(root, comment("reply-1"), comment("reply-1"), comment("reply-2"))))

        assertEquals(listOf("root", "cached", "reply-1", "reply-2"), f.rowIds())
        assertEquals(2, originalGroup.getJSONArray("comment").length())
        assertNotSame(originalGroup, f.detail.comments.first())
        assertSame(root, f.findComment("root"))
        assertSame(cached, f.findComment("cached"))
        assertSame(sibling, f.detail.comments[1])
        assertEquals("retained", f.detail.comments.first().getString("group_marker"))
        assertEquals(ComposeReplyLoadState(cursor = "cursor-1"), f.detail.replyStates.getValue("root"))

        f.actions.replies(root)
        val second = f.callbacks.replies.last()
        assertEquals("root", second.id)
        assertEquals("cursor-1", second.cursor)
        second.callback.onSuccess(replyPage("cursor-2", false, comment("reply-2"), comment("reply-3")))
        assertEquals(listOf("root", "cached", "reply-1", "reply-2", "reply-3"), f.rowIds())
        assertEquals(ComposeReplyLoadState(cursor = "cursor-2", hasMore = false),
            f.detail.replyStates.getValue("root"))
        repeat(3) { f.actions.replies(root) }
        assertEquals(2, f.callbacks.replies.size)
    }

    @Test fun replyFailurePreservesRowsAndCursorAndRetryClearsTheErrorWithoutDuplicateRequests() {
        val f = Fixture()
        val root = f.findComment("root")
        f.actions.replies(root)
        f.callbacks.replies.single().callback.onSuccess(replyPage("cursor-1", true, comment("reply-1")))
        val comments = f.detail.comments
        f.actions.replies(root)
        f.callbacks.replies.last().callback.onError("offline")

        assertSame(comments, f.detail.comments)
        assertEquals(ComposeReplyLoadState(cursor = "cursor-1", error = "offline"),
            f.detail.replyStates.getValue("root"))
        f.actions.replies(root)
        val retry = f.callbacks.replies.last()
        assertEquals("root", retry.id)
        assertEquals("cursor-1", retry.cursor)
        assertEquals(ComposeReplyLoadState(cursor = "cursor-1", loading = true),
            f.detail.replyStates.getValue("root"))
        f.actions.replies(root)
        assertEquals(3, f.callbacks.replies.size)
        retry.callback.onSuccess(replyPage("cursor-2", false, comment("reply-2")))
        assertEquals(listOf("root", "reply-1", "reply-2"), f.rowIds())
        assertFalse(f.detail.replyStates.getValue("root").loading)
        assertEquals("", f.detail.replyStates.getValue("root").error)
    }

    @Test fun missingBlankAndNonAdvancingReplyCursorsDoNotRequestForever() {
        for (lastval in listOf<String?>(null, "", "cursor-1")) {
            val f = Fixture()
            val root = f.findComment("root")
            f.actions.replies(root)
            f.callbacks.replies.single().callback.onSuccess(replyPage("cursor-1", true, comment("reply-1")))
            f.actions.replies(root)
            val result = if (lastval == null) JSONObject() else replyPage(lastval, true, comment("reply-2"))
            f.callbacks.replies.last().callback.onSuccess(result)

            assertFalse(f.detail.replyStates.getValue("root").hasMore)
            assertFalse(f.detail.replyStates.getValue("root").loading)
            f.actions.replies(root)
            assertEquals(2, f.callbacks.replies.size)
        }
    }

    @Test fun replySuccessAndFailureFinishTheSavedParentWithoutNavigatingAwayFromItsAuthor() {
        for (success in listOf(true, false)) {
            val f = Fixture()
            val root = f.findComment("root")
            f.actions.replies(root)
            val request = f.callbacks.replies.single()
            val key = f.detail.entryKey
            f.navigation.navigate("user_space?user=author")

            if (success) request.callback.onSuccess(replyPage("next", false, comment("reply-1")))
            else request.callback.onError("offline")

            assertEquals("user_space", f.navigation.route.value)
            assertTrue(f.navigation.restoreSavedDetailIfNeeded("detail"))
            assertEquals(key, f.detail.entryKey)
            val reply = f.detail.replyStates.getValue("root")
            assertFalse("Returning from the author must not leave replies permanently loading", reply.loading)
            if (success) {
                assertEquals(listOf("root", "reply-1"), f.rowIds())
                assertEquals(ComposeReplyLoadState(cursor = "next", hasMore = false), reply)
            } else {
                assertEquals(listOf("root"), f.rowIds())
                assertEquals("offline", reply.error)
                f.actions.replies(root)
                assertEquals(2, f.callbacks.replies.size)
            }
        }
    }

    @Test fun staleReplyCallbacksCannotAppendOrResetAPendingFreshEntryForTheSamePost() {
        val f = Fixture()
        f.actions.replies(f.findComment("root"))
        val old = f.callbacks.replies.single()
        val key = f.detail.entryKey
        f.enter("post", listOf(thread(comment("root"), comment("new-entry-reply"))))
        assertNotEquals(key, f.detail.entryKey)
        f.actions.replies(f.findComment("root"))
        val current = f.detail

        old.callback.onSuccess(replyPage("stale-cursor", false, comment("stale-reply")))
        old.callback.onError("stale failure")

        assertSame(current, f.detail)
        assertEquals(listOf("root", "new-entry-reply"), f.rowIds())
        assertEquals(ComposeReplyLoadState(loading = true), f.detail.replyStates.getValue("root"))
        f.callbacks.replies.last().callback.onSuccess(replyPage("current-cursor", false, comment("current-reply")))
        assertEquals(listOf("root", "new-entry-reply", "current-reply"), f.rowIds())
    }

    @Test fun likeCallbacksIgnoreAccountChangesWithoutRelyingOnEntryInvalidation() {
        for (account in listOf<String?>(null, "99")) for (success in listOf(true, false)) {
            val f = Fixture(comments = listOf(comment("root", likes = 4)))
            f.actions.like(f.findComment("root"))
            val request = f.callbacks.likes.single()
            changeAccount(f, account)
            val current = f.detail

            if (success) request.callback.onSuccess(JSONObject()) else request.callback.onError("old account failure")

            assertSame("An account change must make both old callback branches inert", current, f.detail)
            assertEquals(setOf("root"), f.detail.pendingLikes)
            assertTrue(CommentData.commentLiked(f.findComment("root")))
            assertEquals(5, CommentData.commentLikes(f.findComment("root")))
            assertTrue(f.messages.isEmpty())
        }
    }

    @Test fun oldAccountLikeCallbacksCannotModifyTheSavedDetailParent() {
        for (account in listOf<String?>(null, "99")) for (success in listOf(true, false)) {
            val f = Fixture(comments = listOf(comment("root", likes = 4)))
            f.actions.like(f.findComment("root"))
            val request = f.callbacks.likes.single()
            f.navigation.navigate("user_space?user=author")
            changeAccount(f, account)
            val parent = f.detail

            if (success) request.callback.onSuccess(JSONObject()) else request.callback.onError("old account failure")

            assertEquals("user_space", f.navigation.route.value)
            assertTrue(f.navigation.restoreSavedDetailIfNeeded("detail"))
            assertSame("The account guard must also protect the saved frame", parent, f.detail)
            assertEquals(setOf("root"), f.detail.pendingLikes)
            assertTrue(f.messages.isEmpty())
        }
    }

    @Test fun replyCallbacksIgnoreAccountChangesWithoutAppendingRowsOrCompletingPagination() {
        for (account in listOf<String?>(null, "99")) for (success in listOf(true, false)) {
            val f = Fixture()
            f.actions.replies(f.findComment("root"))
            val request = f.callbacks.replies.single()
            changeAccount(f, account)
            val current = f.detail

            if (success) request.callback.onSuccess(replyPage("old-account-cursor", false, comment("old-account-reply")))
            else request.callback.onError("old account failure")

            assertSame("An account change must make both old callback branches inert", current, f.detail)
            assertEquals(listOf("root"), f.rowIds())
            assertEquals(ComposeReplyLoadState(loading = true), f.detail.replyStates["root"])
            assertTrue(f.messages.isEmpty())
        }
    }

    @Test fun oldAccountReplyCallbacksCannotModifyTheSavedDetailParent() {
        for (account in listOf<String?>(null, "99")) for (success in listOf(true, false)) {
            val f = Fixture()
            f.actions.replies(f.findComment("root"))
            val request = f.callbacks.replies.single()
            f.navigation.navigate("user_space?user=author")
            changeAccount(f, account)
            val parent = f.detail

            if (success) request.callback.onSuccess(replyPage("old-account-cursor", false, comment("old-account-reply")))
            else request.callback.onError("old account failure")

            assertEquals("user_space", f.navigation.route.value)
            assertTrue(f.navigation.restoreSavedDetailIfNeeded("detail"))
            assertSame("The account guard must also protect the saved frame", parent, f.detail)
            assertEquals(listOf("root"), f.rowIds())
            assertEquals(ComposeReplyLoadState(loading = true), f.detail.replyStates["root"])
            assertTrue(f.messages.isEmpty())
        }
    }

    @Test fun sendCallbacksIgnoreAccountChangesWithoutClearingDraftOrClosingEditor() {
        for (account in listOf<String?>(null, "99")) for (success in listOf(true, false)) {
            val f = Fixture()
            f.actions.edit(null, "")
            f.actions.changeText("submitted before account change")
            f.actions.send()
            val request = f.callbacks.sends.single()
            changeAccount(f, account)
            val currentEditor = f.editor
            val draft = f.session.commentDraft(currentEditor.draftKey)
            val currentDetail = f.detail

            if (success) request.callback.onSuccess(JSONObject()) else request.callback.onError("old account failure")

            assertSame(currentEditor, f.actions.editor.value)
            assertSame(currentDetail, f.detail)
            assertEquals(draft, f.session.commentDraft(currentEditor.draftKey))
            assertTrue(f.callbacks.opened.isEmpty())
            assertTrue(f.messages.isEmpty())
        }
    }

    private fun changeAccount(f: Fixture, account: String?) {
        // Retain the entry to test request ownership separately from host invalidation.
        assertEquals("42", f.session.userId())
        if (account == null) f.session.clearSession()
        else f.session.saveLogin(JSONObject().put("heyboxid", account))
        assertEquals(account.orEmpty(), f.session.userId())
        assertEquals(account != null, f.session.isLoggedIn())
    }
}
