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
import java.util.ArrayDeque

/** UI-thread input queue: a burst accumulates distance without invalidating the page theme. */
@Stable
internal class ComposeFeedRotaryInput {
    internal val signal = mutableIntStateOf(0)
    private val pendingDistances = ArrayDeque<Long>()

    fun offer(distance: Int) {
        if (distance == 0) return
        val last = pendingDistances.peekLast()
        // Preserve reversals: an edge may consume none of the preceding direction.
        if (last != null && (last > 0L) == (distance > 0)) {
            pendingDistances.removeLast()
            pendingDistances.addLast(last + distance.toLong())
        } else {
            pendingDistances.addLast(distance.toLong())
        }
        signal.intValue++
    }

    internal fun takeDistance(): Float = pendingDistances.pollFirst()?.toFloat() ?: 0f

    fun clear() {
        pendingDistances.clear()
    }
}

@Composable
internal fun ObserveFeedRotaryInput(input: ComposeFeedRotaryInput, listState: LazyListState) {
    DisposableEffect(input) {
        onDispose { input.clear() }
    }
    LaunchedEffect(input, listState) {
        snapshotFlow { input.signal.intValue }.collect {
            while (true) {
                val distance = input.takeDistance()
                if (distance == 0f) break
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
