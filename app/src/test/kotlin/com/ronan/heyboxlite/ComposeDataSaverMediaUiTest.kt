package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
class ComposeDataSaverMediaUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val requests = Images()

    @Test fun userAvatarsStayAutomaticWhenTheirSharedMediaSettingsHaveSaverEnabled() {
        compose.setContent {
            CompositionLocalProvider(LocalComposeDataSaver provides true) {
                HeyboxComposeTheme(composePreviewTheme(false)) {
                    Column {
                        ComposeUserAvatar("avatar.jpg", "Author", ComposeMediaSettings(dataSaver = true),
                            28.dp, null, requests)
                        ComposeMediaImage("post.jpg", ComposeMediaSettings(dataSaver = true),
                            Modifier.size(80.dp), requests = requests)
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(listOf("avatar.jpg"), requests.urls) }
    }

    @Test fun automaticAvatarExemptionNeverOverridesTheExistingNoImageSetting() {
        compose.setContent {
            CompositionLocalProvider(LocalComposeDataSaver provides true) {
                HeyboxComposeTheme(composePreviewTheme(false)) {
                    ComposeUserAvatar("avatar.jpg", "Author",
                        ComposeMediaSettings(enabled = false, dataSaver = true), 28.dp, null, requests)
                }
            }
        }
        compose.runOnIdle { assertTrue(requests.loads.isEmpty()); assertTrue(requests.animated.isEmpty()) }
    }

    @Test fun articleKeepsItsFrameAndFirstTapOnlyLoadsEvenInsideAClickableParent() {
        val theme = mutableStateOf(composePreviewTheme(false))
        val opened = mutableListOf<String>()
        var parentOpens = 0
        var preview: ImageView? = null
        compose.setContent {
            HeyboxComposeTheme(theme.value) {
                Box(Modifier.width(200.dp).clickable { parentOpens++ }) {
                    ComposeDetailImage("article.jpg", ComposeMediaSettings(dataSaver = true),
                        opened::add, requests, onOpenPreview = { url, view -> opened += url; preview = view })
                }
            }
        }
        val image = compose.onNodeWithTag("detail-image-article.jpg")
        image.assertHeightIsEqualTo(114.dp)
        compose.runOnIdle { assertTrue(requests.loads.isEmpty()) }
        image.performTouchInput { click() }
        image.assertHeightIsEqualTo(150.dp)
        compose.runOnIdle {
            assertEquals(listOf("article.jpg"), requests.urls)
            assertTrue(opened.isEmpty())
            assertEquals(0, parentOpens)
            theme.value = theme.value.copy(muted = Color.Gray)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, requests.loads.size) }
        image.performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf("article.jpg"), opened)
            assertEquals(0, parentOpens)
            assertNotNull(preview?.drawable)
            assertTrue(requireNotNull(preview).isAttachedToWindow)
        }
    }

    @Test fun commentGifPreviewAndOriginalWaitForTheirOwnTapAndKeepPlaybackPreference() {
        val playGif = mutableStateOf(false)
        val opened = mutableListOf<String>()
        val images = listOf(
            CommentData.CommentImage("preview.jpg", "original.gif", "image/gif", true),
            CommentData.CommentImage("second.jpg", "second-original.jpg", "image/jpeg", false),
        )
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Box(Modifier.width(184.dp)) {
                    ComposeCommentImageGrid(images, ComposeMediaSettings(playGif = playGif.value, dataSaver = true),
                        false, opened::add, requests)
                }
            }
        }
        compose.runOnIdle { assertTrue(requests.loads.isEmpty()); assertTrue(requests.animated.isEmpty()) }
        compose.onAllNodes(hasClickAction())[0].performClick()
        compose.runOnIdle {
            assertEquals(listOf("preview.jpg"), requests.urls)
            assertTrue(opened.isEmpty())
            assertTrue(requests.animated.isEmpty())
            playGif.value = true
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf("original.gif"), requests.animated)
            assertFalse(requests.urls.contains("second.jpg"))
        }
        compose.onAllNodes(hasClickAction())[0].performClick()
        compose.runOnIdle { assertEquals(listOf("original.gif"), opened) }
    }

    @Test fun pagerDoesNotPrefetchAdjacentImagesAndEachTapOnlyReleasesItsOwnPage() {
        val urls = listOf("first.jpg", "second.jpg")
        val opened = mutableListOf<String>()
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                Box(Modifier.width(200.dp).testTag("pager")) {
                    ComposeDetailImagePager(urls, ComposeMediaSettings(dataSaver = true), opened::add, requests)
                }
            }
        }
        val pager = compose.onNodeWithTag("pager")
        pager.assertHeightIsEqualTo(170.dp)
        compose.runOnIdle { assertTrue(requests.loads.isEmpty()) }
        pager.performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf(urls[0]), requests.urls)
            assertTrue(requests.animated.isEmpty())
            assertTrue(opened.isEmpty())
        }
        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithText("2/2").assertExists()
        compose.runOnIdle { assertEquals(listOf(urls[0]), requests.urls) }
        pager.performTouchInput { click() }
        compose.runOnIdle { assertEquals(urls, requests.urls); assertTrue(opened.isEmpty()) }
        pager.performTouchInput { swipeRight() }
        pager.performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(urls[0]), opened) }
    }

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun roundPagerKeepsItsCompactFrameWithoutLoadingAnyPages() {
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(true)) {
                Box(Modifier.width(180.dp).testTag("pager")) {
                    ComposeDetailImagePager(listOf("first.gif", "second.gif"),
                        ComposeMediaSettings(dataSaver = true), {}, requests)
                }
            }
        }
        compose.onNodeWithTag("pager").assertHeightIsEqualTo(104.4.dp)
        compose.runOnIdle { assertTrue(requests.loads.isEmpty()); assertTrue(requests.animated.isEmpty()) }
    }

    @Test fun savedPermissionSurvivesRestorationButCannotReleaseAReplacementImage() {
        val source = mutableStateOf("first.jpg")
        val opened = mutableListOf<String>()
        val restore = StateRestorationTester(compose)
        restore.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeMediaImage(source.value, ComposeMediaSettings(dataSaver = true),
                    Modifier.size(80.dp).testTag("image"), requests = requests,
                    onClick = { opened += source.value })
            }
        }
        compose.onNodeWithTag("image").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("image").performClick()
        compose.runOnIdle {
            assertEquals(listOf("first.jpg"), opened)
            source.value = "replacement.jpg"
        }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(requests.urls.contains("replacement.jpg")) }
        compose.onNodeWithTag("image").performClick()
        compose.runOnIdle {
            assertEquals("replacement.jpg", requests.urls.last())
            assertEquals(listOf("first.jpg"), opened)
        }
    }

    @Test fun videoOverlayLoadsCoverBeforePlayingAndLongPressStillOnlyOpensSettings() {
        val video = VideoData.create("https://example.invalid/video.mp4",
            "https://example.invalid/cover.jpg", "Video")
        var settingsOpened = 0
        var plays = 0
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeVideoCard(video, ComposeMediaSettings(dataSaver = true),
                    onVideoSettings = { settingsOpened++ }, onOpenVideo = { plays++ }, requests = requests)
            }
        }
        val control = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        control.performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(1, settingsOpened)
            assertEquals(0, plays)
            assertTrue(requests.loads.isEmpty())
        }
        control.performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(video.cover), requests.urls); assertEquals(0, plays) }
        control.performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, plays); assertEquals(1, requests.loads.size) }
    }

    @Test fun reusedSlotCannotAuthorizeAnotherPreviewOrGifOriginal() {
        val original = mutableStateOf("first.gif")
        val opened = mutableListOf<String>()
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeMediaImage("shared-preview.jpg", ComposeMediaSettings(dataSaver = true),
                    Modifier.size(80.dp).testTag("image"), animated = true, animatedUrl = original.value,
                    requests = requests, onClick = { opened += original.value })
            }
        }
        compose.onNodeWithTag("image").performClick()
        compose.runOnIdle { original.value = "second.gif" }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, requests.loads.size)
            assertEquals(listOf("first.gif"), requests.animated)
        }
        compose.onNodeWithTag("image").performClick()
        compose.runOnIdle {
            assertEquals(2, requests.loads.size)
            assertEquals(listOf("first.gif", "second.gif"), requests.animated)
            assertTrue(opened.isEmpty())
        }
    }

    @Test fun enablingSaverCancelsAnAutomaticLoadAndRejectsItsLateGifCompletion() {
        requests.immediate = false
        val saver = mutableStateOf(false)
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeMediaImage("pending.gif", ComposeMediaSettings(dataSaver = saver.value),
                    Modifier.size(80.dp).testTag("image"), animated = true, requests = requests)
            }
        }
        compose.runOnIdle { saver.value = true }
        compose.waitForIdle()
        compose.runOnIdle {
            val old = requests.loads.single()
            assertTrue(old.cancelled)
            old.complete(true, requests.bitmap)
            assertTrue(requests.animated.isEmpty())
        }
        compose.onNodeWithTag("image").performClick()
        compose.runOnIdle { assertEquals(2, requests.loads.size) }
    }

    @Test fun noImageAndEmptySourcesDoNotGainRequestsFromSaverTaps() {
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeMediaImage("hidden.gif", ComposeMediaSettings(enabled = false, dataSaver = true),
                    Modifier.size(80.dp).testTag("hidden"), animated = true, requests = requests)
                ComposeMediaImage("", ComposeMediaSettings(dataSaver = true),
                    Modifier.size(80.dp).testTag("empty"), requests = requests)
            }
        }
        compose.onNodeWithTag("hidden").performTouchInput { click() }
        compose.onNodeWithTag("empty").performTouchInput { click() }
        compose.runOnIdle { assertTrue(requests.loads.isEmpty()); assertTrue(requests.animated.isEmpty()) }
    }

    private data class Load(val url: String, val view: ImageView,
        val complete: (Boolean, Bitmap?) -> Unit, var cancelled: Boolean = false)

    private class Images : ComposeMediaImageRequests {
        val loads = mutableListOf<Load>()
        val urls get() = loads.map { it.url }
        val animated = mutableListOf<String>()
        var immediate = true
        val bitmap = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        override fun load(view: ImageView, url: String, targetPx: Int, complete: (Boolean, Bitmap?) -> Unit) {
            loads += Load(url, view, complete)
            if (immediate) { view.setImageBitmap(bitmap); complete(true, bitmap) }
        }
        override fun animate(view: ImageView, url: String) { animated += url }
        override fun cancel(view: ImageView) { loads.filter { it.view === view }.forEach { it.cancelled = true } }
    }
}
