package com.ronan.heyboxlite

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeSectionChip(
    name: String,
    iconUrl: String,
    theme: ComposeThemeState,
    noImage: Boolean,
    modifier: Modifier = Modifier,
    imageLoader: ComposeImageLoader = ExistingComposeImageLoader,
) {
    if (name.isBlank()) return
    val scale = theme.uiScale
    val iconSize = (12 * scale).dp.coerceIn(10.dp, 14.dp)
    val horizontalPadding = (6 * scale).dp.coerceAtLeast(
        (3 * theme.textScale * LocalDensity.current.fontScale).dp)
    Row(
        modifier = modifier
            .widthIn(max = (112 * scale).dp.coerceAtLeast(80.dp))
            .testTag("section-chip")
            .semantics(mergeDescendants = true) { contentDescription = "\u5206\u533a\uff1a$name" }
            .clip(CircleShape)
            .background(theme.panelElevated)
            .border(BorderStroke(0.5.dp, theme.hairline), CircleShape)
            .padding(horizontal = horizontalPadding, vertical = (2 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((3 * scale).dp),
    ) {
        // No-image mode removes the whole icon slot, not only its bitmap.
        if (!noImage) {
            val icon = Modifier.size(iconSize).testTag("section-chip-icon")
            if (iconUrl.isNotBlank()) {
                ComposeRemoteImage(iconUrl, theme, false, iconSize, CircleShape,
                    contentDescription = null, modifier = icon, loader = imageLoader)
            } else {
                Icon(painterResource(R.drawable.il_globe), contentDescription = null,
                    tint = theme.muted, modifier = icon)
            }
        }
        Text(
            text = name,
            color = theme.muted,
            fontSize = 10.5.sp * theme.textScale,
            lineHeight = 14.sp * theme.textScale,
            letterSpacing = 0.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).testTag("section-chip-label"),
        )
    }
}
