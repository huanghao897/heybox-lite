package com.ronan.heyboxlite

import android.app.Application
import android.media.MediaPlayer
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.ViewConfiguration
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowMediaPlayer

/** Offline platform MediaPlayer fixtures exercise the unchanged playback controller. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, shadows = [VideoPlayerMediaShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeVideoPlayerPlaybackTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val state = ComposeVideoPlayerState("Title", "", false)
    private val created = mutableListOf<MediaPlayer>()
    private lateinit var playback: VideoPlaybackController
    private lateinit var surface: SurfaceView
    private lateinit var gestures: ComposeVideoPlayerGestures
    private var downAt = 0L

    @Before fun prepareOfflinePlayer() {
        VideoPlayerMediaShadow.speedUnsupported = false
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(90_000, 0) }
        ShadowMediaPlayer.setCreateListener { player, _ -> created += player }
        surface = SurfaceView(app)
        playback = VideoPlaybackController(app, "https://example.invalid/offline-fixture.mp4",
            object : VideoPlaybackController.Listener {
                override fun onPrepared(durationMs: Int) = state.setPrepared(durationMs)
                override fun onVideoSizeChanged(width: Int, height: Int) = state.setVideoSize(width, height)
                override fun onProgress(positionMs: Int, durationMs: Int) = state.setProgress(positionMs, durationMs)
                override fun onPlayingChanged(playing: Boolean) = state.setPlaying(playing)
                override fun onBufferingChanged(buffering: Boolean) = state.setBuffering(buffering)
                override fun onCompleted() = state.setCompleted()
                override fun onError(message: String) = state.setError(message, false)
            }, false, false, true)
        playback.attach(surface.holder)
        playback.surfaceCreated(surface.holder)
        advance(1)
        assertEquals(90_000, state.ui.durationMs)
        gestures = ComposeVideoPlayerGestures(app, true, state::toggleControls,
            { if (playback.isPlaying) playback.pause() },
            { playback.beginFastForward().also(state::setFastForwarding) },
            { playback.endFastForward(); state.setFastForwarding(false) })
    }

    @After fun releaseFixture() {
        if (::gestures.isInitialized) gestures.close()
        if (::playback.isInitialized) {
            playback.release()
            surface.holder.removeCallback(playback)
        }
        ShadowMediaPlayer.resetStaticState()
        VideoPlayerMediaShadow.speedUnsupported = false
    }

    @Test fun longPressFromPausedTemporarilyPlaysAndReleaseRestoresPausedState() {
        assertFalse(playback.isPlaying)
        longPress()
        assertTrue(playback.isPlaying)
        assertTrue(state.ui.fastForwarding)
        advance(300)
        assertTrue(playback.currentPositionForUi() > 0)
        send(MotionEvent.ACTION_UP)
        assertFalse(playback.isPlaying)
        assertFalse(state.ui.fastForwarding)
        val restored = playback.currentPositionForUi()
        advance(500)
        assertEquals(restored, playback.currentPositionForUi())
    }

    @Test fun cancelledLongPressFromPlayingKeepsNormalPlaybackRunning() {
        playback.play()
        longPress()
        assertTrue(state.ui.fastForwarding)
        send(MotionEvent.ACTION_CANCEL)
        assertTrue(playback.isPlaying)
        assertFalse(state.ui.fastForwarding)
        val native = playback.javaClass.getDeclaredField("fastForwarding").apply { isAccessible = true }
        assertFalse(native.getBoolean(playback))
        advance(300)
        assertTrue(playback.currentPositionForUi() > 0)
    }

    @Test fun pauseAndCloseCancelFastForwardTicksAndPendingPlayback() {
        longPress()
        gestures.pause()
        playback.pause()
        assertFalse(state.ui.fastForwarding)
        assertFalse(playback.isPlaying)
        gestures.close()
        playback.release()
        val before = state.ui
        advance(1_000)
        assertEquals(before, state.ui)
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(created.single()).state)
    }

    @Test fun errorRetryAndCompletionPublishOnlyTheRealPlayerCallbacks() {
        val oldPlayer = created.single()
        shadowOf(oldPlayer).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        assertTrue(state.ui.error.isNotEmpty())
        assertFalse(playback.isPlaying)
        state.retrying()
        playback.retry()
        advance(1)
        assertEquals(2, created.size)
        assertEquals("", state.ui.error)
        assertFalse(state.ui.playing)
        playback.play()
        shadowOf(created.last()).invokeCompletionListener()
        assertTrue(state.ui.completed)
        assertTrue(state.ui.controlsVisible)
        assertFalse(state.ui.playing)
    }

    @Test
    fun fallbackFastForwardKeepsRunningAcrossProgressUpdatesAndStopsOnRelease() {
        VideoPlayerMediaShadow.speedUnsupported = true
        playback.play()
        longPress()
        val startedAt = playback.currentPositionForUi()
        advance(1_000)
        assertTrue("Fallback seeks must survive the 250ms progress refresh",
            playback.currentPositionForUi() - startedAt >= 1_800)
        send(MotionEvent.ACTION_UP)
        val restoredAt = playback.currentPositionForUi()
        advance(1_000)
        assertEquals(1_000, playback.currentPositionForUi() - restoredAt)
    }

    @Test fun nativeFastForwardRestoresSpeedOnReleaseAndAtCompletion() {
        playback.play()
        longPress()
        val player = created.single()
        assertEquals(2f, player.playbackParams.speed, 0f)
        send(MotionEvent.ACTION_UP)
        assertEquals(1f, player.playbackParams.speed, 0f)
        advance(400)
        longPress()
        assertEquals(2f, player.playbackParams.speed, 0f)
        shadowOf(player).invokeCompletionListener()
        assertEquals(1f, player.playbackParams.speed, 0f)
        assertFalse(state.ui.fastForwarding)
        assertTrue(state.ui.completed)
    }

    private fun longPress() {
        downAt = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN)
        advance(ViewConfiguration.getLongPressTimeout().toLong() + ViewConfiguration.getTapTimeout() + 20)
    }
    private fun send(action: Int) {
        val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, 80f, 100f, 0)
        try { assertTrue(gestures.onTouch(event)) } finally { event.recycle() }
    }
    private fun advance(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
}
