package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The functional input fits the round upper arc; no title or return row is reserved. */
@Composable
internal fun ComposeLeadingSearchInput(query: String, placeholder: String, onQueryChange: (String) -> Unit) {
    val theme = LocalHeyboxTheme.current
    val width = LocalConfiguration.current.screenWidthDp.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val horizontal = if (theme.roundScreen) {
            ((maxWidth - width + watchHeaderHorizontal(width, theme) * 2) / 2).coerceAtLeast(0.dp)
        } else 0.dp
        val top = if (theme.roundScreen) watchHeaderTop(width, theme) else 0.dp
        Box(Modifier.fillMaxWidth().padding(horizontal = horizontal).padding(top = top)) {
            ComposeSearchInput(query, placeholder, onQueryChange)
        }
    }
}

@Composable
internal fun ComposeSearchInput(query: String, placeholder: String, onQueryChange: (String) -> Unit) {
    val theme = LocalHeyboxTheme.current
    val shape = RoundedCornerShape(watchDp(WatchRadius.MEDIUM))
    Row(Modifier.fillMaxWidth().heightIn(min = watchTouchDp(36)).clip(shape)
        .background(theme.panel.copy(alpha = if (theme.dark) 0.86f else 0.72f))
        .border(watchDp(1), theme.hairline, shape).padding(horizontal = watchDp(8)),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(androidx.compose.ui.res.painterResource(R.drawable.il_search),
            null, tint = theme.muted, modifier = Modifier.size(watchDp(WatchIcon.MEDIUM)))
        Spacer(Modifier.width(watchDp(6)))
        BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true,
            textStyle = TextStyle(color = theme.text, fontSize = watchSp(WatchType.SECONDARY)),
            modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(placeholder, color = theme.muted,
                        fontSize = watchSp(WatchType.SECONDARY), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                }
            })
        if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") },
            modifier = Modifier.size(WatchTouchTarget)) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_close), "清除",
                tint = theme.muted, modifier = Modifier.size(watchDp(WatchIcon.SMALL)))
        }
    }
}
