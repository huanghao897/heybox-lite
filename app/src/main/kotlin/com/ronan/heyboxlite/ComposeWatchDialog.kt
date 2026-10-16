package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun WatchDialog(
    title: String?,
    onDismiss: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding().testTag("watch-dialog-viewport")
            .pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) },
            contentAlignment = Alignment.Center) {
            // Inscribe the frame in the measured viewport, including when the IME resizes it.
            val diameter = minOf(maxWidth, maxHeight)
            val width = if (theme.roundScreen) diameter * 0.68f
                else minOf(maxWidth - 16.dp, 320.dp)
            val height = if (theme.roundScreen) diameter * 0.68f else maxHeight - 16.dp
            val shape = RoundedCornerShape(watchDp(8))
            Column(Modifier.width(width.coerceAtLeast(1.dp))
                .heightIn(max = height.coerceAtLeast(1.dp))
                .testTag("watch-dialog-frame").clip(shape).background(theme.panel)
                .border(0.5.dp, theme.hairline, shape)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .padding(watchDp(8))) {
                Column(Modifier.weight(1f, fill = false).fillMaxWidth()
                    .testTag("watch-dialog-content").verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(watchDp(6))) {
                    if (!title.isNullOrBlank()) {
                        Text(title, color = theme.text, fontSize = watchSp(15f),
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                    }
                    content()
                }
                if (actions != null) {
                    Spacer(Modifier.height(watchDp(6)))
                    Row(Modifier.fillMaxWidth().testTag("watch-dialog-actions"),
                        horizontalArrangement = Arrangement.spacedBy(watchDp(6)),
                        verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
        }
    }
}
