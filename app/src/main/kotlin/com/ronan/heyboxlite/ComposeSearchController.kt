package com.ronan.heyboxlite

import android.os.Handler
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

/** Owns the debounced search request and stale-response guard for the Compose search page. */
internal class ComposeSearchController(
    private val session: SessionStore,
    private val api: ApiClient,
    private val cache: LocalCache,
    private val handler: Handler,
) {
    val query: MutableState<String> = mutableStateOf("")
    val items: MutableState<List<FeedItem>> = mutableStateOf(emptyList())
    val loading: MutableState<Boolean> = mutableStateOf(false)
    val noMore: MutableState<Boolean> = mutableStateOf(false)

    private var offset = 0
    private var requestSerial = 0
    private var delayed: Runnable? = null
    private var lastKeyword = ""

    fun setQuery(value: String) {
        query.value = value
        delayed?.let(handler::removeCallbacks)
        val keyword = value.trim()
        requestSerial++
        loading.value = false
        if (keyword.isEmpty()) {
            lastKeyword = ""
            items.value = emptyList()
            loading.value = false
            noMore.value = false
            return
        }
        val task = Runnable {
            // A delayed callback can race with the text field clearing or
            // changing again. Never let that stale keyword start a request.
            delayed = null
            if (query.value.trim() == keyword) search(keyword)
        }
        delayed = task
        handler.postDelayed(task, 420L)
    }

    fun loadMore() {
        if (loading.value || noMore.value || lastKeyword.isEmpty()) return
        search(lastKeyword, true)
    }

    fun close() {
        delayed?.let(handler::removeCallbacks)
        delayed = null
        requestSerial++
        loading.value = false
        noMore.value = false
    }

    private fun search(keyword: String, append: Boolean = false) {
        if (loading.value) return
        loading.value = true
        if (!append) {
            offset = 0
            lastKeyword = keyword
            noMore.value = false
            session.addSearchHistory(keyword)
        }
        val serial = ++requestSerial
        api.get(
            EndpointProvider.search(), OfficialRequestParams.search(keyword, offset, 20),
            object : ApiClient.Callback {
                override fun onSuccess(body: JSONObject) {
                    if (serial != requestSerial) return
                    val parsed = FeedCollection.filter(
                        FeedCollection.parse(body), session.blockKeywordList(),
                    )
                    var added = parsed.size
                    items.value = if (append) {
                        val merged = ArrayList(items.value)
                        added = FeedCollection.appendUnique(merged, parsed)
                        merged
                    } else parsed
                    if (parsed.isNotEmpty()) offset += 20
                    if (append && (parsed.isEmpty() || added == 0)) noMore.value = true
                    loading.value = false
                }

                override fun onError(message: String) {
                    if (serial != requestSerial) return
                    cache.log("compose search failed: $message")
                    loading.value = false
                }
            },
        )
    }
}
