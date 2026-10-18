package com.ronan.heyboxlite

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sqrt

/** Round pages start below the watch crown arc, in every layout that owns a fixed top row. */
internal const val WATCH_TOP_RATIO = 0.12f

/** Where the scrolling content ends; the dock and the lower arc occupy the rest. */
internal const val WATCH_BOTTOM_RATIO = 0.72f

internal const val WATCH_CONTENT_RATIO = 0.84f

/**
 * Horizontal inset where a display of [width] is clipped by its circle between
 * [top] and [bottom]. The narrowest chord of the band wins, so content never
 * pokes out of the circle even as it is scrolled under the arc.
 */
internal fun watchChordInset(width: Dp, top: Dp, bottom: Dp = top): Dp =
    calculateChordInset(width.value, top.value, bottom.value).dp

internal fun calculateChordInset(width: Float, top: Float, bottom: Float): Float {
    if (width <= 0f) return 0f
    val radius = width / 2f
    val far = maxOf(abs(top - radius), abs(bottom - radius))
    val half = sqrt((radius * radius - far * far).coerceAtLeast(0f))
    return (radius - half).coerceAtLeast(0f)
}

internal fun watchHeaderTop(width: Dp, theme: ComposeThemeState): Dp =
    if (theme.roundScreen) width * WATCH_TOP_RATIO else (4 * theme.uiScale).dp

internal fun watchHeaderHorizontal(width: Dp, theme: ComposeThemeState): Dp {
    if (!theme.roundScreen) return (8 * theme.uiScale).dp
    val top = watchHeaderTop(width, theme)
    return watchChordInset(width, top, top + 8.dp) + 2.dp
}

/** Scrolling text keeps its width; only leading controls reserve the narrow upper chord. */
internal fun watchPageHorizontal(width: Dp, theme: ComposeThemeState): Dp =
    if (theme.roundScreen) width * ((1f - WATCH_CONTENT_RATIO) / 2f) else (12 * theme.uiScale).dp

internal fun watchReadingHorizontal(width: Dp, theme: ComposeThemeState): Dp =
    maxOf(watchPageHorizontal(width, theme), (theme.reading.pagePadding * theme.uiScale).dp)
        .coerceAtMost(width / 2f)

/** Leading headings fit the upper chord while the article below retains its normal width. */
internal fun watchLeadingHeadingHorizontal(contentWidth: Dp, theme: ComposeThemeState): Dp {
    if (!theme.roundScreen) return 0.dp
    val pageWidth = contentWidth / WATCH_CONTENT_RATIO
    return (watchHeaderHorizontal(pageWidth, theme) - watchPageHorizontal(pageWidth, theme))
        .coerceAtLeast(0.dp)
}
