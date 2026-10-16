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
