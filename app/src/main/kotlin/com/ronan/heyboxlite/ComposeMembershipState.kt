package com.ronan.heyboxlite

import android.graphics.Bitmap
import java.math.BigDecimal

internal data class ComposeMembershipUiState(
    val catalog: CheckinBilling.Membership? = null,
    val selectedSku: String = "",
    val catalogLoading: Boolean = false,
    val requestInFlight: Boolean = false,
    val order: CheckinBilling.Order? = null,
    val qrBitmap: Bitmap? = null,
    val qrLoading: Boolean = false,
    val review: CheckinBilling.Review? = null,
    val paymentReference: String = "",
    val amount: String = "",
    val redeemCode: String = "",
    val purchases: List<CheckinBilling.OrderRecord> = emptyList(),
    val purchasesLoading: Boolean = false,
    val message: String = "",
) {
    fun selectedProduct(): CheckinBilling.Product? = catalog?.products?.firstOrNull { it.sku == selectedSku }
    fun variableSponsorship(): Boolean = catalog?.usesVariableSponsorship() == true
}

internal fun membershipMoney(cents: Int, currency: String = "CNY"): String =
    (if (currency == "CNY") "¥" else "$currency ") + BigDecimal(cents)
        .movePointLeft(2).stripTrailingZeros().toPlainString()

internal fun membershipDate(value: String): String = value.replace('T', ' ').take(16).ifEmpty { "未知时间" }

internal fun membershipTitle(value: CheckinBilling.Membership): String = when {
    value.admin -> "管理员权益"
    !value.required && value.mode == "free" -> "当前免费开放"
    value.entitled -> "会员已开通"
    value.expiresAt.isNotEmpty() -> "会员已到期"
    else -> "未开通会员"
}

internal fun membershipOrderLabel(status: String): String = when (status) {
    "paid" -> "支付已确认"
    "expired" -> "订单已过期"
    "failed" -> "订单失败"
    "pending" -> "等待付款"
    else -> "状态待确认"
}

internal data class ComposeMembershipRequests(
    val catalog: (CheckinCenterClient.Callback<CheckinBilling.Membership>) -> Unit,
    val purchases: (CheckinCenterClient.Callback<List<CheckinBilling.OrderRecord>>) -> Unit,
    val redeem: (String, CheckinCenterClient.Callback<CheckinBilling.Membership>) -> Unit,
    val create: (String, CheckinCenterClient.Callback<CheckinBilling.Order>) -> Unit,
    val createLegacy: (Int, CheckinCenterClient.Callback<CheckinBilling.Order>) -> Unit,
    val poll: (String, CheckinCenterClient.Callback<CheckinBilling.Order>) -> Unit,
    val qr: (String, CheckinCenterClient.Callback<ByteArray>) -> Unit,
    val claim: (String, String, CheckinCenterClient.Callback<CheckinBilling.Review>) -> Unit,
) {
    constructor(coordinator: CheckinCenterCoordinator) : this(coordinator::getMembershipCatalog,
        coordinator::getPurchaseHistory, coordinator::redeemMembership, coordinator::createMembershipOrder,
        coordinator::createBillingOrder, coordinator::getBillingOrder, coordinator::loadBillingQr,
        coordinator::submitBillingClaim)
}
