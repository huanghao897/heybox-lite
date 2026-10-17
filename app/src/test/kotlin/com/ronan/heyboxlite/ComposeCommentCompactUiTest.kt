package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeCommentCompactUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun expandRepliesIsACompactFilledCapsuleAndKeepsFiveAtATime() {
        val root = JSONObject().put("commentid", "root").put("text", "Root")
            .put("child_num", 12).put("user", user("Author", 24))
        val group = JSONObject().put("comment", JSONArray().put(root))
        var requests = 0
        val theme = composePreviewTheme(false)
        compose.setContent {
            HeyboxComposeTheme(theme) {
                Box(Modifier.width(220.dp)) {
                    ComposeDetailThread(group, FeedItem.from(JSONObject().put("linkid", "post")),
                        false, ComposeMediaSettings(enabled = false), { requests++ })
                }
            }
        }
        val capsule = compose.onNodeWithTag("comment-reply-capsule-展开 5 条回复")
        val bounds = capsule.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width < 145f && bounds.height <= 30f)
        compose.runOnIdle {
            val rootView = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
            try {
                rootView.draw(Canvas(bitmap))
                assertEquals(theme.panelElevated.toArgb(), bitmap.getPixel(
                    bounds.center.x.toInt(), (bounds.top + 2).toInt()))
                captureMembershipUi(compose.activity, "comment-expand-reply-capsule")
            } finally { bitmap.recycle() }
        }
        capsule.performClick()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun squareRepliesKeepTheirWidthAndUseOneLineWithoutLevelBadges() = verifyReplies(false, 220)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundRepliesKeepTheirWidthAndUseOneLineWithoutLevelBadges() = verifyReplies(true, 158)

    private fun verifyReplies(round: Boolean, width: Int) {
        val root = JSONObject().put("commentid", "root").put("text", "Root body")
            .put("user", user("Author", 24))
        val reply = JSONObject().put("commentid", "reply").put("text", "ok")
            .put("replyid", "root").put("user", user("Bo", 56))
        val group = JSONObject().put("comment", JSONArray().put(root).put(reply))
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(round)) {
                Column(Modifier.width(width.dp)) {
                    ComposeDetailThread(group, FeedItem.from(JSONObject().put("linkid", "post")),
                        round, ComposeMediaSettings(enabled = false), {})
                }
            }
        }
        compose.onNodeWithText("Lv.24", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Lv.56", useUnmergedTree = true).assertDoesNotExist()
        val section = compose.onNodeWithTag("comment-thread-root").fetchSemanticsNode().boundsInRoot
        val replyBounds = compose.onNodeWithTag("comment-reply").fetchSemanticsNode().boundsInRoot
        val rootBody = compose.onNodeWithText("Root body").fetchSemanticsNode().boundsInRoot
        val replyFrame = compose.onNodeWithTag("comment-replies-root").fetchSemanticsNode().boundsInRoot
        assertEquals("The reply frame must align with the primary body, not its avatar",
            rootBody.left, replyFrame.left, 0.5f)
        assertEquals(rootBody.right, replyFrame.right, 0.5f)
        assertTrue("Only the reply frame's compact padding should reduce body width",
            rootBody.width - replyBounds.width <= 11f)
        if (round) assertTrue("Round replies must not waste the watch width",
            replyBounds.width >= section.width * 0.83f)
        val text = compose.onNodeWithText("Bo：ok").fetchSemanticsNode().boundsInRoot
        assertTrue("A short reply must not reserve a separate author row", text.height <= 19f)
        assertTrue("An empty image wrapper must not add reply height", replyBounds.height <= 20f)
        compose.runOnIdle { captureMembershipUi(compose.activity, "compact-replies-${if (round) "round" else "square"}") }
    }

    @Test fun pinnedCornerOverlaysTheThreadWithoutBecomingAFullWidthBadge() {
        val root = JSONObject().put("commentid", "root").put("text", "Root body")
            .put("top_comment", 1).put("user", user("Author", 24))
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeDetailThread(JSONObject().put("comment", JSONArray().put(root)),
                    FeedItem.from(JSONObject().put("linkid", "post")), false,
                    ComposeMediaSettings(enabled = false), {})
            }
        }
        val thread = compose.onNodeWithTag("comment-thread-root").fetchSemanticsNode().boundsInRoot
        val corner = compose.onNodeWithTag("comment-pinned-corner").fetchSemanticsNode().boundsInRoot
        val avatar = compose.onNodeWithContentDescription("Author的头像").fetchSemanticsNode().boundsInRoot
        assertEquals(thread.left, corner.left, 0.5f)
        assertEquals(thread.top, corner.top, 0.5f)
        assertTrue(corner.width <= 30f && corner.height <= 30f)
        assertTrue(avatar.top + avatar.left - corner.left >= corner.bottom)
        compose.onNodeWithText("置顶", useUnmergedTree = true).assertExists()
        compose.runOnIdle { captureMembershipUi(compose.activity, "comment-official-pinned-corner") }
    }

    @Test fun pinnedTriangleRemainsVisibleOnBothLightAndDarkSurfaces() {
        val dark = mutableStateOf(true)
        val base = composePreviewTheme(false)
        compose.setContent {
            val theme = base.copy(dark = dark.value,
                background = if (dark.value) Color.Black else Color.White)
            HeyboxComposeTheme(theme) {
                Box(Modifier.size(40.dp).background(theme.background)) {
                    ComposeCommentPinnedCorner()
                }
            }
        }
        for (darkMode in listOf(true, false)) {
            compose.runOnIdle { dark.value = darkMode }
            compose.waitForIdle()
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(bitmap))
                    assertEquals("The official gradient must render, not the transparent PNG fallback",
                        android.graphics.Color.rgb(45, 50, 55), bitmap.getPixel(2, 2))
                    assertEquals(if (darkMode) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
                        bitmap.getPixel(27, 27))
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test fun commentSortRemainsCompactAndKeepsDistinctSelectionSemantics() {
        val latest = mutableStateOf(false)
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Row {
                    ComposeDetailSortTab("热门", !latest.value) { latest.value = false }
                    ComposeDetailSortTab("最新", latest.value) { latest.value = true }
                }
            }
        }
        val hot = compose.onNodeWithTag("comment-sort-热门")
        val new = compose.onNodeWithTag("comment-sort-最新")
        hot.assertIsSelected()
        val hotBounds = hot.fetchSemanticsNode().boundsInRoot
        val newBounds = new.fetchSemanticsNode().boundsInRoot
        assertTrue(hotBounds.height <= 26f && newBounds.height <= 26f)
        assertTrue(newBounds.right - hotBounds.left <= 70f)
        new.performClick().assertIsSelected()
        compose.runOnIdle { captureMembershipUi(compose.activity, "comment-compact-sort") }
    }

    @Test fun followUsesTheOfficialTextPatternAndDisablesOnlyWhilePending() {
        val following = mutableStateOf(false)
        val pending = mutableStateOf(false)
        var toggles = 0
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeDetailFollowButton(following.value, pending.value) { toggles++ }
            }
        }
        val button = compose.onNodeWithTag("detail-follow")
        compose.onNodeWithText("关注", useUnmergedTree = true).assertExists()
        val size = button.fetchSemanticsNode().size
        assertTrue("Follow must be a compact text button, not a circular plus", size.width > size.height)
        assertTrue("Follow icon and padding should keep the button narrow", size.width <= 44)
        button.performClick()
        assertEquals(1, toggles)
        compose.runOnIdle { following.value = true; pending.value = true }
        compose.onNodeWithText("已关注", useUnmergedTree = true).assertExists()
        button.performClick()
        assertEquals(1, toggles)
        compose.runOnIdle { pending.value = false }
        compose.onNodeWithContentDescription("取消关注").performClick()
        assertEquals(2, toggles)
        compose.runOnIdle { captureMembershipUi(compose.activity, "comment-official-follow-button") }
    }

    private fun user(name: String, level: Int) = JSONObject().put("userid", name)
        .put("username", name).put("level", level)
}
