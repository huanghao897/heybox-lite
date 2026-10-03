package com.ronan.heyboxlite

import android.text.TextUtils
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Only inline images and game-link spans need the native renderer in feed cards. */
@Composable
internal fun ComposeFeedText(
    source: String,
    rich: Boolean,
    theme: ComposeThemeState,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    maxLines: Int,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    if (rich) {
        ComposeRichText(
            source = source,
            darkMode = theme.dark,
            textColor = color,
            linkColor = theme.link,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            maxLines = maxLines,
            ellipsize = TextUtils.TruncateAt.END,
            modifier = modifier,
        )
    } else {
        Text(
            text = source,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = if (fontWeight.weight >= FontWeight.Medium.weight) FontWeight.Medium else FontWeight.Normal,
            letterSpacing = 0.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            modifier = modifier,
        )
    }
}
