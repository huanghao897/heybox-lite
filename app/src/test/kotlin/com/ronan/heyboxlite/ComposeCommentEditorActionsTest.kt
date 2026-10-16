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
class ComposeCommentEditorActionsTest {
    @Test fun draftsRestoreAfterDismissAndStayIsolatedByPostAndReplyTarget() {
        val reply = comment("reply").put("root_comment_id", "root")
        val f = Fixture(comments = listOf(thread(comment("root"), reply)))
        f.actions.edit(null, "")
        f.actions.changeText("top-level draft")
        assertEquals("post:root", f.editor.draftKey)
        f.actions.dismiss()
        assertNull(f.actions.editor.value)
        f.actions.edit(reply, "")
        assertEquals("", f.editor.text)
        assertEquals("root", f.editor.rootId)
        assertEquals("reply", f.editor.replyId)
        assertEquals("Author reply", f.editor.recipient)
        f.actions.changeText("reply draft")
        f.actions.dismiss()
        f.actions.edit(null, "")
        assertEquals("top-level draft", f.editor.text)
        f.actions.edit(reply, "")
        assertEquals("reply draft", f.editor.text)
        f.enter("other-post", listOf(thread(comment("root"), reply)))
        f.actions.edit(reply, "")
        assertEquals("other-post:reply", f.editor.draftKey)
        assertEquals("", f.editor.text)
        assertEquals("top-level draft", f.session.commentDraft("post:root"))
        assertEquals("reply draft", f.session.commentDraft("post:reply"))
        assertTrue(f.callbacks.sends.isEmpty())
    }

    @Test fun topLevelSendTrimsOnlyTheRequestAndBlocksEditsAndDuplicateSendsWhilePending() {
        val f = Fixture()
        f.actions.edit(null, "")
        f.actions.changeText("  hello\n ")
        f.actions.send()
        val request = f.callbacks.sends.single()
        assertEquals("hello", request.text)
        assertNull(request.rootId)
        assertNull(request.replyId)
        assertEquals("  hello\n ", f.editor.text)
        assertTrue(f.editor.sending)
        f.actions.changeText("must not replace pending text")
        repeat(3) { f.actions.send() }
        assertEquals(1, f.callbacks.sends.size)
        assertEquals("  hello\n ", f.session.commentDraft("post:root"))

        request.callback.onSuccess(JSONObject())

        assertNull(f.actions.editor.value)
        assertEquals("", f.session.commentDraft("post:root"))
        assertSame(f.detail.item, f.callbacks.opened.single())
        assertEquals(1, f.messages.size)
    }

    @Test fun replySendPassesTheExplicitRootAndActualReplyIdAndRefreshesThePostOnSuccess() {
        val reply = comment("reply").put("root_id", "fallback-root")
        val f = Fixture(comments = listOf(thread(comment("root"), reply)))
        f.actions.edit(reply, "explicit-root")
        f.actions.changeText("  reply text  ")
        f.actions.send()
        val request = f.callbacks.sends.single()
        assertEquals("reply text", request.text)
        assertEquals("explicit-root", request.rootId)
        assertEquals("reply", request.replyId)
        assertEquals("Author reply", f.editor.recipient)

        request.callback.onSuccess(JSONObject())

        assertNull(f.actions.editor.value)
        assertEquals("", f.session.commentDraft("post:reply"))
        assertSame(f.detail.item, f.callbacks.opened.single())
    }

    @Test fun sendFailureRetainsTheDraftRestoresEditableTextAndAllowsRetry() {
        val f = Fixture()
        f.actions.edit(null, "")
        f.actions.changeText("draft to retry")
        f.actions.send()
        f.callbacks.sends.single().callback.onError("offline")

        assertFalse(f.editor.sending)
        assertEquals("draft to retry", f.editor.text)
        assertTrue(f.editor.error.contains("offline"))
        assertEquals("draft to retry", f.session.commentDraft("post:root"))
        assertTrue(f.callbacks.opened.isEmpty())
        f.actions.changeText("updated retry")
        assertEquals("", f.editor.error)
        f.actions.send()
        assertEquals(2, f.callbacks.sends.size)
        assertEquals("updated retry", f.callbacks.sends.last().text)
        f.callbacks.sends.last().callback.onSuccess(JSONObject())
        assertNull(f.actions.editor.value)
        assertEquals("", f.session.commentDraft("post:root"))
    }

    @Test fun blankDraftAndMissingCommentIdsDoNotDispatchRequestsAndGuestsCannotWrite() {
        val f = Fixture()
        f.actions.edit(null, "")
        f.actions.send()
        f.actions.changeText(" \n\t ")
        f.actions.send()
        f.actions.like(JSONObject())
        f.actions.replies(JSONObject())
        assertTrue(f.callbacks.sends.isEmpty())
        assertTrue(f.callbacks.likes.isEmpty())
        assertTrue(f.callbacks.replies.isEmpty())

        val guest = Fixture(loggedIn = false)
        guest.actions.edit(null, "")
        guest.actions.like(guest.findComment("root"))
        assertNull(guest.actions.editor.value)
        assertTrue(guest.callbacks.sends.isEmpty())
        assertTrue(guest.callbacks.likes.isEmpty())
        assertEquals(2, guest.messages.size)
        guest.actions.replies(guest.findComment("root"))
        assertEquals("root", guest.callbacks.replies.single().id)
    }

    @Test fun oldSendSuccessCannotDismissADifferentReplyEditor() {
        val f = Fixture(comments = listOf(thread(comment("root"), comment("reply"))))
        f.navigation.detail.value = f.detail.copy(refreshPending = false)
        f.actions.edit(null, "")
        f.actions.changeText("submitted top-level text")
        f.actions.send()
        val old = f.callbacks.sends.single()
        f.actions.edit(f.findComment("reply"), "root")
        f.actions.changeText("different editor draft")
        val current = f.editor

        old.callback.onSuccess(JSONObject())

        assertSame(current, f.actions.editor.value)
        assertEquals("different editor draft", f.session.commentDraft("post:reply"))
        assertTrue("An old send must not reload the page underneath a newer editor", f.callbacks.opened.isEmpty())
        assertTrue("An accepted comment must still invalidate the retained document", f.detail.refreshPending)
    }

    @Test fun dismissingAPendingSendStillRefreshesItsCurrentDetailOnSuccess() {
        val f = Fixture()
        f.navigation.detail.value = f.detail.copy(refreshPending = false)
        f.actions.edit(null, "")
        f.actions.changeText("submitted before dismiss")
        f.actions.send()
        f.actions.dismiss()

        f.callbacks.sends.single().callback.onSuccess(JSONObject())

        assertNull(f.actions.editor.value)
        assertTrue(f.detail.refreshPending)
        assertSame(f.detail.item, f.callbacks.opened.single())
        assertTrue("A dismissed editor must not show a late success notification", f.messages.isEmpty())
    }

    @Test fun aSendCompletingOnAnAuthorPageInvalidatesTheSavedParentWithoutNavigating() {
        val f = Fixture()
        f.navigation.detail.value = f.detail.copy(refreshPending = false)
        f.actions.edit(null, "")
        f.actions.changeText("parent comment")
        f.actions.send()
        f.actions.dismiss()
        f.navigation.navigate("user_space?user=author")

        f.callbacks.sends.single().callback.onSuccess(JSONObject())

        assertEquals("user_space", f.navigation.route.value)
        assertTrue(f.callbacks.opened.isEmpty())
        assertTrue(f.navigation.restoreSavedDetailIfNeeded("detail"))
        assertTrue("Returning to the parent must fetch the accepted comment", f.detail.refreshPending)
    }

    @Test fun aSuccessfulReplyReopensOnlyItsExhaustedPagination() {
        val f = Fixture()
        val other = ComposeReplyLoadState(cursor = "other-cursor", hasMore = false)
        f.navigation.detail.value = f.detail.copy(refreshPending = false,
            replyStates = mapOf("root" to ComposeReplyLoadState(cursor = "old-end", hasMore = false),
                "other" to other))
        f.actions.edit(f.findComment("root"), "root")
        f.actions.changeText("new reply")
        f.actions.send()

        f.callbacks.sends.single().callback.onSuccess(JSONObject())

        assertEquals(ComposeReplyLoadState(), f.detail.replyStates["root"])
        assertSame(other, f.detail.replyStates["other"])
        f.actions.replies(f.findComment("root"))
        assertEquals("", f.callbacks.replies.single().cursor)
        f.callbacks.replies.single().callback.onSuccess(replyPage("end", false, comment("new-reply")))
        assertEquals(listOf("root", "new-reply"), f.rowIds())
    }

    @Test fun aReplyPageStartedBeforeTheSendCannotRestoreItsOldPagination() {
        for (success in listOf(true, false)) {
            val f = Fixture()
            f.actions.replies(f.findComment("root"))
            val old = f.callbacks.replies.single().callback
            f.actions.edit(f.findComment("root"), "root")
            f.actions.changeText("new reply")
            f.actions.send()
            f.callbacks.sends.single().callback.onSuccess(JSONObject())
            val current = f.detail

            if (success) old.onSuccess(replyPage("old-end", false, comment("old-reply")))
            else old.onError("old page error")

            assertSame(current, f.detail)
            assertEquals(ComposeReplyLoadState(), f.detail.replyStates["root"])
            assertEquals(listOf("root"), f.rowIds())
        }
    }

    @Test fun oldSendFailureCannotOverwriteADifferentReplyEditor() {
        val f = Fixture(comments = listOf(thread(comment("root"), comment("reply"))))
        f.actions.edit(null, "")
        f.actions.changeText("submitted text")
        f.actions.send()
        val old = f.callbacks.sends.single()
        f.actions.edit(f.findComment("reply"), "root")
        f.actions.changeText("different reply draft")
        val current = f.editor

        old.callback.onError("old failure")

        assertSame(current, f.actions.editor.value)
        assertEquals("different reply draft", f.session.commentDraft("post:reply"))
        assertTrue(f.callbacks.opened.isEmpty())
    }

    @Test fun oldSendSuccessCannotDismissOrEraseANewDraftForTheSameReplyTarget() {
        val f = Fixture()
        f.actions.edit(f.findComment("root"), "root")
        f.actions.changeText("old submitted reply")
        f.actions.send()
        val old = f.callbacks.sends.single()
        f.actions.dismiss()
        f.actions.edit(f.findComment("root"), "root")
        f.actions.changeText("new same-target draft")
        val current = f.editor

        old.callback.onSuccess(JSONObject())

        assertSame(current, f.actions.editor.value)
        assertEquals("new same-target draft", f.session.commentDraft(current.draftKey))
        assertTrue(f.callbacks.opened.isEmpty())
    }

    @Test fun oldSendFailureCannotOverwriteAReopenedEditorEvenWhenItsDraftTextIsIdentical() {
        val f = Fixture()
        f.actions.edit(f.findComment("root"), "root")
        f.actions.changeText("same text")
        f.actions.send()
        val old = f.callbacks.sends.single()
        f.actions.dismiss()
        f.actions.edit(f.findComment("root"), "root")
        val current = f.editor
        assertEquals("same text", current.text)
        assertFalse(current.sending)

        old.callback.onError("old failure")

        assertSame(current, f.actions.editor.value)
        assertEquals("", f.editor.error)
        assertEquals("same text", f.session.commentDraft(current.draftKey))
    }

    @Test fun oldSendSuccessCannotEraseTheSavedDraftOfAReopenedEditorWithIdenticalText() {
        val f = Fixture()
        f.actions.edit(f.findComment("root"), "root")
        f.actions.changeText("same text")
        f.actions.send()
        val old = f.callbacks.sends.single()
        f.actions.dismiss()
        f.actions.edit(f.findComment("root"), "root")
        val current = f.editor

        old.callback.onSuccess(JSONObject())

        assertSame(current, f.actions.editor.value)
        assertEquals("same text", f.session.commentDraft(current.draftKey))
        assertTrue(f.callbacks.opened.isEmpty())
    }

    @Test fun draftOpenedForAnotherPostCannotDispatchAgainstTheNewCurrentPost() {
        val f = Fixture()
        f.actions.edit(null, "")
        f.actions.changeText("belongs to the original post")
        f.enter("another-post", listOf(thread(comment("another-root"))))

        f.actions.send()

        assertTrue("The callback bridge sends against the current post, not the editor's old post",
            f.callbacks.sends.isEmpty())
        assertEquals("belongs to the original post", f.session.commentDraft("post:root"))
        assertTrue(f.callbacks.opened.isEmpty())
    }

    @Test fun oldCompletionCannotReleaseANewerPendingSendForTheSameTarget() {
        val f = Fixture()
        f.actions.edit(null, "")
        f.actions.changeText("old submitted text")
        f.actions.send()
        val old = f.callbacks.sends.single()
        f.actions.dismiss()
        f.actions.edit(null, "")
        f.actions.changeText("new submitted text")
        f.actions.send()
        val current = f.editor
        assertTrue(current.sending)

        old.callback.onError("stale failure")
        old.callback.onSuccess(JSONObject())

        assertSame(current, f.actions.editor.value)
        assertEquals("new submitted text", f.session.commentDraft(current.draftKey))
        assertTrue(f.callbacks.opened.isEmpty())
        f.callbacks.sends.last().callback.onSuccess(JSONObject())
        assertNull(f.actions.editor.value)
        assertEquals("", f.session.commentDraft(current.draftKey))
        assertEquals(1, f.callbacks.opened.size)
    }

    @Test fun staleSendCallbacksCannotDismissAFreshEntryEditorOrEraseItsSamePostDraft() {
        val f = Fixture()
        f.actions.edit(null, "")
        f.actions.changeText("old post text")
        f.actions.send()
        val old = f.callbacks.sends.single()
        val key = f.detail.entryKey
        f.enter("post", listOf(thread(comment("root"))))
        assertNotEquals(key, f.detail.entryKey)
        f.actions.edit(null, "")
        f.actions.changeText("fresh entry draft")
        val current = f.editor
        val detail = f.detail

        old.callback.onSuccess(JSONObject())
        old.callback.onError("stale failure")

        assertSame(current, f.actions.editor.value)
        assertSame(detail, f.detail)
        assertEquals("fresh entry draft", f.session.commentDraft(current.draftKey))
        assertTrue(f.callbacks.opened.isEmpty())
        assertTrue(f.messages.isEmpty())
    }

    @Test fun closedActionsIgnorePendingLikeReplyAndSendCallbacks() {
        val f = Fixture()
        val root = f.findComment("root")
        f.actions.like(root)
        f.actions.replies(root)
        f.actions.edit(null, "")
        f.actions.changeText("submitted before close")
        f.actions.send()
        f.actions.close()
        val detail = f.detail
        val replacement = ComposeCommentActions(f.navigation, f.session, f.callbacks, f.messages::add)
        replacement.edit(null, "")
        replacement.changeText("draft from replacement actions")
        val editor = replacement.editor.value

        f.callbacks.likes.single().callback.onSuccess(JSONObject())
        f.callbacks.likes.single().callback.onError("late like failure")
        f.callbacks.replies.single().callback.onSuccess(replyPage("late", false, comment("late-reply")))
        f.callbacks.replies.single().callback.onError("late reply failure")
        f.callbacks.sends.single().callback.onSuccess(JSONObject())
        f.callbacks.sends.single().callback.onError("late send failure")

        assertNull(f.actions.editor.value)
        assertSame(detail, f.detail)
        assertSame(editor, replacement.editor.value)
        assertEquals("draft from replacement actions", f.session.commentDraft("post:root"))
        assertTrue(f.callbacks.opened.isEmpty())
        assertTrue(f.messages.isEmpty())
        replacement.close()
    }
}
