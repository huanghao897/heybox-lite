package com.ronan.heyboxlite

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Looper
import android.text.Spanned
import android.util.LruCache
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import java.time.Duration
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowMediaPlayer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi",
    shadows = [VideoPlayerMediaShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeVideoPlayerActivityTest {
    private val players = mutableListOf<MediaPlayer>()
    private val title = "Video [cube_\u6765\u8d22]"
    @get:Rule(order = 0) val fixture = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            SessionStore(app).apply {
                setNoImage(true)
                setRoundScreen(false)
                setDarkMode(true)
                setUiScale(100)
                setTextScale(100)
                setMotionLevel(MotionLevel.OFF)
                setVideoAutoplay(false)
                setVideoLoop(false)
                setVideoMuted(true)
                setVideoLongPressFastForward(true)
                setVideoExternalFallback(false)
            }
            // Cache an offline image for a real bundled emoji token; never fetch a fixture URL.
            EmojiRenderer.clear()
            OfficialEmojiFallback.load(app)
            val field = EmojiRenderer::class.java.getDeclaredField("BITMAPS").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val cache = field.get(null) as LruCache<String, Bitmap>
            cache.put("d:[cube_\u6765\u8d22]", Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                .apply { eraseColor(Color.WHITE) })
            ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(90_000, 0) }
            ShadowMediaPlayer.setCreateListener { player, _ -> players += player }
        }
        override fun after() {
            ShadowMediaPlayer.resetStaticState()
            EmojiRenderer.clear()
        }
    }
    private val scenarioRule = ActivityScenarioRule<VideoPlayerActivity>(Intent(
        ApplicationProvider.getApplicationContext<Application>(), VideoPlayerActivity::class.java)
        .putExtra(VideoPlayerActivity.EXTRA_URL, "https://example.invalid/offline-fixture.mp4")
        .putExtra(VideoPlayerActivity.EXTRA_COVER, "https://example.invalid/not-loaded.jpg")
        .putExtra(VideoPlayerActivity.EXTRA_TITLE, title))
    @get:Rule(order = 1) val compose = AndroidComposeTestRule(scenarioRule) { rule ->
        lateinit var activity: VideoPlayerActivity
        rule.scenario.onActivity { activity = it }
        activity
    }
    private val ui = ComposeVideoPlayerUiAssertions(compose, { compose.activity })
    @get:Rule(order = 2) val failure = object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) {
            runCatching { ui.capture("activity-failure-${description.methodName}") }
                .exceptionOrNull()?.let(error::addSuppressed)
        }
    }

    @Test fun realActivityMountsComposeAndOnlyTheSurfaceAndRichTitleRemainNative() {
        prepare()
        val activity = compose.activity
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        assertEquals(1, content.childCount)
        assertTrue(content.getChildAt(0) is ComposeView)
        val descendants = videoPlayerDescendants(content).toList()
        assertEquals(1, descendants.filterIsInstance<SurfaceView>().size)
        assertFalse(descendants.any { it is SeekBar || it is ImageButton || it is ProgressBar })
        assertTrue(surface().keepScreenOn)
        assertTrue(shadowOf(surface()).fakeSurfaceHolder.callbacks.contains(playback()))
        val rich = descendants.filterIsInstance<ComposeRichTextView>().single()
        assertEquals(title, rich.tag)
        assertTrue((rich.text as Spanned).getSpans(0, rich.text.length, CenteredImageSpan::class.java).isNotEmpty())
        ui.node("video-cover").assertDoesNotExist()
        ui.node("video-player").assertIsDisplayed()
        assertNull(shadowOf(activity).nextStartedActivity)
        ui.capture("activity-mounted")
    }

    @Test fun playAndSeekTouchesReachTheUnchangedControllerAndDoubleTapPausesIt() {
        prepare()
        ui.touch("video-play", scroll = true)
        compose.runOnIdle { assertTrue(playback().isPlaying); assertTrue(state().ui.playing) }
        ui.node("video-play").assertContentDescriptionEquals("\u6682\u505c")
        ui.reach("video-seek").performTouchInput { click(Offset(width * 0.65f, centerY)) }
        compose.runOnIdle {
            val requested = field(playback(), "pendingSeekMs") as Int
            assertTrue(requested in 1..90_000)
            assertNull(state().ui.seekingMs)
        }
        ui.node("video-gesture-surface").performTouchInput { doubleClick(center) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        compose.runOnIdle { assertFalse(playback().isPlaying); assertFalse(state().ui.playing) }
        ui.node("video-play").assertContentDescriptionEquals("\u64ad\u653e")
        ui.node("video-gesture-surface").performTouchInput { click(center) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        compose.runOnIdle { assertFalse(state().ui.controlsVisible) }
        ui.node("video-play").assertDoesNotExist()
    }

    @Test fun pauseStopsPlaybackAndActivityResumeDoesNotOverrideTheOriginalAutoplayPolicy() {
        prepare()
        ui.touch("video-play", scroll = true)
        val controller = playback()
        val snapshot = state()
        scenarioRule.scenario.moveToState(Lifecycle.State.STARTED)
        assertFalse(controller.isPlaying)
        assertFalse(snapshot.ui.playing)
        scenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle { assertFalse(controller.isPlaying); assertFalse(snapshot.ui.playing) }
        ui.node("video-play").assertIsDisplayed()
    }

    @Test fun destroyReleasesPlaybackSurfaceCallbacksAndRejectsLateCallbacks() {
        val media = prepare()
        val activity = compose.activity
        val controller = playback()
        val snapshot = state()
        val view = surface()
        val oldPrepared = shadowOf(media).onPreparedListener
        val oldCompletion = shadowOf(media).onCompletionListener
        scenarioRule.scenario.close()
        assertFalse(view.keepScreenOn)
        assertFalse(shadowOf(view).fakeSurfaceHolder.callbacks.contains(controller))
        assertTrue(field(controller, "released") as Boolean)
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(media).state)
        assertNull(field(activity, "surface"))
        val after = snapshot.ui
        oldPrepared?.onPrepared(media)
        oldCompletion?.onCompletion(media)
        activity.onPrepared(1)
        activity.onProgress(1, 1)
        activity.onPlayingChanged(true)
        activity.onBufferingChanged(true)
        activity.onVideoSizeChanged(1, 1)
        activity.onCompleted()
        activity.onError("late")
        assertEquals(after, snapshot.ui)
        assertNull(shadowOf(media).onPreparedListener)
        assertNull(shadowOf(media).onCompletionListener)
    }

    @Test fun recreationClosesTheOldSurfaceAndKeepsTheOriginalIntentContract() {
        prepare()
        val oldActivity = compose.activity
        val oldSurface = surface()
        val oldController = playback()
        scenarioRule.scenario.recreate()
        prepare()
        assertNotSame(oldActivity, compose.activity)
        assertNotSame(oldSurface, surface())
        assertFalse(oldSurface.keepScreenOn)
        assertFalse(shadowOf(oldSurface).fakeSurfaceHolder.callbacks.contains(oldController))
        assertTrue(field(oldController, "released") as Boolean)
        assertEquals("https://example.invalid/offline-fixture.mp4", compose.activity.intent.getStringExtra("video_url"))
        assertEquals(title, state().ui.title)
        ui.capture("activity-recreated")
    }

    @Test fun nativeErrorRetryUsesANewPlayerAndUnavailableExternalFallbackStaysAnError() {
        val media = prepare()
        compose.runOnIdle { shadowOf(media).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0) }
        assertTrue(state().ui.error.isNotEmpty())
        ui.reach("video-retry").assertIsEnabled()
        ui.node("video-external").assertDoesNotExist()
        val count = players.size
        ui.touch("video-retry", scroll = true)
        prepare()
        assertEquals(count + 1, players.size)
        assertFalse(state().ui.playing)
        assertEquals("", state().ui.error)
        compose.runOnIdle { state().setError("offline", true) }
        ui.touch("video-external", scroll = true)
        assertEquals("\u672a\u5b89\u88c5\u53ef\u7528\u7684\u5916\u90e8\u64ad\u653e\u5668", state().ui.error)
        ui.node("video-external").assertDoesNotExist()
        assertNull(shadowOf(compose.activity).nextStartedActivity)
        ui.capture("activity-external-unavailable")
    }

    @Test fun backTouchFinishesTheRealActivityWithoutLaunchingAnotherPage() {
        prepare()
        val activity = compose.activity
        ui.touch("video-back")
        assertTrue(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    private fun prepare(): MediaPlayer {
        compose.waitForIdle()
        compose.runOnIdle {
            if (field(playback(), "player") == null) playback().surfaceCreated(surface().holder)
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        compose.waitForIdle()
        return compose.runOnIdle {
            assertEquals(90_000, state().ui.durationMs)
            requireNotNull(field(playback(), "player") as MediaPlayer?)
        }
    }
    private fun state() = field(compose.activity, "playerState") as ComposeVideoPlayerState
    private fun playback() = field(compose.activity, "playback") as VideoPlaybackController
    private fun surface() = field(compose.activity, "surface") as SurfaceView
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)
}
