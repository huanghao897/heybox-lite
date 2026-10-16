package com.ronan.heyboxlite

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class ComposeCommentLayout(
    val compactHeader: Boolean,
    val avatarSize: Dp,
    val avatarGap: Dp,
    val bodyInset: Dp,
)

internal fun composeCommentLayout(
    width: Dp,
    roundScreen: Boolean,
    uiScale: Float,
    textScale: Float,
    systemFontScale: Float,
): ComposeCommentLayout {
    val compact = width < 180.dp * textScale * systemFontScale
    val avatarSize = 26.dp * uiScale
    val avatarGap = 6.dp * uiScale
    return ComposeCommentLayout(
        compactHeader = compact,
        avatarSize = avatarSize,
        avatarGap = avatarGap,
        bodyInset = if (roundScreen || compact) 0.dp else avatarSize + avatarGap,
    )
}
