package com.ronan.heyboxlite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.SystemClock
import com.google.zxing.WriterException
import java.util.concurrent.Executors

/** Only this controller owns order creation, payment-page polling and QR decoding. */
internal class ComposeCheckinPaymentController(
    private val requests: ComposeMembershipRequests,
    private val state: () -> ComposeMembershipUiState,
    private val update: (ComposeMembershipUiState) -> Unit,
    private val route: () -> ComposeCheckinRoute,
    private val active: () -> Boolean,
    private val onAuthorizationLost: (String) -> Unit,
    private val onPaid: () -> Unit,
    private val handler: Handler,
) {
    private var foreground = true
    private var generation = 0
    private var querying = false
    private var lastQueryAt = -POLL_INTERVAL
    private val pollTask = Runnable { poll() }
    private val images = Executors.newSingleThreadExecutor { job -> Thread(job, "heybox-payment-qr").apply { isDaemon = true } }

    fun resume() { foreground = true; schedule() }
    fun pause() { foreground = false; handler.removeCallbacks(pollTask) }
    fun stop() { generation++; querying = false; handler.removeCallbacks(pollTask) }
    fun close() { stop(); images.shutdownNow() }

    fun create() {
        if (!active() || route() != ComposeCheckinRoute.CHECKOUT || state().requestInFlight) return
        foreground = true
        val current = state()
        val catalog = current.catalog ?: return
        val product = current.selectedProduct()
        val variableAmount = current.variableSponsorship()
        val amount = if (variableAmount) SponsorshipAmount.parseCents(current.amount,
            catalog.plan.minimumAmountCents, catalog.plan.maximumAmountCents) else 0
        val previous = current.order
        val sameSelection = if (variableAmount) previous?.productSku == "" && previous.amountCents == amount
            else product != null && previous?.productSku == product.sku
        if (previous?.pending() == true && sameSelection) {
            schedule()
            return
        }
        stop()
        val serial = generation
        val callback = object : CheckinCenterClient.Callback<CheckinBilling.Order> {
            override fun onSuccess(value: CheckinBilling.Order) {
                if (!accept(serial)) return
                update(state().copy(order = value, requestInFlight = false, qrBitmap = null, review = value.review, message = ""))
                if (value.status == "paid") onPaid() else if (value.pending()) makeQr(value, serial)
                schedule()
            }
            override fun onError(error: CheckinCenterClient.ApiError) {
                if (!accept(serial)) return
                update(state().copy(requestInFlight = false, message = error.message ?: "订单创建失败"))
                authorization(error)
            }
        }
        if (variableAmount) {
            if (!SponsorshipAmount.validCents(amount)) {
                update(current.copy(message = "请输入有效金额"))
                return
            }
            update(current.copy(requestInFlight = true, order = null, qrBitmap = null, message = ""))
            requests.createLegacy(amount, callback)
        } else if (product != null) {
            update(current.copy(requestInFlight = true, order = null, qrBitmap = null, message = ""))
            requests.create(product.sku, callback)
        }
    }

    private fun poll() {
        val order = state().order ?: return
        if (!visible() || !order.pending() || querying) return
        val remaining = POLL_INTERVAL - (SystemClock.elapsedRealtime() - lastQueryAt)
        if (remaining > 0) { handler.postDelayed(pollTask, remaining); return }
        val serial = generation
        querying = true
        lastQueryAt = SystemClock.elapsedRealtime()
        requests.poll(order.id, object : CheckinCenterClient.Callback<CheckinBilling.Order> {
            override fun onSuccess(value: CheckinBilling.Order) {
                if (!accept(serial) || state().order?.id != order.id) return
                querying = false
                update(state().copy(order = value, review = value.review ?: state().review, message = ""))
                if (value.status == "paid") {
                    update(state().copy(qrBitmap = null, qrLoading = false))
                    onPaid()
                } else if (value.pending() && state().qrBitmap == null && !state().qrLoading) makeQr(value, serial)
                schedule()
            }
            override fun onError(error: CheckinCenterClient.ApiError) {
                if (!accept(serial)) return
                querying = false
                update(state().copy(message = error.message ?: "订单状态暂不可用"))
                if (!authorization(error)) schedule(maxOf(POLL_INTERVAL, error.retryAfterSeconds.toLong() * 1000))
            }
        })
    }

    private fun schedule(delay: Long = POLL_INTERVAL) {
        handler.removeCallbacks(pollTask)
        if (visible() && state().order?.pending() == true && !querying) {
            handler.postDelayed(pollTask, maxOf(delay, POLL_INTERVAL - (SystemClock.elapsedRealtime() - lastQueryAt)))
        }
    }

    private fun makeQr(order: CheckinBilling.Order, serial: Int) {
        update(state().copy(qrLoading = true))
        if (order.checkoutUrl.isNotEmpty()) {
            decode(serial, order.id) { QrCode.create(order.checkoutUrl, 512) }
        } else if (order.qrReady) {
            requests.qr(order.id, object : CheckinCenterClient.Callback<ByteArray> {
                override fun onSuccess(value: ByteArray) {
                    if (accept(serial)) decode(serial, order.id) { decodeImage(value) }
                }
                override fun onError(error: CheckinCenterClient.ApiError) {
                    if (!accept(serial)) return
                    update(state().copy(qrLoading = false, message = error.message ?: "支付码暂不可用"))
                    authorization(error)
                }
            })
        } else {
            update(state().copy(qrLoading = false, message = "支付渠道暂未提供扫码入口"))
        }
    }

    private fun decode(serial: Int, orderId: String, build: () -> Bitmap?) {
        images.execute {
            val bitmap = try { build() } catch (_: WriterException) { null } catch (_: IllegalArgumentException) { null }
            handler.post {
                if (accept(serial) && state().order?.id == orderId) {
                    update(state().copy(qrBitmap = bitmap, qrLoading = false,
                        message = if (bitmap == null) "支付码无法显示" else state().message))
                }
            }
        }
    }

    fun submitClaim() {
        val current = state()
        val order = current.order ?: return
        if (!visible() || !order.manualReview || current.requestInFlight) return
        val reference = current.paymentReference.trim()
        if (!CheckinBilling.validPaymentReference(reference)) {
            update(current.copy(message = "请输入有效的支付订单号"))
            return
        }
        val serial = generation
        update(current.copy(requestInFlight = true, message = ""))
        requests.claim(order.id, reference, object : CheckinCenterClient.Callback<CheckinBilling.Review> {
            override fun onSuccess(value: CheckinBilling.Review) {
                if (!accept(serial)) return
                update(state().copy(requestInFlight = false, review = value, paymentReference = "",
                    message = "已提交，等待服务器核对"))
                schedule()
            }
            override fun onError(error: CheckinCenterClient.ApiError) {
                if (!accept(serial)) return
                update(state().copy(requestInFlight = false, message = error.message ?: "提交失败"))
                authorization(error)
            }
        })
    }

    private fun visible() = active() && foreground && route() == ComposeCheckinRoute.CHECKOUT
    private fun accept(serial: Int) = active() && generation == serial
    private fun authorization(error: CheckinCenterClient.ApiError): Boolean {
        if (!error.authorizationInvalid()) return false
        stop()
        onAuthorizationLost(error.message ?: "签到服务连接已失效")
        return true
    }
    private companion object {
        const val POLL_INTERVAL = 15_000L
        fun decodeImage(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (bounds.outWidth / inSampleSize > 512 || bounds.outHeight / inSampleSize > 512) inSampleSize *= 2
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }
}
