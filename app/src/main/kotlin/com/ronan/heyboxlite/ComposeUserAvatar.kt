package com.ronan.heyboxlite

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComposeUserAvatar(
    url: String,
    name: String,
    media: ComposeMediaSettings,
    size: Dp,
    onOpenUser: (() -> Unit)?,
) {
    val description = "${name}的头像"
    ComposeMediaImage(
        url, media,
        modifier = Modifier.size(size)
            .semantics { contentDescription = description }
            .combinedClickable(
                enabled = onOpenUser != null,
                onLongClickLabel = "查看用户主页",
                onLongClick = onOpenUser,
                onClick = {},
            ),
        shape = CircleShape,
        contentDescription = description,
        placeholderLabel = name.take(1),
        ensureTouchTarget = false,
    )
}
