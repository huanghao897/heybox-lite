package com.ronan.heyboxlite

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState

/** Compose list positions are separate from legacy pixel scroll offsets. */
internal data class ComposeDetailPosition(val index: Int = 0, val offset: Int = 0) {
    val hasProgress: Boolean get() = index > 0 || offset > 0
    companion object {
        fun from(state: LazyListState) = ComposeDetailPosition(
            state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)

        fun save(context: Context, account: String, postId: String, value: ComposeDetailPosition) {
            context.getSharedPreferences("compose-reading-position", Context.MODE_PRIVATE).edit()
                .putInt("$account:$postId:index", value.index)
                .putInt("$account:$postId:offset", value.offset).apply()
        }

        fun restore(context: Context, account: String, postId: String): ComposeDetailPosition {
            val prefs = context.getSharedPreferences("compose-reading-position", Context.MODE_PRIVATE)
            return ComposeDetailPosition(prefs.getInt("$account:$postId:index", 0).coerceAtLeast(0),
                prefs.getInt("$account:$postId:offset", 0).coerceAtLeast(0))
        }
    }
}
