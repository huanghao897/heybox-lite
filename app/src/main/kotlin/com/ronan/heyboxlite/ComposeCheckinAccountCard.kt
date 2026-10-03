package com.ronan.heyboxlite

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeCheckinAccountCard(
    status: CheckinCenterClient.Status,
    stage: ComposeCheckinStage,
    onLogin: () -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    val account = status.account
    val connected = account.state.equals("connected", true)
    val statusText = checkinAccountStatusText(status, stage, connected)
    val statusColor = checkinAccountStatusColor(status, stage, connected)

    CheckinPanel(Modifier.testTag("checkin-account-card")) {
        BoxWithConstraints(Modifier.fillMaxWidth().clickable(onClick = onLogin)) {
            val compact = maxWidth < watchDp(230) * theme.textScale * LocalDensity.current.fontScale
            val narrow = maxWidth < watchDp(180) * theme.textScale * LocalDensity.current.fontScale
            val accountName = account.displayName.ifEmpty { "小黑盒账号" }
            val externalId = account.externalIdMasked.ifEmpty { "尚未连接" }
            if (narrow) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = watchDp(12), vertical = watchDp(10)),
                    verticalArrangement = Arrangement.spacedBy(watchDp(5)),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CheckinAccountAvatar()
                        Spacer(Modifier.width(watchDp(9)))
                        Text(accountName, color = theme.text, fontSize = watchSp(13f),
                            fontWeight = FontWeight.SemiBold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, letterSpacing = 0.sp,
                            modifier = Modifier.weight(1f))
                    }
                    Text(externalId, color = theme.muted, fontSize = watchSp(10f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis, letterSpacing = 0.sp)
                    CheckinAccountStatusBadge(statusText, statusColor)
                }
            } else if (compact) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = watchDp(12), vertical = watchDp(10)),
                    verticalAlignment = Alignment.Top,
                ) {
                    CheckinAccountAvatar()
                    Spacer(Modifier.width(watchDp(9)))
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(watchDp(2)),
                    ) {
                        Text(accountName, color = theme.text, fontSize = watchSp(13f),
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(externalId, color = theme.muted, fontSize = watchSp(10f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        CheckinAccountStatusBadge(statusText, statusColor,
                            Modifier.padding(top = watchDp(2)))
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = watchDp(12), vertical = watchDp(10)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckinAccountAvatar()
                    Spacer(Modifier.width(watchDp(9)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(watchDp(2))) {
                        Text(accountName, color = theme.text, fontSize = watchSp(13f),
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(externalId, color = theme.muted, fontSize = watchSp(10f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(watchDp(7)))
                    CheckinAccountStatusBadge(statusText, statusColor,
                        Modifier.widthIn(max = watchDp(96)))
                }
            }
        }
        if (connected) {
            CheckinDivider()
            BoxWithConstraints(
                Modifier.fillMaxWidth().padding(horizontal = watchDp(12), vertical = watchDp(10)),
            ) {
                val stacked = maxWidth < watchDp(190) * theme.textScale * LocalDensity.current.fontScale
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(watchDp(8))) {
                        CheckinScheduleField(
                            "下次签到时段",
                            checkinWindow(status.task).ifEmpty { "未设置" },
                            prominent = false,
                        )
                        CheckinScheduleField(
                            "预计执行",
                            CheckinTaskSettingsView.scheduleLabel(status.task),
                            prominent = true,
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CheckinScheduleField(
                            "下次签到时段",
                            checkinWindow(status.task).ifEmpty { "未设置" },
                            prominent = false,
                            modifier = Modifier.weight(1.2f),
                        )
                        Spacer(
                            Modifier.width(0.5.dp).height(watchDp(30))
                                .clip(RoundedCornerShape(1.dp))
                                .background(theme.hairline),
                        )
                        CheckinScheduleField(
                            "预计执行",
                            CheckinTaskSettingsView.scheduleLabel(status.task),
                            prominent = true,
                            modifier = Modifier.weight(1f).padding(start = watchDp(10)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckinAccountAvatar() {
    Image(
        painterResource(R.mipmap.heywear),
        contentDescription = "小黑盒账号头像",
        modifier = Modifier.size(watchDp(34)).clip(RoundedCornerShape(watchDp(8))),
    )
}

@Composable
private fun CheckinAccountStatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        fontSize = watchSp(9f),
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
        maxLines = 2,
        softWrap = true,
        overflow = TextOverflow.Clip,
        modifier = modifier.clip(RoundedCornerShape(watchDp(6)))
            .background(LocalHeyboxTheme.current.panelElevated)
            .padding(horizontal = watchDp(7), vertical = watchDp(4)),
    )
}

@Composable
private fun CheckinScheduleField(
    label: String,
    value: String,
    prominent: Boolean,
    modifier: Modifier = Modifier,
) {
    val theme = LocalHeyboxTheme.current
    Column(modifier) {
        Text(label, color = theme.muted, fontSize = watchSp(10f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            color = theme.text,
            fontSize = watchSp(if (prominent) 18f else 13f),
            fontWeight = if (prominent) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(top = watchDp(3)),
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun checkinAccountStatusText(
    status: CheckinCenterClient.Status,
    stage: ComposeCheckinStage,
    connected: Boolean,
): String = when {
    !connected -> "等待手机号登录"
    stage == ComposeCheckinStage.RUNNING -> "签到执行中"
    status.membership.required && !status.membership.entitled -> "等待开通会员"
    status.task.platformBlocked || status.task.signBlocked -> "自动签到已暂停"
    status.task.active() -> "自动签到已开启"
    else -> "自动签到未开启"
}

@Composable
private fun checkinAccountStatusColor(
    status: CheckinCenterClient.Status,
    stage: ComposeCheckinStage,
    connected: Boolean,
): Color {
    val theme = LocalHeyboxTheme.current
    return if (connected && stage != ComposeCheckinStage.RUNNING &&
        status.task.active() && (!status.membership.required || status.membership.entitled)
    ) theme.accent else theme.muted
}
