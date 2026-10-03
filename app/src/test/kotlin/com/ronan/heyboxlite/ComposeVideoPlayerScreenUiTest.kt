@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Color as NativeColor
import android.os.Looper
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import java.time.Duration
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeVideoPlayerScreenUiTest {
    @get:Rule(order = 0) val compose = createAndroidComposeRule<ComponentActivity>()
    private val ui = ComposeVideoPlayerUiAssertions(compose, { compose.activity })
    @get:Rule(order = 1) val failure = object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) {
            runCatching { ui.capture("failure-${description.methodName}") }.exceptionOrNull()?.let(error::addSuppressed)
        }
    }
    private lateinit var state: ComposeVideoPlayerState
    private val crop = mutableStateOf(false)
    private var toggles = 0
    private var backs = 0
    private var retries = 0
    private var external = 0
    private var mediaTaps = 0
    private val seeks = mutableListOf<Int>()
    private val mediaColor = Color(0xFF3F454A)

    @Test fun squareButtonsAreTouchableAndControlsNeverOverlap() {
        show()
        assertControls(false)
        ui.touch("video-play", scroll = true)
        ui.touch("video-back")
        compose.runOnIdle {
            assertEquals(1, toggles)
            assertEquals(1, backs)
            assertFalse(state.ui.playing)
            assertTrue(seeks.isEmpty())
        }
        ui.capture("square-fit")
    }

    @Test @Config(qualifiers = "w320dp-h320dp-mdpi")
    fun wideSquareKeepsInlineTimelineTouchable() {
        show(duration = 7_200_000)
        assertControls(false)
        val play = ui.reach("video-play").fetchSemanticsNode().boundsInRoot
        val seek = ui.reach("video-seek").fetchSemanticsNode().boundsInRoot
        assertTrue("Wide square should retain one-row controls", play.top < seek.bottom && seek.top < play.bottom)
        ui.capture("square-320-inline")
    }

    @Test @Config(qualifiers = "w227dp-h227dp-mdpi")
    fun roundControlsFitInsideTheCircleAndBothButtonsReceiveRealTouches() {
        show(round = true)
        assertControls(true)
        ui.touch("video-play", scroll = true)
        ui.touch("video-back")
        compose.runOnIdle { assertEquals(1, toggles); assertEquals(1, backs) }
        ui.capture("round-227")
    }

    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun smallRoundLargeTextHasCompleteHourLabelsAndReachableSeekTrack() {
        show(round = true, textScale = 1.5f, uiScale = 1.2f, duration = Int.MAX_VALUE)
        assertControls(true)
        ui.reach("video-total-time").assertTextEquals("596:31:23")
        ui.capture("round-192-large-text-time")
        ui.assertInPage(ui.reach("video-seek").fetchSemanticsNode().boundsInRoot, true)
        ui.capture("round-192-large-text-seek")
        ui.touch("video-play", scroll = true)
        ui.touch("video-back")
        compose.runOnIdle { assertEquals(1, toggles); assertEquals(1, backs) }
    }

    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun smallSquareSystemFontScaleDoesNotShrinkOrClipTheTimeLabels() {
        show(fontScale = 1.5f, duration = Int.MAX_VALUE)
        assertControls(false)
        ui.assertTextFits("video-current-time")
        ui.assertTextFits("video-total-time")
        ui.capture("square-192-system-large-text")
    }

    @Test fun seekDragPreviewsUntilReleaseAndControllerProgressCannotOverwriteTheDraft() {
        show(duration = 100_000)
        val slider = ui.reach("video-seek").assertIsEnabled()
        slider.performTouchInput {
            down(Offset(width * 0.25f, centerY))
            moveTo(Offset(width * 0.7f, centerY), delayMillis = 80)
        }
        val draft = compose.runOnIdle {
            assertTrue(seeks.isEmpty())
            assertNotNull(state.ui.seekingMs)
            requireNotNull(state.ui.seekingMs).also { state.setProgress(10_000, 100_000) }
        }
        compose.runOnIdle { assertEquals(draft, state.ui.displayedPosition) }
        slider.performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(listOf(draft), seeks)
            assertNull(state.ui.seekingMs)
        }
    }

    @Test fun unknownDurationDisablesSeekAndPlayPauseReplayHaveDistinctSemantics() {
        show(duration = 0)
        ui.reach("video-seek").assertIsNotEnabled().performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(seeks.isEmpty()) }
        ui.node("video-play").assertContentDescriptionEquals("\u64ad\u653e")
        compose.runOnIdle { state.setPlaying(true) }
        ui.node("video-play").assertContentDescriptionEquals("\u6682\u505c")
        compose.runOnIdle { state.setCompleted() }
        ui.node("video-play").assertContentDescriptionEquals("\u91cd\u65b0\u64ad\u653e")
    }

    @Test fun coverAndSurfaceUseTheSameRealFitBoundsAndCropFillsThePage() {
        show(cover = true)
        val root = ui.node("video-player").fetchSemanticsNode().boundsInRoot
        val surface = ui.node("video-render-slot").fetchSemanticsNode().boundsInRoot
        val cover = ui.node("video-cover").fetchSemanticsNode().boundsInRoot
        assertEquals(surface, cover)
        assertEquals(root.width, surface.width, 1f)
        assertEquals(root.width * 1080f / 1920f, surface.height, 1f)
        ui.capture("square-cover-fit")
        compose.runOnIdle { crop.value = true }
        assertEquals(root, ui.node("video-render-slot").fetchSemanticsNode().boundsInRoot)
        assertEquals(root, ui.node("video-cover").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { state.setPrepared(90_000) }
        ui.node("video-cover").assertDoesNotExist()
        ui.capture("square-crop")
    }

    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun roundErrorMessageAndLastExternalActionCanScrollIntoTheCircle() {
        show(round = true, textScale = 1.5f, uiScale = 1.2f)
        compose.runOnIdle { state.setError("\u64ad\u653e\u5668\u65e0\u6cd5\u6253\u5f00\u6b64\u89c6\u9891\uff0c".repeat(5), true) }
        ui.node("video-seek").assertDoesNotExist()
        ui.assertInPage(ui.node("video-back").fetchSemanticsNode().boundsInRoot, true)
        listOf("video-retry", "video-external").forEach {
            ui.assertInPage(ui.reach(it).fetchSemanticsNode().boundsInRoot, true)
            ui.touch(it, scroll = true)
        }
        compose.runOnIdle { assertEquals(1, retries); assertEquals(1, external) }
        ui.capture("round-192-error-external")
        ui.touch("video-back")
        compose.runOnIdle { assertEquals(1, backs); state.setError("offline", false) }
        ui.node("video-external").assertDoesNotExist()
        ui.reach("video-retry").assertIsEnabled()
    }

    @Test fun titleKeepsTheNativeOfficialEmojiRendererAndFastForwardHintDoesNotOverlapBack() {
        show(title = "Title [cube_test]" )
        val title = compose.runOnIdle {
            videoPlayerDescendants(compose.activity.window.decorView)
                .filterIsInstance<ComposeRichTextView>().single()
        }
        assertEquals("Title [cube_test]", title.tag)
        assertEquals(1, title.maxLines)
        assertEquals(TextUtils.TruncateAt.END, title.ellipsize)
        compose.runOnIdle { state.setFastForwarding(true) }
        ui.node("video-title").assertDoesNotExist()
        ui.node("video-fast-forward").assertContentDescriptionEquals("2\u500d\u901f\u64ad\u653e")
        ui.assertNoOverlap(ui.node("video-back").fetchSemanticsNode().boundsInRoot,
            ui.node("video-fast-forward").fetchSemanticsNode().boundsInRoot)
        ui.capture("fast-forward")
        compose.runOnIdle { state.setFastForwarding(false) }
        ui.node("video-title").assertExists()
        ui.node("video-fast-forward").assertDoesNotExist()
    }

    @Test fun motionOffRemovesChromeImmediatelyAndNoInvisibleButtonBlocksTheCanvas() {
        show()
        val back = ui.node("video-back").fetchSemanticsNode().boundsInRoot.center
        compose.runOnIdle { state.toggleControls() }
        ui.node("video-back").assertDoesNotExist()
        ui.node("video-play").assertDoesNotExist()
        ui.node("video-seek").assertDoesNotExist()
        ui.node("video-gesture-surface").performTouchInput { click(back) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        compose.runOnIdle {
            assertEquals(0, backs)
            assertEquals(1, mediaTaps)
            assertTrue(state.ui.controlsVisible)
        }
        ui.node("video-back").assertIsDisplayed()
    }

    @Test fun enabledMotionFadesChromeUsingTheOriginalShortDuration() {
        crop.value = true
        show(motion = MotionLevel.REDUCED)
        val header = ui.node("video-header").fetchSemanticsNode().boundsInWindow
        val sample = Offset(header.right - 3, header.center.y)
        val visible = ui.pixel(sample)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { state.toggleControls() }
        ui.settleAndroidLayout()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        val during = ui.pixel(sample)
        ui.capture("fade-middle")
        assertNotEquals("Fade must have an intermediate frame", visible, during)
        assertNotEquals(NativeColor.rgb(63, 69, 74), during)
        compose.mainClock.advanceTimeBy(MotionSpec.PRESS_OUT_MS + 32)
        compose.waitForIdle()
        ui.node("video-back").assertDoesNotExist()
        assertEquals(NativeColor.rgb(63, 69, 74), ui.pixel(sample))
        ui.capture("fade-hidden")
    }

    @Test fun autoHideRunsAfterTheExistingDelayAndFastForwardCancelsIt() {
        show(playing = true)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3_300)
        compose.runOnIdle { assertTrue(state.ui.controlsVisible) }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertFalse(state.ui.controlsVisible); state.setFastForwarding(true) }
        ui.settleAndroidLayout()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(4_000)
        ui.settleAndroidLayout()
        compose.runOnIdle { assertTrue(state.ui.controlsVisible) }
        ui.node("video-fast-forward").assertIsDisplayed()
    }

    private fun assertControls(round: Boolean) {
        ui.assertInPage(ui.node("video-back").fetchSemanticsNode().boundsInRoot, round)
        ui.assertInPage(ui.reach("video-play").fetchSemanticsNode().boundsInRoot, round)
        ui.assertInPage(ui.reach("video-seek").fetchSemanticsNode().boundsInRoot, round)
        ui.assertTextFits("video-current-time")
        ui.assertTextFits("video-total-time")
        ui.assertNoOverlap(ui.node("video-current-time").fetchSemanticsNode().boundsInRoot,
            ui.node("video-total-time").fetchSemanticsNode().boundsInRoot)
        ui.assertNoOverlap(ui.node("video-header").fetchSemanticsNode().boundsInRoot,
            ui.node("video-timeline").fetchSemanticsNode().boundsInRoot)
    }

    private fun show(round: Boolean = false, textScale: Float = 1f, uiScale: Float = 1f,
        fontScale: Float = 1f, duration: Int = 90_000, cover: Boolean = false,
        title: String = "Title", motion: Int = MotionLevel.OFF, playing: Boolean = false) {
        state = ComposeVideoPlayerState(title, "fixture://cover", true).apply {
            if (!cover) setPrepared(duration)
            setProgress(0, duration)
            setVideoSize(1920, 1080)
            setBuffering(false)
            setPlaying(playing)
        }
        val theme = composeThemeState(ThemeTokens.of(true, 0, 0), uiScale, textScale, round)
            .copy(motionLevel = motion)
        val gestures = ComposeVideoPlayerGestures(compose.activity, true,
            { mediaTaps++; state.toggleControls() }, {}, { false }, {})
        compose.setContent {
            DisposableEffect(Unit) { onDispose { gestures.close() } }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                HeyboxComposeTheme(theme) {
                    ComposeVideoPlayerScreen(state.ui, crop.value, { backs++ }, { toggles++ },
                        state::previewSeek, { state.finishSeek()?.let { seeks += it } },
                        { retries++ }, { external++ }, state::hideIfPlaying,
                        gestureModifier = Modifier.pointerInteropFilter(onTouchEvent = gestures::onTouch),
                        videoSurface = { bounds -> Box(bounds.testTag("video-render-slot").background(mediaColor)) },
                        coverContent = { _, bounds -> Box(bounds.background(Color(0xFF738068))) })
                }
            }
        }
        compose.waitForIdle()
    }
}
