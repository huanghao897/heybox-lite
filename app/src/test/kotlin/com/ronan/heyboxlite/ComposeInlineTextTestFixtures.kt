package com.ronan.heyboxlite

import android.graphics.Bitmap

internal class ComposeInlineTextTestCatalog(var loaded: Boolean = true) : ComposeInlineTextEmojiCatalog {
    private val light = mutableMapOf<String, String>()
    private val dark = mutableMapOf<String, String>()
    val ready = mutableListOf<Runnable>()

    fun register(code: String, lightUrl: String, darkUrl: String = lightUrl) {
        for (variant in listOf(code, "[$code]")) {
            light[variant] = lightUrl
            dark[variant] = darkUrl
        }
    }

    override fun longestResolvableCode(value: String, darkMode: Boolean): String {
        val urls = if (darkMode) dark else light
        if (value in urls) return value
        if (value.startsWith("[")) return ""
        return urls.keys.filter { !it.startsWith("[") && value.startsWith(it) }
            .maxByOrNull { it.length }.orEmpty()
    }

    override fun url(code: String, darkMode: Boolean): String =
        (if (darkMode) dark else light)[code].orEmpty()

    override fun isLoaded(): Boolean = loaded
    override fun whenReady(ready: Runnable) {
        if (loaded) ready.run() else this.ready += ready
    }
}

internal data class ComposeInlineTextTestImageRequest(
    val url: String,
    val targetPx: Int,
    val complete: (Bitmap?) -> Unit,
    var cancelled: Boolean = false,
)

internal class ComposeInlineTextTestImages : ComposeImageLoader {
    val requests = mutableListOf<ComposeInlineTextTestImageRequest>()
    val cache = mutableMapOf<Pair<String, Int>, Bitmap>()

    override fun cached(sourceUrl: String, targetPx: Int): Bitmap? = cache[sourceUrl to targetPx]
    override fun load(sourceUrl: String, targetPx: Int, callback: (Bitmap?) -> Unit) {
        loadCancellable(sourceUrl, targetPx, callback)
    }

    override fun loadCancellable(sourceUrl: String, targetPx: Int, callback: (Bitmap?) -> Unit): () -> Unit {
        val request = ComposeInlineTextTestImageRequest(sourceUrl, targetPx, callback)
        requests += request
        return { request.cancelled = true }
    }
}
