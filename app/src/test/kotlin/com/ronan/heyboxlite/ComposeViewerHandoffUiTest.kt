package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-notround-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeViewerHandoffUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val files = TemporaryFolder()
    private val sources = mutableListOf<LongImageSource>()

    @After fun cleanup() {
        compose.activity.setContentView(View(compose.activity))
        sources.forEach { it.close() }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun originalReplacementKeepsTheExistingPreviewTilesUntilTheNewViewIsReady() {
        val requests = ViewerTestImages()
        val selected = ComposeViewerSelection()
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeImageViewer(listOf("https://viewer.invalid/long.png"), 0,
                    viewerBitmap(64, 800), false, true, false, true, MotionLevel.OFF,
                    selected, {}, {}, requests = requests)
            }
        }
        compose.waitForIdle()
        val preview = source(Color.GREEN)
        compose.runOnIdle {
            requests.images.single().complete(viewerBitmap(64, 800))
            requests.regions.single().complete(preview)
        }
        await { tileViews().singleOrNull()?.let { it.residentCount() > 0 && !it.pendingTile() } == true }
        compose.runOnIdle { assertEquals(Color.GREEN, renderedCenter()) }
        lateinit var existing: LongImageTileView
        compose.runOnIdle { existing = tileViews().single() }
        val decoder = LongImageTileView::class.java.getDeclaredField("DECODER").apply { isAccessible = true }
            .get(null) as ImageTaskExecutor
        val blocked = CountDownLatch(1)
        val release = CountDownLatch(1)
        decoder.execute { blocked.countDown(); release.await(60, TimeUnit.SECONDS) }
        assertTrue(blocked.await(5, TimeUnit.SECONDS))
        val original = source(Color.BLUE)
        try {
            compose.runOnIdle { selected.page!!.loadOriginal() }
            compose.runOnIdle { requests.images.last().complete(viewerBitmap(128, 1600)) }
            compose.runOnIdle { requests.regions.last().complete(original) }
            compose.runOnIdle {
                assertTrue("A source becoming retained must keep its actual tile View",
                    tileViews().any { it === existing })
                assertTrue("Retained decoded tiles must not be discarded", existing.residentCount() > 0)
                assertEquals("The rendered preview must remain visible while replacement decoding waits",
                    Color.GREEN, renderedCenter())
                assertSame(preview, selected.page!!.retainedLongSource)
            }
        } finally { release.countDown() }
        await { selected.page?.retainedLongSource == null }
        compose.runOnIdle {
            assertFalse(tileViews().any { it === existing })
            assertEquals(Color.BLUE, renderedCenter())
            assertNull(preview.decode(Rect(0, 0, 16, 32), 1))
        }
    }

    @Test fun viewerEnteredWhileInactiveStartsDeferredRequestsOnResume() {
        val active = mutableStateOf(false)
        val requests = ViewerTestImages()
        val selected = ComposeViewerSelection()
        val preview = viewerBitmap(64, 800)
        compose.setContent {
            HeyboxComposeTheme(composePreviewTheme(false)) {
                ComposeImageViewer(listOf("https://viewer.invalid/long.png"), 0,
                    preview, false, true, false, active.value, MotionLevel.OFF,
                    selected, {}, {}, requests = requests)
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(requests.regions.isEmpty()); active.value = true }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, requests.regions.size) }
    }

    private fun source(color: Int): LongImageSource {
        val file = files.newFile()
        val image = viewerBitmap(64, 800).apply { eraseColor(color) }
        file.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
        val decoder = android.graphics.BitmapRegionDecoder.newInstance(file.absolutePath, false)!!
        val lease = StorageFileLeases.acquire(file)
        val constructor = LongImageSource::class.java.getDeclaredConstructor(
            android.graphics.BitmapRegionDecoder::class.java, java.io.File::class.java,
            StorageFileLeases.Lease::class.java).apply { isAccessible = true }
        return constructor.newInstance(decoder, file, lease).also(sources::add)
    }

    private fun tileViews(): List<LongImageTileView> {
        val found = mutableListOf<LongImageTileView>()
        fun visit(view: View) {
            if (view is LongImageTileView) found.add(view)
            if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
        }
        visit(compose.activity.window.decorView)
        return found
    }

    private fun renderedCenter(): Int {
        val view = compose.activity.window.decorView
        val image = viewerBitmap(view.width, view.height)
        return try {
            view.draw(Canvas(image))
            image.getPixel(image.width / 2, image.height / 2)
        } finally { image.recycle() }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            compose.waitForIdle()
            if (compose.runOnIdle(condition)) return
            Thread.sleep(5)
        }
        assertTrue("Viewer tile handoff must settle", compose.runOnIdle(condition))
    }
}
