package com.ronan.heyboxlite

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
internal fun ComposeCheckinTaskSettingsScreen(
    state: ComposeCheckinUiState,
    controller: ComposeCheckinController,
    onBack: () -> Unit,
) {
    val task = state.status?.task
    val context = LocalContext.current
    WatchPage("签到设置", onBack) {
        if (task == null) {
            WatchEmptyState("签到设置暂不可用")
        } else {
            WatchSectionTitle("计划")
            WatchCard {
                WatchSwitchRow(
                    title = "自动签到",
                    checked = task.enabled,
                    icon = R.drawable.il_calendar,
                    enabled = !state.taskSaving,
                    onCheckedChange = controller::setTaskEnabled,
                )
                WatchRow(
                    title = "执行时间",
                    value = CheckinTaskSettingsView.scheduleLabel(task),
                    icon = R.drawable.il_calendar,
                    enabled = !state.taskSaving,
                    onClick = {
                        val normalized = CheckinTaskSettingsView.normalizedTime(task).split(":")
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                controller.setTaskTime(
                                    String.format(Locale.US, "%02d:%02d", hour, minute),
                                )
                            },
                            normalized[0].toInt(),
                            normalized[1].toInt(),
                            true,
                        ).show()
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = watchDp(11),
                        vertical = watchDp(6),
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.il_scroll),
                        contentDescription = null,
                        tint = LocalHeyboxTheme.current.muted,
                        modifier = Modifier.size(watchDp(18)),
                    )
                    Spacer(modifier = Modifier.width(watchDp(8)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "随机偏移",
                            color = LocalHeyboxTheme.current.text,
                            fontSize = watchSp(13f),
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            CheckinTaskSettingsView.offsetLabel(task.offsetMinutes),
                            color = LocalHeyboxTheme.current.muted,
                            fontSize = watchSp(11f),
                        )
                    }
                    IconButton(
                        onClick = { controller.adjustTaskOffset(-30) },
                        enabled = !state.taskSaving && task.offsetMinutes > 0,
                        modifier = Modifier.size(watchDp(34)),
                    ) { Text("-", fontSize = watchSp(18f)) }
                    IconButton(
                        onClick = { controller.adjustTaskOffset(30) },
                        enabled = !state.taskSaving && task.offsetMinutes < 720,
                        modifier = Modifier.size(watchDp(34)),
                    ) { Text("+", fontSize = watchSp(17f)) }
                }
                if (task.platformBlocked || task.signBlocked) {
                    Text(
                        "服务已暂停此任务，当前设置会保留。",
                        color = LocalHeyboxTheme.current.muted,
                        fontSize = watchSp(11f),
                        modifier = Modifier.padding(
                            horizontal = watchDp(11),
                            vertical = watchDp(5),
                        ),
                    )
                }
            }
            WatchSectionTitle("分享任务")
            WatchCard {
                if (task.sharing.available) {
                    WatchSwitchRow(
                        "分享帖子",
                        task.sharing.post,
                        R.drawable.il_globe,
                        !state.taskSaving,
                    ) { controller.setShare("share_post", it) }
                    WatchSwitchRow(
                        "分享游戏",
                        task.sharing.game,
                        R.drawable.il_globe,
                        !state.taskSaving,
                    ) { controller.setShare("share_game", it) }
                    WatchSwitchRow(
                        "分享评价",
                        task.sharing.review,
                        R.drawable.il_info,
                        !state.taskSaving,
                    ) { controller.setShare("share_review", it) }
                } else {
                    WatchRow("暂未开放", icon = R.drawable.il_info, enabled = false)
                }
            }
        }
    }
}
