package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeDetailGalleryUpdatesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun shrinkingFromFourImagesToThreeReplacesTheWholePagerSnapshot() = verifyShrink()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundGalleryCanShrinkAfterReachingItsLastImage() = verifyShrink()

    @Test fun equalImageListsAndMediaSettingsDoNotResetTheCurrentPage() {
        val urls = listOf("first.gif", "second.gif", "third.jpg")
        val playGif = mutableStateOf(true)
        val opened = mutableListOf<String>()
        val requests = Images()
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Box(Modifier.width(180.dp).testTag("gallery")) {
                    ComposeDetailImagePager(urls.toList(), ComposeMediaSettings(playGif = playGif.value),
                        opened::add, requests)
                }
            }
        }
        swipe()
        compose.onNodeWithText("2/3").assertIsDisplayed()
        compose.runOnIdle { playGif.value = false }
        compose.onNodeWithText("2/3").assertIsDisplayed()
        compose.runOnIdle { playGif.value = true }
        compose.onNodeWithText("2/3").assertIsDisplayed()
        compose.onNodeWithTag("gallery").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("second.gif"), opened) }
    }

    @Test fun replacingEqualSizedGalleriesDoesNotKeepThePreviousImageOrCallback() {
        val urls = mutableStateOf(listOf("old-first.jpg", "old-second.jpg"))
        val opened = mutableListOf<String>()
        val requests = Images()
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Box(Modifier.width(180.dp).testTag("gallery")) {
                    ComposeDetailImagePager(urls.value, ComposeMediaSettings(), opened::add, requests)
                }
            }
        }
        swipe()
        compose.onNodeWithText("2/2").assertIsDisplayed()
        compose.runOnIdle { urls.value = listOf("new-first.jpg", "new-second.jpg") }
        compose.onNodeWithText("1/2").assertIsDisplayed()
        compose.onNodeWithTag("gallery").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("new-first.jpg"), opened) }
    }

    private fun verifyShrink() {
        val urls = mutableStateOf((1..4).map { "image-$it.jpg" })
        val requests = Images()
        val opened = mutableListOf<String>()
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(compose.activity.resources.configuration.isScreenRound)) {
                Box(Modifier.width(180.dp).testTag("gallery")) {
                    ComposeDetailImagePager(urls.value, ComposeMediaSettings(), opened::add, requests)
                }
            }
        }
        repeat(3) { swipe() }
        compose.onNodeWithText("4/4").assertIsDisplayed()
        compose.runOnIdle { urls.value = urls.value.take(3) }
        compose.onNodeWithText("1/3").assertIsDisplayed()
        swipe()
        compose.onNodeWithText("2/3").assertIsDisplayed()
        compose.onNodeWithTag("gallery").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("image-2.jpg"), opened) }
        compose.runOnIdle { urls.value = emptyList() }
        compose.onNodeWithText("2/3").assertDoesNotExist()
        compose.runOnIdle { urls.value = listOf("replacement.jpg") }
        compose.onNodeWithText("1/1").assertIsDisplayed()
        compose.onNodeWithTag("gallery").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("image-2.jpg", "replacement.jpg"), opened) }
    }

    private fun swipe() {
        compose.onNodeWithTag("gallery").performTouchInput { swipeLeft() }
        compose.waitForIdle()
    }

    private class Images : ComposeMediaImageRequests {
        private val bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)

        override fun load(view: ImageView, url: String, targetPx: Int, complete: (Boolean, Bitmap?) -> Unit) {
            view.setImageBitmap(bitmap)
            complete(true, bitmap)
        }

        override fun animate(view: ImageView, url: String) = Unit
        override fun cancel(view: ImageView) = Unit
    }
}
