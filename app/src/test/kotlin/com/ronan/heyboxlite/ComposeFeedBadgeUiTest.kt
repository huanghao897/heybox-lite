package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeFeedBadgeUiTest : ComposeWatchActionButtonTestHarness() {
    @Test fun squarePostBadgeIsShortAndFlat() = verifyBadge()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundPostBadgeIsShortAndFlat() = verifyBadge(round = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun largeTextRoundBadgeKeepsItsWholeLabel() = verifyBadge(round = true, large = true)

    private fun verifyBadge(round: Boolean = false, large: Boolean = false) {
        val theme = theme(large = large)
        val pinned = mutableStateOf(false)
        show(theme, large) {
            Column(Modifier.width((if (round) 160 else 220).dp)) {
                ComposeFeedCard(FeedItem.from(JSONObject().put("linkid", "post")
                    .put("title", "Post title").put("is_top", pinned.value)),
                    theme, true, "", {}, null, showFollow = false)
            }
        }
        for (pin in listOf(false, true)) {
            compose.runOnIdle { pinned.value = pin }
            val label = if (pin) "置顶 · 帖子" else "帖子"
            val node = compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
            val size = node.fetchSemanticsNode().size
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = paintedTextLayout(layouts.single())
            assertEquals(label, layout.layoutInput.text.text)
            assertFalse("The compact badge must retain all text", layout.multiParagraph.didExceedMaxLines)
            assertFalse(layout.isLineEllipsized(0))
            assertEquals(label.length, layout.getLineEnd(0))
            if (!large) {
                assertTrue("No inherited Material line height may inflate the capsule", size.height <= 15)
                if (!pin) assertTrue("Post label must have compact horizontal padding", size.width <= 27)
            }
            label.forEachIndexed { index, character ->
                if (!character.isWhitespace()) {
                    val glyph = layout.getBoundingBox(index)
                    assertTrue(glyph.left >= -0.5f && glyph.right <= size.width + 0.5f)
                    assertTrue(glyph.top >= -0.5f && glyph.bottom <= size.height + 0.5f)
                }
            }
            compose.runOnIdle { captureMembershipUi(compose.activity,
                "compact-feed-badge-${if (round) "round" else "square"}-${if (large) "large" else "normal"}-$pin") }
        }
    }
}
