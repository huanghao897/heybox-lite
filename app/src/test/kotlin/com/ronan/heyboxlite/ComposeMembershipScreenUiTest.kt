package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeMembershipScreenUiTest : ComposeMembershipUiRegressionHarness() {
    private lateinit var state: MutableState<ComposeMembershipUiState>
    private val selections = mutableListOf<String>()
    private var checkouts = 0
    private var redeems = 0
    private var purchases = 0
    private var retries = 0
    private var backs = 0

    @Test fun productsDelegateTheirServerSkuAndSelectionChangesOnlyWithTheSnapshot() {
        val customProducts = listOf(
            CheckinBilling.Product(MembershipUiFixtures.MONTH, "Custom monthly", 789, "CNY", 30, true),
            CheckinBilling.Product(MembershipUiFixtures.QUARTER, "Custom quarterly", 2156, "CNY", 90, true),
        )
        show(MembershipUiFixtures.state(MembershipUiFixtures.catalog(products = customProducts)))
        tag("membership-product-" + MembershipUiFixtures.MONTH).assertIsSelected()
        val quarterly = tag("membership-product-" + MembershipUiFixtures.QUARTER)
        quarterly.assertIsNotSelected()
        reach(quarterly).performClick()
        compose.runOnIdle {
            assertEquals(listOf(MembershipUiFixtures.QUARTER), selections)
            assertEquals(MembershipUiFixtures.MONTH, state.value.selectedSku)
            state.value = state.value.copy(selectedSku = selections.single())
        }
        quarterly.assertIsSelected()
        tag("membership-product-" + MembershipUiFixtures.MONTH).assertIsNotSelected()
        assertLabelFits("\u00a57.89")
        assertLabelFits("\u00a521.56")
        assertLabelFits("30 \u5929")
        assertLabelFits("90 \u5929")
        text("\u00a59.99").assertDoesNotExist()
        tapReachable("\u5f00\u901a\u4f1a\u5458")
        compose.runOnIdle {
            assertEquals(1, checkouts)
            assertNull(state.value.order)
            assertFalse(requireNotNull(state.value.catalog).entitled)
        }
    }

    @Test fun threeProductsIncludeAnOddFinalRowAndKeepIndependentCurrencyAndDuration() {
        val extra = CheckinBilling.Product("server_week", "Server weekly", 10773, "EUR", 17, true)
        show(MembershipUiFixtures.state(MembershipUiFixtures.catalog(
            products = MembershipUiFixtures.products() + extra)))
        reach(tag("membership-product-server_week")).performClick()
        compose.runOnIdle {
            assertEquals(listOf("server_week"), selections)
            state.value = state.value.copy(selectedSku = "server_week")
        }
        tag("membership-product-server_week").assertIsSelected()
        assertLabelFits("EUR 107.73")
        assertLabelFits("17 \u5929")
        text("\u00a5107.73").assertDoesNotExist()
        capture("odd-final-product")
    }

    @Test fun inactiveMissingAndRemovedProductsCannotCreateAnOrder() {
        val inactive = CheckinBilling.Product(MembershipUiFixtures.MONTH,
            MembershipUiFixtures.MONTH_NAME, 789, "CNY", 30, false)
        show(MembershipUiFixtures.state(MembershipUiFixtures.catalog(products = listOf(inactive))))
        tag("membership-product-" + MembershipUiFixtures.MONTH).assertIsNotEnabled()
        text("\u6682\u4e0d\u53ef\u552e").assertExists()
        text("\u5f00\u901a\u4f1a\u5458").assertIsNotEnabled()
        compose.runOnIdle { state.value = MembershipUiFixtures.state().copy(selectedSku = "removed_sku") }
        text("\u5f00\u901a\u4f1a\u5458").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(catalog = MembershipUiFixtures.catalog(products = emptyList())) }
        text("\u6682\u65e0\u53ef\u7528\u5957\u9910").assertExists()
        text("\u5f00\u901a\u4f1a\u5458").assertIsNotEnabled()
        compose.runOnIdle {
            assertTrue(selections.isEmpty())
            assertEquals(0, checkouts)
            assertNull(state.value.order)
        }
    }

    @Test fun serverUnavailableAndBusyStatesDisableCheckoutAndPreserveTheProducts() {
        show(MembershipUiFixtures.state(MembershipUiFixtures.catalog(available = false)))
        text("\u5f53\u524d\u65e0\u6cd5\u521b\u5efa\u8ba2\u5355").assertExists()
        text("\u5f00\u901a\u4f1a\u5458").assertIsNotEnabled()
        reach(text("\u91cd\u8bd5")).assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            state.value = MembershipUiFixtures.state().copy(catalogLoading = true)
        }
        text("\u6b63\u5728\u66f4\u65b0\u4f1a\u5458\u4fe1\u606f").assertExists()
        tag("membership-product-" + MembershipUiFixtures.QUARTER).assertIsNotEnabled()
        text("\u5f00\u901a\u4f1a\u5458").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(catalogLoading = false, requestInFlight = true) }
        text("\u5904\u7406\u4e2d").assertIsNotEnabled()
        text("\u5151\u6362\u4f1a\u5458").assertIsNotEnabled()
        text("\u8d2d\u4e70\u8bb0\u5f55").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, checkouts + redeems + purchases) }
    }

    @Test fun missingCatalogHasARealRetryAndNoInventedPriceOrEntitlement() {
        show(MembershipUiFixtures.state(catalog = null).copy(catalogLoading = true))
        text("\u6b63\u5728\u8bfb\u53d6\u4f1a\u5458\u4fe1\u606f").assertExists()
        text("\u91cd\u8bd5").assertIsNotEnabled()
        text("\u00a57.89").assertDoesNotExist()
        text("\u5f00\u901a\u4f1a\u5458").assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(catalogLoading = false, message = "Offline catalog unavailable")
        }
        text("\u4f1a\u5458\u4fe1\u606f\u6682\u4e0d\u53ef\u7528").assertExists()
        assertLabelFits("Offline catalog unavailable")
        tapReachable("\u91cd\u8bd5")
        compose.runOnIdle {
            assertEquals(1, retries)
            assertNull(state.value.catalog)
        }
    }

    @Test fun entitlementExpiryAndFreeModeLabelsComeOnlyFromTheSuppliedCatalog() {
        show(MembershipUiFixtures.state(MembershipUiFixtures.catalog(
            expiresAt = MembershipUiFixtures.MEMBERSHIP_EXPIRY)))
        text("\u4f1a\u5458\u5df2\u5230\u671f").assertExists()
        text("\u5df2\u5230\u671f 2027-02-04 12:34").assertExists()
        text("\u4f1a\u5458\u5df2\u5f00\u901a").assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(catalog = MembershipUiFixtures.catalog(
                entitled = true, expiresAt = MembershipUiFixtures.MEMBERSHIP_EXPIRY))
        }
        text("\u4f1a\u5458\u5df2\u5f00\u901a").assertExists()
        assertLabelFits("\u5230\u671f\u65f6\u95f4 2027-02-04 12:34")
        text("\u7eed\u8d39\u4f1a\u5458").assertIsEnabled()
        compose.runOnIdle {
            state.value = state.value.copy(catalog = MembershipUiFixtures.catalog(
                mode = "free", entitled = true, available = false))
        }
        text("\u5f53\u524d\u514d\u8d39\u5f00\u653e").assertExists()
        text("\u5f53\u524d\u65e0\u9700\u8d2d\u4e70\u4f1a\u5458").assertExists()
        compose.runOnIdle {
            state.value = state.value.copy(catalog = MembershipUiFixtures.catalog(admin = true))
        }
        text("\u7ba1\u7406\u5458\u6743\u76ca").assertExists()
        text("\u6743\u76ca\u6682\u672a\u786e\u8ba4").assertExists()
    }

    @Test fun voluntaryAmountUsesServerBoundsAndNeverBecomesAClientSidePayment() {
        show(MembershipUiFixtures.state(MembershipUiFixtures.catalog(
            products = emptyList(), mode = "free", entitled = true, voluntary = true,
            plan = CheckinBilling.Plan("Custom server sponsorship", 999, "CNY", 0,
                true, 123, 12345))).copy(amount = "1.22"))
        assertLabelFits("\u00a51.23 - \u00a5123.45")
        val input = description("\u8d5e\u52a9\u91d1\u989d")
        text("\u786e\u8ba4\u8d5e\u52a9").assertIsNotEnabled()
        input.performTextReplacement("1.23")
        text("\u786e\u8ba4\u8d5e\u52a9").assertIsEnabled()
        tapReachable("\u786e\u8ba4\u8d5e\u52a9")
        compose.runOnIdle {
            assertEquals(1, checkouts)
            assertEquals("1.23", state.value.amount)
            assertNull(state.value.order)
        }
        listOf("123.46", "not money", "", "1.234").forEach { invalid ->
            reach(input).performTextReplacement(invalid)
            text("\u786e\u8ba4\u8d5e\u52a9").assertIsNotEnabled()
        }
        input.performTextReplacement("123.45")
        text("\u786e\u8ba4\u8d5e\u52a9").assertIsEnabled()
    }

    @Test fun fixedProductsHideLegacySponsorshipInputEvenWhenLegacyFlagsRemainSet() {
        val catalog = MembershipUiFixtures.catalog(
            mode = "free", entitled = false, voluntary = true,
            plan = CheckinBilling.Plan("Legacy sponsorship", 999, "CNY", 0,
                true, 1, 100_000_000))
        show(MembershipUiFixtures.state(catalog).copy(amount = "99.99"))

        assertFalse(state.value.variableSponsorship())
        description("\u8d5e\u52a9\u91d1\u989d").assertDoesNotExist()
        text("\u786e\u8ba4\u8d5e\u52a9").assertDoesNotExist()
        text("\u5f00\u901a\u4f1a\u5458").assertIsEnabled()
        assertLabelFits("\u00a55")
        assertLabelFits("\u00a510")
    }

    @Test fun squareProductsAndLastMenuAreUsable() = verifyLayout()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227ProductsAndLastMenuAreInsideTheCircle() = verifyLayout()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192ProductsAndLastMenuAreInsideTheCircle() = verifyLayout()

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowLargeTextLightProductsAndLastMenuDoNotOverlap() = verifyLayout(largeLight = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundLargeTextLightProductsAndLastMenuDoNotOverlap() = verifyLayout(largeLight = true)

    private fun verifyLayout(largeLight: Boolean = false) {
        show(largeLight = largeLight)
        assertLabelFits(MembershipUiFixtures.MONTH_NAME)
        assertLabelFits("\u00a55")
        assertLabelFits(MembershipUiFixtures.QUARTER_NAME)
        assertLabelFits("\u00a510")
        assertLabelsSeparated(MembershipUiFixtures.MONTH_NAME, MembershipUiFixtures.QUARTER_NAME)
        tapReachable(MembershipUiFixtures.QUARTER_NAME)
        compose.runOnIdle {
            assertEquals(listOf(MembershipUiFixtures.QUARTER), selections)
            state.value = state.value.copy(selectedSku = MembershipUiFixtures.QUARTER)
        }
        tag("membership-product-" + MembershipUiFixtures.QUARTER).assertIsSelected()
        tapReachable("\u5f00\u901a\u4f1a\u5458")
        tapReachable("\u5151\u6362\u4f1a\u5458")
        tapReachable("\u8d2d\u4e70\u8bb0\u5f55")
        capture("last-menu")
        assertBackReachable()
        compose.runOnIdle {
            assertEquals(1, checkouts)
            assertEquals(1, redeems)
            assertEquals(1, purchases)
            assertEquals(1, backs)
            assertNull(state.value.order)
            assertFalse(requireNotNull(state.value.catalog).entitled)
        }
    }

    private fun show(
        initial: ComposeMembershipUiState = MembershipUiFixtures.state(),
        largeLight: Boolean = false,
    ) {
        state = mutableStateOf(initial)
        setScreen(theme(largeLight), systemFontScale = if (largeLight) 1.25f else 1f) {
            ComposeMembershipScreen(state.value, { backs++ }, { selections += it }, { checkouts++ },
                { redeems++ }, { purchases++ }, { retries++ },
                { state.value = state.value.copy(amount = it) })
        }
    }
}
