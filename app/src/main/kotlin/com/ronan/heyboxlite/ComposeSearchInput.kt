package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal fun ComposeSearchInput(query: String, placeholder: String, onQueryChange: (String) -> Unit) {
    val theme = LocalHeyboxTheme.current
    val shape = RoundedCornerShape(watchDp(10))
    Row(Modifier.fillMaxWidth().height(watchDp(36)).clip(shape)
        .background(theme.panel.copy(alpha = if (theme.dark) 0.86f else 0.72f))
        .border(watchDp(1), theme.hairline, shape).padding(horizontal = watchDp(8)),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(androidx.compose.ui.res.painterResource(R.drawable.il_search),
            null, tint = theme.muted, modifier = Modifier.size(watchDp(16)))
        Spacer(Modifier.width(watchDp(6)))
        BasicTextField(value = query, onValueChange = onQueryChange, singleLine = true,
            textStyle = TextStyle(color = theme.text, fontSize = watchSp(12f)),
            modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(placeholder, color = theme.muted,
                        fontSize = watchSp(12f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                }
            })
        if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") },
            modifier = Modifier.size(watchDp(28))) {
            Text("×", color = theme.muted, fontSize = watchSp(17f))
        }
    }
}
