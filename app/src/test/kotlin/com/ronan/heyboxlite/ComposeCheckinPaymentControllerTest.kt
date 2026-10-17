package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ComposeCheckinPaymentControllerTest {
    @Test fun pollingWaitsFifteenSecondsAndOnlyOneStatusRequestIsInFlight() {
        val fixture = Fixture()
        fixture.create()
        fixture.advance(14)
        assertTrue(fixture.polls.isEmpty())
        fixture.advance(1)
        assertEquals(1, fixture.polls.size)
        fixture.advance(60)
        assertEquals(1, fixture.polls.size)
        fixture.polls.single().onSuccess(order())
        fixture.advance(14)
        assertEquals(1, fixture.polls.size)
        fixture.advance(1)
        assertEquals(2, fixture.polls.size)
        fixture.controller.close()
    }

    @Test fun backgroundAndNonCheckoutPagesNeverPoll() {
        val fixture = Fixture()
        fixture.create()
        fixture.controller.pause()
        fixture.advance(120)
        assertTrue(fixture.polls.isEmpty())
        fixture.controller.resume()
        fixture.advance(15)
        assertEquals(1, fixture.polls.size)
        fixture.polls.single().onSuccess(order())
        fixture.route = ComposeCheckinRoute.MEMBERSHIP
        fixture.advance(60)
        assertEquals(1, fixture.polls.size)
        fixture.controller.close()
    }

    @Test fun transientFailureHonorsRetryAfterAndKeepsTheOrder() {
        val fixture = Fixture()
        fixture.create()
        fixture.advance(15)
        val before = fixture.state.order
        fixture.polls.single().onError(CheckinCenterClient.ApiError(
            CheckinCenterClient.Operation.BILLING_STATUS, 429, "Retry later",
            CheckinCenterClient.ErrorKind.HTTP, "", "", 60))
        fixture.advance(59)
        assertEquals(1, fixture.polls.size)
        assertSame(before, fixture.state.order)
        fixture.advance(1)
        assertEquals(2, fixture.polls.size)
        assertTrue(fixture.lost.isEmpty())
        fixture.controller.close()
    }

    @Test fun onlyServerPaidStatusTriggersEntitlementRefresh() {
        val fixture = Fixture()
        fixture.create()
        assertEquals(0, fixture.paid)
        fixture.advance(15)
        fixture.polls.single().onSuccess(order("paid"))
        assertEquals(1, fixture.paid)
        assertEquals("paid", fixture.state.order!!.status)
        fixture.advance(90)
        assertEquals(1, fixture.polls.size)
        fixture.controller.close()
    }

    @Test fun fixedProductsUseSkuOrderEvenWhenLegacySponsorshipFlagsAreSet() {
        val fixture = Fixture(CheckinBilling.Membership("free", false, false,
            false, "", true, true, CheckinBilling.Plan("Legacy sponsorship", 999,
                "CNY", 0, true, 1, 100_000_000), listOf(
                CheckinBilling.Product("monthly", "30 days", 500, "CNY", 30, true),
                CheckinBilling.Product("quarterly", "90 days", 1000, "CNY", 90, true)),
            "afdian"))

        fixture.controller.create()

        assertEquals(listOf("monthly"), fixture.skus)
        assertTrue(fixture.legacyAmounts.isEmpty())
        assertTrue(fixture.state.requestInFlight)
        fixture.controller.close()
    }

    @Test fun invalidAuthorizationStopsPollingAndLateCallbacksCannotReopenPayment() {
        val fixture = Fixture()
        fixture.create()
        fixture.advance(15)
        val callback = fixture.polls.single()
        callback.onError(CheckinCenterClient.ApiError(CheckinCenterClient.Operation.BILLING_STATUS, 401, "Revoked"))
        assertEquals(listOf("Revoked"), fixture.lost)
        callback.onSuccess(order("paid"))
        fixture.advance(60)
        assertEquals(0, fixture.paid)
        assertEquals(1, fixture.polls.size)
        fixture.controller.close()
    }

    @Test fun stopOrCloseRejectsOrderCreationResults() {
        val fixture = Fixture()
        fixture.controller.create()
        val callback = fixture.creates.single()
        fixture.controller.stop()
        callback.onSuccess(order())
        assertNull(fixture.state.order)
        fixture.controller.close()
    }

    @Test fun missingOrderStopsPollingAndAllowsAReplacementWithoutDisconnecting() {
        val fixture = Fixture()
        fixture.create()
        fixture.advance(15)
        fixture.polls.single().onError(error(404, "order_not_found"))
        assertNull(fixture.state.order)
        assertFalse(fixture.state.qrLoading)
        fixture.advance(90)
        assertEquals(1, fixture.polls.size)
        fixture.controller.create()
        assertEquals(2, fixture.creates.size)
        assertTrue(fixture.lost.isEmpty())
        fixture.controller.close()
    }

    @Test fun unclassifiedMissingRouteKeepsTheOrder() {
        val fixture = Fixture()
        fixture.create()
        fixture.advance(15)
        val before = fixture.state.order
        fixture.polls.single().onError(error(404, ""))
        assertSame(before, fixture.state.order)
        assertTrue(fixture.lost.isEmpty())
        fixture.controller.close()
    }

    @Test fun expiredOrderClearsQrAndRejectsLateQrResultsButCanBeRegenerated() {
        val fixture = Fixture()
        fixture.create(order(qrReady = true))
        assertTrue(fixture.state.qrLoading)
        fixture.advance(15)
        fixture.polls.single().onSuccess(order("expired"))
        fixture.qrs.single().onSuccess(byteArrayOf(1, 2, 3))
        fixture.advance(90)
        assertEquals("expired", fixture.state.order!!.status)
        assertNull(fixture.state.qrBitmap)
        assertFalse(fixture.state.qrLoading)
        assertEquals(1, fixture.polls.size)
        fixture.controller.create()
        assertEquals(2, fixture.creates.size)
        assertEquals(0, fixture.paid)
        fixture.controller.close()
    }

    @Test fun expiredQrConfirmsOrderStatusInsteadOfDiscardingPaymentHistory() {
        val fixture = Fixture()
        fixture.create(order(qrReady = true))
        fixture.qrs.single().onError(error(410, "order_expired"))
        assertEquals(1, fixture.polls.size)
        assertNotNull(fixture.state.order)
        fixture.polls.single().onSuccess(order("expired"))
        assertEquals("expired", fixture.state.order!!.status)
        assertTrue(fixture.lost.isEmpty())
        fixture.controller.close()
    }

    @Test fun nonPendingQrChecksForPaidStatusWithoutGrantingMembershipLocally() {
        val fixture = Fixture()
        fixture.create(order(qrReady = true))
        fixture.qrs.single().onError(error(409, "order_not_pending"))
        assertEquals(0, fixture.paid)
        fixture.polls.single().onSuccess(order("paid"))
        assertEquals(1, fixture.paid)
        assertNull(fixture.state.qrBitmap)
        assertFalse(fixture.state.qrLoading)
        fixture.controller.close()
    }

    @Test fun qrRetryRefetchesTheCodeWithoutCreatingAnotherPendingOrder() {
        val fixture = Fixture()
        fixture.create(order(qrReady = true))
        val before = fixture.state.order
        fixture.qrs.single().onError(error(503, "payment_qr_unavailable"))
        fixture.controller.create()
        assertSame(before, fixture.state.order)
        assertEquals(1, fixture.creates.size)
        assertEquals(2, fixture.qrs.size)
        assertTrue(fixture.state.qrLoading)
        assertEquals("", fixture.state.message)
        fixture.qrs.last().onError(error(404, "order_not_found"))
        fixture.qrs.first().onSuccess(byteArrayOf(1, 2, 3))
        assertNull(fixture.state.order)
        assertFalse(fixture.state.qrLoading)
        assertTrue(fixture.lost.isEmpty())
        fixture.controller.close()
    }

    @Test fun statusPollingDoesNotBypassTheQrRateLimitCooldown() {
        val fixture = Fixture()
        fixture.create(order(qrReady = true))
        fixture.qrs.single().onError(CheckinCenterClient.statusError(
            CheckinCenterClient.Operation.BILLING_QR, 429,
            "{\"code\":\"rate_limited\",\"retry_after\":60}"))
        repeat(3) {
            fixture.advance(15)
            fixture.polls.last().onSuccess(order(qrReady = true))
            fixture.controller.create()
            assertEquals(1, fixture.qrs.size)
        }
        fixture.advance(15)
        fixture.polls.last().onSuccess(order(qrReady = true))
        assertEquals(2, fixture.qrs.size)
        assertEquals(1, fixture.creates.size)
        assertTrue(fixture.lost.isEmpty())
        fixture.controller.close()
    }

    @Test fun statusCooldownSurvivesPauseResumeAndPendingOrderReuse() {
        val fixture = Fixture()
        fixture.create()
        fixture.advance(15)
        fixture.polls.single().onError(CheckinCenterClient.statusError(
            CheckinCenterClient.Operation.BILLING_STATUS, 429,
            "{\"code\":\"rate_limited\",\"retry_after\":60}"))
        fixture.controller.pause()
        fixture.controller.resume()
        fixture.controller.create()
        fixture.advance(59)
        assertEquals(1, fixture.polls.size)
        fixture.advance(1)
        assertEquals(2, fixture.polls.size)
        fixture.controller.close()
    }

    @Test fun qrStateConflictCannotBypassTheStatusCooldown() {
        val fixture = Fixture()
        fixture.create(order(qrReady = true))
        fixture.advance(15)
        fixture.polls.single().onError(CheckinCenterClient.statusError(
            CheckinCenterClient.Operation.BILLING_STATUS, 429,
            "{\"code\":\"rate_limited\",\"retry_after\":60}"))
        fixture.qrs.single().onError(error(410, "order_expired"))
        fixture.advance(59)
        assertEquals(1, fixture.polls.size)
        fixture.advance(1)
        assertEquals(2, fixture.polls.size)
        fixture.controller.close()
    }

    private class Fixture(
        catalog: CheckinBilling.Membership = CheckinBilling.Membership("paid", true, false,
            false, "", true, false, CheckinBilling.Plan.empty(),
            listOf(CheckinBilling.Product("monthly", "30 days", 500, "CNY", 30, true)), "afdian"),
    ) {
        var state = ComposeMembershipUiState(catalog = catalog,
            selectedSku = catalog.products.firstOrNull()?.sku ?: "")
        var route = ComposeCheckinRoute.CHECKOUT
        val creates = ArrayList<CheckinCenterClient.Callback<CheckinBilling.Order>>()
        val polls = ArrayList<CheckinCenterClient.Callback<CheckinBilling.Order>>()
        val qrs = ArrayList<CheckinCenterClient.Callback<ByteArray>>()
        val lost = ArrayList<String>()
        val skus = ArrayList<String>()
        val legacyAmounts = ArrayList<Int>()
        var paid = 0
        val requests = ComposeMembershipRequests({ }, { }, { _, _ -> },
            { sku, callback -> skus += sku; creates += callback },
            { amount, callback -> legacyAmounts += amount; creates += callback },
            { id, callback -> assertEquals(order().id, id); polls += callback },
            { _, callback -> qrs += callback }, { _, _, _ -> })
        val controller = ComposeCheckinPaymentController(requests, { state }, { state = it }, { route },
            { true }, lost::add, { paid++ }, Handler(Looper.getMainLooper()))
        fun create(value: CheckinBilling.Order = order()) { controller.create(); creates.single().onSuccess(value) }
        fun advance(seconds: Long) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds)) }
    }

    companion object {
        private fun order(status: String = "pending", qrReady: Boolean = false) =
            CheckinBilling.Order("HB" + "A".repeat(30), "afdian",
                500, 500, "CNY", status, qrReady, false, "", null, "", "monthly", "30 days", 30, "")
        private fun error(status: Int, code: String) = CheckinCenterClient.statusError(
            CheckinCenterClient.Operation.BILLING_QR, status, "{\"code\":\"$code\"}")
    }
}
