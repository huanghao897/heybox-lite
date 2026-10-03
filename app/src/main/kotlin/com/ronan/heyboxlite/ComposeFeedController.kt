package com.ronan.heyboxlite

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Feed state for Compose. Network and filtering stay in the existing Java services;
 * this class only exposes immutable snapshots to the UI.
 */
internal class ComposeFeedController(
    private val session: SessionStore,
    private val api: ApiClient,
    private val cache: LocalCache,
    private val onMessage: (String) -> Unit,
) {
    val items: MutableState<List<FeedItem>> = mutableStateOf(emptyList())
    val loading: MutableState<Boolean> = mutableStateOf(false)
    val refreshing: MutableState<Boolean> = mutableStateOf(false)
    val loadingMore: MutableState<Boolean> = mutableStateOf(false)
    val noMore: MutableState<Boolean> = mutableStateOf(false)
    val error: MutableState<String?> = mutableStateOf(null)

    private var cursor = ""
    private var lastPull: Int? = null
    private var firstRequest = true
    private var requestSerial = 0
    private var restoreSerial = 0
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "heybox-compose-feed-cache").apply { isDaemon = true }
    }

    fun restoreCache() {
        if (items.value.isNotEmpty()) return
        val serial = ++restoreSerial
        io.execute {
            val cached = FeedCollection.filter(cache.feedItems(), session.blockKeywordList())
            main.post {
                if (serial == restoreSerial && items.value.isEmpty() && cached.isNotEmpty()) {
                    items.value = cached
                }
            }
        }
    }

    fun refresh() {
        load(reset = true)
    }

    fun loadMore() {
        if (loading.value || loadingMore.value || noMore.value) return
        load(reset = false)
    }

    fun close() {
        requestSerial++
        restoreSerial++
        loading.value = false
        refreshing.value = false
        loadingMore.value = false
        io.shutdownNow()
    }

    private fun load(reset: Boolean) {
        if (loading.value || loadingMore.value) return
        if (reset) {
            refreshing.value = true
            noMore.value = false
        } else {
            loadingMore.value = true
        }
        loading.value = true
        error.value = null
        val serial = ++requestSerial
        val previous = items.value
        val params = OfficialRequestParams.feed(
            if (reset) 1 else 0,
            if (reset) null else lastPull,
            if (reset) "" else cursor,
            if (reset) true else firstRequest,
        )
        api.get(EndpointProvider.feeds(), params, object : ApiClient.Callback {
            override fun onSuccess(body: JSONObject) {
                if (serial != requestSerial) return
                val result = body.optJSONObject("result")
                val links = result?.optJSONArray("links")
                val parsed = parseLinks(links)
                val filtered = FeedCollection.filter(parsed, session.blockKeywordList())
                val next = if (reset) ArrayList(filtered) else ArrayList(items.value)
                val added = FeedCollection.appendUnique(next, filtered)
                if (reset && filtered.isEmpty() && previous.isNotEmpty()) {
                    onMessage("没有获取到新内容，已保留原列表")
                } else {
                    items.value = next
                }
                cursor = result?.optString("lastval", "") ?: ""
                lastPull = if (reset) 1 else 0
                firstRequest = false
                if (!reset && (parsed.isEmpty() || added == 0)) noMore.value = true
                cache.saveFeed(items.value)
                complete(reset)
            }

            override fun onError(message: String) {
                if (serial != requestSerial) return
                error.value = message
                if (reset && items.value.isEmpty()) restoreCache()
                complete(reset)
            }
        })
    }

    private fun complete(reset: Boolean) {
        loading.value = false
        if (reset) refreshing.value = false else loadingMore.value = false
    }

    private fun parseLinks(links: JSONArray?): List<FeedItem> {
        if (links == null) return emptyList()
        val parsed = ArrayList<FeedItem>(links.length())
        for (index in 0 until links.length()) {
            val value = links.optJSONObject(index) ?: continue
            parsed += FeedItem.from(value)
        }
        return parsed
    }
}
