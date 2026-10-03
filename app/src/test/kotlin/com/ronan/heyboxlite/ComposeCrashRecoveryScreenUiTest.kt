package com.ronan.heyboxlite

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeCrashRecoveryScreenUiTest {
    @get:Rule(order = 0) val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule(order = 1) val testName = TestName()
    private var installed = false
    private val ui = ComposeCrashRecoveryUiAssertions(compose, { compose.activity },
        { "screen-" + testName.methodName })
    @get:Rule(order = 2) val failure = object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) {
            if (installed) runCatching { ui.capture("failure") }.exceptionOrNull()?.let(error::addSuppressed)
        }
    }
    private val state = mutableStateOf(ComposeCrashRecoveryState.fromReport(ComposeCrashRecoveryFixtures.longReport))
    private var restarts = 0
    private var exits = 0
    private var saves = 0

    @Test fun squareLongStackDoesNotDisplaceAnyAction() = checkActions()

    @Test @Config(qualifiers = "w320dp-h320dp-mdpi")
    fun square320LargeTextKeepsEveryActionFullyVisible() = checkActions(large = true)

    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun square192LargeTextKeepsTheLastSaveButtonReachable() = checkActions(large = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192ActionsRemainInsideTheCircleAndReceiveTouch() = checkActions()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227ActionsRemainInsideTheCircleAndReceiveTouch() = checkActions()

    @Test fun squareLargeTextKeepsButtonLabelsComplete() = checkActions(large = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192LargeTextCanReachTheLastSaveButton() = checkActions(large = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192LargeTextDragRevealsAndTouchesTheLastSaveButton() {
        show(large = true)
        val initialSave = compose.onNodeWithTag("crash-save").fetchSemanticsNode()
        assertTrue("Large-text fixture must require scrolling",
            initialSave.boundsInRoot.height < initialSave.size.height - 0.5f)
        repeat(4) {
            compose.onNodeWithTag("crash-actions").performTouchInput { swipeUp(durationMillis = 800) }
            compose.waitForIdle()
        }
        ui.assertFullyVisible("crash-save")
        ui.assertVisibleTextDoesNotOverlap()
        ui.capture("dragged-to-save")
        compose.onNodeWithTag("crash-save").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowLargeTextKeepsButtonLabelsComplete() = checkActions(large = true)

    @Test fun savingDisablesOnlySaveAndKeepsRestartAndExitAvailable() {
        show()
        compose.runOnIdle { state.value = state.value.copy(status = "\u6b63\u5728\u4fdd\u5b58", saving = true) }
        ui.reach("crash-save").assertIsNotEnabled()
        ui.assertLabelComplete("\u6b63\u5728\u4fdd\u5b58")
        ui.touch("crash-save")
        ui.touch("crash-restart", "\u91cd\u542f\u5e94\u7528")
        ui.touch("crash-exit", "\u9000\u51fa")
        compose.runOnIdle {
            assertEquals(0, saves)
            assertEquals(1, restarts)
            assertEquals(1, exits)
        }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun failureIsReadableAndAllowsRetryWithoutDiscardingTheLocalSummary() {
        show()
        val summary = state.value.summary
        compose.runOnIdle {
            state.value = state.value.copy(status = "\u4fdd\u5b58\u5931\u8d25\uff0c\u65e5\u5fd7\u4ecd\u4fdd\u7559\u5728\u672c\u673a")
        }
        ui.assertLabelComplete(state.value.status)
        ui.reach("crash-save").assertIsEnabled()
        ui.touch("crash-save")
        compose.runOnIdle {
            assertEquals(1, saves)
            assertEquals(summary, state.value.summary)
        }
    }

    @Test fun summaryUsesThreeVisualLinesButActionsDoNotEllipsize() {
        state.value = ComposeCrashRecoveryState.fromReport("error: " + "very long summary ".repeat(100))
        show()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("crash-summary").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        assertTrue(layouts.isNotEmpty())
        assertEquals(3, layouts.single().layoutInput.maxLines)
        assertTrue(state.value.summary.length <= 163)
        ui.touch("crash-restart", "\u91cd\u542f\u5e94\u7528")
        ui.touch("crash-exit", "\u9000\u51fa")
    }

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun restoredActionScrollerStillReachesEveryButton() {
        val restoration = StateRestorationTester(compose)
        show(restoration = restoration)
        ui.reach("crash-save")
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        ui.touch("crash-save")
        ui.touch("crash-restart", "\u91cd\u542f\u5e94\u7528")
        ui.touch("crash-exit", "\u9000\u51fa")
        compose.runOnIdle {
            assertEquals(1, saves)
            assertEquals(1, restarts)
            assertEquals(1, exits)
        }
    }

    private fun checkActions(large: Boolean = false) {
        show(large)
        ui.assertContentInDisplay()
        ui.touch("crash-restart", "\u91cd\u542f\u5e94\u7528")
        ui.touch("crash-exit", "\u9000\u51fa")
        ui.touch("crash-save")
        compose.runOnIdle {
            assertEquals(1, restarts)
            assertEquals(1, exits)
            assertEquals(1, saves)
        }
    }

    private fun show(large: Boolean = false, restoration: StateRestorationTester? = null) {
        val theme = crashRecoveryTheme(!large, 1f, if (large) 1.8f else 1f,
            compose.activity.resources.configuration.isScreenRound)
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,
                if (large) 1.3f else 1f)) {
                HeyboxComposeTheme(theme) {
                    ComposeCrashRecoveryScreen(state.value, { restarts++ }, { exits++ }, { saves++ })
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        installed = true
        compose.waitForIdle()
        val config = compose.activity.resources.configuration
        assertEquals(config.screenWidthDp, compose.activity.window.decorView.width)
        assertEquals(config.screenHeightDp, compose.activity.window.decorView.height)
        ui.capture("initial")
    }
}
