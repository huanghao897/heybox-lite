package com.ronan.heyboxlite

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** UI-thread input queue: a burst accumulates distance without invalidating the page theme. */
@Stable
internal class ComposeFeedRotaryInput {
    internal val signal = mutableIntStateOf(0)
    private var pendingDistance = 0L

    fun offer(distance: Int) {
        if (distance == 0) return
        pendingDistance += distance.toLong()
        signal.intValue++
    }

    internal fun takeDistance(): Float {
        val distance = pendingDistance
        pendingDistance = 0L
        return distance.toFloat()
    }

    fun clear() {
        pendingDistance = 0L
    }
}

@Composable
internal fun ObserveFeedRotaryInput(input: ComposeFeedRotaryInput, listState: LazyListState) {
    DisposableEffect(input) {
        onDispose { input.clear() }
    }
    LaunchedEffect(input, listState) {
        snapshotFlow { input.signal.intValue }.collect {
            val distance = input.takeDistance()
            if (distance != 0f) {
                try {
                    listState.scrollBy(distance)
                } catch (_: CancellationException) {
                    // A finger drag has priority; keep accepting later crown ticks.
                    currentCoroutineContext().ensureActive()
                }
            }
        }
    }
}
