package com.ronan.heyboxlite

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
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
class ComposeInlineLinkUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun tappingLinkDoesNotAlsoTriggerTheParentReplyAction() {
        val opened = mutableListOf<String>()
        var replies = 0
        compose.setContent {
            Box(Modifier.width(200.dp).clickable { replies++ }) {
                ComposeInlineText(markup("Lottery", "first"), false, Color.White, Color.Blue,
                    Modifier.testTag("text"), prefix = AnnotatedString("Iris: "),
                    onOpenLink = { opened.add(it) })
            }
        }
        tapText("Lottery")
        compose.runOnIdle {
            assertEquals(listOf("https://example.invalid/first"), opened)
            assertEquals(0, replies)
        }
        tapText("Iris")
        compose.runOnIdle { assertEquals(1, replies) }
    }

    @Test fun replacingTheTextAndPrefixUsesOnlyTheCurrentLinkTarget() {
        val opened = mutableListOf<String>()
        val value = mutableStateOf("first")
        compose.setContent {
            ComposeInlineText(markup("Lottery", value.value), false, Color.White, Color.Blue,
                Modifier.width(200.dp).testTag("text"),
                prefix = AnnotatedString("${value.value}: "), onOpenLink = { opened.add(it) })
        }
        compose.runOnIdle { value.value = "second" }
        tapText("Lottery")
        compose.runOnIdle { assertEquals(listOf("https://example.invalid/second"), opened) }
    }

    private fun markup(label: String, path: String) = RichGameLinkMarkup.normalizeAnchors(
        "<a data-link-type='text' href='https://example.invalid/$path'>$label</a>")

    private fun tapText(value: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("text", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        val layout = layouts.single()
        val bounds = layout.getBoundingBox(layout.layoutInput.text.text.indexOf(value))
        compose.onNodeWithTag("text", useUnmergedTree = true).performTouchInput { click(bounds.center) }
        compose.waitForIdle()
    }
}
