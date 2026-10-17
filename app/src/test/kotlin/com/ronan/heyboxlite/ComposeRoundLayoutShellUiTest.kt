package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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
internal class ComposeRoundLayoutShellUiTest : ComposeRoundLayoutHarness() {
    @Test fun squareSecondaryContentStartsAtTheTopWithoutTitleOrReturn() = verifyNoToolbarSpace()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundSecondaryViewportStartsAtTheTopWithoutTitleOrReturn() = verifyNoToolbarSpace()

    private fun verifyNoToolbarSpace() {
        val services = layoutServices()
        showLayout(services) {
            WatchPage("不得显示的页头", {}) {
                Box(Modifier.fillMaxWidth().height(32.dp).testTag("first-menu-content")) {
                    Text("正文")
                }
                repeat(8) { Text("Scrollable row $it", Modifier.height(32.dp)) }
                WatchActionButton("最后操作", {})
            }
        }
        assertNoTopToolbar()
        text("不得显示的页头").assertDoesNotExist()
        assertEquals("No title or return strip may reduce the scrolling viewport", 0f,
            compose.onNodeWithTag("watch-page-content").fetchSemanticsNode().boundsInRoot.top, 0.5f)
        if (!services.theme.roundScreen) assertEquals("Square content starts at the top", 0f,
            compose.onNodeWithTag("first-menu-content").fetchSemanticsNode().boundsInRoot.top, 0.5f)
        touchReachable("最后操作")
        assertTrue("Leading round safety space must scroll away with the content",
            compose.onNodeWithTag("first-menu-content").fetchSemanticsNode().positionInRoot.y < 0f)
        assertNoTopToolbar()
    }

    @Test fun squarePageKeepsFirstAndLastCommandsTouchable() = verifyPage()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192PageKeepsFirstAndEndCommandsInsideTheDisplay() = verifyPage()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227PageKeepsFirstAndEndCommandsInsideTheDisplay() = verifyPage()

    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun userForcedRoundOnSquareUsesCircularHitTesting() = verifyPage(forceRound = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun largeTextRoundPageKeepsRequiredActionsReadable() = verifyPage(textScale = 1.4f)

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun reducedUiScaleDoesNotShrinkTextWithTheChrome() = verifyPage(uiScale = 0.5f)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun minimumConfiguredScaleUsesSessionEffectiveRoundSizes() {
        val session = SessionStore(compose.activity)
        session.setUiScale(50)
        session.setTextScale(50)
        verifyPage(uiScale = session.uiScale() / 100f, textScale = session.textScale() / 100f)
    }

    private fun verifyPage(forceRound: Boolean = false, uiScale: Float = 1f, textScale: Float = 1f) {
        val services = layoutServices(forceRound, uiScale, textScale)
        if (forceRound) {
            assertFalse(compose.activity.resources.configuration.isScreenRound)
            assertTrue(services.session.roundScreen())
            assertTrue(services.theme.roundScreen)
        }
        var firstClicks = 0
        var lastClicks = 0
        var backs = 0
        showLayout(services) {
            WatchPage("\u9605\u8bfb\u4e2d\u5fc3", { backs++ }) {
                WatchActionButton("\u9996\u4e2a\u64cd\u4f5c", { firstClicks++ })
                repeat(8) { Text("Scrollable row $it", Modifier.height(32.dp)) }
                WatchActionButton("\u6700\u540e\u64cd\u4f5c", { lastClicks++ })
            }
        }
        assertNoTopToolbar()
        text("\u9605\u8bfb\u4e2d\u5fc3").assertDoesNotExist()
        touchReachable("\u9996\u4e2a\u64cd\u4f5c")
        touchReachable("\u6700\u540e\u64cd\u4f5c")
        touchReachable("\u9996\u4e2a\u64cd\u4f5c")
        touchReachable("\u6700\u540e\u64cd\u4f5c")
        assertNoTopToolbar()
        compose.runOnIdle {
            assertEquals(2, firstClicks)
            assertEquals(2, lastClicks)
            assertEquals(0, backs)
        }
    }
}
