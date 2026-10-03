package com.ronan.heyboxlite

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Publishes feed snapshots on the UI thread. Parsing, filtering and page merging
 * run on one worker; requests still use the original Java API and parameters.
 */
internal class ComposeFeedController(
    private val request: (String, Map<String, String>, ApiClient.Callback) -> Unit,
    private val readCache: () -> List<FeedItem>,
    private val writeCache: (List<FeedItem>) -> Unit,
    private val blockedKeywords: () -> List<String>,
    private val io: ExecutorService,
    private val publish: (() -> Unit) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    constructor(session: SessionStore, api: ApiClient, cache: LocalCache,
                onMessage: (String) -> Unit, main: Handler = Handler(Looper.getMainLooper())) : this(
        request = api::get,
        readCache = cache::feedItems,
        writeCache = cache::saveFeed,
        blockedKeywords = session::blockKeywordList,
        io = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "heybox-compose-feed").apply { isDaemon = true }
        },
        publish = { action -> main.post(action); Unit },
        onMessage = onMessage,
    )

    val items: MutableState<List<FeedItem>> = mutableStateOf(emptyList())
    val presentations: MutableState<Map<FeedItem, ComposeFeedPresentation>> = mutableStateOf(emptyMap())
    val loading: MutableState<Boolean> = mutableStateOf(false)
    val refreshing: MutableState<Boolean> = mutableStateOf(false)
    val loadingMore: MutableState<Boolean> = mutableStateOf(false)
    val noMore: MutableState<Boolean> = mutableStateOf(false)
    val error: MutableState<String?> = mutableStateOf(null)

    private var cursor = ""
    private var lastPull: Int? = null
    private var firstRequest = true
    @Volatile private var requestSerial = 0
    @Volatile private var restoreSerial = 0
    @Volatile private var closed = false

    fun restoreCache() {
        if (closed || items.value.isNotEmpty()) return
        val serial = ++restoreSerial
        io.execute {
            if (closed || serial != restoreSerial) return@execute
            val cached = FeedCollection.filter(readCache(), blockedKeywords())
            val prepared = cached.associateWith(::composeFeedPresentation)
            publish {
                if (!closed && serial == restoreSerial && items.value.isEmpty() && cached.isNotEmpty()) {
                    publishItems(cached, prepared)
                }
            }
        }
    }

    fun refresh() {
        load(reset = true)
    }

    fun loadInitial() {
        load(reset = true, showRefreshFeedback = false)
    }

    fun loadMore() {
        if (loading.value || loadingMore.value || noMore.value) return
        load(reset = false)
    }

    fun close() {
        closed = true
        requestSerial++
        restoreSerial++
        loading.value = false
        refreshing.value = false
        loadingMore.value = false
        io.shutdownNow()
    }

    private fun load(reset: Boolean, showRefreshFeedback: Boolean = true) {
        if (closed || loading.value || loadingMore.value) return
        if (reset) {
            refreshing.value = showRefreshFeedback
            noMore.value = false
        } else {
            loadingMore.value = true
        }
        loading.value = true
        error.value = null
        val serial = ++requestSerial
        val previous = items.value
        val previousPresentations = presentations.value
        val params = OfficialRequestParams.feed(
            if (reset) 1 else 0,
            if (reset) null else lastPull,
            if (reset) "" else cursor,
            if (reset) true else firstRequest,
        )
        request(EndpointProvider.feeds(), params, object : ApiClient.Callback {
            override fun onSuccess(body: JSONObject) {
                if (!accept(serial)) return
                io.execute {
                    if (!accept(serial)) return@execute
                    try {
                        val result = body.optJSONObject("result")
                        val parsed = parseLinks(result?.optJSONArray("links"))
                        val filtered = FeedCollection.filter(parsed, blockedKeywords())
                        val next = if (reset) ArrayList<FeedItem>() else ArrayList(previous)
                        val added = FeedCollection.appendUnique(next, filtered)
                        val prepared = next.associateWith { item ->
                            previousPresentations[item] ?: composeFeedPresentation(item)
                        }
                        val nextCursor = result?.optString("lastval", "") ?: ""
                        publish {
                            if (!accept(serial)) return@publish
                            restoreSerial++
                            if (reset && filtered.isEmpty() && items.value.isNotEmpty()) {
                                onMessage("没有获取到新内容，已保留原列表")
                            } else {
                                publishItems(next, prepared)
                            }
                            cursor = nextCursor
                            lastPull = if (reset) 1 else 0
                            firstRequest = false
                            if (!reset && FeedCollection.loadMoreExhausted(parsed.size, added)) {
                                noMore.value = true
                            }
                            complete(reset)
                            writeCache(items.value)
                        }
                    } catch (_: RuntimeException) {
                        publish { fail(serial, reset, "内容解析失败，请重试") }
                    }
                }
            }

            override fun onError(message: String) {
                fail(serial, reset, message)
            }
        })
    }

    private fun accept(serial: Int): Boolean = !closed && serial == requestSerial

    private fun publishItems(next: List<FeedItem>, prepared: Map<FeedItem, ComposeFeedPresentation>) {
        Snapshot.withMutableSnapshot {
            items.value = next
            presentations.value = prepared
        }
    }

    private fun fail(serial: Int, reset: Boolean, message: String) {
        if (!accept(serial)) return
        error.value = message
        if (reset && items.value.isEmpty()) restoreCache()
        complete(reset)
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
