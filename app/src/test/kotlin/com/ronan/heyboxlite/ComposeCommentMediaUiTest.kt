package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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
@Config(sdk = [28], application = Application::class, qualifiers = "w192dp-h192dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeCommentMediaUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val requests = Images()

    @Test fun narrowRoundReplyUsesSingleImageColumnsAndOpensOriginals() {
        val opened = mutableListOf<String>()
        val images = (1..3).map { CommentData.CommentImage("preview-$it", "original-$it", "image/jpeg", false) }
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(true)) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Box(Modifier.width(76.dp)) {
                        ComposeCommentImageGrid(images, ComposeMediaSettings(), true, opened::add, requests)
                    }
                }
            }
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(3)
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        val bounds = nodes.map { it.boundsInRoot }
        assertTrue(bounds.all { it.left >= 58f && it.right <= 134f })
        assertTrue(bounds.zipWithNext().all { (previous, next) -> previous.bottom <= next.top })
        assertTrue(requests.targets.all { it == 96 })
        compose.onAllNodes(hasClickAction())[1].performClick()
        assertEquals(listOf("original-2"), opened)
        compose.runOnIdle { captureMembershipUi(compose.activity, "comment-images-round192-narrow") }
    }

    @Test fun squareCommentUsesThreeCompactColumnsWithoutOpeningThumbnailUrls() {
        val opened = mutableListOf<String>()
        val images = (1..3).map { CommentData.CommentImage("preview-$it", "original-$it", "image/jpeg", false) }
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Box(Modifier.width(184.dp)) {
                        ComposeCommentImageGrid(images, ComposeMediaSettings(), false, opened::add, requests)
                    }
                }
            }
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(3)
        val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertEquals(1, nodes.map { it.boundsInRoot.top }.toSet().size)
        assertTrue(nodes.zipWithNext().all { (left, right) -> left.boundsInRoot.right <= right.boundsInRoot.left })
        compose.onAllNodes(hasClickAction())[2].performClick()
        assertEquals(listOf("original-3"), opened)
        assertEquals(listOf("preview-1", "preview-2", "preview-3"), requests.urls)
        assertTrue(requests.animatedUrls.isEmpty())
    }

    @Test fun mixedCommentMediaAnimatesOnlyTheGifOriginalAndHonorsThePlaybackSetting() {
        val opened = mutableListOf<String>()
        val playGif = mutableStateOf(false)
        val images = CommentData.commentImages(JSONObject().put("imgs", JSONArray()
            .put(JSONObject().put("thumb_url", "gif-preview.jpg").put("original", "original.gif"))
            .put(JSONObject().put("thumb_url", "static-preview.jpg").put("original", "original.jpg"))))
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Box(Modifier.width(184.dp)) {
                        ComposeCommentImageGrid(images, ComposeMediaSettings(playGif = playGif.value),
                            false, opened::add, requests)
                    }
                }
            }
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        compose.runOnIdle {
            assertEquals(listOf("gif-preview.jpg", "static-preview.jpg"), requests.urls)
            assertTrue(requests.animatedUrls.isEmpty())
            playGif.value = true
        }
        compose.runOnIdle { assertEquals(listOf("original.gif"), requests.animatedUrls) }
        compose.onAllNodes(hasClickAction())[0].performClick()
        assertEquals(listOf("original.gif"), opened)
    }

    @Test fun longReplyTargetWrapsWithinSmallRoundWidthRatherThanOverlappingTheBody() {
        val heading = "Very long author name 回复 Very long target user name"
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(true)) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ComposeCommentReplyText("Very long author name", "Very long target user name",
                        "Body", false, "Today", Modifier.width(130.dp))
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(heading).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        val layout = layouts.single()
        assertTrue(layout.lineCount > 1)
        assertFalse(layout.hasVisualOverflow)
        assertEquals(heading.length, layout.getLineEnd(layout.lineCount - 1))
        compose.runOnIdle { captureMembershipUi(compose.activity, "comment-reply-round192-long-names") }
    }

    private class Images : ComposeMediaImageRequests {
        val urls = mutableListOf<String>()
        val targets = mutableListOf<Int>()
        val animatedUrls = mutableListOf<String>()
        override fun load(view: ImageView, url: String, targetPx: Int, complete: (Boolean, Bitmap?) -> Unit) {
            urls += url
            targets += targetPx
            view.setImageDrawable(ColorDrawable(Color.GREEN))
            complete(true, null)
        }
        override fun animate(view: ImageView, url: String) { animatedUrls += url }
        override fun cancel(view: ImageView) { }
    }
}
