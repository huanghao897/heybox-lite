package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w192dp-h192dp-round-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeFeedActionRowsUiTest : ComposeMembershipUiRegressionHarness() {
    @Test fun narrowLargeTextCardKeepsBothCountsAndTouchActions() {
        val palette = theme().copy(uiScale = 0.82f, textScale = 1.8f)
        val item = FeedItem.from(JSONObject().put("linkid", "post")
            .put("like_num", 123456).put("comment_num", 234567))
        val actions = mutableListOf<FeedAction>()
        setScreen(palette, systemFontScale = 1.3f) {
            Box(Modifier.fillMaxSize().background(palette.background), Alignment.Center) {
                Box(Modifier.width(120.dp)) {
                    ComposeFeedCardActions(item, ComposeFeedActionState.from(item), false, false,
                        palette, true, ExistingComposeImageLoader, false,
                        onAction = { _, action -> actions += action })
                }
            }
        }
        val like = description("点赞").fetchSemanticsNode().boundsInRoot
        val comment = description("评论").fetchSemanticsNode().boundsInRoot
        assertTrue("Two long counts need separate rows", like.bottom <= comment.top)
        listOf(Format.commentLikeCount(item.likes), Format.commentLikeCount(item.comments)).forEach { value ->
            val layouts = mutableListOf<TextLayoutResult>()
            text(value).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("The full count must stay visible", layouts.isNotEmpty() && layouts.all {
                !paintedTextLayout(it).hasVisualOverflow
            })
        }
        description("点赞").performTouchInput { click() }
        description("评论").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(FeedAction.LIKE, FeedAction.COMMENT), actions) }
    }
}
