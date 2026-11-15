package com.ronan.heyboxlite

import android.os.Handler
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

/** Owns the debounced search request and stale-response guard for the Compose search page. */
internal class ComposeSearchController(
    private val session: SessionStore,
    api: ApiClient,
    private val cache: LocalCache,
    private val handler: Handler,
    private val request: (String, Map<String, String>, ApiClient.Callback) -> Unit = api::get,
) {
    val query: MutableState<String> = mutableStateOf("")
    val items: MutableState<List<FeedItem>> = mutableStateOf(emptyList())
    val loading: MutableState<Boolean> = mutableStateOf(false)
    val noMore: MutableState<Boolean> = mutableStateOf(false)
    val history: MutableState<List<String>> = mutableStateOf(session.searchHistory())
    /** A debounced request is due but not sent yet; the page should not claim "no results". */
    val pending: MutableState<Boolean> = mutableStateOf(false)
    val error: MutableState<String> = mutableStateOf("")

    private var offset = 0
    private var requestSerial = 0
    private var delayed: Runnable? = null
    private var lastKeyword = ""
    private var previousPageIds: List<String> = emptyList()
    private var continuation: Runnable? = null
    private var mergeInteractions: (Iterable<FeedItem>) -> Unit = {}

    fun setInteractionMerger(merger: (Iterable<FeedItem>) -> Unit) {
        mergeInteractions = merger
        merger(items.value)
    }

    fun setQuery(value: String) {
        query.value = value
        delayed?.let(handler::removeCallbacks)
        delayed = null
        continuation?.let(handler::removeCallbacks)
        continuation = null
        val keyword = value.trim()
        requestSerial++
        loading.value = false
        pending.value = false
        error.value = ""
        if (keyword.isEmpty()) {
            lastKeyword = ""
            items.value = emptyList()
            noMore.value = false
            return
        }
        val task = Runnable {
            // A delayed callback can race with the text field clearing or
            // changing again. Never let that stale keyword start a request.
            delayed = null
            pending.value = false
            if (query.value.trim() == keyword) search(keyword)
        }
        delayed = task
        pending.value = true
        handler.postDelayed(task, 420L)
    }

    fun loadMore() {
        if (delayed != null || continuation != null || loading.value || noMore.value ||
            error.value.isNotBlank() || lastKeyword.isEmpty()) return
        search(lastKeyword, true)
    }

    fun retry() {
        val keyword = query.value.trim()
        if (keyword.isEmpty() || pending.value || loading.value) return
        search(keyword, append = keyword == lastKeyword && offset > 0)
    }

    fun clearHistory() {
        session.clearSearchHistory()
        history.value = emptyList()
    }

    fun invalidateSession() {
        items.value = emptyList()
        offset = 0
        lastKeyword = ""
        noMore.value = false
        history.value = session.searchHistory()
        setQuery(query.value)
    }

    fun close() {
        delayed?.let(handler::removeCallbacks)
        delayed = null
        continuation?.let(handler::removeCallbacks)
        continuation = null
        requestSerial++
        loading.value = false
        pending.value = false
        noMore.value = false
        error.value = ""
    }

    private fun search(keyword: String, append: Boolean = false, emptyPagesRemaining: Int = 4) {
        if (loading.value) return
        loading.value = true
        error.value = ""
        if (!append) {
            items.value = emptyList()
            offset = 0
            lastKeyword = keyword
            noMore.value = false
            previousPageIds = emptyList()
            session.addSearchHistory(keyword)
            history.value = session.searchHistory()
        }
        val serial = ++requestSerial
        request(
            EndpointProvider.search(), OfficialRequestParams.search(keyword, offset, 20),
            object : ApiClient.Callback {
                override fun onSuccess(body: JSONObject) {
                    if (serial != requestSerial) return
                    val raw = FeedCollection.parse(body)
                    val returned = FeedCollection.returnedCount(body)
                    val parsed = FeedCollection.filter(raw, session.blockKeywordList())
                    mergeInteractions(parsed)
                    var added = parsed.size
                    items.value = if (append) {
                        val merged = ArrayList(items.value)
                        added = FeedCollection.appendUnique(merged, parsed)
                        merged
                    } else parsed
                    val pageIds = raw.map { it.id }
                    val result = body.optJSONObject("result") ?: body
                    val ended = result.has("has_more") && !Json.truthy(result, "has_more")
                    noMore.value = ended || returned == 0 ||
                        (append && pageIds.isNotEmpty() && pageIds == previousPageIds)
                    previousPageIds = pageIds
                    offset += returned
                    if (added == 0 && !noMore.value && emptyPagesRemaining > 0) {
                        // Pages that only held filtered posts are skipped while still loading,
                        // so the page never flashes an empty result between requests.
                        val next = Runnable {
                            continuation = null
                            if (serial != requestSerial) return@Runnable
                            loading.value = false
                            search(keyword, true, emptyPagesRemaining - 1)
                        }
                        continuation = next
                        handler.post(next)
                    } else {
                        loading.value = false
                    }
                }

                override fun onError(message: String) {
                    if (serial != requestSerial) return
                    cache.log("compose search failed: $message")
                    error.value = message.ifBlank { "搜索失败，请重试" }
                    loading.value = false
                }
            },
        )
    }
}
