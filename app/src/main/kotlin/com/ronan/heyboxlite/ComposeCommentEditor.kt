package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle

@Composable
internal fun ComposeCommentEditor(state: ComposeCommentEditorState, actions: ComposeCommentActions) {
    val theme = LocalHeyboxTheme.current
    WatchDialog(if (state.replyId.isBlank()) "发表评论" else "回复评论", actions::dismiss,
        actions = {
            WatchActionButton("取消", actions::dismiss, Modifier.weight(1f), fontSize = 12f)
            WatchActionButton(if (state.sending) "发送中" else "发送", actions::send,
                Modifier.weight(1f), enabled = !state.sending && state.text.isNotBlank(),
                primary = true, fontSize = 12f)
        }) {
        if (state.recipient.isNotBlank()) Text("回复 ${state.recipient}", color = theme.muted,
            fontSize = watchSp(11f))
        BasicTextField(value = state.text, onValueChange = actions::changeText,
            enabled = !state.sending, textStyle = TextStyle(color = theme.text,
                fontSize = watchSp(13f), lineHeight = watchSp(19f)),
            modifier = Modifier.fillMaxWidth().heightIn(min = watchDp(80))
                .testTag("comment-editor-text")
                .clip(RoundedCornerShape(watchDp(8))).background(theme.panel)
                .padding(watchDp(10)),
            decorationBox = { input ->
                Column {
                    if (state.text.isEmpty()) Text("友好交流，理性讨论", color = theme.subtle,
                        fontSize = watchSp(12f))
                    input()
                }
            })
        if (state.error.isNotBlank()) Text(state.error, color = theme.muted, fontSize = watchSp(11f))
    }
}
