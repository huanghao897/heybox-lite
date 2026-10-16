package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import kotlin.math.hypot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeWatchDialogRoundUiTest : ComposeWatchActionButtonTestHarness() {
    private var client: ApiClient? = null

    @After fun closeClient() { client?.close() }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundContentScrollsButConfirmationNeverMoves() = verifyFixedActions()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun roundLargeFontsKeepContentAndActionsInSeparateRegions() = verifyFixedActions(large = true)

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowSquareLargeFontsKeepFixedConfirmation() = verifyFixedActions(large = true)

    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun configuredRoundLayoutUsesCircleSafeBoundsOnSquareHardware() =
        verifyFixedActions(forceRound = true)

    private fun verifyFixedActions(large: Boolean = false, forceRound: Boolean = false) {
        val palette = theme(large = large).let { if (forceRound) it.copy(roundScreen = true) else it }
        var confirmations = 0
        var dismissals = 0
        show(palette, large) {
            WatchDialog("\u5185\u5bb9\u6e05\u7406", { dismissals++ }, actions = {
                WatchActionButton("\u53d6\u6d88", { dismissals++ }, Modifier.weight(1f))
                WatchActionButton("\u786e\u8ba4", { confirmations++ }, Modifier.weight(1f))
            }) {
                repeat(40) { Text("\u5185\u5bb9 $it", color = palette.text, fontSize = watchSp(12f)) }
            }
        }
        assertFrame(palette)
        assertFixedAction("\u53d6\u6d88")
        assertFixedAction("\u786e\u8ba4")
        val before = bounds(text("\u786e\u8ba4"))
        text("\u5185\u5bb9 39").performScrollTo().assertIsDisplayed()
        assertFits("\u5185\u5bb9 39")
        assertEquals("Scrolling content must not displace actions", before, bounds(text("\u786e\u8ba4")))
        text("\u786e\u8ba4").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, confirmations)
            assertEquals(0, dismissals)
        }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundLongConfirmationDelegatesConfirmThenDismiss() {
        val palette = theme(large = true)
        val calls = mutableListOf<String>()
        show(palette, large = true) {
            ComposeConfirmDialog("\u6062\u590d\u9ed8\u8ba4\u663e\u793a\u8bbe\u7f6e", "Long content. ".repeat(200),
                { calls += "dismiss" }, { calls += "confirm" })
        }
        assertFrame(palette)
        assertFixedAction("\u53d6\u6d88")
        assertFixedAction("\u786e\u8ba4")
        text("\u786e\u8ba4").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("confirm", "dismiss"), calls) }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundBlankAnnouncementHasNoTitleAndWrapsActionLabels() = verifyAnnouncement()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227LongAnnouncementKeepsBothActionsVisible() = verifyAnnouncement()

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowSquareAnnouncementKeepsLargeLabelsTouchable() = verifyAnnouncement()

    private fun verifyAnnouncement() {
        val palette = theme(dark = false, large = true)
        var acknowledgements = 0
        var suppressions = 0
        var dismissals = 0
        val item = AnnouncementChecker.Item("fixture", "   ", "Announcement line. ".repeat(150),
            "normal", "", false, true)
        show(palette, large = true) {
            ComposeAnnouncementDialog(item, { dismissals++ }, { acknowledgements++ }, { suppressions++ })
        }
        text("   ", unmerged = true).assertDoesNotExist()
        assertFrame(palette)
        assertFixedAction("\u4e0d\u518d\u63d0\u9192")
        assertFixedAction("\u77e5\u9053\u4e86")
        assertFits("\u4e0d\u518d\u63d0\u9192", wrapped = true)
        val before = bounds(text("\u77e5\u9053\u4e86"))
        compose.onNodeWithTag("watch-dialog-content")
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100f) }
        assertEquals(before, bounds(text("\u77e5\u9053\u4e86")))
        text("\u4e0d\u518d\u63d0\u9192").performTouchInput { click() }
        text("\u77e5\u9053\u4e86").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, suppressions)
            assertEquals(1, acknowledgements)
            assertEquals(0, dismissals)
        }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundTextDraftCanCancelAndThenSaveWithoutClippedActions() = verifyTextSetting()

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowSquareTextDraftPreservesApplyCancelBehavior() = verifyTextSetting()

    private fun verifyTextSetting() {
        val palette = theme(large = true)
        val saved = mutableListOf<String>()
        show(palette, large = true) {
            ComposeSettingText("\u5173\u952e\u8bcd", "kept", "Filter", onValueChange = saved::add)
        }
        text("\u5173\u952e\u8bcd").performTouchInput { click() }
        val input = compose.onNodeWithContentDescription("\u5173\u952e\u8bcd")
        input.performScrollTo().performTextReplacement("discarded")
        assertFrame(palette)
        assertFixedAction("\u53d6\u6d88")
        text("\u53d6\u6d88").performTouchInput { click() }
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
        text("\u5173\u952e\u8bcd").performTouchInput { click() }
        input.performScrollTo().performTextReplacement("  saved  ")
        assertFixedAction("\u786e\u5b9a")
        text("\u786e\u5b9a").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("saved"), saved) }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundNumericErrorDoesNotHideConfirmationOrCommitInvalidInput() = verifyNumericSetting()

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowSquareNumericInputKeepsUnboundedInputAndConfirmation() = verifyNumericSetting()

    private fun verifyNumericSetting() {
        val palette = theme(large = true)
        val saved = mutableListOf<Int>()
        show(palette, large = true) {
            ComposeSettingRange("\u6570\u503c", 100, 1, 1000, 1, "%",
                allowAboveSliderMaximum = true, onValueChange = saved::add)
        }
        text("\u6570\u503c").performTouchInput { click() }
        val input = compose.onNodeWithContentDescription("\u6570\u503c")
        input.performScrollTo().performTextReplacement("")
        assertFixedAction("\u786e\u5b9a")
        text("\u786e\u5b9a").performTouchInput { click() }
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
        input.performScrollTo().performTextReplacement("2500")
        assertFrame(palette)
        assertFixedAction("\u786e\u5b9a")
        text("\u786e\u5b9a").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(2500), saved) }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundUpdateKeepsDownloadVisibleBeforeScrollingNotes() {
        val palette = theme(large = true)
        var downloads = 0
        var dismissals = 0
        val result = UpdateChecker.Result(true, "9.0", "Fixture", "Release line\n".repeat(150),
            "https://heyboxlite.xyz/", "", 999, "test")
        show(palette, large = true) {
            ComposeAboutDialogs(false, result, {}, { dismissals++ }, { downloads++ })
        }
        assertFrame(palette)
        assertFixedAction("\u7a0d\u540e")
        assertFixedAction("\u4e0b\u8f7d")
        text("\u4e0b\u8f7d").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, downloads)
            assertEquals(0, dismissals)
        }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundThemeGridKeepsLastSwatchAndCaptionReachable() {
        val palette = theme(large = true, uiScale = 1f)
        val session = SessionStore(compose.activity)
        val api = ApiClient(session).also { client = it }
        val services = ComposeServices(compose.activity, session, api, GameDetailClient(api),
            LocalCache(compose.activity), Handler(Looper.getMainLooper()),
            ReadingTimeTracker(compose.activity), null, palette, ComposeToast {})
        var changes = 0
        var dismissals = 0
        show(palette, large = true) { ComposeThemeDialog(services, { dismissals++ }, { changes++ }) }
        val last = ThemePalette.count() - 1
        val label = ThemePalette.name(last)
        val swatch = compose.onNodeWithContentDescription(label).performScrollTo().assertIsDisplayed()
        assertFits(label)
        assertFrame(palette)
        swatch.performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(ThemePalette.primary(last), ThemePalette.currentPrimary(session))
            assertEquals(1, changes)
            assertEquals(1, dismissals)
        }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun backDismissesWithoutConfirming() {
        val palette = theme()
        val visible = mutableStateOf(true)
        var confirmations = 0
        var dismissals = 0
        show(palette) {
            if (visible.value) ComposeConfirmDialog("Confirm", "Long content. ".repeat(100),
                { dismissals++; visible.value = false }, { confirmations++ })
        }
        val dialog = ShadowDialog.getLatestDialog()
        compose.runOnIdle {
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        compose.waitForIdle()
        assertFalse(dialog.isShowing)
        assertEquals(0, confirmations)
        assertEquals(1, dismissals)
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun tappingTheFrameDoesNotDismissButTappingTheSurroundingMaskDoes() {
        val visible = mutableStateOf(true)
        var dismissals = 0
        show(theme()) {
            if (visible.value) WatchDialog(null, {
                dismissals++
                visible.value = false
            }) {
                Text("Inside", Modifier.testTag("inside-dialog-text"))
            }
        }
        compose.onNodeWithTag("inside-dialog-text").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, dismissals) }
        compose.onNodeWithTag("watch-dialog-frame").assertIsDisplayed()
        val dialog = ShadowDialog.getLatestDialog()
        compose.onNodeWithTag("watch-dialog-viewport").performTouchInput {
            click(Offset(width / 2f, 4f))
        }
        compose.waitForIdle()
        assertEquals(1, dismissals)
        assertFalse(dialog.isShowing)
    }

    private fun assertFixedAction(label: String) {
        val node = text(label).assertIsDisplayed().fetchSemanticsNode()
        assertFalse("Action must not belong to the scrolling content", generateSequence(node.parent) { it.parent }
            .any { it.config.contains(SemanticsActions.ScrollBy) })
        assertFits(label)
    }

    private fun assertFrame(palette: ComposeThemeState) {
        val rect = bounds(compose.onNodeWithTag("watch-dialog-frame").assertIsDisplayed())
        val view = ShadowDialog.getLatestDialog().window!!.decorView
        assertTrue("Frame must have positive measured bounds", rect.width > 0f && rect.height > 0f)
        assertTrue("Frame must fit its actual dialog window", rect.left >= 0f && rect.top >= 0f &&
            rect.right <= view.width && rect.bottom <= view.height)
        if (palette.roundScreen) {
            val radius = minOf(view.width, view.height) / 2f - 2f
            listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight).forEach { point ->
                assertTrue("Frame corner must be inside the configured circle: $point / $rect",
                    hypot(point.x - view.width / 2f, point.y - view.height / 2f) < radius)
            }
        }
    }
}
