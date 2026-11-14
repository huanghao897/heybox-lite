package com.ronan.heyboxlite

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-notround-mdpi")
class ComposeViewerGesturesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val transform = ComposeViewerTransform().apply {
        layout(Size(240f, 320f), Size(240f, 320f), false)
    }

    @Test fun threeDoubleTapsReturnToFitAndZoomedHorizontalPanDoesNotPage() {
        lateinit var pager: PagerState
        compose.setContent {
            pager = rememberPagerState(pageCount = { 2 })
            HorizontalPager(pager, userScrollEnabled = !transform.zoomed,
                modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().testTag("gesture-$it")
                    .viewerGestures(transform, false, {}, {}))
            }
        }
        val page = compose.onNodeWithTag("gesture-0")
        page.performTouchInput { doubleClick(center) }
        compose.runOnIdle { assertTrue("double tap should zoom, actual=${transform.zoom}",
            transform.zoom >= ImageZoomPolicy.MIN_DOUBLE_TAP_ZOOM) }
        page.performTouchInput { swipeLeft() }
        compose.runOnIdle {
            assertEquals(0, pager.currentPage)
            assertTrue(transform.pan.x < 0)
        }
        page.performTouchInput { doubleClick(center) }
        page.performTouchInput { doubleClick(center) }
        compose.runOnIdle { assertEquals(1f, transform.zoom, 0f) }
        page.performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, pager.currentPage) }
    }

    @Test fun pinchConsumesBothPointersAndRemainingFingerDoesNotJump() {
        var finished = false
        surface { finished = true }
        val page = compose.onNodeWithTag("gestures")
        page.performTouchInput {
            down(0, Offset(90f, 160f))
            down(1, Offset(150f, 160f))
            moveTo(0, Offset(65f, 160f))
            moveTo(1, Offset(175f, 160f))
        }
        compose.runOnIdle { assertTrue(transform.zoom > 1.2f) }
        val before = transform.pan
        page.performTouchInput { up(1) }
        compose.runOnIdle { assertEquals(before, transform.pan) }
        page.performTouchInput { moveBy(0, Offset(0f, 25f)); up(0) }
        compose.runOnIdle {
            assertFalse(finished)
            assertEquals(Offset.Zero, transform.dismiss)
        }
    }

    @Test fun blankTapClosesButImageTapDoesNot() {
        var closed = 0
        transform.layout(Size(240f, 320f), Size(240f, 120f), false)
        surface { closed++ }
        compose.onNodeWithTag("gestures").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(0, closed) }
        compose.onNodeWithTag("gestures").performTouchInput { click(Offset(120f, 20f)) }
        compose.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun longImageDismissesOnlyAtItsBoundary() {
        var atBoundary = false
        var closed = 0
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("gestures")
                .viewerGestures(transform, true, { closed++ }, {}, canPull = { atBoundary }))
        }
        compose.onNodeWithTag("gestures").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(0, closed); atBoundary = true }
        compose.onNodeWithTag("gestures").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun twoFingerParallelDragMovesTheLongViewportWithoutChangingZoom() {
        var movement = Offset.Zero
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("gestures")
                .viewerGestures(transform, true, {}, {}, onLongPan = { movement += it }))
        }
        compose.onNodeWithTag("gestures").performTouchInput {
            down(0, Offset(80f, 140f))
            down(1, Offset(160f, 140f))
            moveTo(0, Offset(80f, 180f), delayMillis = 0)
            moveTo(1, Offset(160f, 180f))
            up(0)
            up(1)
        }
        compose.runOnIdle { assertTrue("long viewport must receive centroid pan", movement.y > 0f) }
    }

    @Test fun removingGesturesMidPullClearsTheOffsetWithoutDismissing() {
        val active = mutableStateOf(true)
        var dismissed = 0
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("gestures").then(if (active.value)
                Modifier.viewerGestures(transform, false, { dismissed++ }, {}) else Modifier))
        }
        val page = compose.onNodeWithTag("gestures")
        page.performTouchInput { down(Offset(120f, 80f)); moveBy(Offset(0f, 90f)) }
        compose.runOnIdle { assertTrue(transform.dismiss.y > 0); active.value = false }
        compose.runOnIdle { assertEquals(Offset.Zero, transform.dismiss); assertEquals(0, dismissed) }
        page.performTouchInput { up() }
        compose.runOnIdle { active.value = true }
        compose.runOnIdle { assertEquals(Offset.Zero, transform.dismiss) }
    }

    private fun surface(onDismiss: () -> Unit) {
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("gestures")
                .viewerGestures(transform, false, onDismiss, {}))
        }
    }
}
