package com.ronan.heyboxlite

import androidx.compose.runtime.mutableStateOf

/** Keeps readable cached content visible while the original asynchronous loader refreshes. */
internal class ComposeReadingCenterController(
    private val loadSnapshot: (ReadingCenterLoader.Callback) -> Unit,
    private val mapSnapshot: (ReadingCenterLoader.Snapshot) -> ComposeReadingCenterState,
    private val closeLoader: () -> Unit,
) {
    val state = mutableStateOf<ComposeReadingCenterState?>(null)
    val loading = mutableStateOf(false)
    private var closed = false

    fun open() {
        if (closed || loading.value) return
        loading.value = true
        loadSnapshot(object : ReadingCenterLoader.Callback {
            override fun onLoaded(snapshot: ReadingCenterLoader.Snapshot) {
                if (closed) return
                state.value = mapSnapshot(snapshot)
                loading.value = false
            }

            override fun onError() {
                if (!closed) loading.value = false
            }
        })
    }

    fun close() {
        closed = true
        closeLoader()
    }
}
