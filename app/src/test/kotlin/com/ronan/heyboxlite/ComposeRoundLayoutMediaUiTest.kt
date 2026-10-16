package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
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
internal class ComposeRoundLayoutMediaUiTest : ComposeRoundLayoutHarness() {
    @Test fun squareVideoControlsKeepTheirRealTouchCallbacks() = verifyVideo()
    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192VideoControlsStayInsideBothCircularChords() = verifyVideo()
    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227VideoControlsStayInsideBothCircularChords() = verifyVideo()
    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundVideoControlsDoNotUseSquareHitBounds() = verifyVideo(forceRound = true)

    private fun verifyVideo(forceRound: Boolean = false) {
        val services = layoutServices(forceRound)
        var backs = 0
        var toggles = 0
        showLayout(services) {
            ComposeVideoPlayerScreen(
                state = ComposeVideoPlayerUiState(title = "Local video", buffering = false,
                    durationMs = 90_000, videoWidth = 1920, videoHeight = 1080),
                crop = false,
                onBack = { backs++ }, onTogglePlayback = { toggles++ },
                onSeekPreview = {}, onSeekCommit = {}, onRetry = {}, onExternalPlayer = {},
                onHideControls = {}, videoSurface = {}, coverContent = { _, _ -> },
            )
        }
        val back = compose.onNodeWithTag("video-back")
        assertControlInsideDisplay(back, "fixed video return button")
        val play = circleReach(compose.onNodeWithTag("video-play"))
        assertControlInsideDisplay(play, "video play button")
        play.performTouchInput { click() }
        val seek = circleReach(compose.onNodeWithTag("video-seek"))
        assertTouchable(seek, "video seek control")
        back.performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, backs)
            assertEquals(1, toggles)
        }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundVideoErrorKeepsRetryAndExternalActionsReachable() {
        val services = layoutServices(forceRound = true)
        var retries = 0
        var external = 0
        showLayout(services) {
            ComposeVideoPlayerScreen(
                state = ComposeVideoPlayerUiState(error = "Local failure details. ".repeat(15),
                    externalAvailable = true, buffering = false),
                crop = false, onBack = {}, onTogglePlayback = {}, onSeekPreview = {},
                onSeekCommit = {}, onRetry = { retries++ }, onExternalPlayer = { external++ },
                onHideControls = {}, videoSurface = {}, coverContent = { _, _ -> },
            )
        }
        listOf("video-retry", "video-external").forEach { tag ->
            val target = circleReach(compose.onNodeWithTag(tag))
            assertControlInsideDisplay(target, tag)
            target.performTouchInput { click() }
        }
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, external)
        }
    }

    @Test fun squareCrashActionsRemainIndependentOfLongStack() = verifyCrash()
    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192CrashActionsRemainIndependentOfLongStack() = verifyCrash()
    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227CrashActionsRemainIndependentOfLongStack() = verifyCrash()
    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundCrashActionsRemainIndependentOfLongStack() = verifyCrash(forceRound = true)

    private fun verifyCrash(forceRound: Boolean = false) {
        val services = layoutServices(forceRound)
        val actions = mutableListOf<String>()
        showLayout(services) {
            ComposeCrashRecoveryScreen(
                ComposeCrashRecoveryState.fromReport(ComposeCrashRecoveryFixtures.longReport),
                { actions += "restart" }, { actions += "exit" }, { actions += "save" },
            )
        }
        listOf("crash-restart", "crash-exit", "crash-save").forEach { tag ->
            val target = circleReach(compose.onNodeWithTag(tag))
            assertControlInsideDisplay(target, tag)
            target.performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(listOf("restart", "exit", "save"), actions) }
    }
}
