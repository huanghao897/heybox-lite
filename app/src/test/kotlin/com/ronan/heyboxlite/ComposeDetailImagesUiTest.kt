package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeDetailImagesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun openingAnArticleImagePassesItsActualThumbnailToTheViewer() {
        val requests = Images()
        var preview: ImageView? = null
        var selected = ""
        var fallbackCalls = 0
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Box(Modifier.width(200.dp)) {
                    ComposeDetailImage("original.jpg", ComposeMediaSettings(), { fallbackCalls++ },
                        requests, onOpenPreview = { url, source -> selected = url; preview = source })
                }
            }
        }
        compose.onNodeWithTag("detail-image-original.jpg").performClick()
        compose.runOnIdle {
            assertEquals("original.jpg", selected)
            assertEquals(0, fallbackCalls)
            assertNotNull(preview)
            assertTrue(requireNotNull(preview).isAttachedToWindow)
            assertNotNull(requireNotNull(preview).drawable)
            val bounds = ImageTransitionSource.visibleBoundsOnScreen(preview)
            assertTrue(bounds.width() > 0 && bounds.height() > 0)
        }
    }

    @Test fun decodedDimensionsResizeTheArticleImageAndClickKeepsItsOriginalUrl() {
        val url = "https://example.invalid/original.jpg?quality=original"
        val requests = Images()
        val opened = mutableListOf<String>()
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Box(Modifier.width(200.dp)) {
                    ComposeDetailImage(url, ComposeMediaSettings(imageTargetPx = 120), opened::add, requests)
                }
            }
        }
        compose.onNodeWithTag("detail-image-$url").assertHeightIsEqualTo(150.dp).performClick()
        compose.runOnIdle {
            assertEquals(listOf(url), opened)
            assertEquals(listOf(url), requests.urls)
            assertTrue(requests.targets.isNotEmpty())
            assertTrue(requests.targets.all { it == 120 })
            assertTrue(requests.animated.isEmpty())
        }
    }

    @Test fun gifPlaybackSettingChangesAnimationWithoutChangingTheOpenCallback() {
        val url = "https://example.invalid/original.gif?token=keep"
        val requests = Images()
        val enabled = mutableStateOf(false)
        val opened = mutableListOf<String>()
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Box(Modifier.width(200.dp)) {
                    ComposeDetailImage(url, ComposeMediaSettings(playGif = enabled.value), opened::add, requests)
                }
            }
        }
        compose.runOnIdle {
            assertTrue(requests.animated.isEmpty())
            enabled.value = true
        }
        compose.onNodeWithTag("detail-image-$url").performClick()
        compose.runOnIdle {
            assertEquals(listOf(url), requests.animated)
            assertEquals(listOf(url), opened)
            enabled.value = false
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(url), requests.animated) }
    }

    @Test fun disabledArticleImagesAndPagersPerformNoRequestsOrClicks() {
        val requests = Images()
        val opened = mutableListOf<String>()
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeDetailImage("hidden.gif", ComposeMediaSettings(enabled = false), opened::add, requests)
                ComposeDetailImagePager(listOf("hidden.gif"), ComposeMediaSettings(enabled = false), opened::add, requests)
                ComposeDetailImagePager(emptyList(), ComposeMediaSettings(), opened::add, requests)
            }
        }
        compose.onNodeWithTag("detail-image-hidden.gif").assertDoesNotExist()
        compose.onNodeWithText("1/1").assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.runOnIdle {
            assertTrue(requests.urls.isEmpty())
            assertTrue(requests.animated.isEmpty())
            assertTrue(opened.isEmpty())
        }
    }

    @Test fun pagerOpensTheVisibleOriginalAndOnlyAnimatesTheSettledGif() {
        val urls = listOf("https://example.invalid/first.gif", "https://example.invalid/second.gif")
        val requests = Images()
        val opened = mutableListOf<String>()
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Box(Modifier.width(200.dp).testTag("pager")) {
                    ComposeDetailImagePager(urls, ComposeMediaSettings(), opened::add, requests)
                }
            }
        }
        compose.onNodeWithText("1/2").assertIsDisplayed()
        compose.onNodeWithTag("pager").assertHeightIsEqualTo(170.dp)
        compose.runOnIdle { assertEquals(listOf(urls[0]), requests.animated) }
        compose.onNodeWithTag("pager").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(urls[0]), opened) }
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("2/2").assertIsDisplayed()
        compose.onNodeWithTag("pager").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(urls, opened)
            assertEquals(urls, requests.animated)
            assertTrue(requests.urls.containsAll(urls))
            captureMembershipUi(compose.activity, "detail-media-square240-pager")
        }
    }

    @Test
    @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun roundPagerRemainsCompactAndReachable() {
        val url = "https://example.invalid/round.jpg"
        val requests = Images()
        val opened = mutableListOf<String>()
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(true)) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Box(Modifier.width(180.dp).testTag("pager")) {
                        ComposeDetailImagePager(listOf(url), ComposeMediaSettings(), opened::add, requests)
                    }
                }
            }
        }
        compose.onNodeWithTag("pager").assertHeightIsEqualTo(104.4.dp).assertIsDisplayed()
        compose.onNodeWithText("1/1").assertIsDisplayed()
        compose.onNodeWithTag("pager").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(url), opened)
            assertEquals(listOf(url), requests.urls)
            captureMembershipUi(compose.activity, "detail-media-round227-pager")
        }
    }

    @Test fun videoLongPressOpensSettingsWithoutPlayingAndNormalTapStillPlays() {
        val video = VideoData.create("https://example.invalid/video.mp4", "", "Video")
        var settingsOpened = 0
        val played = mutableListOf<VideoData>()
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeVideoCard(video, ComposeMediaSettings(enabled = false),
                    onVideoSettings = { settingsOpened++ }, onOpenVideo = played::add)
            }
        }
        val control = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        assertEquals("\u89c6\u9891\u64ad\u653e\u8bbe\u7f6e",
            control.fetchSemanticsNode().config[SemanticsActions.OnLongClick].label)
        control.performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(1, settingsOpened)
            assertTrue(played.isEmpty())
        }
        control.performClick()
        compose.runOnIdle {
            assertEquals(1, settingsOpened)
            assertEquals(1, played.size)
            assertSame(video, played.single())
        }
    }

    private class Images : ComposeMediaImageRequests {
        val urls = mutableListOf<String>()
        val targets = mutableListOf<Int>()
        val animated = mutableListOf<String>()
        private val bitmap = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        override fun load(view: ImageView, url: String, targetPx: Int, complete: (Boolean, Bitmap?) -> Unit) {
            urls += url
            targets += targetPx
            view.setImageBitmap(bitmap)
            complete(true, bitmap)
        }
        override fun animate(view: ImageView, url: String) { animated += url }
        override fun cancel(view: ImageView) { }
    }
}
