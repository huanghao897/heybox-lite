package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeWatchActionButtonUiTest : ComposeWatchActionButtonTestHarness() {
    @Test fun genuinelyHeightClippedGlyphsAreRejected() = verifyClippingIsRejected(heightClipped = true)
    @Test fun genuinelyWidthClippedGlyphsAreRejected() = verifyClippingIsRejected(heightClipped = false)

    private fun verifyClippingIsRejected(heightClipped: Boolean) {
        val label = "\u786e\u8ba4"
        show(theme()) {
            Text(label, Modifier.width(if (heightClipped) 100.dp else 12.dp)
                .height(if (heightClipped) 6.dp else 40.dp), fontSize = 11.sp, maxLines = 1)
        }
        val failure = runCatching { assertFits(label) }.exceptionOrNull()
        assertTrue("Controlled glyph clipping must still fail the harness", failure is AssertionError)
    }

    @Test fun darkSquareSurfaceAndDisabledSemantics() = verifyButton(dark = true)
    @Test fun lightSquareSurfaceAndDisabledSemantics() = verifyButton(dark = false)

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun darkRoundSurfaceAndDisabledSemantics() = verifyButton(dark = true)

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun lightRoundSurfaceAndDisabledSemantics() = verifyButton(dark = false)

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun darkNarrowBigTextWrapsWithoutClipping() = verifyButton(dark = true, large = true)

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun lightNarrowBigTextWrapsWithoutClipping() = verifyButton(dark = false, large = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun darkSmallRoundBigTextWrapsInsideCircle() = verifyButton(dark = true, large = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun lightSmallRoundBigTextWrapsInsideCircle() = verifyButton(dark = false, large = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun darkReducedScaleSmallRoundBigTextWrapsInsideCircle() =
        verifyButton(dark = true, large = true, uiScale = 0.5f)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun lightReducedScaleSmallRoundBigTextWrapsInsideCircle() =
        verifyButton(dark = false, large = true, uiScale = 0.5f)

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun darkNarrowBigTextPrimaryLabelWrapsWithoutClipping() =
        verifyButton(dark = true, large = true, primary = true)

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun lightNarrowBigTextPrimaryLabelWrapsWithoutClipping() =
        verifyButton(dark = false, large = true, primary = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun darkReducedScaleSmallRoundPrimaryLabelWrapsInsideCircle() =
        verifyButton(dark = true, large = true, uiScale = 0.5f, primary = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun lightReducedScaleSmallRoundPrimaryLabelWrapsInsideCircle() =
        verifyButton(dark = false, large = true, uiScale = 0.5f, primary = true)

    private fun verifyButton(dark: Boolean, large: Boolean = false,
                             uiScale: Float? = null, primary: Boolean = false) {
        val palette = theme(dark, large, uiScale)
        val enabled = mutableStateOf(true)
        var clicks = 0
        val label = "\u53d1\u9001\u90ae\u7bb1\u9a8c\u8bc1\u7801"
        show(palette, large) {
            if (primary) Box(Modifier.width(76.dp)) {
                ComposeCheckinPrimaryButton(label, enabled.value) { clicks++ }
            } else WatchActionButton(label, { clicks++ },
                Modifier.width(if (large) 76.dp else 116.dp).testTag("command"),
                enabled = enabled.value)
        }
        val button = if (primary) text(label) else compose.onNodeWithTag("command")
        assertFits(label, wrapped = large)
        assertSurface(button, palette, true, "enabled", primary = primary)
        button.assertIsEnabled().performClick().performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(2, clicks)
            enabled.value = false
        }
        button.assertIsNotEnabled().performClick().performTouchInput { click() }
        assertFits(label, wrapped = large)
        assertSurface(button, palette, false, "disabled", primary = primary)
        compose.runOnIdle {
            assertEquals("Neither touch attempt may bypass disabled", 2, clicks)
            enabled.value = true
        }
        button.assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(3, clicks) }
    }

    @Test fun existingDarkSquareCommandsPaintSurfacesAndDelegateOnce() = verifyCommands(true)
    @Test fun existingLightSquareCommandsPaintSurfacesAndDelegateOnce() = verifyCommands(false)

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun existingDarkRoundCommandsPaintSurfacesAndDelegateOnce() = verifyCommands(true)

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun existingLightRoundCommandsPaintSurfacesAndDelegateOnce() = verifyCommands(false)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun darkReducedScaleCommandsKeep28dpTargetsInsideSmallRound() = verifyCommands(true, 0.5f)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun lightReducedScaleCommandsKeep28dpTargetsInsideSmallRound() = verifyCommands(false, 0.5f)

    private fun verifyCommands(dark: Boolean, uiScale: Float? = null) {
        val palette = theme(dark, uiScale = uiScale)
        val selected = mutableStateOf(0)
        val enabled = mutableStateOf(true)
        val clicks = mutableListOf<Int>()
        val download = "\u4e0b\u8f7d"
        val cancel = "\u53d6\u6d88\u8fde\u63a5"
        val retry = "\u91cd\u8bd5"
        val loadMore = "\u4e0a\u6ed1\u52a0\u8f7d\u66f4\u591a"
        val reload = "\u91cd\u65b0\u83b7\u53d6"
        val connect = "\u9a8c\u8bc1\u5e76\u8fde\u63a5"
        show(palette) {
            Box(Modifier.width(132.dp)) {
                val click = { clicks.add(selected.value); Unit }
                when (selected.value) {
                    0 -> WatchActionText(download, click)
                    1 -> ComposeCheckinQuietButton(cancel, enabled.value, click)
                    2 -> MembershipRetry(click, enabled.value)
                    3 -> SavedLoadError("Offline", false, click)
                    4 -> FeedFooter(!enabled.value, false, palette, click)
                    5 -> FeedEmpty("\u6682\u65e0\u5185\u5bb9", reload, palette, click)
                    6 -> ComposeCheckinPrimaryButton(connect, enabled.value, click)
                }
            }
        }
        listOf(download, cancel, retry, retry, loadMore, reload, connect).forEachIndexed { index, label ->
            compose.runOnIdle { selected.value = index; enabled.value = true }
            assertFits(label)
            val button = text(label)
            assertSurface(button, palette, true, "command-$index-enabled", primary = index == 6)
            if (uiScale == 0.5f) {
                assertTrue("Reduced UI scale must not retain a 36dp/48dp button floor",
                    bounds(button).height < 35.5f)
                if (index == 0 || index == 6) {
                    assertEquals("Compact and primary commands retain the modest minimum",
                        28f, bounds(button).height, 0.5f)
                }
            }
            if (index == 0) assertTrue("Compact command must not be forced full-width", bounds(button).width < 132)
            button.assertIsEnabled().performTouchInput { click() }
            compose.runOnIdle { assertEquals((0..index).toList(), clicks) }
            if (index in listOf(1, 2, 4, 6)) {
                compose.runOnIdle { enabled.value = false }
                val disabled = text(if (index == 4) "\u6b63\u5728\u52a0\u8f7d\u66f4\u591a\u2026" else label)
                disabled.assertIsNotEnabled().performClick().performTouchInput { click() }
                assertSurface(disabled, palette, false, "command-$index-disabled", primary = index == 6)
                compose.runOnIdle { assertEquals((0..index).toList(), clicks) }
            }
        }
    }

    @Test fun exhaustedPaginationIsVisiblyDisabledAndNeverLoads() {
        val palette = theme(false)
        var loads = 0
        show(palette) { Box(Modifier.width(132.dp)) { FeedFooter(false, true, palette) { loads++ } } }
        val label = "\u6ca1\u6709\u66f4\u591a\u5185\u5bb9"
        val button = text(label).assertIsNotEnabled()
        assertSurface(button, palette, false, "exhausted")
        button.performClick().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, loads) }
    }
}
