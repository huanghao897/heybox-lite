package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeRoundLayoutModalUiTest : ComposeRoundLayoutHarness() {
    @Test fun squareConfirmationActionsRemainReachableAfterLongContent() = verifyConfirmation()
    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192ConfirmationActionsRemainReachableAfterLongContent() = verifyConfirmation()
    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227ConfirmationActionsRemainReachableAfterLongContent() = verifyConfirmation()
    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundConfirmationUsesCircularRatherThanRectangularBounds() =
        verifyConfirmation(forceRound = true)

    private fun verifyConfirmation(forceRound: Boolean = false) {
        val services = layoutServices(forceRound)
        var confirmations = 0
        var dismissals = 0
        showLayout(services) {
            ComposeConfirmDialog("\u6e05\u7406\u5185\u5bb9", "Scrollable confirmation content. ".repeat(20),
                { dismissals++ }, { confirmations++ })
        }
        val cancel = circleReach("\u53d6\u6d88")
        assertTouchable(cancel, "cancel")
        assertPaintedLabelInsideDisplay("\u53d6\u6d88")
        touchReachable("\u786e\u8ba4")
        compose.runOnIdle {
            assertEquals(1, confirmations)
            assertEquals(1, dismissals)
        }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192TextSettingKeepsBothActionsTouchable() {
        val services = layoutServices()
        val saved = mutableListOf<String>()
        showLayout(services) {
            WatchPage("\u8bbe\u7f6e", {}) {
                ComposeSettingText("\u7b5b\u9009\u5173\u952e\u8bcd", "kept", "Filter", onValueChange = saved::add)
            }
        }
        touchReachable("\u7b5b\u9009\u5173\u952e\u8bcd")
        assertTouchable(circleReach("\u53d6\u6d88"), "cancel text setting")
        touchReachable("\u786e\u5b9a")
        compose.runOnIdle { assertEquals(listOf("kept"), saved) }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundNumericSettingKeepsConfirmationTouchable() {
        val services = layoutServices(forceRound = true)
        val saved = mutableListOf<Int>()
        showLayout(services) {
            WatchPage("\u8bbe\u7f6e", {}) {
                ComposeSettingRange("\u8868\u51a0\u901f\u5ea6", 100, 1, 1000, 1, "%",
                    allowAboveSliderMaximum = true, onValueChange = saved::add)
            }
        }
        touchReachable("\u8868\u51a0\u901f\u5ea6")
        touchReachable("\u786e\u5b9a")
        compose.runOnIdle { assertEquals(listOf(100), saved) }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192ChoiceDialogLetsItsLastOptionReachTheCenter() {
        val services = layoutServices()
        val selected = mutableListOf<Int>()
        val options = listOf("First", "Second", "Third", "Last")
        showLayout(services) {
            WatchPage("\u8bbe\u7f6e", {}) {
                ComposeSettingChoice("\u52a8\u753b\u6548\u679c", options, 0, onSelected = selected::add)
            }
        }
        touchReachable("\u52a8\u753b\u6548\u679c")
        touchReachable("Last")
        compose.runOnIdle { assertEquals(listOf(3), selected) }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192ThemeDialogKeepsTheLastSwatchAndLabelReachable() {
        val services = layoutServices()
        var changes = 0
        var dismissals = 0
        showLayout(services) { ComposeThemeDialog(services, { dismissals++ }, { changes++ }) }
        val name = ThemePalette.name(ThemePalette.count() - 1)
        circleReach(text(name, unmerged = true))
        assertPaintedLabelInsideDisplay(name)
        val last = compose.onNodeWithContentDescription(name)
        circleReach(last)
        assertControlInsideDisplay(last, "last theme swatch")
        last.performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(ThemePalette.primary(ThemePalette.count() - 1),
                ThemePalette.currentPrimary(services.session))
            assertEquals(1, changes)
            assertEquals(1, dismissals)
        }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192UpdateDialogKeepsDownloadReachableWithFullReleaseNotes() {
        val services = layoutServices()
        var downloads = 0
        val result = UpdateChecker.Result(true, "9.0", "", "Release line\n".repeat(90),
            "https://heyboxlite.xyz/", "", 999, "test")
        showLayout(services) {
            ComposeAboutDialogs(false, result, {}, {}, { downloads++ })
        }
        touchReachable("\u4e0b\u8f7d")
        compose.runOnIdle { assertEquals(1, downloads) }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundFeedbackDialogKeepsItsFixedConfirmationInsideTheCircle() {
        val services = layoutServices(forceRound = true)
        var confirmations = 0
        showLayout(services) { ComposeFeedbackGroupDialog { confirmations++ } }
        val confirm = text("\u786e\u5b9a")
        assertControlInsideDisplay(confirm, "fixed group dialog confirmation")
        assertPaintedLabelInsideDisplay("\u786e\u5b9a")
        confirm.performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, confirmations) }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundLongAnnouncementKeepsBothFixedActionsTouchable() {
        val services = layoutServices(forceRound = true)
        var acknowledgements = 0
        var suppressions = 0
        val item = AnnouncementChecker.Item("round-fixture", "", "Announcement line. ".repeat(100),
            "normal", "", true, true)
        showLayout(services) {
            ComposeAnnouncementDialog(item, {}, { acknowledgements++ }, { suppressions++ })
        }
        listOf("\u4e0d\u518d\u63d0\u9192", "\u77e5\u9053\u4e86").forEach { label ->
            // Long announcement content may scroll, but these actions must remain anchored.
            assertControlInsideDisplay(text(label), "fixed announcement action $label")
            assertPaintedLabelInsideDisplay(label)
            text(label).performTouchInput { click() }
        }
        compose.runOnIdle {
            assertEquals(1, acknowledgements)
            assertEquals(1, suppressions)
        }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundTaskTimeDialogKeepsInputAndConfirmationReachable() {
        val services = layoutServices(forceRound = true)
        val times = mutableListOf<String>()
        showLayout(services) { CheckinTaskTimeDialog("09:15", true, {}, times::add) }
        val input = circleReach(compose.onNodeWithContentDescription("\u8f93\u5165\u6267\u884c\u65f6\u95f4"))
        assertControlInsideDisplay(input, "task time input")
        touchReachable("\u786e\u8ba4")
        compose.runOnIdle { assertEquals(listOf("09:15"), times) }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundTaskOffsetDialogKeepsInputAndConfirmationReachable() {
        val services = layoutServices(forceRound = true)
        val offsets = mutableListOf<Int>()
        showLayout(services) { CheckinTaskOffsetDialog(45, true, {}, offsets::add) }
        val input = circleReach(compose.onNodeWithContentDescription("\u8f93\u5165\u968f\u673a\u504f\u79fb\u5206\u949f"))
        assertControlInsideDisplay(input, "task offset input")
        touchReachable("\u786e\u8ba4")
        compose.runOnIdle { assertEquals(listOf(45), offsets) }
    }
}
