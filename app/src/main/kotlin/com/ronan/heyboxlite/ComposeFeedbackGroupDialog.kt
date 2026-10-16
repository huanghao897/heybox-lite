package com.ronan.heyboxlite

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun ComposeFeedbackGroupDialog(onDismiss: () -> Unit) {
    val theme = LocalHeyboxTheme.current
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val diameter = minOf(maxWidth, maxHeight)
            val width = if (theme.roundScreen) diameter * 0.68f else minOf(maxWidth - 16.dp, 280.dp)
            val height = if (theme.roundScreen) diameter * 0.68f else maxHeight - 16.dp
            val shape = RoundedCornerShape(watchDp(8))
            Column(Modifier.width(width).heightIn(max = height)
                .testTag("feedback-group-dialog").clip(shape).background(theme.panel)
                .border(0.5.dp, theme.hairline, shape).padding(watchDp(8))) {
                BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    val qrSize = minOf(maxWidth, maxHeight, 200.dp)
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(watchDp(6))) {
                        Text("\u4ea4\u6d41\u7fa4", color = theme.text, fontSize = watchSp(13f),
                            fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
                        Image(painterResource(R.drawable.qq_feedback_group_qr),
                            "QQ\u7fa4 781941517 \u4e8c\u7ef4\u7801", Modifier.size(qrSize))
                        Text("QQ\u7fa4\uff1a781941517", color = theme.muted,
                            fontSize = watchSp(11f), letterSpacing = 0.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(watchDp(6)))
                WatchActionButton("\u786e\u5b9a", onDismiss, Modifier.fillMaxWidth())
            }
        }
    }
}
