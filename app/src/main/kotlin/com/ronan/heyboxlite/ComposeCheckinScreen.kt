package com.ronan.heyboxlite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun ComposeCheckinScreen(services: ComposeServices, onNavigate: (String) -> Unit, onBack: () -> Unit,
                                 controller: ComposeCheckinController?, page: ComposeCheckinRoute) {
    HeyboxComposeTheme(services.theme) {
        if (controller == null) {
            WatchPage("小黑盒签到", onBack) { WatchEmptyState("签到服务暂不可用") }
            return@HeyboxComposeTheme
        }
        val state = controller.uiState.copy(route = page)
        val back = { controller.navigateBack(onBack) }
            when (page) {
                ComposeCheckinRoute.CENTER -> ComposeCheckinCenterContent(state, back,
                    controller::openPairing, controller::refreshStatus, controller::openMobileLogin,
                    controller::runNow, controller::openTaskSettings, controller::openMembership,
                    { onNavigate("leaderboard") }, controller::requestRevoke, controller::openHistory)
                ComposeCheckinRoute.PAIRING -> ComposeCheckinPairingScreen(state, controller, back)
                ComposeCheckinRoute.MOBILE_LOGIN -> ComposeCheckinMobileLoginScreen(state, controller, back)
                ComposeCheckinRoute.TASK_SETTINGS -> ComposeCheckinTaskSettingsScreen(state, controller, back)
                ComposeCheckinRoute.MEMBERSHIP -> ComposeMembershipScreen(state.membership, back,
                    controller.membership::selectProduct, controller.membership::openCheckout,
                    controller.membership::openRedeem, controller.membership::openPurchases,
                    controller.membership::refreshCatalog, controller.membership::setAmount)
                ComposeCheckinRoute.CHECKOUT -> ComposeMembershipCheckoutScreen(state.membership,
                    state.status?.account, back, controller.membership::finishOrRegenerate,
                    controller.membership::setPaymentReference, controller.membership::submitClaim)
                ComposeCheckinRoute.REDEEM -> ComposeMembershipRedeemScreen(state.membership, back,
                    controller.membership::setRedeemCode, controller.membership::redeem)
                ComposeCheckinRoute.PURCHASES -> ComposeMembershipPurchasesScreen(state.membership, back,
                    controller.membership::refreshPurchases)
                ComposeCheckinRoute.HISTORY -> ComposeCheckinHistoryScreen(state, back,
                    controller::refreshHistory, controller::openHistoryEntry)
                ComposeCheckinRoute.HISTORY_DETAIL -> ComposeCheckinHistoryDetailScreen(state.selectedHistoryEntry, back)
            }
        if (state.showRevokeConfirm) ComposeConfirmDialog("撤销此设备",
            "撤销后需重新连接才能查看或执行签到。服务器定时任务不会自动删除。",
            controller::dismissRevoke, controller::revokeDevice)
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
    onMembership: () -> Unit,
    onLeaderboard: () -> Unit,
    onRevoke: () -> Unit,
    onHistory: () -> Unit,
) {
    WatchPage("小黑盒签到", onBack) {
        val status = state.status
        if (!state.paired) {
            CheckinPanel {
                Column(Modifier.padding(watchDp(13)), verticalArrangement = Arrangement.spacedBy(watchDp(5))) {
                    Text("未连接签到服务", color = LocalHeyboxTheme.current.text,
                        fontSize = watchSp(14f), fontWeight = FontWeight.SemiBold)
                    Text("连接签到服务后，使用手机号登录需要签到的小黑盒账号。",
                        color = LocalHeyboxTheme.current.muted, fontSize = watchSp(11f))
                }
            }
            CheckinActionButton("连接签到服务", onConnect, enabled = state.supported,
                icon = R.drawable.il_calendar)
        } else if (status == null) {
            CheckinPanel {
                Column(Modifier.padding(watchDp(13))) {
                    Text(if (state.stage == ComposeCheckinStage.ERROR) "暂时无法连接" else "正在读取账号",
                        color = LocalHeyboxTheme.current.text, fontSize = watchSp(14f), fontWeight = FontWeight.SemiBold)
                    Text(state.errorMessage.ifEmpty { "读取签到计划与服务状态" },
                        color = LocalHeyboxTheme.current.muted, fontSize = watchSp(11f),
                        modifier = Modifier.padding(top = watchDp(5)))
                }
            }
            if (state.stage == ComposeCheckinStage.ERROR) CheckinActionButton("重新读取", onRefresh,
                icon = R.drawable.il_refresh)
        } else {
            val connected = status.account.state.equals("connected", true)
            ComposeCheckinAccountCard(status, state.stage, onLogin)
            if (connected) CheckinActionButton(
                if (state.stage == ComposeCheckinStage.RUNNING) "正在签到" else "立即签到", onRunNow,
                enabled = state.stage != ComposeCheckinStage.RUNNING && status.task.active() &&
                    (!status.membership.required || status.membership.entitled), icon = R.drawable.ic_play)
            else CheckinActionButton("手机号登录", onLogin, icon = R.drawable.il_person)
        }
        if (state.paired) {
            val connected = status?.account?.state.equals("connected", true)
            Column(
                Modifier.fillMaxWidth().testTag("checkin-home-entry-group"),
                verticalArrangement = Arrangement.spacedBy(watchDp(7)),
            ) {
                if (state.errorMessage.isNotBlank() && status != null) {
                    CheckinPanel {
                        CheckinMenuRow("重新读取状态", R.drawable.il_refresh, onRefresh,
                            subtitle = state.errorMessage)
                    }
                }
                if (connected) {
                    CheckinPanel {
                        CheckinMenuRow("签到设置", R.drawable.il_settings, onTaskSettings)
                    }
                }
                CheckinPanel {
                    CheckinMenuRow("会员服务", R.drawable.il_crown, onMembership)
                }
                CheckinPanel {
                    CheckinMenuRow("连续签到排行榜", R.drawable.il_leaderboard, onLeaderboard)
                }
                if (connected) {
                    CheckinPanel {
                        CheckinMenuRow("签到记录", R.drawable.il_history, onHistory)
                    }
                }
                CheckinDangerRow("撤销此设备", onRevoke,
                    enabled = state.stage != ComposeCheckinStage.RUNNING,
                    modifier = Modifier.testTag("checkin-danger-row"))
            }
        }
    }
}
