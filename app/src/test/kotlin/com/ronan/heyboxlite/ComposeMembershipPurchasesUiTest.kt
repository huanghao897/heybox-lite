package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeMembershipPurchasesUiTest : ComposeMembershipUiRegressionHarness() {
    private lateinit var state: MutableState<ComposeMembershipUiState>
    private var retries = 0
    private var backs = 0

    @Test fun archivedRowsUsePayableAmountsCurrencyPurchaseDatesAndServerStatus() {
        val customRecords = listOf(
            CheckinBilling.OrderRecord(MembershipUiFixtures.ORDER_ID, MembershipUiFixtures.QUARTER,
                "Historical quarterly", 2156, 2119, "CNY", "paid", "2026-09-12T10:20:00Z",
                MembershipUiFixtures.ORDER_EXPIRY, 90),
            CheckinBilling.OrderRecord("HBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", MembershipUiFixtures.MONTH,
                "Historical monthly", 789, 731, "USD", "expired", "2026-09-30T11:22:00Z",
                MembershipUiFixtures.ORDER_EXPIRY, 30),
        )
        show(MembershipUiFixtures.state().copy(purchases = customRecords))
        listOf("Historical quarterly", "\u00a521.19", "2026-09-12 10:20", "90 \u5929", "\u652f\u4ed8\u5df2\u786e\u8ba4",
            "Historical monthly", "USD 7.31", "2026-09-30 11:22", "30 \u5929", "\u8ba2\u5355\u5df2\u8fc7\u671f")
            .forEach(::assertLabelFits)
        text("\u00a521.56").assertDoesNotExist()
        text("\u00a57.89").assertDoesNotExist()
        text("2026-10-03 15:45").assertDoesNotExist()
        text("\u4f1a\u5458\u5df2\u5f00\u901a").assertDoesNotExist()
        val rows = customRecords.map { tag("membership-purchase-" + it.orderId).fetchSemanticsNode() }
        assertTrue("Preserve server history order", rows[0].positionInRoot.y < rows[1].positionInRoot.y)
        rows.forEach { assertFalse("History must not initiate a payment",
            it.config.contains(SemanticsActions.OnClick)) }
        compose.runOnIdle { assertFalse(requireNotNull(state.value.catalog).entitled) }
    }

    @Test fun unnamedHistoricalProductsUseTheirSkuNotTheSelectedProductOrCurrentPrice() {
        val records = listOf(
            CheckinBilling.OrderRecord("LOCAL_KNOWN", MembershipUiFixtures.QUARTER, "", 2156, 1237,
                "CNY", "pending", "2026-08-19T01:02:00Z", "", 17),
            CheckinBilling.OrderRecord("LOCAL_RETIRED", "retired_server_sku", "", 9999, 9405,
                "CHF", "provider_unknown", "", "", 0),
            CheckinBilling.OrderRecord("LOCAL_UNKNOWN", "", "", 789, 456,
                "CNY", "failed", "2026-08-20T03:04:00Z", "", 0),
        )
        show(MembershipUiFixtures.state().copy(purchases = records))
        listOf(MembershipUiFixtures.QUARTER_NAME, "\u00a512.37", "17 \u5929", "2026-08-19 01:02",
            "\u7b49\u5f85\u4ed8\u6b3e", "retired_server_sku", "CHF 94.05", "\u672a\u77e5\u65f6\u95f4",
            "\u72b6\u6001\u5f85\u786e\u8ba4", "\u5957\u9910\u4fe1\u606f\u5f85\u786e\u8ba4", "\u00a54.56", "\u8ba2\u5355\u5931\u8d25").forEach(::assertLabelFits)
        text(MembershipUiFixtures.MONTH_NAME).assertDoesNotExist()
        text("\u00a521.56").assertDoesNotExist()
        text("90 \u5929").assertDoesNotExist()
    }

    @Test fun emptyLoadingAndErrorStatesAreDistinctAndRetryIsDelegatedOnly() {
        show(MembershipUiFixtures.state().copy(purchasesLoading = true))
        text("\u6b63\u5728\u8bfb\u53d6\u8d2d\u4e70\u8bb0\u5f55").assertExists()
        text("\u6682\u65e0\u8d2d\u4e70\u8bb0\u5f55").assertDoesNotExist()
        text("\u91cd\u8bd5").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(purchasesLoading = false) }
        text("\u6682\u65e0\u8d2d\u4e70\u8bb0\u5f55").assertExists()
        compose.runOnIdle { state.value = state.value.copy(message = "Offline purchase history failed") }
        text("\u6682\u65e0\u8d2d\u4e70\u8bb0\u5f55").assertDoesNotExist()
        text("\u8d2d\u4e70\u8bb0\u5f55\u6682\u4e0d\u53ef\u7528").assertExists()
        assertLabelFits("Offline purchase history failed")
        tapReachable("\u91cd\u8bd5")
        compose.runOnIdle {
            assertEquals(1, retries)
            assertTrue(state.value.purchases.isEmpty())
        }
    }

    @Test fun refreshingOrFailedRefreshKeepsExistingRowsAndDisablesDuplicateRequests() {
        show(MembershipUiFixtures.state().copy(purchases = MembershipUiFixtures.purchases(),
            purchasesLoading = true, message = "Offline refresh unavailable"))
        text(MembershipUiFixtures.QUARTER_NAME).assertExists()
        text(MembershipUiFixtures.MONTH_NAME).assertExists()
        text("\u8d2d\u4e70\u8bb0\u5f55\u6682\u4e0d\u53ef\u7528").assertDoesNotExist()
        text("\u91cd\u8bd5").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(purchasesLoading = false, requestInFlight = true) }
        text("\u91cd\u8bd5").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(requestInFlight = false) }
        text("\u91cd\u8bd5").assertIsEnabled()
        assertLabelFits("Offline refresh unavailable")
        tapReachable("\u91cd\u8bd5")
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(2, state.value.purchases.size)
        }
    }

    @Test fun squarePurchaseRowsAndFinalRetryAreUsable() = verifyLayout()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227PurchasesAndFinalRetryAreInsideTheCircle() = verifyLayout()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192PurchasesAndFinalRetryAreInsideTheCircle() = verifyLayout()

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowLargeTextLightPurchasesDoNotOverlapAmountsOrDates() = verifyLayout(largeLight = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundLargeTextLightPurchasesKeepTheLastRetryReachable() = verifyLayout(largeLight = true)

    private fun verifyLayout(largeLight: Boolean = false) {
        show(largeLight = largeLight)
        listOf(MembershipUiFixtures.QUARTER_NAME, "\u00a510", "2026-09-12 10:20", "\u652f\u4ed8\u5df2\u786e\u8ba4")
            .forEach(::assertLabelFits)
        capture("first-archived-purchase")
        listOf(MembershipUiFixtures.MONTH_NAME, "\u00a55", "2026-09-30 11:22", "\u8ba2\u5355\u5df2\u8fc7\u671f")
            .forEach(::assertLabelFits)
        capture("last-archived-purchase")
        tapReachable("\u91cd\u8bd5")
        capture("last-retry")
        assertBackReachable()
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, backs)
            assertEquals(MembershipUiFixtures.purchases().map { it.orderId }, state.value.purchases.map { it.orderId })
            assertFalse(requireNotNull(state.value.catalog).entitled)
        }
    }

    private fun show(
        initial: ComposeMembershipUiState = MembershipUiFixtures.state().copy(purchases = MembershipUiFixtures.purchases()),
        largeLight: Boolean = false,
    ) {
        state = mutableStateOf(initial)
        setScreen(theme(largeLight), systemFontScale = if (largeLight) 1.25f else 1f) {
            ComposeMembershipPurchasesScreen(state.value, { backs++ }, { retries++ })
        }
    }
}
