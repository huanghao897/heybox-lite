package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.widget.ImageView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ComposeDetailMediaTest {
    @Test fun previewAndOriginalSelectTheSameIndexWithoutMixingCommentGalleries() {
        val root = comment("root", "root-a", "root-b")
        val reply = comment("reply", "reply-a", "reply-b")
        val other = comment("other", "other-a")
        val comments = listOf(root, reply, other)
        val before = comments.map { it.toString() }
        val expected = ComposeDetailGallery(listOf("reply-a.gif", "reply-b.gif"), 1)

        assertEquals(expected, composeCommentGallery(reply, "reply-b-preview.jpg"))
        assertEquals(expected, composeCommentGallery(reply, "reply-b.gif"))
        assertEquals(ComposeDetailGallery(listOf("root-a.gif", "root-b.gif"), 0),
            composeCommentGallery(root, "root-a-preview.jpg"))
        assertEquals(ComposeDetailGallery(listOf("other-a.gif"), 0),
            composeCommentGallery(other, "other-a-preview.jpg"))
        assertEquals(before, comments.map { it.toString() })
    }

    @Test fun commentWithOnlyAPreviewStillHasAnOpenableGallery() {
        val comment = JSONObject().put("imgs", JSONArray()
            .put(JSONObject().put("thumb_url", "preview-only.jpg")))
        assertEquals(ComposeDetailGallery(listOf("preview-only.jpg"), 0),
            composeCommentGallery(comment, "preview-only.jpg"))
    }

    @Test fun sharedArticleAndCommentUrlStillOpensTheArticleGallery() {
        val first = image("shared")
        val second = image("article-b")
        val comment = JSONObject().put("imgs", JSONArray().put(JSONObject()
            .put("thumb_url", first.value).put("original", "comment-original.gif")))
        val state = state(listOf(comment), listOf(first, second))
        assertEquals(ComposeDetailGallery(listOf(first.value, second.value), 0),
            composeDetailGallery(state, first.value))
        assertEquals(ComposeDetailGallery(listOf("comment-original.gif"), 0),
            composeCommentGallery(comment, first.value))
    }

    @Test fun reusedImageUrlInDifferentCommentsStaysInTheSelectedCommentsGallery() {
        val root = comment("root", "shared", "root-only")
        val reply = comment("reply", "reply-only", "shared")
        assertEquals(ComposeDetailGallery(listOf("shared.gif", "root-only.gif"), 0),
            composeCommentGallery(root, "shared.gif"))
        assertEquals(ComposeDetailGallery(listOf("reply-only.gif", "shared.gif"), 1),
            composeCommentGallery(reply, "shared-preview.jpg"))
        assertEquals(ComposeDetailGallery(listOf("unrelated.jpg"), 0),
            composeCommentGallery(root, "unrelated.jpg"))
    }

    @Test fun articleGalleryDeduplicatesInOrderAndExcludesCommentAndFeedImages() {
        val first = image("article-a")
        val second = image("article-b")
        val state = state(listOf(comment("root", "comment-a")),
            listOf(first, RichContent.Block.text("Between"), second, first))
        assertEquals(ComposeDetailGallery(listOf(first.value, second.value), 1),
            composeDetailGallery(state, second.value))
        assertEquals(ComposeDetailGallery(listOf(first.value, second.value), 0),
            composeDetailGallery(state, first.value))
    }

    @Test fun missingArticleImagesFallBackToFeedButUnknownSelectionStaysIsolated() {
        val state = state(content = listOf(RichContent.Block.text("No article images")))
        assertEquals(ComposeDetailGallery(listOf("feed-a.jpg", "feed-b.jpg"), 1),
            composeDetailGallery(state, "feed-b.jpg"))
        assertEquals(ComposeDetailGallery(listOf("unrelated.gif?original=1"), 0),
            composeDetailGallery(state, "unrelated.gif?original=1"))
    }

    @Test fun pagerGroupsOnlyAdjacentImagesAndPreservesOriginalBlockIndices() {
        val blocks = listOf(RichContent.Block.text("Before"), image("a"), image("b"),
            RichContent.Block.gameCard(JSONObject().put("appid", "42"), "42"), image("c"),
            RichContent.Block.text("Between"), image("d"), image("e"))
        val rows = composeDetailBodyRows(blocks, pager = true)
        assertEquals(listOf(0, 1, 3, 4, 5, 6), rows.map { it.index })
        assertEquals(listOf(1, 2, 1, 1, 1, 2), rows.map { it.blocks.size })
        assertEquals(blocks, rows.flatMap { it.blocks })
        assertSame(blocks[1], rows[1].blocks[0])
        assertSame(blocks[2], rows[1].blocks[1])
    }

    @Test fun pagerDisabledKeepsEvenAdjacentImagesInSeparateRows() {
        val blocks = listOf(image("a"), image("b"), RichContent.Block.text("After"))
        val rows = composeDetailBodyRows(blocks, pager = false)
        assertEquals(listOf(0, 1, 2), rows.map { it.index })
        assertTrue(rows.all { it.blocks.size == 1 })
        assertEquals(blocks, rows.flatMap { it.blocks })
        assertTrue(composeDetailBodyRows(emptyList(), pager = true).isEmpty())
        assertTrue(composeDetailBodyRows(emptyList(), pager = false).isEmpty())
    }

    @Test fun imageHeightBoundsScaleWithUiAndRejectExtremeAspectRatios() {
        assertEquals(150f, composeDetailImageHeight(200f, 0.75f, 1f), 0.001f)
        assertEquals(114f, composeDetailImageHeight(200f, -1f, 1f), 0.001f)
        assertEquals(360f, composeDetailImageHeight(200f, 100f, 1f), 0.001f)
        assertEquals(150f, composeDetailImageHeight(500f, 0.01f, 1f), 0.001f)
        assertEquals(280f, composeDetailImageHeight(100f, 100f, 1f), 0.001f)
        for (scale in listOf(0.82f, 1f, 1.4f)) {
            for (width in listOf(0f, 76f, 160f, 227f, 240f, 1000f)) {
                for (aspect in listOf(0f, 0.1f, 0.5625f, 1f, 3f, 100f)) {
                    val height = composeDetailImageHeight(width, aspect, scale)
                    assertTrue("height=$height width=$width aspect=$aspect scale=$scale",
                        height >= 114f * scale && height <= 360f * scale)
                }
            }
        }
    }

    @Test fun decodedDimensionsAreDeliveredBeforeCompletionAndGifAnimation() {
        val requests = PendingImages()
        val events = mutableListOf<String>()
        requests.onAnimate = { events += "gif:$it" }
        val binding = ComposeMediaImageBinding(ImageView(RuntimeEnvironment.getApplication()), requests)
        binding.bind(ComposeMediaImageRequest("preview.jpg", 120, "original.gif"),
            complete = { events += "loaded:$it" },
            imageSize = { width, height -> events += "size:$width:$height" })
        requests.callbacks.single()(true, Bitmap.createBitmap(40, 90, Bitmap.Config.ARGB_8888))
        assertEquals(listOf("size:40:90", "loaded:true", "gif:original.gif"), events)
    }

    @Test fun staleFailedAndNullBitmapCallbacksCannotPublishInvalidDimensions() {
        val requests = PendingImages()
        val binding = ComposeMediaImageBinding(ImageView(RuntimeEnvironment.getApplication()), requests)
        val sizes = mutableListOf<Pair<Int, Int>>()
        val completed = mutableListOf<Boolean>()
        val dimensions: (Int, Int) -> Unit = { width, height -> sizes += width to height }
        binding.bind(ComposeMediaImageRequest("old", 96, "old.gif"), completed::add, dimensions)
        binding.bind(ComposeMediaImageRequest("failed", 96, "failed.gif"), completed::add, dimensions)
        val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
        requests.callbacks[0](true, bitmap)
        requests.callbacks[1](false, bitmap)
        binding.bind(ComposeMediaImageRequest("without-bitmap", 96), completed::add, dimensions)
        requests.callbacks[2](true, null)
        binding.reset()
        requests.callbacks[2](true, bitmap)
        assertTrue(sizes.isEmpty())
        assertTrue(requests.animated.isEmpty())
        assertEquals(listOf(false, true), completed)
    }

    private fun state(comments: List<JSONObject> = emptyList(), content: List<RichContent.Block> = emptyList()) =
        ComposeDetailState(FeedItem.from(JSONObject().put("linkid", "post")
            .put("imgs", JSONArray().put("feed-a.jpg").put("feed-b.jpg"))),
            content, emptyList(), comments, false)

    private fun comment(id: String, vararg names: String): JSONObject {
        val images = JSONArray()
        names.forEach { images.put(JSONObject().put("thumb_url", "$it-preview.jpg").put("original", "$it.gif")) }
        return JSONObject().put("commentid", id).put("imgs", images)
    }

    private fun image(name: String): RichContent.Block =
        RichContent.parse("<img src=\"https://example.invalid/$name.jpg\">", null).single { it.image }

    private class PendingImages : ComposeMediaImageRequests {
        val callbacks = mutableListOf<(Boolean, Bitmap?) -> Unit>()
        val animated = mutableListOf<String>()
        var onAnimate: (String) -> Unit = {}
        override fun load(view: ImageView, url: String, targetPx: Int, complete: (Boolean, Bitmap?) -> Unit) {
            callbacks += complete
        }
        override fun animate(view: ImageView, url: String) {
            animated += url
            onAnimate(url)
        }
        override fun cancel(view: ImageView) { }
    }
}
