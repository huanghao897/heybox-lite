package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Rect
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageViewerRequestLifecycleTest {
    @get:Rule val files = TemporaryFolder()
    private val sources = mutableListOf<LongImageSource>()

    @After fun closeSources() { sources.forEach { it.close() } }

    @Test fun enteringRepeatedlyDoesNotDuplicatePendingOriginal() {
        val images = ViewerTestImages()
        val page = ComposeViewerPageState("https://viewer.invalid/one.jpg", 480, viewerBitmap(), images)
        page.enterCompleted(true)
        page.enterCompleted(true)
        assertEquals(1, images.images.size)
        assertTrue(images.images.single().original)
        page.close()
    }

    @Test fun entryCompletedWhilePausedDefersLongImageSetupUntilResume() {
        val images = ViewerTestImages()
        val page = ComposeViewerPageState("https://viewer.invalid/long.png", 480,
            viewerBitmap(64, 800), images)
        page.setActive(false)
        page.enterCompleted(false)
        assertTrue(images.images.isEmpty())
        assertTrue(images.regions.isEmpty())
        page.setActive(true)
        assertEquals(1, images.regions.size)
        assertFalse(images.regions.single().original)
        page.close()
    }

    @Test fun entryCompletedWhilePausedRetainsOriginalAndGifPreferences() {
        val images = ViewerTestImages()
        val page = ComposeViewerPageState("https://viewer.invalid/one.gif", 480, viewerBitmap(), images)
        page.setActive(false)
        page.enterCompleted(true, false)
        assertTrue(images.images.isEmpty())
        page.setActive(true)
        assertTrue(images.images.single().original)
        assertTrue(images.gifs.isEmpty())
        page.enterCompleted(true, true)
        assertEquals(listOf("https://viewer.invalid/one.gif"), images.gifs)
        page.close()
    }

    @Test fun disposedPageCancelsRequestAndRejectsAlreadyDispatchedCallback() {
        val images = ViewerTestImages()
        val page = ComposeViewerPageState("https://viewer.invalid/one.jpg", 480, requests = images)
        page.start()
        page.close()
        val request = images.images.single()
        assertTrue(request.cancelled)
        request.complete(viewerBitmap())
        assertNull(page.bitmap)
        assertFalse(page.loading)
    }

    @Test fun pauseCancelsOpeningLongSourceAndResumeRetriesWithoutAcceptingOldFailure() {
        val images = ViewerTestImages()
        val page = ComposeViewerPageState("https://viewer.invalid/long.png", 480, requests = images)
        page.start()
        images.images.single().complete(viewerBitmap(64, 800))
        page.enterCompleted(false)
        assertEquals(1, images.regions.size)
        assertFalse(images.regions.single().original)
        page.setActive(false)
        assertTrue(images.regions[0].cancelled)
        page.setActive(true)
        assertEquals(2, images.regions.size)
        images.regions[0].callback(null)
        assertFalse(page.longFailed)
        images.regions[1].callback(null)
        assertTrue(page.longFailed)
        assertNotNull(page.bitmap)
        page.close()
    }

    @Test fun cancelledPreviewResumesAndOldResultCannotOverwriteNewRequest() {
        val images = ViewerTestImages()
        val page = ComposeViewerPageState("https://viewer.invalid/one.jpg", 480, requests = images)
        page.start()
        page.setActive(false)
        page.setActive(true)
        assertEquals(2, images.images.size)
        val expected = viewerBitmap(200, 200)
        images.images[1].complete(expected)
        images.images[0].complete(viewerBitmap())
        assertSame(expected, page.bitmap)
        page.close()
    }

    @Test fun failedOriginalKeepsExistingPreviewAndAllowsManualRetry() {
        val images = ViewerTestImages()
        val preview = viewerBitmap()
        val page = ComposeViewerPageState("https://viewer.invalid/one.jpg", 480, preview, images)
        page.enterCompleted(true)
        images.images[0].complete(null)
        assertSame(preview, page.bitmap)
        assertTrue(page.failed)
        page.loadOriginal()
        assertEquals(2, images.images.size)
        val original = viewerBitmap(300, 300)
        images.images[1].complete(original)
        assertSame(original, page.bitmap)
        assertTrue(page.original)
        page.close()
    }

    @Test fun retryRepeatsFailedOriginalInsteadOfThumbnail() {
        val images = ViewerTestImages()
        val preview = viewerBitmap()
        val page = ComposeViewerPageState("https://viewer.invalid/one.jpg", 480, preview, images)
        page.enterCompleted(true)
        images.images.single().complete(null)
        page.retry()
        assertEquals(2, images.images.size)
        assertTrue(images.images.last().original)
        assertEquals(2400, images.images.last().targetPx)
        assertSame(preview, page.bitmap)
        assertFalse(page.failed)
        val original = viewerBitmap(300, 300)
        images.images.last().complete(original)
        assertSame(original, page.bitmap)
        assertTrue(page.original)
        page.close()
    }

    @Test fun retryRepeatsFailedThumbnailAtItsOriginalTargetSize() {
        val images = ViewerTestImages()
        val page = ComposeViewerPageState("https://viewer.invalid/one.jpg", 480, requests = images)
        page.start()
        images.images.single().complete(null)
        page.retry()
        assertEquals(2, images.images.size)
        assertFalse(images.images.last().original)
        assertEquals(480, images.images.last().targetPx)
        page.close()
    }

    @Test fun originalBitmapSupersedesPendingPreviewRegionAndRejectsLateSource() {
        val images = ViewerTestImages()
        val page = ComposeViewerPageState("https://viewer.invalid/long.png", 480,
            viewerBitmap(64, 800), images)
        page.enterCompleted(true)
        assertFalse(images.regions.single().original)
        images.images.single().complete(viewerBitmap(128, 1600))
        assertEquals(2, images.regions.size)
        assertTrue(images.regions[0].cancelled)
        assertTrue(images.regions[1].original)
        val stale = source()
        images.regions[0].complete(stale)
        assertNull(page.longSource)
        assertSourceClosed(stale)
        assertFalse(page.longFailed)
        val original = source()
        images.regions[1].complete(original)
        assertSame(original, page.longSource)
        assertSourceOpen(original)
        page.close()
        assertSourceClosed(original)
    }

    @Test fun previewRegionDeliveredFirstKeepsItsQualityAndIsRetainedUntilOriginalReady() {
        val images = ViewerTestImages()
        val page = ComposeViewerPageState("https://viewer.invalid/long.png", 480,
            viewerBitmap(64, 800), images)
        page.enterCompleted(true)
        val preview = source()
        images.regions.single().complete(preview)
        images.images.single().complete(viewerBitmap(128, 1600))
        assertEquals(2, images.regions.size)
        assertTrue(images.regions.last().original)
        assertSame(preview, page.longSource)
        assertSourceOpen(preview)
        val original = source()
        images.regions.last().complete(original)
        assertSame(original, page.longSource)
        assertSame(preview, page.retainedLongSource)
        assertSourceOpen(preview)
        page.regionReady(original)
        assertSourceClosed(preview)
        page.regionFailed(preview)
        assertSame(original, page.longSource)
        assertFalse(page.longFailed)
        assertSourceOpen(original)
        page.close()
    }

    @Test fun cancelledPreviewFailureCannotClearOrFailOriginalRegionRequest() {
        val images = ViewerTestImages()
        val page = ComposeViewerPageState("https://viewer.invalid/long.png", 480,
            viewerBitmap(64, 800), images)
        page.enterCompleted(true)
        images.images.single().complete(viewerBitmap(128, 1600))
        images.regions[0].complete(null)
        assertFalse(page.longFailed)
        page.enterCompleted(true)
        assertEquals(2, images.regions.size)
        assertFalse(images.regions.last().cancelled)
        val original = source()
        images.regions.last().complete(original)
        assertSame(original, page.longSource)
        page.close()
    }

    @Test fun tileFailureClearsOwnedSourceKeepsPreviewAndRetriesOnlyRegion() {
        val images = ViewerTestImages()
        val page = ComposeViewerPageState("https://viewer.invalid/long.png", 480, requests = images)
        page.start()
        val preview = viewerBitmap(64, 800)
        images.images.single().complete(preview)
        page.enterCompleted(false)
        val broken = source()
        images.regions.single().complete(broken)
        page.regionFailed(broken)
        assertNull(page.longSource)
        assertTrue(page.longFailed)
        assertFalse(page.failed)
        assertSame(preview, page.bitmap)
        assertSourceClosed(broken)
        page.retryLongImage()
        page.retryLongImage()
        assertEquals(1, images.images.size)
        assertEquals(2, images.regions.size)
        assertFalse(images.regions.last().original)
        assertEquals(480, images.regions.last().targetPx)
        val recovered = source()
        images.regions.last().complete(recovered)
        assertSame(recovered, page.longSource)
        assertFalse(page.longFailed)
        assertSourceOpen(recovered)
        page.close()
    }

    @Test fun failedOriginalRegionRetriesOriginalAndKeepsPreviewSourceUntilSuccess() {
        val images = ViewerTestImages()
        val page = ComposeViewerPageState("https://viewer.invalid/long.png", 480,
            viewerBitmap(64, 800), images)
        page.enterCompleted(true)
        val preview = source()
        images.regions.single().complete(preview)
        val originalBitmap = viewerBitmap(128, 1600)
        images.images.single().complete(originalBitmap)
        images.regions.last().complete(null)
        assertTrue(page.longFailed)
        assertSame(preview, page.longSource)
        assertSourceOpen(preview)
        page.retry()
        assertEquals(1, images.images.size)
        assertEquals(3, images.regions.size)
        assertTrue(images.regions.last().original)
        assertSame(originalBitmap, page.bitmap)
        val original = source()
        images.regions.last().complete(original)
        assertSame(original, page.longSource)
        assertFalse(page.longFailed)
        assertSourceOpen(preview)
        page.regionReady(original)
        assertSourceClosed(preview)
        page.close()
    }

    @Test fun pauseRejectsLateRegionSourceWithoutDiscardingResumedSource() {
        val images = ViewerTestImages()
        val page = ComposeViewerPageState("https://viewer.invalid/long.png", 480, requests = images)
        page.start()
        images.images.single().complete(viewerBitmap(64, 800))
        page.enterCompleted(false)
        page.setActive(false)
        page.setActive(true)
        assertTrue(images.regions[0].cancelled)
        val current = source()
        images.regions[1].complete(current)
        val stale = source()
        images.regions[0].complete(stale)
        assertSame(current, page.longSource)
        assertSourceClosed(stale)
        assertSourceOpen(current)
        page.close()
    }

    @Test fun disposedPageClosesAlreadyDispatchedRegionSource() {
        val images = ViewerTestImages()
        val page = ComposeViewerPageState("https://viewer.invalid/long.png", 480, requests = images)
        page.start()
        images.images.single().complete(viewerBitmap(64, 800))
        page.enterCompleted(false)
        page.close()
        assertTrue(images.regions.single().cancelled)
        val stale = source()
        images.regions.single().complete(stale)
        assertNull(page.longSource)
        assertSourceClosed(stale)
    }

    @Test fun defaultLongImageRequestsScreenWidthVariantNotOriginalUrl() {
        val source = "https://viewer.invalid/long.jpg?old=1"
        val fitted = ViewerRegionLoader.url(source, 480, false)
        assertTrue(fitted.contains("thumbnail/480x/format/jpg"))
        assertEquals("https://viewer.invalid/long.jpg", ViewerRegionLoader.url(source, 480, true))
    }

    @Test fun failingReplacementRestoresUsablePreviewAndCloseReleasesBothSources() {
        val images = ViewerTestImages()
        val page = ComposeViewerPageState("https://viewer.invalid/long.png", 480,
            viewerBitmap(64, 800), images)
        page.enterCompleted(true)
        val preview = source()
        images.regions.single().complete(preview)
        images.images.single().complete(viewerBitmap(128, 1600))
        val broken = source()
        images.regions.last().complete(broken)
        assertSame(preview, page.retainedLongSource)
        page.regionFailed(broken)
        assertSame(preview, page.longSource)
        assertNull(page.retainedLongSource)
        assertSourceOpen(preview)
        assertSourceClosed(broken)
        page.retryLongImage()
        assertTrue(images.regions.last().original)
        val original = source()
        images.regions.last().complete(original)
        page.close()
        assertSourceClosed(original)
        assertSourceClosed(preview)
    }

    private fun source(): LongImageSource = viewerLongSource(files.newFile()).also(sources::add)

    private fun assertSourceOpen(source: LongImageSource) {
        val tile = source.decode(Rect(0, 0, 32, 64), 1)
        assertNotNull("The page still owns a usable decoder", tile)
        tile?.recycle()
    }

    private fun assertSourceClosed(source: LongImageSource) {
        assertNull("A rejected or released source cannot decode", source.decode(Rect(0, 0, 32, 64), 1))
    }
}
