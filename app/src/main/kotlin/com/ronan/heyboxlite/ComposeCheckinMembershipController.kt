package com.ronan.heyboxlite

import android.app.Activity
import android.os.Handler
import android.os.Looper

/** Account membership operations are separate from check-in credentials and execution. */
internal class ComposeCheckinMembershipController(
    private val requests: ComposeMembershipRequests,
    private val state: () -> ComposeMembershipUiState,
    private val update: (ComposeMembershipUiState) -> Unit,
    private val route: () -> ComposeCheckinRoute,
    private val navigate: (ComposeCheckinRoute) -> Unit,
    private val active: () -> Boolean,
    private val onAuthorizationLost: (String) -> Unit,
    private val onCatalogChanged: (CheckinBilling.Membership) -> Unit,
    handler: Handler = Handler(Looper.getMainLooper()),
) {
    private var generation = 0
    private var closed = false
    private var lifecycle: ComposeCheckinActivityLifecycle? = null
    private val payment = ComposeCheckinPaymentController(requests, state, update, route,
        { !closed && active() }, onAuthorizationLost, { refreshCatalog() }, handler)

    fun start(activity: Activity) {
        if (closed || lifecycle != null) return
        lifecycle = ComposeCheckinActivityLifecycle(activity, payment::resume, payment::pause).also { it.start() }
    }

    fun open(initial: CheckinBilling.Membership?) {
        if (closed || !active()) return
        generation++
        payment.stop()
        update(ComposeMembershipUiState(catalog = initial,
            selectedSku = initial?.products?.firstOrNull { it.active }?.sku ?: "",
            amount = initial?.let { SponsorshipAmount.formatYuan(it.plan.amountCents) } ?: ""))
        navigate(ComposeCheckinRoute.MEMBERSHIP)
        refreshCatalog()
    }

    fun refreshCatalog() {
        if (closed || !active() || state().catalogLoading) return
        val serial = generation
        update(state().copy(catalogLoading = true, message = ""))
        requests.catalog(object : CheckinCenterClient.Callback<CheckinBilling.Membership> {
            override fun onSuccess(value: CheckinBilling.Membership) {
                if (!accept(serial)) return
                val selected = state().selectedSku.takeIf { sku -> value.products.any { it.active && it.sku == sku } }
                    ?: value.products.firstOrNull { it.active }?.sku ?: ""
                update(state().copy(catalog = value, selectedSku = selected, catalogLoading = false, message = ""))
                onCatalogChanged(value)
            }
            override fun onError(error: CheckinCenterClient.ApiError) {
                if (!accept(serial)) return
                update(state().copy(catalogLoading = false, message = error.message ?: "会员信息暂不可用"))
                authorization(error)
            }
        })
    }

    fun selectProduct(sku: String) {
        if (state().requestInFlight || state().catalog?.products?.none { it.active && it.sku == sku } != false) return
        update(state().copy(selectedSku = sku, message = ""))
    }

    fun setAmount(value: String) { update(state().copy(amount = value)) }
    fun openCheckout() {
        if (closed || !active() || state().catalog?.checkoutAvailable != true || state().requestInFlight) return
        if (!state().variableSponsorship() && state().selectedProduct() == null) return
        navigate(ComposeCheckinRoute.CHECKOUT)
        payment.create()
    }

    fun openRedeem() {
        if (closed || !active()) return
        payment.pause()
        update(state().copy(redeemCode = "", message = ""))
        navigate(ComposeCheckinRoute.REDEEM)
    }
    fun setRedeemCode(value: String) { update(state().copy(redeemCode = value)) }
    fun redeem() {
        if (closed || !active() || state().requestInFlight || route() != ComposeCheckinRoute.REDEEM) return
        val serial = generation
        val code = state().redeemCode.trim()
        update(state().copy(requestInFlight = true, message = ""))
        requests.redeem(code, object : CheckinCenterClient.Callback<CheckinBilling.Membership> {
            override fun onSuccess(value: CheckinBilling.Membership) {
                if (!accept(serial)) return
                update(state().copy(catalog = value, requestInFlight = false, redeemCode = "", message = "兑换成功"))
                onCatalogChanged(value)
                if (route() == ComposeCheckinRoute.REDEEM) navigate(ComposeCheckinRoute.MEMBERSHIP)
            }
            override fun onError(error: CheckinCenterClient.ApiError) {
                if (!accept(serial)) return
                update(state().copy(requestInFlight = false, message = error.message ?: "兑换失败"))
                authorization(error)
            }
        })
    }

    fun openPurchases() {
        if (closed || !active()) return
        payment.pause()
        navigate(ComposeCheckinRoute.PURCHASES)
        refreshPurchases()
    }
    fun refreshPurchases() {
        if (closed || !active() || state().purchasesLoading) return
        val serial = generation
        update(state().copy(purchasesLoading = true, message = ""))
        requests.purchases(object : CheckinCenterClient.Callback<List<CheckinBilling.OrderRecord>> {
            override fun onSuccess(value: List<CheckinBilling.OrderRecord>) {
                if (accept(serial)) update(state().copy(purchases = value, purchasesLoading = false, message = ""))
            }
            override fun onError(error: CheckinCenterClient.ApiError) {
                if (!accept(serial)) return
                update(state().copy(purchasesLoading = false, message = error.message ?: "购买记录暂不可用"))
                authorization(error)
            }
        })
    }

    fun backToMembership() {
        payment.pause()
        navigate(ComposeCheckinRoute.MEMBERSHIP)
    }
    fun leave() {
        generation++
        payment.stop()
        update(state().copy(catalogLoading = false, purchasesLoading = false, requestInFlight = false,
            redeemCode = "", qrLoading = false, qrBitmap = null))
    }
    fun close() {
        if (closed) return
        closed = true
        generation++
        lifecycle?.close()
        lifecycle = null
        payment.close()
    }
    fun finishOrRegenerate() {
        if (state().order?.status == "paid") backToMembership() else payment.create()
    }
    fun setPaymentReference(value: String) { update(state().copy(paymentReference = value)) }
    fun submitClaim() = payment.submitClaim()
    private fun accept(serial: Int) = !closed && active() && serial == generation
    private fun authorization(error: CheckinCenterClient.ApiError) {
        if (error.authorizationInvalid()) onAuthorizationLost(error.message ?: "签到服务连接已失效")
    }
}
