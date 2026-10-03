package com.ronan.heyboxlite

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ComposeVideoPlayerStateTest {
    @Test fun preparationUsesControllerFactsAndHonorsTheNoImageSetting() {
        val state = ComposeVideoPlayerState("<b>Title</b> [cube_test]", "cover", true)
        assertEquals("Title [cube_test]", state.ui.title)
        assertTrue(state.ui.coverVisible)
        assertTrue(state.ui.buffering)
        state.setPrepared(90_000)
        assertEquals(90_000, state.ui.durationMs)
        assertFalse(state.ui.coverVisible)
        assertFalse(state.ui.buffering)
        assertFalse(state.ui.playing)
        assertFalse(ComposeVideoPlayerState(null, "cover", false).ui.coverVisible)
    }

    @Test fun seekDraftSurvivesProgressAndCommitsOnlyOnce() {
        val state = prepared()
        state.previewSeek(42_000f)
        state.setProgress(12_000, 90_000)
        assertEquals(42_000, state.ui.displayedPosition)
        assertEquals(12_000, state.ui.positionMs)
        assertEquals(42_000, state.finishSeek())
        assertNull(state.ui.seekingMs)
        assertNull(state.finishSeek())
        state.previewSeek(Float.MAX_VALUE)
        assertEquals(90_000, state.ui.displayedPosition)
        state.previewSeek(-100f)
        assertEquals(0, state.ui.displayedPosition)
    }

    @Test fun progressClampsToTheKnownDurationAndKeepsDurationWhenAProgressTickHasNone() {
        val state = prepared()
        state.setProgress(100_000, 0)
        assertEquals(90_000, state.ui.positionMs)
        assertEquals(90_000, state.ui.durationMs)
        state.setProgress(-100, -1)
        assertEquals(0, state.ui.positionMs)
        state.setVideoSize(1920, 1080)
        state.setVideoSize(0, -1)
        assertEquals(1920, state.ui.videoWidth)
        assertEquals(1080, state.ui.videoHeight)
    }

    @Test fun onlyPlayingIdleControlsAutoHideAndPauseRestoresThem() {
        val state = prepared()
        state.hideIfPlaying()
        assertTrue(state.ui.controlsVisible)
        state.setPlaying(true)
        state.setFastForwarding(true)
        state.hideIfPlaying()
        assertTrue(state.ui.controlsVisible)
        state.setFastForwarding(false)
        state.previewSeek(100f)
        state.hideIfPlaying()
        assertTrue(state.ui.controlsVisible)
        state.finishSeek()
        state.hideIfPlaying()
        assertFalse(state.ui.controlsVisible)
        state.setPlaying(false)
        assertTrue(state.ui.controlsVisible)
        state.setCompleted()
        assertTrue(state.ui.completed)
        state.setPlaying(true)
        assertFalse(state.ui.completed)
    }

    @Test fun retryIsNotReportedAsSuccessfulAndErrorRestoresTheCoverAndActions() {
        val state = prepared()
        state.previewSeek(12_000f)
        state.setFastForwarding(true)
        state.setError("offline", true)
        assertEquals("offline", state.ui.error)
        assertTrue(state.ui.externalAvailable)
        assertTrue(state.ui.coverVisible)
        assertFalse(state.ui.playing)
        assertFalse(state.ui.buffering)
        assertFalse(state.ui.fastForwarding)
        assertNull(state.ui.seekingMs)
        state.retrying()
        assertEquals("", state.ui.error)
        assertTrue(state.ui.buffering)
        assertFalse(state.ui.playing)
        assertTrue(state.ui.coverVisible)
        state.setError(null, false)
        assertEquals("\u89c6\u9891\u64ad\u653e\u5931\u8d25", state.ui.error)
        assertFalse(state.ui.externalAvailable)
    }

    @Test fun closedStateRejectsEveryLateUiCallbackAndSeekCommit() {
        val state = prepared()
        state.previewSeek(20_000f)
        val before = state.ui
        state.close()
        state.setPrepared(1)
        state.setProgress(1, 1)
        state.setPlaying(true)
        state.setBuffering(true)
        state.setVideoSize(1, 1)
        state.setCompleted()
        state.setFastForwarding(true)
        state.setError("late", true)
        state.retrying()
        state.toggleControls()
        state.hideIfPlaying()
        state.previewSeek(1f)
        assertNull(state.finishSeek())
        assertEquals(before, state.ui)
    }

    @Test fun timeLabelsSupportLongVideosWithoutWrappingAtOneHour() {
        assertEquals("00:00", videoPlayerTime(-1))
        assertEquals("01:05", videoPlayerTime(65_000))
        assertEquals("59:59", videoPlayerTime(3_599_999))
        assertEquals("1:00:00", videoPlayerTime(3_600_000))
        assertEquals("596:31:23", videoPlayerTime(Int.MAX_VALUE))
    }

    @Test fun fitAndRoundChordIncludeTheWholeBandRatherThanOnlyItsCenter() {
        assertEquals(240f to 135f, videoPlayerFit(240f, 320f, 1920, 1080, false))
        assertEquals(240f to 320f, videoPlayerFit(240f, 320f, 1920, 1080, true))
        assertEquals(240f to 320f, videoPlayerFit(240f, 320f, 0, 0, false))
        listOf(192f, 227f, 320f).forEach { width ->
            val top = width * 0.12f
            val bottom = top + 40f
            val inset = videoPlayerCircleInset(width, width, top, bottom)
            listOf(inset to top, (width - inset) to top, inset to bottom,
                (width - inset) to bottom).forEach { (x, y) ->
                val dx = x - width / 2
                val dy = y - width / 2
                assertTrue(dx * dx + dy * dy <= (width / 2 - 4) * (width / 2 - 4) + 0.1f)
            }
        }
    }

    private fun prepared() = ComposeVideoPlayerState("Title", "cover", true).apply {
        setPrepared(90_000)
    }
}
