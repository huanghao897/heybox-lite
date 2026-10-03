package com.ronan.heyboxlite

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
class ComposeRichTextReuseUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val created = mutableListOf<ComposeRichTextView>()
    private val factory: (Context) -> ComposeRichTextView = { context ->
        ComposeRichTextView(context).also { created += it }
    }
    private val visible = mutableStateOf(true)
    private lateinit var state: LazyListState

    @Before fun prepareRenderer() { EmojiRenderer.clear() }
    @After fun clearRenderer() { EmojiRenderer.clear() }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun continuousLazyScrollReusesTheTwoTextViewsAndKeepsSourcesMetricsAndPosition() {
        showList()
        val seen = mutableMapOf<ComposeRichTextView, MutableSet<String>>()
        fun verifyRow(index: Int) {
            compose.runOnIdle {
                val title = richView("Title $index")
                val summary = richView("Summary $index")
                assertEquals("Title $index", title.text.toString())
                assertEquals("Summary $index", summary.text.toString())
                assertEquals(if (index % 2 == 0) 14f else 16f, title.textSize, 0.01f)
                assertEquals(10f, summary.textSize, 0.01f)
                assertEquals(2, title.maxLines)
                assertEquals(1, summary.maxLines)
                assertEquals(TextUtils.TruncateAt.END, title.ellipsize)
                for (view in richViews(compose.activity.window.decorView)) {
                    val source = view.tag as? String ?: continue
                    assertEquals(source, view.text.toString())
                    seen.getOrPut(view) { mutableSetOf() }.add(source)
                }
            }
        }
        verifyRow(0)
        for (index in 1..30) {
            compose.onNodeWithTag("rich-list").performScrollToIndex(index)
            verifyRow(index)
        }
        for (index in 29 downTo 0) {
            compose.onNodeWithTag("rich-list").performScrollToIndex(index)
            verifyRow(index)
        }
        compose.runOnIdle {
            // Without onReset, visiting 31 rows alone creates at least 62 TextViews.
            assertTrue("Expected a viewport-sized pool, created ${created.size} TextViews", created.size < 31)
            assertTrue("At least one View must be rebound to another row", seen.values.any { it.size > 2 })
            assertEquals(0, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
            val root = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val title = richView("Title 0")
            val location = IntArray(2).also { title.getLocationOnScreen(it) }
            val origin = IntArray(2).also { root.getLocationOnScreen(it) }
            val pixels = IntArray(title.width * title.height)
            bitmap.getPixels(pixels, 0, title.width, location[0] - origin[0], location[1] - origin[1],
                title.width, title.height)
            assertTrue("Reused TextViews must draw visible text", pixels.any { it == android.graphics.Color.WHITE })
            bitmap.recycle()
            visible.value = false
        }
        compose.runOnIdle {
            assertTrue(created.all { it.tag == null && it.text.isEmpty() })
        }
    }

    @Test fun queuedEmojiCallbacksCannotOverwriteAPooledOrReboundLazyView() {
        val loading = EmojiStore::class.java.getDeclaredField("loading").apply { isAccessible = true }
        val loaded = EmojiStore::class.java.getDeclaredField("catalogLoaded").apply { isAccessible = true }
        val ready = EmojiStore::class.java.getDeclaredField("READY").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val callbacks = ready.get(null) as MutableList<Runnable>
        val priorLoading = loading.getBoolean(null)
        val priorLoaded = loaded.getBoolean(null)
        val priorCallbacks = callbacks.toList()
        try {
            loading.setBoolean(null, true)
            loaded.setBoolean(null, false)
            callbacks.clear()
            showList(suffix = " [compose_pending_reuse_937]")
            compose.waitForIdle()
            val oldView = compose.runOnIdle { richView("Title 0 [compose_pending_reuse_937]") }
            val oldCallbacks = callbacks.toList()
            assertTrue(oldCallbacks.isNotEmpty())
            for (index in 1..12) compose.onNodeWithTag("rich-list").performScrollToIndex(index)
            compose.runOnIdle {
                val tag = oldView.tag
                val text = oldView.text
                assertNotEquals("Title 0 [compose_pending_reuse_937]", tag)
                oldCallbacks.forEach { it.run() }
                assertEquals(tag, oldView.tag)
                assertSame(text, oldView.text)
                for (view in richViews(compose.activity.window.decorView)) {
                    val source = view.tag as? String ?: continue
                    assertEquals(source, view.text.toString())
                }
                visible.value = false
            }
            compose.runOnIdle {
                val texts = created.map { it.text }
                callbacks.toList().forEach { it.run() }
                created.forEachIndexed { index, view ->
                    assertNull(view.tag)
                    assertSame(texts[index], view.text)
                    assertEquals("", view.text.toString())
                }
            }
        } finally {
            loading.setBoolean(null, priorLoading)
            loaded.setBoolean(null, priorLoaded)
            callbacks.clear()
            callbacks.addAll(priorCallbacks)
        }
    }

    private fun showList(suffix: String = "") {
        compose.setContent {
            if (visible.value) {
                state = rememberLazyListState()
                LazyColumn(Modifier.fillMaxWidth().height(240.dp).background(Color.Black)
                    .testTag("rich-list"), state = state) {
                    items((0 until 40).toList(), key = { it }, contentType = { "rich-row" }) { index ->
                        Column(Modifier.fillMaxWidth().height(96.dp)) {
                            ComposeRichText("Title $index$suffix", index % 2 != 0, Color.White,
                                Color.Blue, fontSize = (if (index % 2 == 0) 14 else 16).sp,
                                lineHeight = 20.sp, fontWeight = FontWeight.Bold,
                                maxLines = 2, ellipsize = TextUtils.TruncateAt.END, viewFactory = factory)
                            ComposeRichText("Summary $index$suffix", false, Color.White, Color.Blue,
                                fontSize = 10.sp, maxLines = 1, viewFactory = factory)
                        }
                    }
                }
            } else Box(Modifier.fillMaxWidth().height(240.dp))
        }
    }

    private fun richView(source: String) =
        richViews(compose.activity.window.decorView).single { it.tag == source }

    private fun richViews(root: View): List<ComposeRichTextView> = when (root) {
        is ComposeRichTextView -> listOf(root)
        is ViewGroup -> (0 until root.childCount).flatMap { richViews(root.getChildAt(it)) }
        else -> emptyList()
    }
}
