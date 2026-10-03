package com.ronan.heyboxlite

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Image

@Composable
internal fun ComposeCheckinSponsorshipScreen(
    state: ComposeCheckinUiState,
    controller: ComposeCheckinController,
    onBack: () -> Unit,
) {
    val membership = state.billingMembership
    WatchPage("赞助", onBack) {
        if (membership == null) {
            WatchEmptyState("赞助信息暂不可用")
            return@WatchPage
        }
        WatchCard {
            Text(
                "自愿赞助",
                color = LocalHeyboxTheme.current.text,
                fontSize = watchSp(15f),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(watchDp(4)))
            Text("签到功能永久免费", color = LocalHeyboxTheme.current.muted, fontSize = watchSp(12f))
            Spacer(modifier = Modifier.height(watchDp(9)))
            ComposeCheckinInfoRow("用途", "服务器运行与维护")
            Spacer(modifier = Modifier.height(watchDp(5)))
            Text(
                "赞助不会解锁功能，也不会影响签到计划。",
                color = LocalHeyboxTheme.current.muted,
                fontSize = watchSp(11f),
            )
            if (state.billingOrder == null) {
                Spacer(modifier = Modifier.height(watchDp(9)))
                if (membership.plan.variableAmount) {
                    ComposeCheckinTextField(
                        value = state.billingAmount,
                        placeholder = "赞助金额（元）",
                        onValueChange = controller::setBillingAmount,
                        keyboardType = KeyboardType.Number,
                    )
                } else {
                    ComposeCheckinInfoRow(
                        "金额",
                        "¥" + SponsorshipAmount.formatYuan(membership.plan.amountCents),
                    )
                }
            }
        }

        val order = state.billingOrder
        if (order == null) {
            Text(
                if (membership.checkoutAvailable) "完全自愿，不赞助也可以使用全部签到功能"
                else "当前没有可用赞助码",
                color = LocalHeyboxTheme.current.muted,
                fontSize = watchSp(11f),
                modifier = Modifier.padding(horizontal = watchDp(3)),
            )
            ComposeCheckinPrimaryButton(
                text = "显示赞助码",
                enabled = membership.checkoutAvailable && !state.billingRequestInFlight,
                onClick = controller::createBillingOrder,
            )
        } else {
            WatchCard {
                Text(
                    "¥" + SponsorshipAmount.formatYuan(order.payableAmountCents),
                    color = LocalHeyboxTheme.current.text,
                    fontSize = watchSp(25f),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    orderStateLabel(order),
                    color = if (order.status == "paid") LocalHeyboxTheme.current.text
                    else LocalHeyboxTheme.current.muted,
                    fontSize = watchSp(11f),
                    modifier = Modifier.padding(top = watchDp(2)),
                )
                Spacer(modifier = Modifier.height(watchDp(9)))
                ComposeCheckinQr(order = order, state = state)
                Spacer(modifier = Modifier.height(watchDp(6)))
                Text(
                    "使用${providerLabel(order)}扫描赞助码",
                    color = LocalHeyboxTheme.current.muted,
                    fontSize = watchSp(11f),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                if (order.manualReview) {
                    ComposeCheckinManualClaim(order, state, controller)
                }
            }
            ComposeCheckinQuietButton(
                text = if (order.status == "paid") "完成" else "重新生成赞助码",
                enabled = !state.billingRequestInFlight && !order.pending(),
                onClick = controller::finishOrRegenerateBilling,
            )
        }
        if (state.billingMessage.isNotEmpty()) {
            Text(
                state.billingMessage,
                color = LocalHeyboxTheme.current.muted,
                fontSize = watchSp(11f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = watchDp(3)),
            )
        }
        ComposeCheckinQuietButton("返回签到中心", onClick = onBack)
    }
}

@Composable
private fun ComposeCheckinInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = LocalHeyboxTheme.current.muted, fontSize = watchSp(11f),
            modifier = Modifier.weight(0.35f))
        Text(
            value,
            color = LocalHeyboxTheme.current.text,
            fontSize = watchSp(11f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.65f),
        )
    }
}

@Composable
private fun ComposeCheckinQr(order: CheckinBilling.Order, state: ComposeCheckinUiState) {
    val bitmap = remember(state.billingQrBytes) {
        state.billingQrBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val preferred = if (LocalHeyboxTheme.current.roundScreen) 148 else 210
        val size = minOf(maxWidth - watchDp(4), watchDp(preferred))
        Box(
            modifier = Modifier
                .size(size)
                .background(Color.White, RoundedCornerShape(watchDp(4))),
            contentAlignment = Alignment.Center,
        ) {
            when {
                bitmap != null -> Image(
                    bitmap = bitmap,
                    contentDescription = "${providerLabel(order)}赞助码",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(watchDp(7)),
                )
                state.billingQrLoading -> Text("正在加载", color = Color.DarkGray,
                    fontSize = watchSp(11f))
                else -> Text("赞助码暂不可用", color = Color.DarkGray,
                    fontSize = watchSp(11f))
            }
        }
    }
}

@Composable
private fun ComposeCheckinManualClaim(
    order: CheckinBilling.Order,
    state: ComposeCheckinUiState,
    controller: ComposeCheckinController,
) {
    Spacer(modifier = Modifier.height(watchDp(8)))
    Text(
        if (order.review == null) {
            "赞助后填写账单中的支付订单号，管理员核对到账后记录赞助。"
        } else {
            reviewLabel(order.review)
        },
        color = LocalHeyboxTheme.current.muted,
        fontSize = watchSp(11f),
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
    )
    val review = order.review
    val canSubmit = review == null ||
        (!review.pending() && review.status != "approved" &&
            (order.pending() || order.status == "expired"))
    if (canSubmit) {
        Spacer(modifier = Modifier.height(watchDp(7)))
        ComposeCheckinTextField(
            value = state.billingPaymentReference,
            placeholder = "支付订单号",
            onValueChange = controller::setBillingPaymentReference,
        )
        Spacer(modifier = Modifier.height(watchDp(7)))
        ComposeCheckinPrimaryButton(
            "提交赞助记录",
            enabled = !state.billingRequestInFlight,
            onClick = controller::submitBillingClaim,
        )
    }
}

private fun orderStateLabel(order: CheckinBilling.Order): String {
    if (order.status == "paid") return "赞助已确认，感谢支持"
    if (order.status == "expired") return "赞助码已过期，请重新生成"
    if (order.status == "failed") return "赞助记录创建失败"
    if (order.review?.pending() == true) return "赞助记录待审核"
    return "等待赞助"
}

private fun providerLabel(order: CheckinBilling.Order): String =
    if (order.provider.contains("alipay")) "支付宝" else "微信"

private fun reviewLabel(review: CheckinBilling.Review): String {
    if (review.status == "approved") return "审核通过，感谢支持"
    if (review.status == "rejected") {
        return if (review.reason.isEmpty()) "审核未通过，请检查赞助记录"
        else "审核未通过：${review.reason}"
    }
    return "赞助记录待审核"
}
