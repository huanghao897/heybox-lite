package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.json.JSONObject
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
class ComposeDataSaverFeedUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val loader = Images()

    @Test fun evenAMemoryCachedImageIsNotReadOrRenderedUntilTapped() {
        loader.cachedBitmap = loader.bitmap
        val theme = mutableStateOf(composePreviewTheme(false))
        compose.setContent {
            ComposeRemoteImage("cached.jpg", theme.value, false, 64.dp, RoundedCornerShape(4.dp),
                "content", Modifier.size(64.dp).testTag("image"), loader, dataSaver = true)
        }
        compose.runOnIdle { assertTrue(loader.lookups.isEmpty()); assertTrue(loader.urls.isEmpty()) }
        assertFalse(pixels().contains(android.graphics.Color.GREEN))
        val bounds = compose.onNodeWithTag("image").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("image").performClick()
        compose.runOnIdle {
            assertEquals(listOf("cached.jpg"), loader.lookups)
            assertTrue(loader.urls.isEmpty())
            theme.value = theme.value.copy(muted = Color.Gray)
        }
        compose.waitForIdle()
        assertTrue(pixels().contains(android.graphics.Color.GREEN))
        assertEquals(bounds, compose.onNodeWithTag("image").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(1, loader.lookups.size); assertTrue(loader.urls.isEmpty()) }
    }

    @Test fun feedAndGameCoverTapsLoadWithoutOpeningTheirParentWhileAvatarRemainsAutomatic() {
        val theme = mutableStateOf(composePreviewTheme(false))
        var opened = 0
        compose.setContent {
            CompositionLocalProvider(LocalComposeDataSaver provides true) {
                HeyboxComposeTheme(theme.value) {
                    ComposeFeedCard(post(), theme.value, false, { opened++ }, null,
                        imageLoader = loader, showActions = false)
                }
            }
        }
        compose.runOnIdle {
            assertEquals(listOf("avatar"), loader.urls)
            assertEquals(listOf("avatar"), loader.lookups)
        }
        val waiting = "\u52a0\u8f7d\u56fe\u7247"
        compose.onAllNodesWithContentDescription(waiting)[0].performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf("avatar", "content"), loader.urls)
            assertEquals(0, opened)
            theme.value = theme.value.copy(muted = Color.Gray)
        }
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription(waiting)[0].performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf("avatar", "content", "game"), loader.urls)
            assertEquals(0, opened)
        }
        // The loaded content image still forwards a subsequent touch to its post.
        compose.onNodeWithContentDescription("\u5185\u5bb9\u56fe\u7247").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, opened); assertEquals(3, loader.urls.size) }
    }

    @Test fun lazyReuseKeepsTappedImagesAllowedWithoutReleasingUntappedOrChangedSources() {
        val firstSource = mutableStateOf("image-0")
        val theme = composePreviewTheme(false)
        compose.setContent {
            LazyColumn(Modifier.height(180.dp).testTag("list")) {
                items((0 until 16).toList(), key = { it }, contentType = { "image" }) { index ->
                    Column(Modifier.height(100.dp)) {
                        ComposeRemoteImage(if (index == 0) firstSource.value else "image-$index", theme,
                            false, 64.dp, RoundedCornerShape(4.dp), "image $index",
                            Modifier.size(64.dp).testTag("image-$index"), loader, dataSaver = true)
                    }
                }
            }
        }
        compose.runOnIdle { assertTrue(loader.urls.isEmpty()); assertTrue(loader.lookups.isEmpty()) }
        compose.onNodeWithTag("image-0").performClick()
        for (index in 1..10) compose.onNodeWithTag("list").performScrollToIndex(index)
        compose.runOnIdle { assertEquals(listOf("image-0"), loader.urls) }
        for (index in 9 downTo 0) compose.onNodeWithTag("list").performScrollToIndex(index)
        compose.runOnIdle {
            assertEquals(listOf("image-0", "image-0"), loader.urls)
            firstSource.value = "replacement"
        }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(loader.lookups.contains("replacement")) }
        compose.onNodeWithTag("image-0").performClick()
        compose.runOnIdle { assertEquals(listOf("image-0", "image-0", "replacement"), loader.urls) }
    }

    @Test fun existingNoImageAndGameCardNoImageKeepTheirOwnBehavior() {
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeFeedCard(post(), composePreviewTheme(false), false, {}, null,
                    gameCardNoImage = true, dataSaver = true, imageLoader = loader, showActions = false)
            }
        }
        compose.runOnIdle { assertEquals(listOf("avatar"), loader.urls) }
        compose.onAllNodesWithContentDescription("\u52a0\u8f7d\u56fe\u7247")[0].performClick()
        compose.runOnIdle {
            assertEquals(listOf("avatar", "content"), loader.urls)
            assertFalse(loader.lookups.contains("game"))
        }
    }

    @Test fun noImageStillSuppressesFeedAvatarsAndContentEvenWithSaverEnabled() {
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeFeedCard(post(), composePreviewTheme(false), true, {}, null,
                    dataSaver = true, imageLoader = loader, showActions = false)
            }
        }
        compose.runOnIdle { assertTrue(loader.lookups.isEmpty()); assertTrue(loader.urls.isEmpty()) }
    }

    @Test fun officialInlineEmojiIgnoresTheContentImageGate() {
        val catalog = ComposeInlineTextTestCatalog().apply { register("cube_wave", "official-emoji") }
        compose.setContent {
            CompositionLocalProvider(LocalComposeDataSaver provides true) {
                ComposeInlineText("Hello [cube_wave]", false, Color.White, Color.Blue,
                    imageLoader = loader, emojiCatalog = catalog)
            }
        }
        compose.runOnIdle { assertEquals(listOf("official-emoji"), loader.urls) }
    }

    private fun post() = FeedItem.from(JSONObject().put("linkid", "post").put("title", "Title")
        .put("image", "content").put("user", JSONObject().put("username", "Author")
            .put("userid", "author").put("avatar", "avatar"))
        .put("communityPostPreload", JSONObject().put("game", JSONObject().put("appid", "42")
            .put("name", "Game").put("image", "game"))))

    private fun pixels(): IntArray = compose.runOnIdle {
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
        }
    }

    private class Images : ComposeImageLoader {
        val lookups = mutableListOf<String>()
        val urls = mutableListOf<String>()
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.GREEN)
        }
        var cachedBitmap: Bitmap? = null
        override fun cached(sourceUrl: String, targetPx: Int): Bitmap? {
            lookups += sourceUrl
            return cachedBitmap
        }
        override fun load(sourceUrl: String, targetPx: Int, callback: (Bitmap?) -> Unit) {
            urls += sourceUrl
            callback(bitmap)
        }
    }
}
