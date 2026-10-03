package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ImageView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ComposeMediaImageBindingTest {
    private class Requests : ComposeMediaImageRequests {
        val loads = mutableListOf<ComposeMediaImageRequest>()
        val callbacks = mutableListOf<(Boolean, Bitmap?) -> Unit>()
        val gifs = mutableListOf<String>()
        var cancels = 0
        override fun load(view: ImageView, url: String, targetPx: Int, complete: (Boolean, Bitmap?) -> Unit) {
            loads += ComposeMediaImageRequest(url, targetPx)
            callbacks += complete
        }
        override fun animate(view: ImageView, url: String) { gifs += url }
        override fun cancel(view: ImageView) { cancels++ }
    }

    @Test fun sameBindingDoesNotReloadOnEveryRecomposition() {
        val requests = Requests()
        val binding = ComposeMediaImageBinding(ImageView(RuntimeEnvironment.getApplication()), requests)
        val request = ComposeMediaImageRequest("https://example.invalid/image", 96)
        repeat(20) { binding.bind(request) { } }
        assertEquals(1, requests.loads.size)
        assertEquals(1, requests.cancels)
    }

    @Test fun oldCallbacksCannotStartAnOldGifOrPublishItsErrorToTheNewItem() {
        val requests = Requests()
        val view = ImageView(RuntimeEnvironment.getApplication())
        val binding = ComposeMediaImageBinding(view, requests)
        val completed = mutableListOf<Boolean>()
        binding.bind(ComposeMediaImageRequest("old-preview", 96, "old.gif"), completed::add)
        binding.bind(ComposeMediaImageRequest("new-preview", 120, "new.gif"), completed::add)
        requests.callbacks[0](true, null)
        requests.callbacks[0](false, null)
        assertTrue(completed.isEmpty())
        assertTrue(requests.gifs.isEmpty())
        requests.callbacks[1](true, null)
        assertEquals(listOf(true), completed)
        assertEquals(listOf("new.gif"), requests.gifs)
    }

    @Test fun resetClearsTheDrawableTransformsAndRejectsItsQueuedCompletion() {
        val requests = Requests()
        val view = ImageView(RuntimeEnvironment.getApplication())
        val binding = ComposeMediaImageBinding(view, requests)
        var completed = false
        binding.bind(ComposeMediaImageRequest("image", 120, "animated")) { completed = true }
        view.setImageDrawable(ColorDrawable(Color.RED))
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        view.alpha = 0.2f
        view.scaleX = 0.5f
        view.scaleY = 0.6f
        binding.reset()
        requests.callbacks[0](true, null)
        assertFalse(completed)
        assertNull(view.drawable)
        assertEquals(View.LAYER_TYPE_NONE, view.layerType)
        assertEquals(1f, view.alpha, 0f)
        assertEquals(1f, view.scaleX, 0f)
        assertEquals(1f, view.scaleY, 0f)
    }

    @Test fun retryAndAnimatedModeChangeReloadWithoutKeepingAnOldGif() {
        val requests = Requests()
        val binding = ComposeMediaImageBinding(ImageView(RuntimeEnvironment.getApplication()), requests)
        binding.bind(ComposeMediaImageRequest("preview", 96, "original.gif")) { }
        requests.callbacks[0](true, null)
        binding.bind(ComposeMediaImageRequest("preview", 96)) { }
        requests.callbacks[1](true, null)
        binding.bind(ComposeMediaImageRequest("preview", 96, retry = 1)) { }
        assertEquals(3, requests.loads.size)
        assertEquals(listOf("original.gif"), requests.gifs)
    }
}
