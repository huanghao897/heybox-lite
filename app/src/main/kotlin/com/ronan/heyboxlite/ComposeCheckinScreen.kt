package com.ronan.heyboxlite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview

internal @Composable fun ComposeCheckinScreen(
    services: ComposeServices,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
) {
    HeyboxComposeTheme(services.theme) {
        val coordinator = services.checkin
        if (coordinator == null) {
            ComposeCheckinUnavailableScreen(onBack)
            return@HeyboxComposeTheme
        }
        val controller = remember(coordinator) {
            ComposeCheckinController(services, coordinator)
        }
        DisposableEffect(controller) {
            controller.start()
            onDispose { controller.close() }
        }
        val state = controller.uiState
        when (state.route) {
            ComposeCheckinRoute.CENTER -> ComposeCheckinCenterContent(
                state = state,
                onBack = { controller.navigateBack(onBack) },
                onConnect = controller::openPairing,
                onRefresh = controller::refreshStatus,
                onLogin = controller::openMobileLogin,
                onRunNow = controller::runNow,
                onTaskSettings = controller::openTaskSettings,
                onSponsorship = controller::openSponsorship,
                onLeaderboard = { onNavigate("leaderboard") },
                onRevoke = controller::requestRevoke,
                onHistoryRetry = controller::refreshHistory,
            )
            ComposeCheckinRoute.PAIRING -> ComposeCheckinPairingScreen(
                state,
                controller,
                { controller.navigateBack(onBack) },
            )
            ComposeCheckinRoute.MOBILE_LOGIN -> ComposeCheckinMobileLoginScreen(
                state,
                controller,
                { controller.navigateBack(onBack) },
            )
            ComposeCheckinRoute.TASK_SETTINGS -> ComposeCheckinTaskSettingsScreen(
                state,
                controller,
                { controller.navigateBack(onBack) },
            )
            ComposeCheckinRoute.SPONSORSHIP -> ComposeCheckinSponsorshipScreen(
                state,
                controller,
                { controller.navigateBack(onBack) },
            )
        }
        if (state.showRevokeConfirm) {
            ComposeConfirmDialog(
                title = "撤销此设备",
                message = "撤销后，这台设备需要重新连接才能查看或执行小黑盒签到。服务器中的定时任务不会自动删除。",
                onDismiss = controller::dismissRevoke,
                onConfirm = controller::revokeDevice,
            )
        }
    }
}

@Composable
private fun ComposeCheckinUnavailableScreen(onBack: () -> Unit) {
    WatchPage("小黑盒签到", onBack) {
        WatchCard(highlighted = true) {
            Text(
                text = "签到服务暂不可用",
                color = LocalHeyboxTheme.current.text,
                fontSize = watchSp(15f),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(watchDp(5)))
            Text(
                text = "请稍后重新打开此页面。",
                color = LocalHeyboxTheme.current.muted,
                fontSize = watchSp(12f),
            )
        }
    }
}

@Composable
internal fun ComposeCheckinCenterContent(
    state: ComposeCheckinUiState,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    onRunNow: () -> Unit,
    onTaskSettings: () -> Unit,
    onSponsorship: (CheckinBilling.Membership?) -> Unit,
    onLeaderboard: () -> Unit,
    onRevoke: () -> Unit,
    onHistoryRetry: () -> Unit,
) {
    WatchPage("小黑盒签到", onBack) {
        WatchSectionTitle(if (!state.paired) "连接" else "状态")
        val status = state.status
        if (!state.paired) {
            ComposeCheckinUnpairedCard(state, onConnect)
        } else if (status == null) {
            ComposeCheckinLoadingCard(state, onRefresh)
        } else {
            ComposeCheckinConnectedContent(
                state = state,
                onLogin = onLogin,
                onRunNow = onRunNow,
                onTaskSettings = onTaskSettings,
                onSponsorship = onSponsorship,
                onLeaderboard = onLeaderboard,
                onRevoke = onRevoke,
                onHistoryRetry = onHistoryRetry,
            )
        }
    }
}

@Composable
private fun ComposeCheckinUnpairedCard(
    state: ComposeCheckinUiState,
    onConnect: () -> Unit,
) {
    WatchCard(highlighted = true) {
        Text(
            text = if (state.errorMessage.isEmpty()) "未连接" else "连接不可用",
            color = LocalHeyboxTheme.current.text,
            fontSize = watchSp(15f),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(watchDp(4)))
        Text(
            text = state.errorMessage.ifEmpty {
                if (state.supported) "连接后由服务器按计划执行" else "当前设备不支持安全连接"
            },
            color = LocalHeyboxTheme.current.muted,
            fontSize = watchSp(12f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(watchDp(9)))
        Text(
            text = "登录签到服务后，再连接需要签到的小黑盒账号。",
            color = LocalHeyboxTheme.current.muted,
            fontSize = watchSp(12f),
        )
        Spacer(modifier = Modifier.height(watchDp(11)))
        ComposeCheckinPrimaryButton(
            text = "连接签到服务",
            enabled = state.supported,
            onClick = onConnect,
        )
    }
}

@Composable
private fun ComposeCheckinLoadingCard(
    state: ComposeCheckinUiState,
    onRefresh: () -> Unit,
) {
    WatchCard(highlighted = state.stage == ComposeCheckinStage.ERROR) {
        Text(
            text = if (state.stage == ComposeCheckinStage.ERROR) "暂时无法连接" else "正在连接",
            color = LocalHeyboxTheme.current.text,
            fontSize = watchSp(15f),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(watchDp(4)))
        Text(
            text = state.errorMessage.ifEmpty { "正在读取账号与签到计划" },
            color = LocalHeyboxTheme.current.muted,
            fontSize = watchSp(12f),
        )
        if (state.stage == ComposeCheckinStage.ERROR) {
            Spacer(modifier = Modifier.height(watchDp(8)))
            ComposeCheckinQuietButton("重新加载", onClick = onRefresh)
        }
    }
}

@Composable
private fun ComposeCheckinConnectedContent(
    state: ComposeCheckinUiState,
    onLogin: () -> Unit,
    onRunNow: () -> Unit,
    onTaskSettings: () -> Unit,
    onSponsorship: (CheckinBilling.Membership?) -> Unit,
    onLeaderboard: () -> Unit,
    onRevoke: () -> Unit,
    onHistoryRetry: () -> Unit,
) {
    val status = state.status ?: return
    val accountConnected = status.account.state.equals("connected", ignoreCase = true)
    WatchCard(highlighted = state.stage == ComposeCheckinStage.RUNNING) {
        Text(
            text = if (!accountConnected) "等待手机号登录"
            else if (state.stage == ComposeCheckinStage.RUNNING) "执行中"
            else taskStateLabel(status.task),
            color = if (status.task.active() || state.stage == ComposeCheckinStage.RUNNING) {
                LocalHeyboxTheme.current.text
            } else {
                LocalHeyboxTheme.current.muted
            },
            fontSize = watchSp(15f),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(watchDp(4)))
        Text(
            text = if (accountConnected) accountLabel(status.account) else "尚未连接小黑盒账号",
            color = LocalHeyboxTheme.current.muted,
            fontSize = watchSp(12f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!accountConnected) {
            Spacer(modifier = Modifier.height(watchDp(9)))
            Text(
                text = "自动签到需要单独使用手机号登录小黑盒。",
                color = LocalHeyboxTheme.current.muted,
                fontSize = watchSp(12f),
            )
            Spacer(modifier = Modifier.height(watchDp(10)))
            ComposeCheckinPrimaryButton("手机号登录", onClick = onLogin)
        } else {
            Spacer(modifier = Modifier.height(watchDp(9)))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("下次签到", color = LocalHeyboxTheme.current.subtle, fontSize = watchSp(10f))
                    Spacer(modifier = Modifier.height(watchDp(2)))
                    Text(
                        text = windowLabel(status.task).ifEmpty {
                            CheckinTaskSettingsView.enabledLabel(status.task)
                        },
                        color = LocalHeyboxTheme.current.muted,
                        fontSize = watchSp(11f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = CheckinTaskSettingsView.scheduleLabel(status.task),
                    color = LocalHeyboxTheme.current.text,
                    fontSize = watchSp(20f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(watchDp(10)))
            ComposeCheckinPrimaryButton(
                text = if (state.stage == ComposeCheckinStage.RUNNING) "正在签到" else "立即签到",
                enabled = state.stage != ComposeCheckinStage.RUNNING && status.task.active(),
                onClick = onRunNow,
            )
        }
    }

    WatchSectionTitle(if (accountConnected) "管理" else "服务")
    WatchCard {
        if (accountConnected) {
            WatchRow(
                title = "签到设置",
                value = CheckinTaskSettingsView.scheduleLabel(status.task) + " · " +
                    CheckinTaskSettingsView.offsetLabel(status.task.offsetMinutes),
                icon = R.drawable.il_settings,
                onClick = onTaskSettings,
            )
        }
        AddSponsorshipRow(status.membership, onSponsorship)
        WatchRow(
            title = "连续签到排行榜",
            icon = R.drawable.il_leaderboard,
            onClick = onLeaderboard,
        )
        if (accountConnected) {
            WatchRow(
                title = "更换账号",
                value = "手机号登录",
                icon = R.drawable.il_person,
                onClick = onLogin,
            )
        }
        WatchRow(
            title = "撤销此设备",
            icon = R.drawable.ic_logout,
            enabled = state.stage != ComposeCheckinStage.RUNNING,
            onClick = onRevoke,
        )
    }

    if (accountConnected) {
        ComposeCheckinHistoryContent(state, onHistoryRetry)
    }
}

@Composable
    private fun AddSponsorshipRow(
    membership: CheckinBilling.Membership?,
    onSponsorship: (CheckinBilling.Membership?) -> Unit,
) {
    if (membership == null || !membership.voluntarySponsorship) return
    WatchRow(
        title = "赞助",
        value = if (membership.checkoutAvailable) "自愿支持" else "暂不可用",
        icon = R.drawable.il_qr,
        enabled = membership.checkoutAvailable,
        onClick = if (membership.checkoutAvailable) {
            { onSponsorship(membership) }
        } else {
            null
        },
    )
}

@Composable
private fun ComposeCheckinHistoryContent(
    state: ComposeCheckinUiState,
    onRetry: () -> Unit,
) {
    WatchSectionTitle("最近签到")
    WatchCard {
        when {
            state.historyLoading -> {
                WatchRow("正在同步记录", "读取最近执行结果", R.drawable.il_refresh)
            }
            state.historyError.isNotEmpty() -> {
                WatchRow("暂时无法读取记录", state.historyError, R.drawable.il_info)
                WatchActionText("重新读取", onRetry)
            }
            state.history == null || state.history.entries.isEmpty() -> {
                WatchRow("暂无记录", "签到任务执行后会显示在这里", R.drawable.il_history)
            }
            else -> state.history.entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(watchDp(1))
                            .padding(horizontal = watchDp(4)),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = watchDp(11), vertical = watchDp(8)),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.state,
                            color = if (entry.state == "失败") {
                                LocalHeyboxTheme.current.muted
                            } else {
                                LocalHeyboxTheme.current.text
                            },
                            fontSize = watchSp(13f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.height(watchDp(2)))
                        Text(
                            text = entry.summaryPreview(),
                            color = LocalHeyboxTheme.current.muted,
                            fontSize = watchSp(11f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(watchDp(8)))
                    Text(
                        text = entry.displayTime(),
                        color = LocalHeyboxTheme.current.subtle,
                        fontSize = watchSp(10f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun accountLabel(account: CheckinCenterClient.Account): String {
    val name = account.displayName.ifEmpty { "小黑盒账号" }
    return if (account.externalIdMasked.isEmpty()) name
    else "$name  ${account.externalIdMasked}"
}

private fun taskStateLabel(task: CheckinCenterClient.Task): String {
    if (task.platformBlocked || task.signBlocked) return "自动签到已暂停"
    return if (task.active()) "自动签到已启用" else "自动签到未启用"
}

private fun windowLabel(task: CheckinCenterClient.Task): String {
    if (task.windowStart.isEmpty()) return task.windowEnd
    if (task.windowEnd.isEmpty()) return task.windowStart
    return "${task.windowStart} - ${task.windowEnd}"
}

@Preview(name = "Checkin round", showBackground = true, widthDp = 192, heightDp = 192)
@Composable
private fun ComposeCheckinRoundPreview() {
    HeyboxComposeTheme(composePreviewTheme(roundScreen = true)) {
        ComposeCheckinCenterContent(
            state = ComposeCheckinUiState(supported = true),
            onBack = {},
            onConnect = {},
            onRefresh = {},
            onLogin = {},
            onRunNow = {},
            onTaskSettings = {},
            onSponsorship = {},
            onLeaderboard = {},
            onRevoke = {},
            onHistoryRetry = {},
        )
    }
}
