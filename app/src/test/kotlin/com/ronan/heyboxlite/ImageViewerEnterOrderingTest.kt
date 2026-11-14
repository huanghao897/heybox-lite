package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ImageViewerEnterOrderingTest {
    @Test fun preparedPreviewIsNotUpgradedBeforeActualEnterCompletion() {
        val requests = ViewerTestImages()
        val preview = viewerBitmap()
        val state = ComposeViewerPageState("https://viewer.invalid/one.jpg", 480, preview, requests)
        state.start()
        assertSame(preview, state.bitmap)
        assertTrue(requests.images.isEmpty())
        state.enterCompleted(false)
        assertEquals(1, requests.images.size)
        assertFalse(requests.images.single().original)
        state.close()
    }

    @Test fun closeDuringEnterNeverStartsDeferredUpgrade() {
        val requests = ViewerTestImages()
        val state = ComposeViewerPageState("https://viewer.invalid/one.jpg", 480, viewerBitmap(), requests)
        state.start()
        state.close()
        state.enterCompleted(true)
        assertTrue(requests.images.isEmpty())
        assertNull(state.bitmap)
    }

    @Test fun longSourceCannotReplaceThePreviewWhileItIsEntering() {
        val requests = ViewerTestImages()
        val state = ComposeViewerPageState("https://viewer.invalid/long.png", 480, viewerBitmap(64, 800), requests)
        state.start()
        assertTrue(requests.regions.isEmpty())
        state.enterCompleted(false)
        assertEquals(1, requests.regions.size)
        state.close()
    }

    @Test fun thirdDoubleTapFitsAndBitmapUpgradeDoesNotResetZoom() {
        val transform = ComposeViewerTransform()
        transform.layout(Size(240f, 320f), Size(300f, 600f), false)
        transform.doubleTap(Offset(120f, 160f))
        assertTrue(transform.zoom >= ImageZoomPolicy.MIN_DOUBLE_TAP_ZOOM)
        val zoom = transform.zoom
        transform.layout(Size(240f, 320f), Size(600f, 1200f), false)
        assertEquals(zoom, transform.zoom, 0f)
        transform.doubleTap(Offset(120f, 160f))
        assertTrue(transform.zoom >= 4f)
        transform.doubleTap(Offset(120f, 160f))
        assertEquals(1f, transform.zoom, 0f)
        assertEquals(Offset.Zero, transform.pan)
    }

    @Test fun roundZoomCanBringImageCornersIntoCentralVisibleArea() {
        val transform = ComposeViewerTransform()
        transform.layout(Size(227f, 227f), Size(800f, 1600f), true)
        transform.pinch(1.3f, Offset(113f, 113f), Offset.Zero)
        transform.move(Offset(10000f, -10000f))
        assertTrue(transform.pan.x > 0f)
        assertTrue(transform.pan.y < 0f)
        assertTrue(transform.pan.x < 1000f)
        transform.reset()
        assertEquals(Offset.Zero, transform.pan)
    }

    @Test fun regionSampleKeepsAtLeastDisplayResolutionUnlessMemoryBudgetRequiresLess() {
        assertEquals(2, ImageDecodePolicy.sample(1000, 500, 480, 1024 * 1024))
        val limited = ImageDecodePolicy.sample(1000, 500, 480, 40 * 1024)
        assertTrue(ImageDecodePolicy.bytes(1000, 500, limited) <= 40 * 1024)
    }
}
