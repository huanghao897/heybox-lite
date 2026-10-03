package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val taskTimePattern = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
private val taskOffsetPattern = Regex("[0-9]{1,3}")

internal fun parseCheckinTaskTime(input: String): String? =
    input.trim().takeIf { taskTimePattern.matches(it) }

internal fun parseCheckinTaskOffset(input: String): Int? {
    val text = input.trim()
    if (!taskOffsetPattern.matches(text)) return null
    return text.toIntOrNull()?.takeIf { it in 0..720 }
}

@Composable
internal fun CheckinTaskTimeDialog(
    initial: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(initial) }
    val time = parseCheckinTaskTime(draft)
    TaskSettingsInputDialog(
        title = "\u6267\u884c\u65f6\u95f4",
        label = "\u8f93\u5165\u6267\u884c\u65f6\u95f4",
        draft = draft,
        hint = "HH:mm",
        errorMessage = if (time == null) "\u8bf7\u8f93\u5165 00:00-23:59" else "",
        keyboardType = KeyboardType.Ascii,
        enabled = enabled,
        valid = time != null,
        onDraftChange = { draft = it },
        onDismiss = onDismiss,
        onConfirm = { if (enabled && time != null) onConfirm(time) },
    )
}

@Composable
internal fun CheckinTaskOffsetDialog(
    initial: Int,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(initial.toString()) }
    val offset = parseCheckinTaskOffset(draft)
    TaskSettingsInputDialog(
        title = "\u968f\u673a\u504f\u79fb",
        label = "\u8f93\u5165\u968f\u673a\u504f\u79fb\u5206\u949f",
        draft = draft,
        hint = "0-720",
        errorMessage = if (offset == null) "\u8bf7\u8f93\u5165 0-720 \u5206\u949f" else "",
        keyboardType = KeyboardType.Number,
        enabled = enabled,
        valid = offset != null,
        onDraftChange = { draft = it },
        onDismiss = onDismiss,
        onConfirm = { if (enabled && offset != null) onConfirm(offset) },
    )
}

@Composable
private fun TaskSettingsInputDialog(
    title: String,
    label: String,
    draft: String,
    hint: String,
    errorMessage: String,
    keyboardType: KeyboardType,
    enabled: Boolean,
    valid: Boolean,
    onDraftChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    TaskSettingsDialogSurface(onDismiss) {
        Text(title, color = theme.text, fontSize = watchSp(13f),
            fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center)
        BasicTextField(
            value = draft,
            onValueChange = onDraftChange,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(color = theme.text, fontSize = watchSp(17f),
                textAlign = TextAlign.Center),
            cursorBrush = SolidColor(theme.text),
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = watchDp(36))
                .background(theme.panelElevated, RoundedCornerShape(watchDp(6)))
                .padding(horizontal = watchDp(8), vertical = watchDp(6))
                .semantics {
                    contentDescription = label
                    if (errorMessage.isNotBlank()) error(errorMessage)
                },
            decorationBox = { field ->
                if (draft.isEmpty()) {
                    Text(hint, color = theme.subtle, fontSize = watchSp(17f),
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                field()
            },
        )
        if (errorMessage.isNotBlank()) {
            Text(errorMessage, color = theme.muted, fontSize = watchSp(10f),
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(watchDp(4))) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = watchDp(4))) {
                Text("\u53d6\u6d88", color = theme.muted, fontSize = watchSp(12f))
            }
            TextButton(onClick = onConfirm, enabled = enabled && valid,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = watchDp(4))) {
                Text("\u786e\u8ba4", color = if (enabled && valid) theme.text else theme.subtle,
                    fontSize = watchSp(12f))
            }
        }
    }
}

@Composable
private fun TaskSettingsDialogSurface(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding(),
            contentAlignment = Alignment.Center) {
            // An inscribed square keeps every corner inside the circular display.
            val diameter = minOf(maxWidth, maxHeight)
            val width = if (theme.roundScreen) diameter * 0.68f
                else minOf(maxWidth - 16.dp, 280.dp)
            val height = if (theme.roundScreen) diameter * 0.68f else maxHeight - 16.dp
            Column(
                modifier = Modifier.width(width.coerceAtLeast(1.dp))
                    .heightIn(max = height.coerceAtLeast(1.dp))
                    .clip(RoundedCornerShape(watchDp(8)))
                    .background(theme.panel)
                    .verticalScroll(rememberScrollState())
                    .padding(watchDp(10)),
                verticalArrangement = Arrangement.spacedBy(watchDp(5)),
                content = content,
            )
        }
    }
}
