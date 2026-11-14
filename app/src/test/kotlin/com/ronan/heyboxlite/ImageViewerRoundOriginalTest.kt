package com.ronan.heyboxlite

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w227dp-h227dp-round-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageViewerRoundOriginalTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun roundDoesNotForceOriginalAndManualControlsRemainReachable() {
        val requests = ViewerTestImages()
        val selection = ComposeViewerSelection()
        var saved = ""
        var finished = false
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(true)) {
                ComposeImageViewer(listOf("https://viewer.invalid/one.jpg"), 0, viewerBitmap(),
                    true, true, false, true, MotionLevel.OFF, selection,
                    { saved = it }, { finished = true }, requests = requests)
            }
        }
        compose.onNodeWithContentDescription("下载图片").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭图片").assertIsDisplayed()
        assertInCircle("下载图片")
        assertInCircle("关闭图片")
        compose.runOnIdle {
            assertTrue(requests.images.isNotEmpty())
            assertTrue(requests.images.none { it.original })
            requests.images.last().complete(viewerBitmap(400, 400))
        }
        compose.onNodeWithTag("viewer-original").performClick()
        compose.runOnIdle { assertTrue(requests.images.last().original) }
        compose.onNodeWithContentDescription("下载图片").performClick()
        compose.runOnIdle { assertEquals("https://viewer.invalid/one.jpg", saved) }
        compose.onNodeWithContentDescription("关闭图片").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(finished) }
    }

    @Test @Config(qualifiers = "w240dp-h320dp-notround-mdpi")
    fun squareViewerRetainsCounterAndManualOriginalButton() {
        var original = 0
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    ComposeViewerChrome(false, 1, 3, true, false, false, { original++ }, {}, {})
                }
            }
        }
        compose.onNodeWithText("2/3").assertIsDisplayed()
        compose.onNodeWithTag("viewer-original").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, original)
            captureMembershipUi(compose.activity, "compose-viewer-square240")
        }
    }

    @Test fun roundChromeScreenshotHasNoClippedControls() {
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(true)) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    ComposeViewerChrome(true, 0, 3, true, false, false, {}, {}, {})
                }
            }
        }
        assertInCircle("下载图片")
        assertInCircle("关闭图片")
        compose.onNodeWithText("查看原图").assertIsDisplayed()
        compose.runOnIdle { captureMembershipUi(compose.activity, "compose-viewer-round227") }
    }

    private fun assertInCircle(name: String) {
        val node = compose.onNodeWithContentDescription(name).fetchSemanticsNode()
        val bounds = node.boundsInRoot
        val radius = 227f / 2
        for ((x, y) in listOf(bounds.left to bounds.top, bounds.right to bounds.top,
            bounds.left to bounds.bottom, bounds.right to bounds.bottom)) {
            assertTrue("$name must stay in the round screen",
                (x - radius).pow(2) + (y - radius).pow(2) <= radius.pow(2) + 1)
        }
    }
}
