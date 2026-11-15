package com.ronan.heyboxlite

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-notround-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeViewerLoadingExitUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val requests = ViewerTestImages()
    private val selection = ComposeViewerSelection()
    private var finished = 0

    @Test fun closeButtonWorksBeforeTheFirstImageArrives() = verifyCloseButton()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundCloseButtonWorksBeforeTheFirstImageArrives() = verifyCloseButton()

    @Test fun loadingImageCanBeDismissedDownward() = verifyDrag(upward = false)

    @Test fun loadingImageCanBeDismissedUpward() = verifyDrag(upward = true)

    @Test fun backWhileLoadingCancelsTheRequestWithoutWaitingForItsCallback() {
        show()
        compose.runOnIdle { selection.back() }
        verifyClosed()
    }

    @Test fun pinchingAnUnloadedImageDoesNotTrapBackInAnInvisibleZoom() {
        show()
        compose.onNodeWithTag("viewer-image-page").performTouchInput {
            down(0, Offset(90f, 160f))
            down(1, Offset(150f, 160f))
            moveTo(0, Offset(60f, 160f))
            moveTo(1, Offset(180f, 160f))
            up(1)
            up(0)
        }
        compose.runOnIdle {
            assertEquals(1f, selection.transform!!.zoom, 0f)
            selection.back()
        }
        verifyClosed()
    }

    private fun verifyCloseButton() {
        show()
        compose.runOnIdle { captureMembershipUi(compose.activity,
            "viewer-loading-${if (compose.activity.resources.configuration.isScreenRound) "round" else "square"}") }
        compose.onNodeWithContentDescription("关闭图片").assertIsDisplayed().performClick()
        verifyClosed()
    }

    private fun verifyDrag(upward: Boolean) {
        show()
        compose.onNodeWithTag("viewer-image-page").performTouchInput {
            if (upward) swipeUp() else swipeDown()
        }
        verifyClosed()
    }

    private fun show() {
        val round = compose.activity.resources.configuration.isScreenRound
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(round)) {
                ComposeImageViewer(listOf("https://viewer.invalid/pending.jpg"), 0,
                    null, round, true, false, true, MotionLevel.FULL, selection,
                    {}, { finished++ }, requests = requests)
            }
        }
        compose.runOnIdle {
            assertEquals(1, requests.images.size)
            assertTrue(selection.page!!.loading)
        }
    }

    private fun verifyClosed() {
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, finished)
            assertTrue(requests.images.single().cancelled)
            requests.images.single().complete(viewerBitmap())
            assertNull(selection.page!!.bitmap)
            assertEquals(1, requests.images.size)
        }
    }
}
