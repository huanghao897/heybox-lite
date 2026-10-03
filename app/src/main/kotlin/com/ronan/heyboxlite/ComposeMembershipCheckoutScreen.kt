package com.ronan.heyboxlite

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeMembershipCheckoutScreen(
    state: ComposeMembershipUiState,
    account: CheckinCenterClient.Account?,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onReference: (String) -> Unit,
    onClaim: () -> Unit,
) {
    val order = state.order
    val sponsorship = state.catalog?.voluntarySponsorship == true
    WatchPage(if (sponsorship) "\u786e\u8ba4\u8d5e\u52a9" else "\u786e\u8ba4\u5f00\u901a", onBack) {
        if (order == null) {
            WatchEmptyState(if (state.requestInFlight) "\u6b63\u5728\u521b\u5efa\u8ba2\u5355"
                else "\u8ba2\u5355\u6682\u4e0d\u53ef\u7528")
            MembershipNotice(state.message)
            MembershipRetry(onFinish, !state.requestInFlight)
            return@WatchPage
        }
        val provider = membershipProviderName(order.provider.ifBlank {
            state.catalog?.checkoutProvider.orEmpty()
        })
        CheckinPanel {
            Column(Modifier.padding(watchDp(12)),
                verticalArrangement = Arrangement.spacedBy(watchDp(4))) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(watchDp(7))) {
                    CheckinResultIcon(if (order.status == "paid") CheckinResultKind.SUCCESS
                        else CheckinResultKind.UNKNOWN)
                    MembershipBodyText(if (order.status == "paid") "\u670d\u52a1\u5668\u652f\u4ed8\u5df2\u786e\u8ba4"
                        else membershipOrderLabel(order.status), Modifier.weight(1f),
                        fontSize = 13f, weight = FontWeight.SemiBold)
                }
                MembershipInfoRow("\u5e94\u4ed8\u91d1\u989d",
                    membershipMoney(order.payableAmountCents, order.currency),
                    Modifier.testTag("membership-payable"))
                MembershipInfoRow("\u5f00\u901a\u8d26\u53f7", membershipMaskedAccount(account))
                MembershipInfoRow("\u5957\u9910", membershipOrderProduct(order, state.catalog))
                val product = state.catalog?.products?.firstOrNull { it.sku == order.productSku }
                val duration = if (order.durationDays > 0) order.durationDays else product?.durationDays ?: 0
                if (duration > 0) MembershipInfoRow("\u65f6\u957f", "$duration \u5929")
                MembershipInfoRow("\u652f\u4ed8\u6e20\u9053", provider)
                if (order.pending() && order.expiresAt.isNotBlank()) {
                    MembershipInfoRow("\u4ed8\u6b3e\u622a\u6b62", membershipDate(order.expiresAt))
                }
            }
        }
        if (order.status == "paid") {
            val catalog = state.catalog
            if (catalog == null) MembershipNotice("\u4f1a\u5458\u6743\u76ca\u5f85\u540c\u6b65")
            else MembershipOverview(catalog)
        } else if (order.pending()) {
            MembershipPaymentQr(state, provider)
            MembershipBodyText(if (provider == "\u652f\u4ed8\u6e20\u9053\u5f85\u786e\u8ba4")
                "\u626b\u7801\u652f\u4ed8" else "\u901a\u8fc7$provider\u652f\u4ed8",
                muted = true, fontSize = 11f)
        }
        val legacy = order.manualReview && order.checkoutUrl.isBlank()
        val review = state.review ?: order.review
        if (legacy && order.status != "paid") MembershipManualClaim(state, order, review, onReference, onClaim)
        MembershipNotice(state.message)
        val regenerate = !order.pending() || (state.message.isNotBlank() && state.qrBitmap == null &&
            !state.qrLoading && review?.pending() != true)
        val reviewInProgress = legacy && (review?.pending() == true || review?.status == "approved")
        CheckinActionButton(
            text = when {
                order.status == "paid" -> "\u5b8c\u6210"
                state.requestInFlight -> "\u5904\u7406\u4e2d"
                regenerate && order.pending() -> "\u91cd\u8bd5"
                regenerate -> "\u91cd\u65b0\u751f\u6210\u8ba2\u5355"
                else -> "\u7b49\u5f85\u786e\u8ba4"
            },
            onClick = onFinish,
            enabled = !state.requestInFlight && !state.qrLoading &&
                (order.status == "paid" || (regenerate && !reviewInProgress)),
            icon = if (order.status == "paid") R.drawable.ic_check else R.drawable.il_refresh,
        )
    }
}

@Composable
private fun MembershipPaymentQr(state: ComposeMembershipUiState, provider: String) {
    val bitmap = remember(state.qrBitmap) { state.qrBitmap?.asImageBitmap() }
    val round = LocalHeyboxTheme.current.roundScreen
    val screenHeight = LocalConfiguration.current.screenHeightDp
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, watchDp(if (round) 168 else 210),
            (screenHeight * if (round) 0.55f else 0.7f).dp)
        Box(Modifier.size(side).background(Color.White).testTag("membership-qr"),
            contentAlignment = Alignment.Center) {
            when {
                bitmap != null -> Image(bitmap, "$provider\u652f\u4ed8\u4e8c\u7ef4\u7801",
                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else -> androidx.compose.material3.Text(
                    if (state.qrLoading) "\u6b63\u5728\u52a0\u8f7d\u4ed8\u6b3e\u7801" else "\u4ed8\u6b3e\u7801\u6682\u4e0d\u53ef\u7528",
                    color = Color.DarkGray, fontSize = watchSp(11f), letterSpacing = 0.sp,
                    modifier = Modifier.padding(watchDp(8)),
                )
            }
        }
    }
}

@Composable
private fun MembershipManualClaim(
    state: ComposeMembershipUiState,
    order: CheckinBilling.Order,
    review: CheckinBilling.Review?,
    onReference: (String) -> Unit,
    onClaim: () -> Unit,
) {
    if (review != null) {
        MembershipNotice(when (review.status) {
            "approved" -> "\u5ba1\u6838\u5df2\u901a\u8fc7"
            "rejected" -> "\u5ba1\u6838\u672a\u901a\u8fc7" +
                if (review.reason.isNotBlank()) "\uff1a" + review.reason else ""
            "pending" -> "\u8ba2\u5355\u53f7\u5f85\u5ba1\u6838"
            else -> "\u5ba1\u6838\u72b6\u6001\u5f85\u786e\u8ba4"
        })
    }
    if (order.status !in listOf("pending", "expired") ||
        (review != null && review.status != "rejected")) return
    MembershipInput(state.paymentReference, "\u652f\u4ed8\u8ba2\u5355\u53f7", onReference,
        enabled = !state.requestInFlight, placeholder = "\u652f\u4ed8\u8ba2\u5355\u53f7")
    CheckinActionButton("\u63d0\u4ea4\u8ba2\u5355\u53f7", onClaim,
        enabled = !state.requestInFlight && CheckinBilling.validPaymentReference(state.paymentReference.trim()),
        icon = R.drawable.il_ticket)
}

@Composable
internal fun MembershipInfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val minimum = (160 * LocalHeyboxTheme.current.textScale * density.fontScale).dp
    BoxWithConstraints(modifier.fillMaxWidth().padding(vertical = watchDp(3))) {
        if (maxWidth >= minimum) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(watchDp(7))) {
                MembershipBodyText(label, Modifier.weight(0.38f), muted = true, fontSize = 11f)
                MembershipBodyText(value, Modifier.weight(0.62f), fontSize = 12f)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(watchDp(2))) {
                MembershipBodyText(label, muted = true, fontSize = 10f)
                MembershipBodyText(value, fontSize = 12f)
            }
        }
    }
}

internal fun membershipOrderProduct(order: CheckinBilling.Order, catalog: CheckinBilling.Membership?): String {
    if (order.productName.isNotBlank()) return order.productName
    return catalog?.products?.firstOrNull { it.sku == order.productSku }?.name
        ?: order.productSku.ifBlank {
            if (catalog?.voluntarySponsorship == true) catalog.plan.name else "\u5957\u9910\u4fe1\u606f\u5f85\u786e\u8ba4"
        }
}

internal fun membershipProviderName(provider: String): String = when (provider.lowercase()) {
    "afdian" -> "\u7231\u53d1\u7535"
    "monitor_wechat", "wechat" -> "\u5fae\u4fe1"
    "monitor_alipay", "alipay" -> "\u652f\u4ed8\u5b9d"
    else -> provider.ifBlank { "\u652f\u4ed8\u6e20\u9053\u5f85\u786e\u8ba4" }
}

internal fun membershipMaskedAccount(account: CheckinCenterClient.Account?): String {
    if (account == null) return "\u8d26\u53f7\u4fe1\u606f\u6682\u4e0d\u53ef\u7528"
    val masked = account.externalIdMasked.trim()
    if ('*' in masked) return masked
    val value = masked.ifBlank { account.displayName }.trim()
    if (value.isBlank()) return "\u8d26\u53f7\u4fe1\u606f\u6682\u4e0d\u53ef\u7528"
    return if (value.length <= 2) "**" else value.take(1) + "***" + value.takeLast(1)
}
