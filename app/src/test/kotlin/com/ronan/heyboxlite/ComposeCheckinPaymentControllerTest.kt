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

    private class Fixture {
        var state = ComposeMembershipUiState(catalog = CheckinBilling.Membership("paid", true, false,
            false, "", true, false, CheckinBilling.Plan.empty(),
            listOf(CheckinBilling.Product("monthly", "30 days", 500, "CNY", 30, true)), "afdian"), selectedSku = "monthly")
        var route = ComposeCheckinRoute.CHECKOUT
        val creates = ArrayList<CheckinCenterClient.Callback<CheckinBilling.Order>>()
        val polls = ArrayList<CheckinCenterClient.Callback<CheckinBilling.Order>>()
        val lost = ArrayList<String>()
        var paid = 0
        val requests = ComposeMembershipRequests({ }, { }, { _, _ -> },
            { sku, callback -> assertEquals("monthly", sku); creates += callback },
            { _, _ -> fail("Paid mode must not send an amount") },
            { id, callback -> assertEquals(order().id, id); polls += callback },
            { _, _ -> fail("No legacy QR URL is supplied") }, { _, _, _ -> })
        val controller = ComposeCheckinPaymentController(requests, { state }, { state = it }, { route },
            { true }, lost::add, { paid++ }, Handler(Looper.getMainLooper()))
        fun create() { controller.create(); creates.single().onSuccess(order()) }
        fun advance(seconds: Long) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds)) }
    }

    companion object {
        private fun order(status: String = "pending") = CheckinBilling.Order("HB" + "A".repeat(30), "afdian",
            500, 500, "CNY", status, false, false, "", null, "", "monthly", "30 days", 30, "")
    }
}
