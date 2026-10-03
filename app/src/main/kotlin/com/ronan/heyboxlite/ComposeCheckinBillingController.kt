package com.ronan.heyboxlite

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper

/** Keeps sponsorship polling and QR validation outside the Compose screen. */
internal class ComposeCheckinBillingController(
    private val services: ComposeServices,
    private val coordinator: CheckinCenterCoordinator,
    private val state: () -> ComposeCheckinUiState,
    private val setState: (ComposeCheckinUiState) -> Unit,
    private val active: () -> Boolean,
    private val onAuthorizationLost: (String) -> Unit,
    private val onClosePage: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val pollTask = Runnable { pollOrder() }
    private val qrRetryTask = Runnable { loadQr() }
    private var visible = false
    private var orderGeneration = 0
    private var qrGeneration = 0

    fun open(membership: CheckinBilling.Membership?) {
        if (membership == null || !membership.checkoutAvailable || !active()) return
        stop()
        visible = true
        setState(
            state().copy(
                route = ComposeCheckinRoute.SPONSORSHIP,
                billingMembership = membership,
                billingOrder = null,
                billingAmount = SponsorshipAmount.formatYuan(membership.plan.amountCents),
                billingPaymentReference = "",
                billingRequestInFlight = false,
                billingQrLoading = false,
                billingQrBytes = null,
                billingMessage = "",
            ),
        )
    }

    fun stop() {
        visible = false
        handler.removeCallbacks(pollTask)
        handler.removeCallbacks(qrRetryTask)
        orderGeneration++
        qrGeneration++
    }

    fun setAmount(value: String) {
        setState(state().copy(billingAmount = value))
    }

    fun setPaymentReference(value: String) {
        setState(state().copy(billingPaymentReference = value))
    }

    fun finishOrRegenerate() {
        val order = state().billingOrder ?: return
        if (order.status == "paid") {
            stop()
            onClosePage()
            return
        }
        if (state().billingRequestInFlight || order.pending()) return
        stop()
        setState(
            state().copy(
                billingOrder = null,
                billingQrLoading = false,
                billingQrBytes = null,
                billingPaymentReference = "",
                billingMessage = "",
            ),
        )
        visible = true
    }

    fun createOrder() {
        val current = state()
        val membership = current.billingMembership ?: return
        if (current.billingRequestInFlight || !billingActive()) return
        val amount = if (membership.plan.variableAmount) {
            SponsorshipAmount.parseCents(
                current.billingAmount,
                membership.plan.minimumAmountCents,
                membership.plan.maximumAmountCents,
            )
        } else {
            membership.plan.amountCents
        }
        if (!SponsorshipAmount.validCents(amount) ||
            amount < membership.plan.minimumAmountCents ||
            amount > membership.plan.maximumAmountCents
        ) {
            services.toast.show("请输入有效的赞助金额")
            return
        }
        val generation = ++orderGeneration
        setState(state().copy(billingRequestInFlight = true, billingMessage = ""))
        coordinator.createBillingOrder(
            amount,
            object : CheckinCenterClient.Callback<CheckinBilling.Order> {
                override fun onSuccess(value: CheckinBilling.Order) {
                    if (!billingActive() || generation != orderGeneration) return
                    setState(
                        state().copy(
                            billingRequestInFlight = false,
                            billingOrder = value,
                            billingQrBytes = null,
                            billingMessage = "",
                        ),
                    )
                    if (value.pending() && value.qrReady) loadQr()
                    schedulePoll()
                }

                override fun onError(error: CheckinCenterClient.ApiError) {
                    if (!billingActive() || generation != orderGeneration) return
                    val message = errorMessage(error)
                    setState(state().copy(billingRequestInFlight = false, billingMessage = message))
                    if (error.authorizationInvalid()) onAuthorizationLost(message)
                }
            },
        )
    }

    fun loadQr() {
        val order = state().billingOrder ?: return
        if (!billingActive() || !order.pending() || !order.qrReady ||
            state().billingQrLoading
        ) return
        val generation = ++qrGeneration
        setState(state().copy(billingQrLoading = true, billingMessage = ""))
        coordinator.loadBillingQr(
            order.id,
            object : CheckinCenterClient.Callback<ByteArray> {
                override fun onSuccess(value: ByteArray) {
                    if (!billingActive() || generation != qrGeneration) return
                    val valid = BitmapFactory.decodeByteArray(value, 0, value.size) != null
                    setState(
                        state().copy(
                            billingQrLoading = false,
                            billingQrBytes = if (valid) value else null,
                            billingMessage = if (valid) "" else "赞助码无法显示",
                        ),
                    )
                    if (!valid) scheduleQrRetry()
                }

                override fun onError(error: CheckinCenterClient.ApiError) {
                    if (!billingActive() || generation != qrGeneration) return
                    val message = errorMessage(error)
                    setState(state().copy(billingQrLoading = false, billingMessage = message))
                    if (error.authorizationInvalid()) {
                        onAuthorizationLost(message)
                    } else {
                        scheduleQrRetry()
                    }
                }
            },
        )
    }

    fun submitClaim() {
        val order = state().billingOrder ?: return
        if (!billingActive() || state().billingRequestInFlight) return
        val reference = state().billingPaymentReference.trim()
        if (!CheckinBilling.validPaymentReference(reference)) {
            services.toast.show("请输入有效的支付订单号")
            return
        }
        setState(state().copy(billingRequestInFlight = true, billingMessage = ""))
        coordinator.submitBillingClaim(
            order.id,
            reference,
            object : CheckinCenterClient.Callback<CheckinBilling.Review> {
                override fun onSuccess(value: CheckinBilling.Review) {
                    if (!billingActive()) return
                    setState(
                        state().copy(
                            billingRequestInFlight = false,
                            billingOrder = CheckinBilling.Order(
                                order.id,
                                order.provider,
                                order.amountCents,
                                order.payableAmountCents,
                                order.currency,
                                order.status,
                                order.qrReady,
                                order.manualReview,
                                order.expiresAt,
                                value,
                            ),
                        ),
                    )
                    schedulePoll()
                }

                override fun onError(error: CheckinCenterClient.ApiError) {
                    if (!billingActive()) return
                    val message = errorMessage(error)
                    setState(state().copy(billingRequestInFlight = false, billingMessage = message))
                    if (error.authorizationInvalid()) onAuthorizationLost(message)
                }
            },
        )
    }

    private fun pollOrder() {
        val current = state().billingOrder
        if (!billingActive() || current == null || !current.pending()) return
        coordinator.getBillingOrder(
            current.id,
            object : CheckinCenterClient.Callback<CheckinBilling.Order> {
                override fun onSuccess(value: CheckinBilling.Order) {
                    if (!billingActive() || state().billingOrder?.id != current.id) return
                    val changed = !current.sameUiState(value)
                    setState(
                        state().copy(
                            billingOrder = value,
                            billingMessage = if (changed) "" else state().billingMessage,
                        ),
                    )
                    schedulePoll()
                }

                override fun onError(error: CheckinCenterClient.ApiError) {
                    if (!billingActive() || state().billingOrder?.id != current.id) return
                    val message = errorMessage(error)
                    setState(state().copy(billingMessage = message))
                    if (error.authorizationInvalid()) {
                        onAuthorizationLost(message)
                    } else {
                        schedulePoll()
                    }
                }
            },
        )
    }

    private fun schedulePoll() {
        handler.removeCallbacks(pollTask)
        val order = state().billingOrder ?: return
        if (billingActive() && order.pending() &&
            (order.review == null || order.review.pending())
        ) {
            handler.postDelayed(pollTask, BILLING_POLL_DELAY_MS)
        }
    }

    private fun scheduleQrRetry() {
        handler.removeCallbacks(qrRetryTask)
        val order = state().billingOrder ?: return
        if (billingActive() && order.pending() && order.qrReady) {
            handler.postDelayed(qrRetryTask, BILLING_POLL_DELAY_MS)
        }
    }

    private fun billingActive(): Boolean = active() && visible &&
        state().route == ComposeCheckinRoute.SPONSORSHIP

    private fun errorMessage(error: CheckinCenterClient.ApiError?): String =
        error?.message?.takeIf { it.isNotEmpty() } ?: "签到服务请求失败"

    private companion object {
        const val BILLING_POLL_DELAY_MS = 4_000L
    }
}
