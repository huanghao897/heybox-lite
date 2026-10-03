package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeFeedImageUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val loader = DeferredImages()
    private val red = image(android.graphics.Color.RED)
    private val blue = image(android.graphics.Color.BLUE)

    @Before fun prepareRenderer() { EmojiRenderer.clear() }
    @After fun clearRenderer() { EmojiRenderer.clear() }

    @Test fun sourceSizeAndNoImageChangesCannotShowThePreviousBitmapOrAcceptItsCallback() {
        val source = mutableStateOf("first-source")
        val target = mutableStateOf(64.dp)
        val noImage = mutableStateOf(false)
        val theme = composePreviewTheme(false)
        compose.setContent {
            LazyColumn(Modifier.fillMaxWidth().height(240.dp)) {
                item(key = "stable-row") {
                    Column(Modifier.fillMaxWidth().height(128.dp)) {
                        ComposeRemoteImage(source.value, theme, noImage.value, target.value,
                            RoundedCornerShape(4.dp), "image", loader = loader)
                    }
                }
            }
        }
        compose.waitForIdle()
        val first = loader.requests.single()
        assertEquals("first-source", first.source)
        assertEquals(64, first.targetPx)
        compose.runOnIdle { first.callback(red) }
        assertContains(android.graphics.Color.RED)
        compose.runOnIdle { source.value = "second-source" }
        compose.waitForIdle()
        val second = loader.requests.last()
        assertEquals("second-source", second.source)
        assertMissing(android.graphics.Color.RED)
        compose.runOnIdle { first.callback(red) }
        assertMissing(android.graphics.Color.RED)
        compose.runOnIdle { second.callback(blue) }
        assertContains(android.graphics.Color.BLUE)
        compose.runOnIdle { first.callback(red) }
        assertContains(android.graphics.Color.BLUE)
        assertMissing(android.graphics.Color.RED)
        compose.runOnIdle { target.value = 32.dp }
        compose.waitForIdle()
        val resized = loader.requests.last()
        assertEquals("second-source", resized.source)
        assertEquals(32, resized.targetPx)
        assertMissing(android.graphics.Color.BLUE)
        compose.runOnIdle { second.callback(blue) }
        assertMissing(android.graphics.Color.BLUE)
        compose.runOnIdle { resized.callback(red) }
        assertContains(android.graphics.Color.RED)
        val count = loader.requests.size
        compose.runOnIdle { noImage.value = true }
        assertMissing(android.graphics.Color.RED)
        compose.runOnIdle { resized.callback(red) }
        assertMissing(android.graphics.Color.RED)
        assertEquals(count, loader.requests.size)
        compose.runOnIdle { source.value = ""; noImage.value = false }
        assertEquals(count, loader.requests.size)
        assertMissing(android.graphics.Color.RED)
    }

    @Test fun scrollingBackToTheSameSourceStartsAFreshImageBindingAndRejectsTheOldResult() {
        val theme = composePreviewTheme(false)
        compose.setContent {
            LazyColumn(Modifier.fillMaxWidth().height(240.dp).testTag("image-list")) {
                items((0 until 20).toList(), key = { it }, contentType = { "image-row" }) { index ->
                    Column(Modifier.fillMaxWidth().height(120.dp)) {
                        ComposeRemoteImage("image-$index", theme, false, 64.dp,
                            RoundedCornerShape(4.dp), "image $index", loader = loader)
                    }
                }
            }
        }
        compose.waitForIdle()
        val old = loader.requests.first { it.source == "image-0" }
        compose.runOnIdle { old.callback(red) }
        assertContains(android.graphics.Color.RED)
        for (index in 1..12) compose.onNodeWithTag("image-list").performScrollToIndex(index)
        compose.runOnIdle { old.callback(red) }
        assertMissing(android.graphics.Color.RED)
        for (index in 11 downTo 0) compose.onNodeWithTag("image-list").performScrollToIndex(index)
        val current = loader.requests.last { it.source == "image-0" }
        assertNotSame(old, current)
        compose.runOnIdle { old.callback(red) }
        assertMissing(android.graphics.Color.RED)
        compose.runOnIdle { current.callback(blue) }
        assertContains(android.graphics.Color.BLUE)
        compose.runOnIdle { old.callback(red) }
        assertContains(android.graphics.Color.BLUE)
        assertMissing(android.graphics.Color.RED)
    }

    @Test fun reusedFeedCardKeepsEachImageSourceAndRequestsOnlyItsScaledDisplaySize() {
        val current = mutableStateOf(post("first"))
        val theme = mutableStateOf(composePreviewTheme(true))
        compose.setContent {
            HeyboxComposeTheme(theme.value) {
                LazyColumn(Modifier.fillMaxWidth().height(300.dp)) {
                    item(key = "same-post") {
                        ComposeFeedCard(current.value, theme.value, false, "", {}, null,
                            imageLoader = loader)
                    }
                }
            }
        }
        compose.waitForIdle()
        val first = loader.requests.toList()
        assertRequests("first", theme.value.uiScale, first)
        compose.runOnIdle { first.forEach { it.callback(red) } }
        assertContains(android.graphics.Color.RED)
        compose.runOnIdle {
            current.value = post("second")
            theme.value = theme.value.copy(uiScale = 0.7f)
        }
        compose.waitForIdle()
        val second = loader.requests.drop(first.size)
        assertRequests("second", 0.7f, second)
        assertMissing(android.graphics.Color.RED)
        compose.runOnIdle { first.forEach { it.callback(red) } }
        assertMissing(android.graphics.Color.RED)
        compose.runOnIdle { second.forEach { it.callback(blue) } }
        assertContains(android.graphics.Color.BLUE)
        compose.runOnIdle { first.forEach { it.callback(red) } }
        assertContains(android.graphics.Color.BLUE)
        assertMissing(android.graphics.Color.RED)
    }

    private fun assertRequests(prefix: String, scale: Float, requests: List<Request>) {
        assertEquals(4, requests.size)
        assertEquals(mapOf("$prefix-avatar" to (26 * scale).toInt(),
            "$prefix-content" to (68 * scale).toInt(),
            "$prefix-topic" to (13 * scale).toInt(),
            "$prefix-game" to (42 * scale).toInt()), requests.associate { it.source to it.targetPx })
    }

    private fun post(prefix: String) = FeedItem.from(
        JSONObject().put("linkid", "same-post-id").put("title", "Title")
            .put("description", "Summary").put("image", "$prefix-content")
            .put("topic_name", "Topic").put("topic_icon", "$prefix-topic")
            .put("user", JSONObject().put("username", "Author").put("userid", "author")
                .put("avatar", "$prefix-avatar"))
            .put("communityPostPreload", JSONObject().put("game", JSONObject()
                .put("appid", "42").put("name", "Game").put("image", "$prefix-game"))),
    )

    private fun assertContains(color: Int) {
        assertTrue("The current image must render pixels of $color", pixels().any { it == color })
    }

    private fun assertMissing(color: Int) {
        assertFalse("A previous source must not remain on screen", pixels().any { it == color })
    }

    private fun pixels(): IntArray = compose.runOnIdle {
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
        }
    }

    private fun image(color: Int) = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(color) }

    private data class Request(val source: String, val targetPx: Int, val callback: (Bitmap?) -> Unit)

    private class DeferredImages : ComposeImageLoader {
        val requests = mutableListOf<Request>()
        override fun load(sourceUrl: String, targetPx: Int, callback: (Bitmap?) -> Unit) {
            requests += Request(sourceUrl, targetPx, callback)
        }
    }
}
