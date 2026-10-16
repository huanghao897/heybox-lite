package com.ronan.heyboxlite

import android.app.Application
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeCommentThreadUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val media = ComposeMediaSettings(enabled = false)

    @Test fun squareMetadataBadgesOfficialLikeAndAvatarUseCurrentSnapshot() {
        val opened = mutableListOf<List<String>>()
        val actions = mutableListOf<ComposeDetailAction>()
        val group = mutableStateOf(group("root", replies = 0, expected = 0))
        val original = group.value.getJSONArray("comment").getJSONObject(0)
        original.put("top_comment", 1).put("comment_award_num", 8).put("ip_location", "Shanghai")
        original.getJSONObject("user").put("avatar", "").put("avartar", "original-avatar")
            .put("level_info", JSONObject().put("level", 24))
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeDetailThread(group.value, authorItem(), false, media, actions::add,
                    { id, name, avatar -> opened += listOf(id, name, avatar) })
            }
        }
        compose.onNodeWithText("置顶").assertExists()
        compose.onNodeWithText("作者", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Lv.24", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Shanghai", useUnmergedTree = true).assertExists()
        val like = compose.onNodeWithContentDescription("点赞评论")
        val name = compose.onNodeWithText("Author", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(name.right <= like.fetchSemanticsNode().boundsInRoot.left)
        like.performClick()
        compose.runOnIdle {
            assertSame(original, (actions.single() as ComposeDetailAction.LikeComment).comment)
            val clone = JSONObject(group.value.toString())
            clone.getJSONArray("comment").getJSONObject(0).put("is_support", 1)
                .put("comment_award_num", 9)
            group.value = clone
        }
        compose.onNodeWithText("9", useUnmergedTree = true).assertExists()
        assertEquals("已点赞", like.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        compose.onNodeWithContentDescription("Author的头像").performClick()
        assertEquals(listOf(listOf("author-id", "Author", "original-avatar")), opened)
        compose.runOnIdle {
            assertFalse(containsNativeRichText(compose.activity.window.decorView))
            captureMembershipUi(compose.activity, "comment-thread-square240-metadata")
        }
    }

    @Test fun narrowRoundNamesBadgesAndReplyRelationsStayInsideTheirSections() {
        val payload = group("root", replies = 1, expected = 1)
        val root = payload.getJSONArray("comment").getJSONObject(0)
        root.getJSONObject("user").put("username", "A very long author name").put("level", 40)
        root.put("text", "Root text wraps on the small watch")
        payload.getJSONArray("comment").getJSONObject(1)
            .put("replyid", "sibling").put("replyuser", JSONObject().put("username", "Long sibling name"))
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(true).copy(textScale = 1.4f)) {
                Column(Modifier.width(148.dp).verticalScroll(rememberScrollState())) {
                    ComposeDetailThread(payload, authorItem(), true, media, {})
                }
            }
        }
        val section = compose.onNodeWithTag("comment-thread-root").fetchSemanticsNode().boundsInRoot
        val replies = compose.onNodeWithTag("comment-replies-root").fetchSemanticsNode().boundsInRoot
        assertTrue(replies.left - section.left < 14f)
        val name = compose.onNodeWithText("A very long author name", useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        name.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow)
        val like = compose.onNodeWithContentDescription("点赞评论").fetchSemanticsNode().boundsInRoot
        assertTrue(name.fetchSemanticsNode().boundsInRoot.bottom <= like.top)
        for (label in listOf("作者", "Lv.40")) {
            val badge = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(badge.left >= section.left && badge.right <= section.right)
            assertTrue(badge.bottom <= like.top)
        }
        compose.onNodeWithText("Reply 1 回复 Long sibling name").performScrollTo().assertExists()
        compose.runOnIdle { captureMembershipUi(compose.activity, "comment-thread-round148-large-text") }
    }

    @Test fun replyAndCopyActionsKeepTheRootAssociationWithoutMutatingJson() {
        val actions = mutableListOf<ComposeDetailAction>()
        val copied = mutableListOf<String>()
        val payload = group("root", replies = 2, expected = 2)
        val comments = payload.getJSONArray("comment")
        val sibling = comments.getJSONObject(2).put("replyid", "reply-1")
            .put("replyuser", JSONObject().put("username", "Reply 1"))
        sibling.put("text", "Before <a data-link-type=\"game\" data-game-id=\"42\">Game</a> after")
        val before = payload.toString()
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ComposeDetailThread(payload, authorItem(), false, media, actions::add,
                        onCopyComment = copied::add)
                }
            }
        }
        compose.onNodeWithTag("comment-reply-2").performScrollTo()
        invokeCommentAction("reply-2", "回复评论")
        compose.onNodeWithTag("comment-reply-2").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        invokeCommentAction("root", "回复评论")
        compose.runOnIdle {
            val replies = actions.filterIsInstance<ComposeDetailAction.Reply>()
            assertEquals(listOf("root", "root"), replies.map { it.rootId })
            assertSame(sibling, replies.first().comment)
            assertSame(comments.getJSONObject(0), replies.last().comment)
            assertEquals(listOf("Before Game after"), copied)
            assertEquals(before, payload.toString())
        }
    }

    @Test fun doubleTapSettingAndUpdatedCallbacksDoNotRetainTheOldCommentSnapshot() {
        val actions = mutableListOf<ComposeDetailAction>()
        val enabled = mutableStateOf(false)
        val payload = mutableStateOf(group("root", replies = 0, expected = 0))
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeDetailThread(payload.value, authorItem(), false, media, actions::add,
                    doubleTapReplyEnabled = enabled.value)
            }
        }
        compose.onNodeWithText("Root body").performTouchInput { doubleClick() }
        compose.runOnIdle {
            assertTrue(actions.isEmpty())
            enabled.value = true
        }
        compose.onNodeWithText("Root body").performTouchInput { doubleClick() }
        val replacement = JSONObject(payload.value.toString())
        compose.runOnIdle { payload.value = replacement }
        compose.onNodeWithText("Root body").performTouchInput { doubleClick() }
        compose.runOnIdle {
            val replies = actions.filterIsInstance<ComposeDetailAction.Reply>()
            assertEquals(2, replies.size)
            assertSame(replacement.getJSONArray("comment").getJSONObject(0), replies.last().comment)
            assertEquals("root", replies.last().rootId)
        }
    }

    @Test fun authoritativeHasMoreOverridesMissingAndStaleCountsAndRootChangesResetThePreview() {
        val payload = mutableStateOf(group("root", replies = 0, expected = 0))
        val state = mutableStateOf(ComposeReplyLoadState(hasMore = true))
        val actions = mutableListOf<ComposeDetailAction>()
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ComposeDetailThread(payload.value, authorItem(), false, media, actions::add,
                        replyState = state.value)
                }
            }
        }
        compose.onNodeWithText("展开 5 条回复").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, actions.size)
            payload.value = group("root", replies = 3, expected = 20)
            state.value = ComposeReplyLoadState(hasMore = false)
        }
        compose.onNodeWithText("展开 5 条回复").assertDoesNotExist()
        compose.onNodeWithText("Reply 3").assertExists()
        compose.runOnIdle {
            payload.value = group("root", replies = 10, expected = 10)
        }
        compose.onNodeWithText("展开 5 条回复").performScrollTo().performClick()
        compose.onNodeWithText("Reply 10").assertExists()
        compose.runOnIdle { payload.value = group("another-root", replies = 10, expected = 10) }
        compose.onNodeWithText("Reply 6").assertDoesNotExist()
        compose.onNodeWithText("Reply 5").assertExists()
    }

    @Test fun collapsingDuringARealRequestDoesNotReexpandWhenItsRepliesArrive() {
        val payload = mutableStateOf(group("root", replies = 7, expected = 13))
        val state = mutableStateOf(ComposeReplyLoadState())
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ComposeDetailThread(payload.value, authorItem(), false, media, {
                        state.value = ComposeReplyLoadState(loading = true)
                    }, replyState = state.value)
                }
            }
        }
        repeat(2) { compose.onNodeWithText("展开 5 条回复").performScrollTo().performClick() }
        compose.onNodeWithText("收起回复").performScrollTo().performClick()
        compose.runOnIdle {
            payload.value = group("root", replies = 13, expected = 13)
            state.value = ComposeReplyLoadState(hasMore = false)
        }
        compose.onNodeWithText("Reply 6").assertDoesNotExist()
        compose.onNodeWithText("Reply 5").assertExists()
    }

    @Test fun cachedReplyExpansionSurvivesRestorationAndStillAdvancesFiveAtATime() {
        val restoration = StateRestorationTester(compose)
        val payload = group("root", replies = 12, expected = 12)
        restoration.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ComposeDetailThread(payload, authorItem(), false, media, {})
                }
            }
        }
        compose.onNodeWithText("展开 5 条回复").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Reply 10").assertExists()
        compose.onNodeWithText("Reply 11").assertDoesNotExist()
        compose.onNodeWithText("展开 2 条回复").performScrollTo().performClick()
        compose.onNodeWithText("Reply 12").assertExists()
        compose.onNodeWithText("收起回复").performScrollTo().performClick()
        compose.onNodeWithText("Reply 6").assertDoesNotExist()
    }

    private fun invokeCommentAction(commentId: String, label: String) {
        val action = compose.onNodeWithTag("comment-$commentId").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single { it.label == label }
        compose.runOnIdle { assertTrue(action.action()) }
    }

    private fun containsNativeRichText(view: View): Boolean = view is ComposeRichTextView ||
        (view is ViewGroup && (0 until view.childCount).any { containsNativeRichText(view.getChildAt(it)) })

    private fun authorItem() = FeedItem.from(JSONObject().put("linkid", "post").put("user",
        JSONObject().put("userid", "author-id").put("username", "Author")))

    private fun group(id: String, replies: Int, expected: Int): JSONObject {
        val comments = JSONArray().put(JSONObject().put("commentid", id).put("child_num", expected)
            .put("text", "Root body").put("user", JSONObject().put("userid", "author-id")
                .put("username", "Author")))
        for (index in 1..replies) comments.put(JSONObject().put("comment_id", "reply-$index")
            .put("create_at", index).put("text", "Body $index").put("replyid", id)
            .put("replyuser", JSONObject().put("username", "Author"))
            .put("user", JSONObject().put("username", "Reply $index")))
        return JSONObject().put("comment", comments)
    }
}
