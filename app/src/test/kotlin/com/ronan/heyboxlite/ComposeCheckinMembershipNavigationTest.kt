package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeCheckinMembershipNavigationTest {
    private val fixtures = mutableListOf<Fixture>()

    @After fun closeControllers() {
        fixtures.forEach { it.controller.close() }
    }

    @Test fun checkoutBackPausesPollingAndReentryReusesThePendingOrder() {
        val fixture = fixture()
        fixture.open()
        fixture.createOrder()
        fixture.advance(14)
        assertTrue(fixture.polls.isEmpty())
        fixture.advance(1)
        assertEquals(1, fixture.polls.size)

        fixture.controller.backToMembership()
        assertEquals(ComposeCheckinRoute.MEMBERSHIP, fixture.route)
        fixture.advance(90)
        assertEquals(1, fixture.polls.size)
        val pending = order()
        fixture.polls.single().onSuccess(pending)
        fixture.advance(60)
        assertEquals(1, fixture.polls.size)
        assertSame(pending, fixture.state.order)

        fixture.controller.openCheckout()
        assertEquals(ComposeCheckinRoute.CHECKOUT, fixture.route)
        assertEquals(listOf("monthly"), fixture.skus)
        assertEquals(1, fixture.creates.size)
        assertSame(pending, fixture.state.order)
        fixture.advance(14)
        assertEquals(1, fixture.polls.size)
        fixture.advance(1)
        assertEquals(2, fixture.polls.size)
        assertEquals(listOf(pending.id, pending.id), fixture.pollIds)
    }

    @Test fun orderCreationCanFinishOnMembershipWithoutReopeningCheckout() {
        val fixture = fixture()
        fixture.open()
        fixture.controller.openCheckout()
        assertTrue(fixture.state.requestInFlight)
        fixture.controller.backToMembership()
        fixture.controller.openCheckout()
        assertEquals(ComposeCheckinRoute.MEMBERSHIP, fixture.route)
        assertEquals(1, fixture.creates.size)

        val pending = order()
        fixture.creates.single().onSuccess(pending)
        assertFalse(fixture.state.requestInFlight)
        assertSame(pending, fixture.state.order)
        assertEquals(ComposeCheckinRoute.MEMBERSHIP, fixture.route)
        fixture.advance(60)
        assertTrue(fixture.polls.isEmpty())
        fixture.controller.openCheckout()
        assertEquals(1, fixture.creates.size)
        assertSame(pending, fixture.state.order)
        fixture.advance(15)
        assertEquals(listOf(pending.id), fixture.pollIds)
    }

    @Test fun aDifferentSkuDoesNotReuseAnotherProductsPendingOrder() {
        val fixture = fixture()
        fixture.open()
        fixture.createOrder()
        fixture.controller.backToMembership()
        fixture.controller.selectProduct("quarterly")
        fixture.controller.openCheckout()
        fixture.controller.openCheckout()
        assertEquals(listOf("monthly", "quarterly"), fixture.skus)
        assertEquals(2, fixture.creates.size)
        assertTrue(fixture.state.requestInFlight)
        assertNull(fixture.state.order)
        assertTrue(fixture.legacyAmounts.isEmpty())
    }

    @Test fun leaveRejectsOldCatalogAndCreationResultsEvenAfterReopening() {
        val fixture = fixture()
        val (catalog, create) = queueCatalogAndCreation(fixture)
        fixture.controller.leave()
        assertFalse(fixture.state.catalogLoading)
        assertFalse(fixture.state.requestInFlight)
        fixture.controller.open(null)
        val snapshot = fixture.state
        val navigation = fixture.navigation.toList()

        catalog.onSuccess(membership(entitled = true))
        create.onSuccess(order(status = "paid"))
        assertSame(snapshot, fixture.state)
        assertEquals(navigation, fixture.navigation)
        assertEquals(3, fixture.catalogs.size)
        assertEquals(1, fixture.changed.size)
        assertTrue(fixture.lost.isEmpty())
        fixture.advance(60)
        assertTrue(fixture.polls.isEmpty())

        val current = membership()
        fixture.catalogs.last().onSuccess(current)
        assertSame(current, fixture.state.catalog)
        assertFalse(fixture.state.catalogLoading)
        assertNull(fixture.state.order)
        assertEquals(2, fixture.changed.size)
        assertEquals(ComposeCheckinRoute.MEMBERSHIP, fixture.route)
    }

    @Test fun closeRejectsOldCatalogAndCreationResultsAndCannotStartNewRequests() {
        val fixture = fixture()
        val (catalog, create) = queueCatalogAndCreation(fixture)
        fixture.controller.close()
        val snapshot = fixture.state
        val navigation = fixture.navigation.toList()

        catalog.onSuccess(membership(entitled = true))
        create.onSuccess(order(status = "paid"))
        fixture.controller.open(membership())
        fixture.controller.openCheckout()
        fixture.controller.refreshCatalog()
        fixture.advance(90)
        assertSame(snapshot, fixture.state)
        assertEquals(navigation, fixture.navigation)
        assertEquals(2, fixture.catalogs.size)
        assertEquals(1, fixture.creates.size)
        assertEquals(1, fixture.changed.size)
        assertTrue(fixture.lost.isEmpty())
        assertTrue(fixture.polls.isEmpty())
    }

    @Test fun leaveAndCloseRejectOldCatalogAndCreationAuthorizationErrors() {
        for (close in listOf(false, true)) {
            val fixture = fixture()
            val (catalog, create) = queueCatalogAndCreation(fixture)
            if (close) fixture.controller.close() else fixture.controller.leave()
            val snapshot = fixture.state
            val navigation = fixture.navigation.toList()
            catalog.onError(error(CheckinCenterClient.Operation.BILLING_CATALOG))
            create.onError(error(CheckinCenterClient.Operation.BILLING_CREATE))
            assertSame(snapshot, fixture.state)
            assertEquals(navigation, fixture.navigation)
            assertTrue(fixture.lost.isEmpty())
            assertEquals(1, fixture.changed.size)
            fixture.advance(60)
            assertTrue(fixture.polls.isEmpty())
        }
    }

    @Test fun leaveAndCloseRejectAnInFlightPaidPollWithoutRefreshingEntitlement() {
        for (close in listOf(false, true)) {
            val fixture = fixture()
            fixture.open()
            fixture.createOrder()
            fixture.advance(15)
            val callback = fixture.polls.single()
            if (close) fixture.controller.close() else fixture.controller.leave()
            val snapshot = fixture.state
            callback.onSuccess(order(status = "paid"))
            assertSame(snapshot, fixture.state)
            assertEquals("pending", fixture.state.order!!.status)
            assertEquals(1, fixture.catalogs.size)
            assertEquals(1, fixture.changed.size)
            fixture.advance(90)
            assertEquals(1, fixture.polls.size)
        }
    }

    @Test fun membershipKeysRoundTripAndUseTheirInternalParentsAsLiveTargets() {
        val keys = linkedMapOf(
            ComposeCheckinRoute.CENTER to "checkin_center",
            ComposeCheckinRoute.MEMBERSHIP to "checkin_page_membership",
            ComposeCheckinRoute.CHECKOUT to "checkin_page_checkout",
            ComposeCheckinRoute.REDEEM to "checkin_page_redeem",
            ComposeCheckinRoute.PURCHASES to "checkin_page_purchases",
        )
        assertEquals(keys.size, keys.values.toSet().size)
        for ((page, key) in keys) {
            assertEquals(key, ComposeCheckinNavigation.key(page))
            assertEquals(page, ComposeCheckinNavigation.page(key))
            assertTrue(ComposeSwipePresentation.isLiveRoute(key))
            val expectedParent = when (page) {
                ComposeCheckinRoute.CENTER -> null
                ComposeCheckinRoute.MEMBERSHIP -> ComposeCheckinRoute.CENTER
                else -> ComposeCheckinRoute.MEMBERSHIP
            }
            assertEquals(expectedParent, ComposeCheckinNavigation.parent(page))
            if (expectedParent != null) {
                val parentKey = keys.getValue(expectedParent)
                assertEquals(listOf(key, parentKey), ComposeSwipePresentation.routes(key, parentKey))
            }
        }
        assertNull(ComposeCheckinNavigation.page("checkin_page_missing"))
        assertFalse(ComposeSwipePresentation.isLiveRoute("checkin_page_missing"))
    }

    @Test fun internalBackCommitsTheSameLiveMembershipIdentityWithoutReopeningCatalog() {
        val fixture = fixture()
        fixture.open()
        val membershipKey = ComposeCheckinNavigation.key(ComposeCheckinRoute.MEMBERSHIP)
        for (page in listOf(ComposeCheckinRoute.CHECKOUT, ComposeCheckinRoute.REDEEM,
            ComposeCheckinRoute.PURCHASES)) {
            when (page) {
                ComposeCheckinRoute.CHECKOUT -> fixture.createOrder()
                ComposeCheckinRoute.REDEEM -> fixture.controller.openRedeem()
                ComposeCheckinRoute.PURCHASES -> fixture.controller.openPurchases()
                else -> fail("Unexpected membership child")
            }
            val childKey = ComposeCheckinNavigation.key(fixture.route)
            assertEquals(page, fixture.route)
            val parent = requireNotNull(ComposeCheckinNavigation.parent(page))
            assertEquals(membershipKey, ComposeCheckinNavigation.key(parent))
            assertEquals(listOf(childKey, membershipKey),
                ComposeSwipePresentation.routes(childKey, membershipKey))
            for (width in listOf(192f, 227f, 240f, 320f)) {
                val drag = width / 4f
                assertEquals(drag, ComposeSwipePresentation.translation(
                    childKey, membershipKey, 1, drag, width), 0f)
                assertEquals(drag - width, ComposeSwipePresentation.translation(
                    membershipKey, membershipKey, 1, drag, width), 0f)
                assertEquals(0f, ComposeSwipePresentation.translation(
                    membershipKey, membershipKey, 1, width, width), 0f)
            }
            val snapshot = fixture.state
            fixture.controller.backToMembership()
            val committedKey = ComposeCheckinNavigation.key(fixture.route)
            assertEquals(membershipKey, committedKey)
            assertSame(snapshot, fixture.state)
            assertEquals(listOf(membershipKey),
                ComposeSwipePresentation.routes(committedKey, membershipKey))
            assertEquals(0f, ComposeSwipePresentation.translation(
                committedKey, null, 0, 0f, 240f), 0f)
        }
        assertEquals(1, fixture.catalogs.size)
        assertEquals(1, fixture.creates.size)
        assertEquals(1, fixture.histories.size)
        fixture.advance(90)
        assertTrue(fixture.polls.isEmpty())
    }

    @Test fun legacyCheckoutReentryAlsoReusesThePendingOrderWithoutASku() {
        val fixture = fixture()
        val legacy = CheckinBilling.Membership("free", false, true, false, "", true,
            true, CheckinBilling.Plan.empty())
        fixture.open(legacy)
        fixture.controller.setAmount("5.00")
        val pending = CheckinBilling.Order("HB" + "B".repeat(30), "monitor_wechat",
            500, 500, "CNY", "pending", false, true, "", null)
        fixture.createOrder(pending)
        assertTrue(fixture.state.variableSponsorship())
        assertEquals("", pending.productSku)
        assertNull(fixture.state.selectedProduct())
        fixture.controller.backToMembership()
        fixture.advance(60)
        assertTrue(fixture.polls.isEmpty())
        fixture.controller.openCheckout()
        assertEquals("Internal back must not create another legacy payment order",
            listOf(500), fixture.legacyAmounts)
        assertEquals(1, fixture.creates.size)
        assertSame(pending, fixture.state.order)
        fixture.advance(15)
        assertEquals(listOf(pending.id), fixture.pollIds)
    }

    @Test fun changingTheLegacyAmountDoesNotReuseAnotherAmountsOrder() {
        val fixture = fixture()
        val legacy = CheckinBilling.Membership("free", false, true, false, "", true,
            true, CheckinBilling.Plan.empty())
        fixture.open(legacy)
        fixture.controller.setAmount("5.00")
        val pending = CheckinBilling.Order("HB" + "B".repeat(30), "monitor_wechat",
            500, 500, "CNY", "pending", false, true, "", null)
        fixture.createOrder(pending)
        fixture.controller.backToMembership()
        fixture.controller.setAmount("10.00")
        fixture.controller.openCheckout()
        assertEquals(listOf(500, 1000), fixture.legacyAmounts)
        assertEquals(2, fixture.creates.size)
        assertNull(fixture.state.order)
        assertTrue(fixture.state.requestInFlight)
    }

    private fun fixture() = Fixture().also { fixtures += it }

    private fun queueCatalogAndCreation(fixture: Fixture): Pair<
        CheckinCenterClient.Callback<CheckinBilling.Membership>,
        CheckinCenterClient.Callback<CheckinBilling.Order>> {
        fixture.open()
        fixture.controller.refreshCatalog()
        fixture.controller.openCheckout()
        assertTrue(fixture.state.catalogLoading)
        assertTrue(fixture.state.requestInFlight)
        return fixture.catalogs.last() to fixture.creates.single()
    }

    private class Fixture {
        var state = ComposeMembershipUiState()
        var route = ComposeCheckinRoute.CENTER
        val navigation = mutableListOf<ComposeCheckinRoute>()
        val catalogs = mutableListOf<CheckinCenterClient.Callback<CheckinBilling.Membership>>()
        val creates = mutableListOf<CheckinCenterClient.Callback<CheckinBilling.Order>>()
        val polls = mutableListOf<CheckinCenterClient.Callback<CheckinBilling.Order>>()
        val histories = mutableListOf<CheckinCenterClient.Callback<List<CheckinBilling.OrderRecord>>>()
        val skus = mutableListOf<String>()
        val legacyAmounts = mutableListOf<Int>()
        val pollIds = mutableListOf<String>()
        val changed = mutableListOf<CheckinBilling.Membership>()
        val lost = mutableListOf<String>()
        private val requests = ComposeMembershipRequests(
            catalog = { catalogs += it },
            purchases = { histories += it },
            redeem = { _, _ -> fail("Navigation tests must not redeem membership") },
            create = { sku, callback -> skus += sku; creates += callback },
            createLegacy = { amount, callback -> legacyAmounts += amount; creates += callback },
            poll = { id, callback -> pollIds += id; polls += callback },
            qr = { _, _ -> fail("Fixture orders do not provide a QR endpoint") },
            claim = { _, _, _ -> fail("Navigation tests must not submit payment claims") },
        )
        // Keep the parent active so stale results must be rejected by the real request generations.
        val controller = ComposeCheckinMembershipController(requests, { state }, { state = it },
            { route }, { route = it; navigation += it }, { true }, { lost += it },
            { changed += it }, Handler(Looper.getMainLooper()))

        fun open(catalog: CheckinBilling.Membership = membership()) {
            controller.open(catalog)
            catalogs.last().onSuccess(catalog)
        }
        fun createOrder(value: CheckinBilling.Order = order()) {
            val previousRequests = creates.size
            controller.openCheckout()
            assertEquals(previousRequests + 1, creates.size)
            creates.last().onSuccess(value)
        }
        fun advance(seconds: Long) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
        }
    }

    private companion object {
        fun membership(entitled: Boolean = false) = CheckinBilling.Membership("paid", true,
            entitled, false, "", true, false, CheckinBilling.Plan.empty(), listOf(
                CheckinBilling.Product("monthly", "30 days", 500, "CNY", 30, true),
                CheckinBilling.Product("quarterly", "90 days", 1000, "CNY", 90, true)), "afdian")

        fun order(status: String = "pending") = CheckinBilling.Order("HB" + "A".repeat(30),
            "afdian", 500, 500, "CNY", status, false, false, "", null, "",
            "monthly", "30 days", 30, "")

        fun error(operation: CheckinCenterClient.Operation) =
            CheckinCenterClient.ApiError(operation, 401, "Stale authorization")
    }
}
