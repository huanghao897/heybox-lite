package com.ronan.heyboxlite

import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

internal data class ComposeSavedPostsState(
    val items: List<FeedItem> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String = "",
)

/** Shares the cached-list contract for cloud history, favorites and individual folders. */
internal class ComposeSavedPostsController(
    private val path: String,
    private val accountId: () -> String,
    private val request: (String, Map<String, String>, ApiClient.Callback) -> Unit,
    private val readCache: (String) -> List<FeedItem>,
    private val writeCache: (String, List<FeedItem>) -> Unit,
    private val filter: (List<FeedItem>) -> List<FeedItem>,
    private val background: (() -> Unit) -> Unit,
    private val publish: (() -> Unit) -> Unit,
    private val cacheFailure: (RuntimeException) -> Unit,
    private val markFavorites: Boolean = false,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    val state = mutableStateOf(ComposeSavedPostsState())
    private var key = ""
    private var generation = 0
    private var closed = false
    private var loadedAt = 0L

    fun load(collection: String, params: Map<String, String>, force: Boolean = false) {
        if (closed) return
        val account = accountId()
        if (account.isBlank()) {
            invalidate()
            return
        }
        val cacheKey = "compose-saved|$account|$path|$collection"
        if (!force && cacheKey == key && (state.value.loading
                || (state.value.loaded && nowMillis() - loadedAt < 60_000L))) return
        val previous = if (cacheKey == key) state.value.items else emptyList()
        key = cacheKey
        val token = ++generation
        state.value = ComposeSavedPostsState(previous, loading = true)
        background {
            val cached = try {
                filter(readCache(cacheKey))
            } catch (error: RuntimeException) {
                cacheFailure(error)
                emptyList()
            }
            publish {
                if (!accept(token, account)) return@publish
                if (state.value.items.isEmpty() && cached.isNotEmpty()) {
                    state.value = state.value.copy(items = cached)
                }
                request(path, params, object : ApiClient.Callback {
                    override fun onSuccess(body: JSONObject) {
                        if (!accept(token, account)) return
                        val items = filter(SavedPostParser.feedItems(body))
                        if (markFavorites) items.forEach { it.favorited = true }
                        state.value = ComposeSavedPostsState(items, loaded = true)
                        loadedAt = nowMillis()
                        background {
                            try {
                                writeCache(cacheKey, items)
                            } catch (error: RuntimeException) {
                                cacheFailure(error)
                            }
                        }
                    }

                    override fun onError(message: String) {
                        if (!accept(token, account)) return
                        state.value = state.value.copy(loading = false,
                            error = message.ifBlank { "内容加载失败" })
                    }
                })
            }
        }
    }

    fun invalidate() {
        generation++
        key = ""
        state.value = ComposeSavedPostsState()
    }

    fun close() {
        closed = true
        generation++
    }

    private fun accept(token: Int, account: String): Boolean {
        if (closed || token != generation) return false
        if (account != accountId()) {
            invalidate()
            return false
        }
        return true
    }
}
