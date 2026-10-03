package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ComposeCheckinMembershipControllerTest {
    @Test fun openingFetchesRealCatalogAndUnavailableCheckoutStillShowsMembership() {
        val fixture = Fixture()
        fixture.controller.open(null)
        assertEquals(ComposeCheckinRoute.MEMBERSHIP, fixture.route)
        assertTrue(fixture.state.catalogLoading)
        fixture.catalog.single().onSuccess(membership(checkout = false))
        assertEquals("monthly", fixture.state.selectedSku)
        assertFalse(fixture.state.catalogLoading)
        fixture.controller.openCheckout()
        assertTrue(fixture.orders.isEmpty())
        assertEquals(ComposeCheckinRoute.MEMBERSHIP, fixture.route)
        fixture.controller.close()
    }

    @Test fun selectionUsesTheServerSkuAndDoesNotSendClientPrices() {
        val fixture = Fixture()
        fixture.open()
        fixture.controller.selectProduct("quarterly")
        fixture.controller.openCheckout()
        assertEquals(ComposeCheckinRoute.CHECKOUT, fixture.route)
        assertEquals(listOf("quarterly"), fixture.skus)
        assertEquals(0, fixture.legacyCreates)
        assertTrue(fixture.state.requestInFlight)
        fixture.controller.openCheckout()
        assertEquals(1, fixture.orders.size)
        fixture.controller.close()
    }

    @Test fun redemptionUpdatesEntitlementOnlyAfterTheServerAcceptsAndClearsTheCode() {
        val fixture = Fixture()
        fixture.open()
        fixture.controller.openRedeem()
        fixture.controller.setRedeemCode("HBX-TEST")
        fixture.controller.redeem()
        fixture.controller.redeem()
        assertEquals(listOf("HBX-TEST"), fixture.codes)
        assertFalse(fixture.state.catalog!!.entitled)
        fixture.redemptions.single().onSuccess(membership(entitled = true))
        assertTrue(fixture.state.catalog!!.entitled)
        assertEquals("", fixture.state.redeemCode)
        assertEquals("兑换成功", fixture.state.message)
        assertEquals(ComposeCheckinRoute.MEMBERSHIP, fixture.route)
        assertEquals(2, fixture.changed.size)
        fixture.controller.close()
    }

    @Test fun transientCatalogErrorKeepsTheSnapshotAndDoesNotClearDeviceAuthorization() {
        val fixture = Fixture()
        val initial = membership()
        fixture.controller.open(initial)
        fixture.catalog.single().onError(error(503))
        assertSame(initial, fixture.state.catalog)
        assertTrue(fixture.lost.isEmpty())
        assertFalse(fixture.state.catalogLoading)
        fixture.controller.refreshCatalog()
        fixture.catalog.last().onError(error(401))
        assertEquals(1, fixture.lost.size)
        fixture.controller.close()
    }

    @Test fun lateCatalogAndRedemptionCallbacksAreIgnoredAfterLeavingOrClosing() {
        val fixture = Fixture()
        fixture.open()
        fixture.controller.openRedeem()
        fixture.controller.setRedeemCode("CODE")
        fixture.controller.redeem()
        val pending = fixture.redemptions.single()
        fixture.controller.leave()
        pending.onSuccess(membership(entitled = true))
        assertFalse(fixture.state.catalog!!.entitled)
        assertTrue(fixture.state.redeemCode.isEmpty())
        fixture.controller.open(null)
        fixture.controller.close()
        fixture.catalog.last().onSuccess(membership())
        assertNull(fixture.state.catalog)
    }

    @Test fun purchaseHistoryIsLoadedOnDemandAndSingleFlight() {
        val fixture = Fixture()
        fixture.open()
        assertTrue(fixture.history.isEmpty())
        fixture.controller.openPurchases()
        fixture.controller.refreshPurchases()
        assertEquals(1, fixture.history.size)
        val records = listOf(CheckinBilling.OrderRecord("HB" + "A".repeat(30), "monthly", "30 天", 500, 500,
            "CNY", "paid", "2026-10-03T05:00:00Z", "2026-10-03T05:30:00Z", 30))
        fixture.history.single().onSuccess(records)
        assertSame(records, fixture.state.purchases)
        assertFalse(fixture.state.purchasesLoading)
        fixture.controller.close()
    }

    private class Fixture {
        var state = ComposeMembershipUiState()
        var route = ComposeCheckinRoute.CENTER
        val catalog = ArrayList<CheckinCenterClient.Callback<CheckinBilling.Membership>>()
        val history = ArrayList<CheckinCenterClient.Callback<List<CheckinBilling.OrderRecord>>>()
        val redemptions = ArrayList<CheckinCenterClient.Callback<CheckinBilling.Membership>>()
        val orders = ArrayList<CheckinCenterClient.Callback<CheckinBilling.Order>>()
        val codes = ArrayList<String>()
        val skus = ArrayList<String>()
        val changed = ArrayList<CheckinBilling.Membership>()
        val lost = ArrayList<String>()
        var legacyCreates = 0
        val requests = ComposeMembershipRequests(catalog::add, history::add,
            { code, callback -> codes += code; redemptions += callback },
            { sku, callback -> skus += sku; orders += callback },
            { _, _ -> legacyCreates++ }, { _, _ -> }, { _, _ -> }, { _, _, _ -> })
        val controller = ComposeCheckinMembershipController(requests, { state }, { state = it }, { route },
            { route = it }, { true }, lost::add, changed::add, Handler(Looper.getMainLooper()))
        fun open() { controller.open(null); catalog.last().onSuccess(membership()) }
    }

    companion object {
        private fun membership(checkout: Boolean = true, entitled: Boolean = false) = CheckinBilling.Membership(
            "paid", true, entitled, false, "", checkout, false, CheckinBilling.Plan.empty(), listOf(
                CheckinBilling.Product("monthly", "30 天", 500, "CNY", 30, true),
                CheckinBilling.Product("quarterly", "90 天", 1000, "CNY", 90, true)), "afdian")
        private fun error(code: Int) = CheckinCenterClient.ApiError(CheckinCenterClient.Operation.BILLING_CATALOG, code, "Unavailable")
    }
}
