package com.ronan.heyboxlite

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-notround-mdpi")
class ComposeViewerZoomAnimationUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val transform = ComposeViewerTransform().apply {
        layout(Size(240f, 320f), Size(240f, 320f), false)
    }

    @Test fun doubleTapHasIntermediateFramesAndThirdDoubleTapAnimatesBackToFit() {
        show()
        doubleTap()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            assertTrue("Zoom must animate instead of jumping directly to its target",
                transform.zoom > 1f && transform.zoom < ImageZoomPolicy.MIN_DOUBLE_TAP_ZOOM)
        }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(ImageZoomPolicy.MIN_DOUBLE_TAP_ZOOM, transform.zoom, .001f) }
        doubleTap()
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(4f, transform.zoom, .001f) }
        doubleTap()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertTrue(transform.zoom > 1f && transform.zoom < 4f) }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle {
            assertEquals(1f, transform.zoom, .001f)
            assertEquals(Offset.Zero, transform.pan)
        }
    }

    @Test fun resetCancelsAnUnfinishedZoomSoLaterFramesCannotRestoreIt() {
        show()
        doubleTap()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { transform.reset() }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle {
            assertEquals(1f, transform.zoom, 0f)
            assertEquals(Offset.Zero, transform.pan)
        }
    }

    @Test fun disablingMotionKeepsDoubleTapImmediate() {
        show(MotionLevel.OFF)
        doubleTap()
        compose.runOnIdle { assertEquals(ImageZoomPolicy.MIN_DOUBLE_TAP_ZOOM, transform.zoom, .001f) }
    }

    @Test fun pinchCancelsAnUnfinishedZoomAndKeepsTheUsersNewScale() {
        show()
        doubleTap()
        compose.mainClock.advanceTimeBy(64)
        val page = compose.onNodeWithTag("gestures")
        page.performTouchInput {
            down(0, Offset(90f, 160f))
            down(1, Offset(150f, 160f))
            moveTo(0, Offset(80f, 160f))
            moveTo(1, Offset(160f, 160f))
            up(1)
            up(0)
        }
        val scale = compose.runOnIdle { transform.zoom }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(scale, transform.zoom, .001f) }
    }

    private fun show(motion: Int = MotionLevel.FULL) {
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("gestures")
                .viewerGestures(transform, false, {}, {}, motion = motion))
        }
        compose.mainClock.autoAdvance = false
    }

    private fun doubleTap() {
        compose.onNodeWithTag("gestures").performTouchInput { doubleClick(center) }
    }
}
