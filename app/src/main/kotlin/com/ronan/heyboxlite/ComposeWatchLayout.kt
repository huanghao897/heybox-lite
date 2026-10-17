package com.ronan.heyboxlite

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/** Only fixed controls use the upper chord; scrolling content keeps its width. */
internal fun watchHeaderTop(width: Dp, theme: ComposeThemeState): Dp =
    if (theme.roundScreen) width * 0.12f else (4 * theme.uiScale).dp

internal fun watchHeaderHorizontal(width: Dp, theme: ComposeThemeState): Dp {
    if (!theme.roundScreen) return (8 * theme.uiScale).dp
    val radius = width.value / 2f
    val distance = radius - watchHeaderTop(width, theme).value
    return (radius - sqrt((radius * radius - distance * distance).coerceAtLeast(0f))).dp + 2.dp
}

internal fun watchPageHorizontal(width: Dp, theme: ComposeThemeState): Dp =
    if (theme.roundScreen) width * 0.08f else (12 * theme.uiScale).dp

/** Leading headings fit the upper chord while the article below retains its normal width. */
internal fun watchLeadingHeadingHorizontal(contentWidth: Dp, theme: ComposeThemeState): Dp {
    if (!theme.roundScreen) return 0.dp
    val pageWidth = contentWidth / 0.84f
    return (watchHeaderHorizontal(pageWidth, theme) - watchPageHorizontal(pageWidth, theme))
        .coerceAtLeast(0.dp)
}
