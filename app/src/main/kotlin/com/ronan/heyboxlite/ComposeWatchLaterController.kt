package com.ronan.heyboxlite

import androidx.compose.runtime.mutableStateOf

internal data class ComposeOfflineEntry(
    val item: FeedItem,
    val bytes: Long,
    val updatedAt: Long,
)

internal data class ComposeWatchLaterState(
    val entries: List<ComposeOfflineEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String = "",
)

/** Disk metadata and removal always run off the UI thread, including size calculation. */
internal class ComposeWatchLaterController(
    private val readEntries: () -> List<ComposeOfflineEntry>,
    private val remove: (String) -> Unit,
    private val background: (() -> Unit) -> Unit,
    private val publish: (() -> Unit) -> Unit,
    private val reportFailure: (RuntimeException) -> Unit,
) {
    val state = mutableStateOf(ComposeWatchLaterState())
    private var generation = 0
    private var closed = false

    fun refresh() = load(null)

    fun remove(entry: ComposeOfflineEntry) = load(entry.item.id)

    fun close() {
        closed = true
        generation++
    }

    private fun load(removeId: String?) {
        if (closed || state.value.loading) return
        val token = ++generation
        state.value = state.value.copy(loading = true, error = "")
        background {
            try {
                if (removeId != null) remove(removeId)
                val entries = readEntries()
                publish {
                    if (!closed && token == generation) state.value = ComposeWatchLaterState(entries)
                }
            } catch (error: RuntimeException) {
                reportFailure(error)
                publish {
                    if (!closed && token == generation) state.value = state.value.copy(
                        loading = false, error = "本地内容读取失败",
                    )
                }
            }
        }
    }
}
