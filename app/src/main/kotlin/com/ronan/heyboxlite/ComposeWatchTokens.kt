package com.ronan.heyboxlite

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The watch type scale. Every interface label uses one of these steps, scaled
 * once by textScale; nothing renders below [CAPTION]. Article bodies follow the
 * reader's own settings instead (see ComposeReadingStyle).
 */
internal object WatchType {
    /** Times, counts, chips and section labels. */
    const val CAPTION = 11f
    /** Descriptions, row values, replies and buttons. */
    const val SECONDARY = 12f
    /** Row titles and comment bodies. */
    const val BODY = 13f
    /** Card titles, dialog titles and key values. */
    const val SUBTITLE = 15f
    /** Page and post titles. */
    const val TITLE = 17f
    /** Hero numbers on statistics pages. */
    const val DISPLAY = 22f

    fun lineHeight(size: Float): Float = when {
        size <= CAPTION -> 15f
        size <= SECONDARY -> 16f
        size <= BODY -> 18f
        size <= SUBTITLE -> 20f
        size <= TITLE -> 23f
        else -> 28f
    }
}

/** Corner radii before uiScale. Pills and avatars use a 50% shape instead. */
internal object WatchRadius {
    /** Thumbnails, badges and tiles. */
    const val SMALL = 6
    /** Rows, inputs and inner panels. */
    const val MEDIUM = 10
    /** Cards and dialogs. */
    const val LARGE = 16
}

/** Icon sizes before uiScale. */
internal object WatchIcon {
    const val SMALL = 14
    const val MEDIUM = 16
    const val LARGE = 18
    const val XLARGE = 22
}

/** Fingers do not shrink with uiScale: interactive targets never go below this. */
internal val WatchTouchTarget: Dp = 36.dp

@Composable
internal fun watchLineHeight(size: Float): TextUnit =
    (WatchType.lineHeight(size) * LocalHeyboxTheme.current.textScale).sp

/** Scaled size for a target that must also stay physically tappable. */
@Composable
internal fun watchTouchDp(value: Int): Dp = maxOf(watchDp(value), WatchTouchTarget)
