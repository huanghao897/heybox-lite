package com.ronan.heyboxlite

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** A reselect is consumed once by the active list, never by a retained swipe preview. */
internal class ComposeFeedNavigation {
    var request by mutableIntStateOf(0)
        private set
    private var consumed = 0

    fun reselect() { request++ }

    fun pending(value: Int): Boolean = value > consumed

    fun consume(value: Int): Boolean {
        if (value <= consumed) return false
        consumed = value
        return true
    }
}

@Composable
internal fun ObserveFeedNavigation(
    navigation: ComposeFeedNavigation,
    listState: LazyListState,
    active: Boolean,
    loading: Boolean = false,
    onRefresh: () -> Unit,
) {
    val request = navigation.request
    LaunchedEffect(request, active, loading) {
        if (active && navigation.pending(request)) {
            listState.scrollToItem(0)
            if (!loading && navigation.consume(request)) onRefresh()
        }
    }
}
