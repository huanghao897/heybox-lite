package com.ronan.heyboxlite

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.app.Application

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w192dp-h192dp-round-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeAccountStatsUiTest : ComposeMembershipUiRegressionHarness() {
    @Test fun roundLargeTextStatsWrapCountsAndLabelsWithoutEllipsizing() {
        val palette = theme().copy(uiScale = 0.5f, textScale = 2.4f)
        setScreen(palette) {
            Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.Center) {
                Row(Modifier.width(170.dp)) {
                    WatchStat("关注", 12345, Modifier.weight(1f))
                    WatchStat("粉丝", 23456, Modifier.weight(1f))
                    WatchStat("获赞", 34567, Modifier.weight(1f))
                }
            }
        }
        val labels = listOf(12345, 23456, 34567).map(Format::commentLikeCount) + listOf("关注", "粉丝", "获赞")
        labels.forEach { value ->
            val layouts = layouts(text(value)).map(::paintedTextLayout)
            assertTrue("Wrapped statistics must scale their line height with their type",
                layouts.all { it.layoutInput.style.lineHeight.value >= it.layoutInput.style.fontSize.value })
            assertTrue("$value must remain fully readable at large watch text", layouts.isNotEmpty() &&
                layouts.all { layout -> !layout.hasVisualOverflow && !layout.multiParagraph.didExceedMaxLines &&
                    (0 until layout.lineCount).none(layout::isLineEllipsized) })
        }
    }

    private fun layouts(node: androidx.compose.ui.test.SemanticsNodeInteraction): List<TextLayoutResult> {
        val result = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
        return result
    }
}
