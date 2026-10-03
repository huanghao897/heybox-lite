package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
class ComposeCheckinTaskSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var state: MutableState<ComposeCheckinUiState>
    private val enabledChanges = mutableListOf<Boolean>()
    private val timeChanges = mutableListOf<String>()
    private val offsetChanges = mutableListOf<Int>()
    private val shareChanges = mutableListOf<Pair<String, Boolean>>()
    private var backs = 0

    @Test fun switchesDelegateWithoutOptimisticallyChangingTheServerSnapshot() {
        show()
        text("\u81ea\u52a8\u7b7e\u5230").assertIsOn().performClick().assertIsOn()
        text("\u5206\u4eab\u5e16\u5b50").performScrollTo().assertIsOff().performClick()
        text("\u5206\u4eab\u6e38\u620f").performScrollTo().assertIsOff().performClick()
        text("\u5206\u4eab\u8bc4\u4ef7").performScrollTo().assertIsOff().performClick()
        description("\u8fd4\u56de").performClick()
        compose.runOnIdle {
            assertEquals(listOf(false), enabledChanges)
            assertEquals(listOf("share_post" to true, "share_game" to true,
                "share_review" to true), shareChanges)
            assertEquals(1, backs)
            assertTrue(offsetChanges.isEmpty() && timeChanges.isEmpty())
            assertEquals(null, state.value.status?.lastRun)
        }
    }

    @Test fun steppersKeepThirtyMinuteStepsAndDisableAtExistingBounds() {
        show(task(offset = 0))
        description("\u51cf\u5c11\u968f\u673a\u504f\u79fb").assertIsNotEnabled()
        description("\u589e\u52a0\u968f\u673a\u504f\u79fb").performScrollTo().performClick()
        updateTask(task(offset = 720))
        description("\u589e\u52a0\u968f\u673a\u504f\u79fb").assertIsNotEnabled()
        description("\u51cf\u5c11\u968f\u673a\u504f\u79fb").performClick()
        updateTask(task(offset = 706))
        description("\u589e\u52a0\u968f\u673a\u504f\u79fb").performClick()
        updateTask(task(offset = 12))
        description("\u51cf\u5c11\u968f\u673a\u504f\u79fb").performClick()
        compose.runOnIdle { assertEquals(listOf(30, 690, 720, 0), offsetChanges) }
    }

    @Test fun offsetEditingRejectsInvalidDraftsAndSavesOnlyAfterConfirmation() {
        show()
        description("\u8f93\u5165\u968f\u673a\u504f\u79fb").performScrollTo().performClick()
        val input = description("\u8f93\u5165\u968f\u673a\u504f\u79fb\u5206\u949f")
        listOf("721", "-1", "abc", "").forEach { draft ->
            input.performTextReplacement(draft)
            text("\u786e\u8ba4").assertIsNotEnabled()
            compose.runOnIdle { assertTrue(offsetChanges.isEmpty()) }
        }
        input.performTextReplacement("720")
        compose.runOnIdle { assertTrue(offsetChanges.isEmpty()) }
        text("\u786e\u8ba4").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(720), offsetChanges) }
        description("\u8f93\u5165\u968f\u673a\u504f\u79fb").assertTextContains("30")
    }

    @Test fun offsetCancelAndUnchangedConfirmationDoNotWrite() {
        show()
        description("\u8f93\u5165\u968f\u673a\u504f\u79fb").performScrollTo().performClick()
        description("\u8f93\u5165\u968f\u673a\u504f\u79fb\u5206\u949f")
            .performTextReplacement("90")
        text("\u53d6\u6d88").performScrollTo().performClick()
        description("\u8f93\u5165\u968f\u673a\u504f\u79fb").performClick()
        description("\u8f93\u5165\u968f\u673a\u504f\u79fb\u5206\u949f").assertTextContains("30")
        text("\u786e\u8ba4").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(offsetChanges.isEmpty()) }
    }

    @Test fun timeWindowIsTheServerWindowAndTimeNeedsConfirmation() {
        show(task(windowStart = "23:40", windowEnd = "00:10"))
        text("23:40 - 00:10").assertExists()
        text("\u6267\u884c\u65f6\u95f4").performScrollTo().performClick()
        val input = description("\u8f93\u5165\u6267\u884c\u65f6\u95f4")
        input.assertTextContains("08:30").performTextReplacement("24:00")
        text("\u786e\u8ba4").assertIsNotEnabled()
        input.performTextReplacement("09:45")
        compose.runOnIdle { assertTrue(timeChanges.isEmpty()) }
        text("\u786e\u8ba4").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("09:45"), timeChanges) }
        text("08:30").assertExists()
    }

    @Test fun cancelTimeAndAnAbsentWindowNeverFabricateAWindow() {
        show(task(windowStart = "", windowEnd = "", scheduleTime = ""))
        text("\u65f6\u95f4\u7a97\u6682\u4e0d\u53ef\u7528").assertExists()
        text("\u6267\u884c\u65f6\u95f4").performScrollTo().performClick()
        description("\u8f93\u5165\u6267\u884c\u65f6\u95f4").assertTextContains("08:30")
            .performTextReplacement("11:00")
        text("\u53d6\u6d88").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(timeChanges.isEmpty())
            assertEquals("23:40", taskSettingsWindowLabel(task(windowStart = "23:40", windowEnd = "")))
            assertEquals("00:10", taskSettingsWindowLabel(task(windowStart = "", windowEnd = "00:10")))
        }
    }

    @Test fun confirmingTheDefaultTimeSavesWhenTheServerHasNoScheduleYet() {
        show(task(scheduleTime = "", windowStart = "", windowEnd = ""))
        text("\u6267\u884c\u65f6\u95f4").performScrollTo().performClick()
        text("\u786e\u8ba4").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("08:30"), timeChanges) }
        text("\u65f6\u95f4\u7a97\u6682\u4e0d\u53ef\u7528").assertExists()
    }

    @Test fun savingDisablesEveryWriteAndAnAlreadyOpenDialog() {
        show()
        description("\u8f93\u5165\u968f\u673a\u504f\u79fb").performScrollTo().performClick()
        compose.runOnIdle { state.value = state.value.copy(taskSaving = true) }
        description("\u8f93\u5165\u968f\u673a\u504f\u79fb\u5206\u949f").assertIsNotEnabled()
        text("\u786e\u8ba4").assertIsNotEnabled()
        text("\u53d6\u6d88").performScrollTo().performClick()
        listOf("\u81ea\u52a8\u7b7e\u5230", "\u6267\u884c\u65f6\u95f4",
            "\u5206\u4eab\u5e16\u5b50", "\u5206\u4eab\u6e38\u620f", "\u5206\u4eab\u8bc4\u4ef7")
            .forEach { text(it).assertIsNotEnabled() }
        listOf("\u8f93\u5165\u968f\u673a\u504f\u79fb", "\u51cf\u5c11\u968f\u673a\u504f\u79fb",
            "\u589e\u52a0\u968f\u673a\u504f\u79fb").forEach { description(it).assertIsNotEnabled() }
        text("\u6b63\u5728\u4fdd\u5b58\u8bbe\u7f6e").assertExists()
        compose.runOnIdle {
            assertTrue(enabledChanges.isEmpty() && timeChanges.isEmpty() &&
                offsetChanges.isEmpty() && shareChanges.isEmpty())
            state.value = state.value.copy(taskSaving = false)
        }
        text("\u81ea\u52a8\u7b7e\u5230").assertIsEnabled()
    }

    @Test fun platformAndSignBlocksUnavailableSharingAndErrorsRemainVisible() {
        show(task(platformBlocked = true, sharingAvailable = false))
        compose.runOnIdle { state.value = state.value.copy(errorMessage = "Request failed") }
        text("Request failed").assertExists()
        text("\u670d\u52a1\u5df2\u6682\u505c\u6b64\u4efb\u52a1\uff0c\u5f53\u524d\u8bbe\u7f6e\u4f1a\u4fdd\u7559\u3002")
            .assertExists()
        text("\u6682\u672a\u5f00\u653e").performScrollTo().assertIsDisplayed()
        text("\u5206\u4eab\u5e16\u5b50").assertDoesNotExist()
        updateTask(task(signBlocked = true, sharingAvailable = false))
        text("\u670d\u52a1\u5df2\u6682\u505c\u6b64\u4efb\u52a1\uff0c\u5f53\u524d\u8bbe\u7f6e\u4f1a\u4fdd\u7559\u3002")
            .assertExists()
        compose.runOnIdle { assertTrue(shareChanges.isEmpty()) }
    }

    @Test fun missingTaskHasNoWriteControls() {
        show()
        compose.runOnIdle { state.value = state.value.copy(status = null, errorMessage = "Unavailable") }
        text("Unavailable").assertExists()
        text("\u7b7e\u5230\u8bbe\u7f6e\u6682\u4e0d\u53ef\u7528").assertExists()
        text("\u81ea\u52a8\u7b7e\u5230").assertDoesNotExist()
    }

    @Test fun offsetDraftAndOpenDialogSurviveStateRestorationWithoutWriting() {
        val restoration = StateRestorationTester(compose)
        show(restoration = restoration)
        description("\u8f93\u5165\u968f\u673a\u504f\u79fb").performScrollTo().performClick()
        description("\u8f93\u5165\u968f\u673a\u504f\u79fb\u5206\u949f").performTextReplacement("123")
        restoration.emulateSavedInstanceStateRestore()
        description("\u8f93\u5165\u968f\u673a\u504f\u79fb\u5206\u949f").assertTextContains("123")
        compose.runOnIdle { assertTrue(offsetChanges.isEmpty()) }
        text("\u786e\u8ba4").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(123), offsetChanges) }
    }

    @Test
    @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun roundLastSwitchAndBackCanBeReachedInsideTheCircle() {
        val theme = composePreviewTheme(true)
        show(theme = theme)
        assertCompactSwitch(theme.uiScale)
        val last = text("\u5206\u4eab\u8bc4\u4ef7").performScrollTo().assertIsDisplayed()
        assertInsideCircle(last.fetchSemanticsNode().boundsInRoot)
        assertInsideCircle(description("\u8fd4\u56de").fetchSemanticsNode().boundsInRoot)
        val track = compose.onNodeWithTag("task-switch-\u5206\u4eab\u8bc4\u4ef7", useUnmergedTree = true)
        capture("task-settings-round-last-switch")
        assertInsideCircle(track.fetchSemanticsNode().boundsInRoot)
        assertInsideCircle(track.fetchSemanticsNode().boundsInRoot, corners = true)
        last.performClick()
        compose.runOnIdle { assertEquals(listOf("share_review" to true), shareChanges) }
        capture("task-settings-round")
    }

    @Test
    @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun roundTimeDialogKeepsInputAndConfirmationInsideTheCircle() {
        show(theme = composePreviewTheme(true))
        text("\u6267\u884c\u65f6\u95f4").performScrollTo().performClick()
        val input = description("\u8f93\u5165\u6267\u884c\u65f6\u95f4")
        assertInsideCircle(input.fetchSemanticsNode().boundsInRoot, corners = true)
        input.performTextReplacement("23:59")
        val confirm = text("\u786e\u8ba4").performScrollTo().assertIsDisplayed()
        assertInsideCircle(confirm.fetchSemanticsNode().boundsInRoot, corners = true)
        confirm.performClick()
        compose.runOnIdle { assertEquals(listOf("23:59"), timeChanges) }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun squareLayoutHasCompactControlsAndNoOverlappingOffsetLabels() {
        show(task(offset = 720))
        assertCompactSwitch()
        assertOffsetGeometry("task-settings-square-offset")
        text("\u5206\u4eab\u8bc4\u4ef7").performScrollTo().assertIsDisplayed().performClick()
        capture("task-settings-square")
    }

    @Test
    @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun smallRoundLightThemeWithLargerTextDoesNotSqueezeTheOffsetOrFinalSwitch() {
        show(task(offset = 720), composePreviewTheme(true).copy(dark = false,
            background = Color.White, panel = Color(0xFFF2F2F2), panelElevated = Color(0xFFE8E8E8),
            text = Color.Black, muted = Color.DarkGray, subtle = Color.Gray,
            textScale = 1.4f, uiScale = 1.2f))
        assertOffsetGeometry("task-settings-small-round-light-offset")
        val last = text("\u5206\u4eab\u8bc4\u4ef7").performScrollTo().assertIsDisplayed()
        assertInsideCircle(last.fetchSemanticsNode().boundsInRoot)
        last.performClick()
        capture("task-settings-small-round-light")
    }

    @Test
    @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun roundSwitchTrackAndBackRespondToRealTouch() {
        show(theme = composePreviewTheme(true))
        text("\u5206\u4eab\u8bc4\u4ef7").performScrollTo().assertIsDisplayed()
        val track = compose.onNodeWithTag("task-switch-\u5206\u4eab\u8bc4\u4ef7", useUnmergedTree = true)
        assertInsideCircle(track.fetchSemanticsNode().boundsInRoot, corners = true)
        track.performTouchInput { click(center) }
        val back = description("\u8fd4\u56de")
        assertInsideCircle(back.fetchSemanticsNode().boundsInRoot)
        back.performTouchInput { click(center) }
        compose.runOnIdle {
            assertEquals(listOf("share_review" to true), shareChanges)
            assertEquals(1, backs)
        }
        capture("task-settings-round-touch")
    }

    @Test
    @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun smallRoundMaximumOffsetAt150PercentTextRemainsReadableAndTappable() {
        show(task(offset = 720), composePreviewTheme(true).copy(textScale = 1.5f, uiScale = 1.2f))
        assertOffsetGeometry("task-settings-small-round-large-text-offset")
        val value = description("\u8f93\u5165\u968f\u673a\u504f\u79fb")
            .performScrollTo().assertIsDisplayed()
        assertInsideCircle(value.fetchSemanticsNode().boundsInRoot)
        value.performTouchInput { click(center) }
        description("\u8f93\u5165\u968f\u673a\u504f\u79fb\u5206\u949f").assertTextContains("720")
        text("\u53d6\u6d88").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(offsetChanges.isEmpty()) }
        val last = text("\u5206\u4eab\u8bc4\u4ef7").performScrollTo().assertIsDisplayed()
        val track = compose.onNodeWithTag("task-switch-\u5206\u4eab\u8bc4\u4ef7", useUnmergedTree = true)
        assertInsideCircle(track.fetchSemanticsNode().boundsInRoot, corners = true)
        last.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(listOf("share_review" to true), shareChanges) }
        capture("task-settings-small-round-large-text-last")
    }

    private fun show(
        task: CheckinCenterClient.Task = task(),
        theme: ComposeThemeState = composePreviewTheme(false),
        restoration: StateRestorationTester? = null,
    ) {
        // No coordinator, API, login, or successful check-in result is created by these fixtures.
        state = mutableStateOf(ComposeCheckinUiState(status = CheckinCenterClient.Status(
            CheckinCenterClient.Account("connected", "UI fixture", ""), task, null, null)))
        val content: @Composable () -> Unit = {
            HeyboxComposeTheme(theme) {
                ComposeCheckinTaskSettingsContent(state.value, { backs++ },
                    { enabledChanges += it }, { timeChanges += it }, { offsetChanges += it },
                    { action, enabled -> shareChanges += action to enabled })
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    private fun task(
        offset: Int = 30,
        windowStart: String = "08:30",
        windowEnd: String = "09:00",
        scheduleTime: String = "08:30",
        platformBlocked: Boolean = false,
        signBlocked: Boolean = false,
        sharingAvailable: Boolean = true,
    ) = CheckinCenterClient.Task(true, true, scheduleTime, offset, windowStart, windowEnd,
        platformBlocked, signBlocked,
        CheckinSharing.parse(JSONObject().put("share_actions_available", sharingAvailable)))

    private fun updateTask(task: CheckinCenterClient.Task) {
        compose.runOnIdle { state.value = state.value.copy(status = CheckinCenterClient.Status(
            CheckinCenterClient.Account("connected", "UI fixture", ""), task, null, null)) }
    }

    private fun text(value: String) = compose.onNodeWithText(value)
    private fun description(value: String) = compose.onNodeWithContentDescription(value)

    private fun assertCompactSwitch(scale: Float = 1f) {
        compose.onNodeWithTag("task-switch-\u81ea\u52a8\u7b7e\u5230", useUnmergedTree = true)
            .assertWidthIsEqualTo(36.dp * scale).assertHeightIsEqualTo(22.dp * scale)
    }

    private fun assertOffsetGeometry(screenshot: String) {
        description("\u8f93\u5165\u968f\u673a\u504f\u79fb").performScrollTo()
        val minus = description("\u51cf\u5c11\u968f\u673a\u504f\u79fb").fetchSemanticsNode().boundsInRoot
        val value = description("\u8f93\u5165\u968f\u673a\u504f\u79fb").fetchSemanticsNode().boundsInRoot
        val plus = description("\u589e\u52a0\u968f\u673a\u504f\u79fb").fetchSemanticsNode().boundsInRoot
        assertTrue(minus.right <= value.left + 0.5f && value.right <= plus.left + 0.5f)
        assertTrue(minus.left >= 0 && plus.right <= compose.activity.window.decorView.width)
        listOf("720", "\u968f\u673a\u504f\u79fb", "\u5206\u4eab\u8bc4\u4ef7").forEach { label ->
            val node = compose.onNodeWithText(label, useUnmergedTree = true).performScrollTo()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Text layout must be available for $label", layouts.isNotEmpty())
            if (label == "720") capture(screenshot)
            assertFalse("Text must not overflow for $label: " +
                layouts.joinToString { "size=" + it.size + ", paragraph=" + it.multiParagraph.width +
                    "x" + it.multiParagraph.height }, layouts.any { it.hasVisualOverflow })
        }
    }

    private fun assertInsideCircle(bounds: Rect, corners: Boolean = false) {
        val view = compose.activity.window.decorView
        val radius = minOf(view.width, view.height) / 2f - 4f
        val points = if (corners) listOf(bounds.left to bounds.top, bounds.right to bounds.top,
            bounds.left to bounds.bottom, bounds.right to bounds.bottom)
            else listOf(bounds.center.x to bounds.center.y)
        points.forEach { (x, y) ->
            val dx = x - view.width / 2f
            val dy = y - view.height / 2f
            assertTrue("Tap bounds must be inside the circular display: bounds=" + bounds +
                ", point=" + x + "," + y + ", view=" + view.width + "x" + view.height,
                dx * dx + dy * dy < radius * radius)
        }
    }

    private fun capture(name: String) {
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("Rendered page must not be blank", pixels.toSet().size > 5)
        val file = File("build/outputs/ui-regression/checkin-$name.png")
        requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        println("Native task-settings screenshot: " + file.absolutePath)
    }
}
