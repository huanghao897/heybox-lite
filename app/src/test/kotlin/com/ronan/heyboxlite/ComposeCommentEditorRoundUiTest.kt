package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeCommentEditorRoundUiTest : ComposeRoundLayoutHarness() {
    private var fixture: ComposeCommentActionsTestFixtures.Fixture? = null

    @After fun closeActions() { fixture?.actions?.close() }

    @Test fun squareEditorKeepsSendSeparateFromTheScrollableDraft() = verifyEditor()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192EditorCanSubmitWithoutTouchingTheClippedCorners() = verifyEditor()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227EditorCanSubmitWithoutTouchingTheClippedCorners() = verifyEditor()

    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun manuallySelectedRoundEditorUsesTheCircularDialog() = verifyEditor(forceRound = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundLargeTextEditorKeepsSendTouchable() = verifyEditor(textScale = 1.4f)

    private fun verifyEditor(forceRound: Boolean = false, textScale: Float = 1f) {
        val f = ComposeCommentActionsTestFixtures.Fixture().also { fixture = it }
        val services = layoutServices(forceRound, textScale = textScale)
        f.actions.edit(null, "")
        f.actions.changeText("Offline draft")
        showLayout(services) {
            f.actions.editor.value?.let { ComposeCommentEditor(it, f.actions) }
        }
        text("发表评论").assertIsDisplayed()
        assertFits("发送")
        assertControlInsideDisplay(compose.onNodeWithTag("watch-dialog-frame"), "comment editor frame")
        touchReachable("发送")
        compose.runOnIdle {
            assertEquals("Offline draft", f.callbacks.sends.single().text)
            assertNotNull(f.actions.editor.value)
        }
    }
}
