package com.ronan.heyboxlite

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-notround-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeViewerLongImageUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val files = TemporaryFolder()
    private var previousDirectory: File? = null
    private var previousContext: Context? = null
    private var source: LongImageSource? = null
    private val transform = ComposeViewerTransform().apply {
        layout(Size(240f, 320f), Size(240f, 320f), false)
    }

    @Before fun imageFile() {
        previousDirectory = field(ImageDiskCache::class.java, "directory").get(null) as File?
        previousContext = field(ImageLoader::class.java, "appContext").get(null) as Context?
        ImageLoader.init(compose.activity)
        field(ImageDiskCache::class.java, "directory").set(null, files.newFolder("images"))
        val image = viewerBitmap(16, 18000)
        val pixels = IntArray(image.width * image.height) { index ->
            val y = index / image.width
            Color.rgb(index % image.width * 17, y % 251, y / 251)
        }
        image.setPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val output = ByteArrayOutputStream()
        assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output))
        image.recycle()
        ImageDiskCache.write(URL, output.toByteArray())
        source = LongImageSource.open(URL, files.root)
        assertNotNull(source)
    }

    @After fun cleanup() {
        compose.activity.setContentView(android.view.View(compose.activity))
        source?.close()
        shadowOf(Looper.getMainLooper()).idle()
        field(ImageDiskCache::class.java, "directory").set(null, previousDirectory)
        field(ImageLoader::class.java, "appContext").set(null, previousContext)
    }

    @Test fun extremeLongImageUsesFixedViewportAndCanZoomBeyondComposeHeightLimit() {
        var ready = false
        var failed = false
        var active by mutableStateOf(true)
        compose.setContent {
            ComposeViewerLongImage(source!!, active, false, transform,
                { ready = true }, { failed = true }, {})
        }
        await { ready || failed }
        assertFalse(failed)
        assertEquals(320f, compose.onNodeWithTag("viewer-long-image")
            .fetchSemanticsNode().boundsInRoot.height, 1f)
        var initialCenter = 0
        compose.runOnIdle { initialCenter = renderedCenter() }
        compose.onNodeWithTag("viewer-long-image").performTouchInput { doubleClick(center) }
        compose.runOnIdle { assertTrue(transform.zoom > 2f) }
        compose.onNodeWithTag("viewer-long-image").performTouchInput { swipeUp() }
        compose.runOnIdle { active = false }
        compose.runOnIdle { active = true }
        await { !failed && ImageTileBudget.SHARED.used() > 0 }
        await { renderedCenter() != initialCenter }
        compose.runOnIdle { captureMembershipUi(compose.activity, "compose-viewer-long240") }
    }

    @Test fun cancelledTileViewDoesNotCloseTheSourceOwnedByItsPage() {
        var visible by mutableStateOf(true)
        var ready = false
        compose.setContent {
            if (visible) ComposeViewerLongImage(source!!, true, false, transform,
                { ready = true }, { fail("Valid fixture must decode") }, {})
        }
        await { ready }
        compose.runOnIdle { visible = false }
        compose.waitForIdle()
        val region = source!!.decode(android.graphics.Rect(0, 0, 16, 100), 1)
        assertNotNull(region)
        region.recycle()
    }

    @Test fun zoomKeepsDecodedPixelsOnSquareViewportWhileDecoderIsBusy() {
        zoomKeepsDecodedPixels(false)
    }

    @Test
    @Config(qualifiers = "w240dp-h240dp-round-mdpi")
    fun zoomKeepsDecodedPixelsOnRoundViewportWhileDecoderIsBusy() {
        transform.layout(Size(240f, 240f), Size(240f, 240f), true)
        zoomKeepsDecodedPixels(true)
    }

    private fun zoomKeepsDecodedPixels(round: Boolean) {
        var ready = false
        compose.setContent {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Magenta)) {
                ComposeViewerLongImage(source!!, true, round, transform,
                    { ready = true }, { fail("Valid fixture must decode") }, {})
            }
        }
        await { ready }
        val decoder = field(LongImageTileView::class.java, "DECODER").get(null) as ImageTaskExecutor
        val blocked = CountDownLatch(1)
        val release = CountDownLatch(1)
        decoder.execute { blocked.countDown(); release.await(30, TimeUnit.SECONDS) }
        try {
            assertTrue(blocked.await(5, TimeUnit.SECONDS))
            repeat(8) {
                compose.runOnIdle {
                    transform.pinch(1.1f, Offset(120f, if (round) 120f else 160f), Offset.Zero)
                }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertNotEquals("Zoom must keep drawing tiles before new decodes finish",
                        Color.MAGENTA, renderedCenter())
                }
            }
        } finally {
            release.countDown()
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(5)
        }
        assertTrue("Region tiles must settle", condition())
    }

    private fun field(type: Class<*>, name: String) = type.getDeclaredField(name).apply { isAccessible = true }

    private fun renderedCenter(): Int {
        val view = compose.activity.window.decorView
        val image = viewerBitmap(view.width, view.height)
        return try {
            view.draw(Canvas(image))
            image.getPixel(image.width / 2, image.height / 2)
        } finally {
            image.recycle()
        }
    }

    private companion object { const val URL = "https://long.invalid/compose-fixture.png" }
}
