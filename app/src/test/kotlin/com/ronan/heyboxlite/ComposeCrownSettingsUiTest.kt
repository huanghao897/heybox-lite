package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
class ComposeCrownSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var api: ApiClient? = null
    private val speedTitle = "\u6eda\u52a8\u901f\u5ea6"
    private val confirm = "\u786e\u5b9a"
    private val cancel = "\u53d6\u6d88"
    private val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @After fun close() { api?.close() }

    @Test fun customSpeedSurvivesConfirmReopenAndRemountWithAFreshSession() = verifyCustomSpeed()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundWatchCustomSpeedSurvivesConfirmReopenAndRemount() = verifyCustomSpeed()

    private fun verifyCustomSpeed() {
        val current = mutableStateOf(services())
        var changes = 0
        compose.setContent {
            val services = current.value
            key(services.session) {
                HeyboxComposeTheme(services.theme) {
                    ComposeContentCacheScreen(services, {}, {}, { changes++ })
                }
            }
        }

        listOf(501, 100_000, Int.MAX_VALUE).forEachIndexed { index, value ->
            openSpeed()
            input().performScrollTo().performTextReplacement(value.toString())
            compose.onNodeWithText(confirm).performClick()
            compose.onNodeWithText("$value%").assertExists()
            compose.runOnIdle {
                assertEquals(value, current.value.session.crownScrollSpeed())
                assertEquals(value, SessionStore(compose.activity).crownScrollSpeed())
            }

            openSpeed()
            input().assertTextContains(value.toString())
            assertSlidersAtMaximum()
            compose.onNodeWithText(confirm).performClick()
            compose.runOnIdle {
                assertEquals(value, current.value.session.crownScrollSpeed())
                assertEquals((index + 1) * 2, changes)
                current.value = current.value.copy(session = SessionStore(compose.activity))
            }

            compose.onNodeWithText("$value%").assertExists()
            openSpeed()
            input().assertTextContains(value.toString())
            assertSlidersAtMaximum()
            compose.onNodeWithText(cancel).performClick()
            compose.runOnIdle { assertEquals((index + 1) * 2, changes) }
        }
    }

    @Test fun invalidAndOverflowingInputCannotOverwriteTheSavedSpeed() {
        val services = services()
        services.session.setCrownScrollSpeed(501)
        var changes = 0
        compose.setContent {
            HeyboxComposeTheme(services.theme) {
                ComposeContentCacheScreen(services, {}, {}, { changes++ })
            }
        }
        openSpeed()

        listOf("", "0", "4", "2147483648", "9".repeat(80)).forEach { raw ->
            input().performScrollTo().performTextReplacement(raw)
            compose.onNodeWithText(confirm).performClick()
            input().assertExists()
            compose.runOnIdle {
                assertEquals(501, services.session.crownScrollSpeed())
                assertEquals(0, changes)
            }
        }
    }

    @Test fun anExplicitSliderChangeStillUsesTheCommonRange() {
        val services = services()
        services.session.setCrownScrollSpeed(501)
        compose.setContent {
            HeyboxComposeTheme(services.theme) {
                ComposeContentCacheScreen(services, {}, {}, {})
            }
        }
        assertSlidersAtMaximum()
        compose.onNode(slider).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(150f)) }

        compose.onNodeWithText("150%").assertExists()
        compose.runOnIdle { assertEquals(150, services.session.crownScrollSpeed()) }
        openSpeed()
        input().assertTextContains("150")
    }

    private fun openSpeed() = compose.onNodeWithText(speedTitle).performScrollTo().performClick()

    private fun input() = compose.onNodeWithContentDescription(speedTitle)

    private fun assertSlidersAtMaximum() {
        val sliders = compose.onAllNodes(slider).fetchSemanticsNodes()
        assertTrue(sliders.isNotEmpty())
        sliders.forEach { node ->
            val info = node.config[SemanticsProperties.ProgressBarRangeInfo]
            assertEquals(CrownScrollController.SLIDER_MAX_SPEED_PERCENT.toFloat(), info.current, 0f)
            assertEquals(CrownScrollController.MIN_SPEED_PERCENT.toFloat()..
                CrownScrollController.SLIDER_MAX_SPEED_PERCENT.toFloat(), info.range)
        }
    }

    private fun services(): ComposeServices {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val session = SessionStore(context)
        val client = ApiClient(session).also { api = it }
        return ComposeServices(compose.activity, session, client, GameDetailClient(client),
            LocalCache(context), Handler(Looper.getMainLooper()), ReadingTimeTracker(context), null,
            composePreviewTheme(false).copy(motionLevel = MotionLevel.OFF,
                roundScreen = RoundLayoutMetrics.isRoundDisplay(compose.activity)), ComposeToast {})
    }
}
