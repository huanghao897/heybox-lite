package com.ronan.heyboxlite

import android.app.Application
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.descendants
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeTutorialUiTest : ComposeRoundLayoutHarness() {
    @Test fun nextPreviousAndFinalConfirmationPersistTheActualCompletedTopic() {
        val store = TutorialStore(compose.activity)
        val state = TutorialState(store)
        show(theme()) {
            CompositionLocalProvider(LocalTutorialState provides state) {
                ComposeTutorialTrigger(TutorialTopic.HOME)
                ComposeTutorialOverlay(state)
            }
        }
        text("下拉，换一批帖子").assertIsDisplayed()
        click("下一步")
        text("再点一次首页").assertIsDisplayed()
        assertFalse(store.completed(TutorialTopic.HOME))
        click("上一步")
        text("下拉，换一批帖子").assertIsDisplayed()
        click("下一步")
        click("下一步")
        click("知道了")
        assertTrue(store.completed(TutorialTopic.HOME))
        compose.onNodeWithTag("tutorial-viewport").assertDoesNotExist()
    }

    @Test fun laterAndBackKeepTheRouteOpenAndAllowManualReplay() {
        val store = TutorialStore(compose.activity)
        val state = TutorialState(store)
        show(theme()) {
            CompositionLocalProvider(LocalTutorialState provides state) {
                ComposeTutorialTrigger(TutorialTopic.HOME)
                ComposeTutorialOverlay(state)
            }
        }
        click("稍后")
        assertFalse(store.completed(TutorialTopic.HOME))
        compose.runOnIdle { state.replay(TutorialTopic.HOME) }
        text("下拉，换一批帖子").assertIsDisplayed()
        compose.runOnIdle {
            val dialog = ShadowDialog.getLatestDialog()
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        compose.onNodeWithTag("tutorial-viewport").assertDoesNotExist()
        assertFalse(store.completed(TutorialTopic.HOME))
    }

    @Test fun retainedInactivePagesCannotOfferTutorials() {
        val active = mutableStateOf(false)
        val state = TutorialState(TutorialStore(compose.activity))
        show(theme()) {
            CompositionLocalProvider(LocalTutorialState provides state) {
                ComposeTutorialTrigger(TutorialTopic.DETAIL, active.value)
                ComposeTutorialOverlay(state)
            }
        }
        compose.onNodeWithTag("tutorial-viewport").assertDoesNotExist()
        compose.runOnIdle { active.value = true }
        compose.onNodeWithTag("tutorial-viewport").assertIsDisplayed()
        compose.runOnIdle { active.value = false }
        compose.onNodeWithTag("tutorial-viewport").assertDoesNotExist()
    }

    @Test fun settingsExposesTheTutorialDirectory() {
        val services = layoutServices()
        val routes = mutableListOf<String>()
        show(services.theme) { ComposeSettingsScreen("settings_home", services, routes::add, {}, {}) }
        reach("使用教程").performClick()
        assertEquals(listOf("tutorials"), routes)
    }

    @Test fun directoryCanReplayTheImageTutorialWithoutOpeningAnImageOrLeavingTheDirectory() {
        val services = layoutServices()
        val state = TutorialState(TutorialStore(compose.activity))
        var backs = 0
        show(services.theme) {
            ComposeTutorialLibrary(state) { backs++ }
            ComposeTutorialOverlay(state)
        }
        compose.onNodeWithTag("tutorial-entry-images").performScrollTo().performClick()
        compose.onNodeWithTag("tutorial-viewport").assertIsDisplayed()
        assertEquals(TutorialTopic.IMAGES, state.topic)
        click("稍后")
        compose.onNodeWithTag("tutorial-entry-images").assertIsDisplayed()
        assertEquals(0, backs)
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundTutorialKeepsActionsInsideTheCircleAndAllTextScrollable() = verifyGeometry()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun roundTutorialSupportsLargeText() = verifyGeometry(large = true)

    @Test @Config(qualifiers = "w192dp-h256dp-mdpi")
    fun smallSquareTutorialSupportsLargeText() = verifyGeometry(large = true)

    @Test @Config(qualifiers = "w393dp-h851dp-mdpi")
    fun phoneTutorialUsesACompactFrameAndKeepsTheWholeFooterVisible() = verifyPhoneGeometry()

    @Test @Config(qualifiers = "w800dp-h360dp-land-mdpi")
    fun landscapePhoneTutorialKeepsItsContentScrollableAndItsActionsVisible() = verifyPhoneGeometry()

    @Test @Config(sdk = [30], qualifiers = "w393dp-h851dp-mdpi")
    fun phoneTutorialKeepsItsFrameAndActionsOutOfSystemBars() = verifyPhoneGeometry(withInsets = true)

    @Test @Config(qualifiers = "w800dp-h240dp-land-mdpi")
    fun shortLandscapePhoneKeepsLongLargeTextScrollableAboveItsButtons() {
        val state = TutorialState(TutorialStore(compose.activity))
        state.replay(TutorialTopic.SERVICE_REGISTER)
        val services = layoutServices(textScale = 1.4f)
        show(services.theme, large = true) { ComposeTutorialOverlay(state) }
        assertControlInsideDisplay(compose.onNodeWithTag("tutorial-actions"), "Tutorial footer")
        assertFits("稍后")
        assertFits("下一步")
        val range = compose.onNodeWithTag("tutorial-content").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("The long tutorial must scroll in a short window", range.maxValue() > 0f)
        assertScrollableBody(TutorialCatalog.steps(TutorialTopic.SERVICE_REGISTER).first().body)
        click("下一步")
        click("知道了")
        assertTrue(TutorialStore(compose.activity).completed(TutorialTopic.SERVICE_REGISTER))
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun crownScrollsTheTutorialWithoutChangingTheStep() {
        val state = TutorialState(TutorialStore(compose.activity))
        state.replay(TutorialTopic.SERVICE_REGISTER)
        show(theme()) { ComposeTutorialOverlay(state) }
        compose.runOnIdle { state.scrollRotary(140) }
        compose.runOnIdle {
            val range = compose.onNodeWithTag("tutorial-content").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue(range.value() > 0f)
        }
        text("下一步").assertIsDisplayed()
        assertEquals(TutorialTopic.SERVICE_REGISTER, state.topic)
    }

    private fun verifyGeometry(large: Boolean = false) {
        val state = TutorialState(TutorialStore(compose.activity))
        state.replay(TutorialTopic.SERVICE_REGISTER)
        val services = layoutServices(uiScale = if (compose.activity.resources.configuration.isScreenRound) 0.82f else 1f,
            textScale = if (large) 1.4f else 1f)
        show(services.theme, large) { ComposeTutorialOverlay(state) }
        assertTouchable(text("稍后"), "稍后")
        assertTouchable(text("下一步"), "下一步")
        assertFits("稍后")
        assertFits("下一步")
        assertFits("使用教程 · 1/2")
        val body = TutorialCatalog.steps(TutorialTopic.SERVICE_REGISTER).first().body
        assertScrollableBody(body)
        text("下一步").assertIsDisplayed()
        click("下一步")
        assertTouchable(text("知道了"), "知道了")
        click("知道了")
        assertTrue(TutorialStore(compose.activity).completed(TutorialTopic.SERVICE_REGISTER))
    }

    private fun verifyPhoneGeometry(withInsets: Boolean = false) {
        val state = TutorialState(TutorialStore(compose.activity))
        state.replay(TutorialTopic.HOME)
        val services = layoutServices()
        show(services.theme) { ComposeTutorialOverlay(state) }
        val originalCenter = compose.onNodeWithTag("tutorial-frame").fetchSemanticsNode().boundsInRoot.center
        if (withInsets) {
            compose.runOnIdle {
                val decor = ShadowDialog.getLatestDialog().window!!.decorView
                val owner = (decor as ViewGroup).descendants.first {
                    it.javaClass.simpleName == "AndroidComposeView"
                }
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 48, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 72))
                    .setVisible(WindowInsetsCompat.Type.systemBars(), true).build()
                ViewCompat.dispatchApplyWindowInsets(owner, insets)
            }
            compose.waitForIdle()
        }
        click("下一步")
        val frameNode = compose.onNodeWithTag("tutorial-frame").fetchSemanticsNode()
        val frame = frameNode.size
        val display = compose.activity.window.decorView
        assertTrue("A short tutorial must not fill a phone with empty space", frame.height < display.height * 0.9f)
        assertTrue("Phone text must have a bounded reading width", frame.width <= 420)
        if (withInsets) {
            val bounds = frameNode.boundsInRoot
            assertEquals("The frame must center within the inset-adjusted viewport",
                originalCenter.y + (48f - 72f) / 2f, bounds.center.y, 0.5f)
            assertTrue("Frame overlaps the status bar: $bounds", bounds.top >= 48f)
            assertTrue("Frame overlaps the navigation bar: $bounds", bounds.bottom <= display.height - 72f)
        }
        assertControlInsideDisplay(compose.onNodeWithTag("tutorial-actions"), "Tutorial footer")
        assertFits("上一步")
        assertFits("下一步")
        val body = TutorialCatalog.steps(TutorialTopic.HOME)[1].body
        assertScrollableBody(body)
        click("下一步")
        click("知道了")
        assertTrue(TutorialStore(compose.activity).completed(TutorialTopic.HOME))
    }

    private fun assertScrollableBody(body: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("tutorial-step-body").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(body, layout.layoutInput.text.text)
        assertFalse(layout.multiParagraph.didExceedMaxLines)
        assertEquals(body.length, layout.getLineEnd(layout.lineCount - 1))
        for (line in 0 until layout.lineCount) {
            assertFalse(layout.isLineEllipsized(line))
            assertTrue(layout.getLineRight(line) <= layout.size.width + 0.5f)
        }
        compose.onNodeWithTag("tutorial-content").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10000f) }
        compose.waitForIdle()
        val node = compose.onNodeWithTag("tutorial-step-body").fetchSemanticsNode()
        val last = layout.getBoundingBox(body.lastIndex)
        val point = node.layoutInfo.coordinates.localToRoot(last.center)
        assertTrue("The last line must remain reachable", node.boundsInRoot.contains(point))
    }

    private fun click(label: String) {
        text(label).performTouchInput { click() }
        compose.waitForIdle()
    }
}
