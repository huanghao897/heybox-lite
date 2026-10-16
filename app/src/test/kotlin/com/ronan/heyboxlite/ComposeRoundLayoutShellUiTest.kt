package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
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
    @Test fun squarePageKeepsFirstAndLastCommandsTouchable() = verifyPage()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192PageKeepsHeaderAndEndCommandsInsideTheDisplay() = verifyPage()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227PageKeepsHeaderAndEndCommandsInsideTheDisplay() = verifyPage()

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
        assertBackAnchor()
        assertPaintedLabelInsideDisplay("\u9605\u8bfb\u4e2d\u5fc3")
        touchReachable("\u9996\u4e2a\u64cd\u4f5c")
        touchReachable("\u6700\u540e\u64cd\u4f5c")
        // Reaching back must use the stationary viewport, not the scrolled content coordinates.
        touchReachable("\u9996\u4e2a\u64cd\u4f5c")
        touchReachable("\u6700\u540e\u64cd\u4f5c")
        assertBackAnchor()
        compose.runOnIdle {
            assertEquals(2, firstClicks)
            assertEquals(2, lastClicks)
            assertEquals(0, backs)
        }
    }
}
