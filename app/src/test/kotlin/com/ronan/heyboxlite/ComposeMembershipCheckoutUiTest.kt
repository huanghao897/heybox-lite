package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeMembershipCheckoutUiTest : ComposeMembershipUiRegressionHarness() {
    private lateinit var state: MutableState<ComposeMembershipUiState>
    private var finishes = 0
    private var claims = 0
    private var backs = 0
    private val references = mutableListOf<String>()

    @Test fun pendingCheckoutDisplaysTheOrderNotTheSelectedProductOrListPrice() {
        val catalog = MembershipUiFixtures.catalog(products = listOf(
            CheckinBilling.Product(MembershipUiFixtures.MONTH, "Custom monthly", 789, "CNY", 30, true),
            CheckinBilling.Product(MembershipUiFixtures.QUARTER, "Custom quarterly", 2156, "CNY", 90, true),
        ))
        show(MembershipUiFixtures.state(catalog, MembershipUiFixtures.order(amount = 2156,
            payable = 2119, name = "Ordered quarterly")).copy(qrBitmap = MembershipUiFixtures.qr()))
        assertLabelFits("\u00a521.19")
        assertLabelFits("Ordered quarterly")
        assertLabelFits("90 \u5929")
        assertLabelFits("2026-10-03 15:45")
        assertLabelFits("123****89")
        text("\u00a521.56").assertDoesNotExist()
        text("\u00a57.89").assertDoesNotExist()
        text("Custom monthly").assertDoesNotExist()
        text("Private fixture name").assertDoesNotExist()
        text("\u7b49\u5f85\u786e\u8ba4").assertIsNotEnabled()
        text("\u670d\u52a1\u5668\u652f\u4ed8\u5df2\u786e\u8ba4").assertDoesNotExist()
        assertQrPixels()
        compose.runOnIdle {
            assertEquals("pending", state.value.order?.status)
            assertFalse(requireNotNull(state.value.catalog).entitled)
            assertEquals(0, claims + finishes)
        }
    }

    @Test fun anotherCurrencyAndProviderAreNotReplacedWithYuanOrAClientDefault() {
        show(MembershipUiFixtures.state(order = MembershipUiFixtures.order(
            currency = "USD", amount = 2156, payable = 1703, provider = "monitor_alipay")))
        assertLabelFits("USD 17.03")
        text("\u652f\u4ed8\u5b9d").assertExists()
        text("\u901a\u8fc7\u652f\u4ed8\u5b9d\u652f\u4ed8").assertExists()
        text("\u00a517.03").assertDoesNotExist()
        text("\u7231\u53d1\u7535").assertDoesNotExist()
        text("\u786e\u8ba4\u5151\u6362").assertDoesNotExist()
    }

    @Test fun missingOrderFieldsFallBackToTheirServerSkuAndMaskedAccountOnly() {
        show(MembershipUiFixtures.state(order = MembershipUiFixtures.order(
            provider = "", name = "", duration = 0)),
            account = CheckinCenterClient.Account("connected", "Unmasked private account", "123456789"))
        text(MembershipUiFixtures.QUARTER_NAME).assertExists()
        text("90 \u5929").assertExists()
        text("\u7231\u53d1\u7535").assertExists()
        text("1***9").assertExists()
        text("123456789").assertDoesNotExist()
        text("Unmasked private account").assertDoesNotExist()
        compose.runOnIdle {
            state.value = MembershipUiFixtures.state(catalog = null, order = MembershipUiFixtures.order(
                provider = "", sku = "orphan_server_sku", name = "", duration = 0))
        }
        text("orphan_server_sku").assertExists()
        text("90 \u5929").assertDoesNotExist()
        text("\u652f\u4ed8\u6e20\u9053\u5f85\u786e\u8ba4").assertExists()
    }

    @Test fun checkoutUrlNeverExposesTheLegacyManualClaimEvenIfTheFlagIsSet() {
        show(MembershipUiFixtures.state(order = MembershipUiFixtures.order(manual = true)))
        description("\u652f\u4ed8\u8ba2\u5355\u53f7").assertDoesNotExist()
        text("\u63d0\u4ea4\u8ba2\u5355\u53f7").assertDoesNotExist()
        text("\u7b49\u5f85\u786e\u8ba4").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, claims + finishes) }
    }

    @Test fun legacyReferenceRequiresValidInputAndSubmissionDoesNotConfirmPayment() {
        show(MembershipUiFixtures.state(order = MembershipUiFixtures.order(
            manual = true, checkout = "", provider = "monitor_wechat")))
        val input = description("\u652f\u4ed8\u8ba2\u5355\u53f7")
        listOf("", "abc", "has spaces", "!", "x".repeat(81)).forEach { invalid ->
            reach(input).performTextReplacement(invalid)
            text("\u63d0\u4ea4\u8ba2\u5355\u53f7").assertIsNotEnabled()
        }
        input.performTextReplacement("LOCAL_REFERENCE_123")
        text("\u63d0\u4ea4\u8ba2\u5355\u53f7").assertIsEnabled()
        tapReachable("\u63d0\u4ea4\u8ba2\u5355\u53f7")
        compose.runOnIdle {
            assertEquals(1, claims)
            assertEquals("LOCAL_REFERENCE_123", references.last())
            assertEquals("pending", state.value.order?.status)
            assertNull(state.value.review)
            assertFalse(requireNotNull(state.value.catalog).entitled)
            state.value = state.value.copy(requestInFlight = true)
        }
        input.assertIsNotEnabled()
        text("\u63d0\u4ea4\u8ba2\u5355\u53f7").assertIsNotEnabled()
        text("\u5904\u7406\u4e2d").assertIsNotEnabled()
        text("\u670d\u52a1\u5668\u652f\u4ed8\u5df2\u786e\u8ba4").assertDoesNotExist()
    }

    @Test fun pendingOrApprovedManualReviewCannotResubmitOrRegenerateAnUnpaidOrder() {
        val pending = CheckinBilling.Review("LOCAL_CLAIM", "LOCAL_REFERENCE", "pending", "")
        show(MembershipUiFixtures.state(order = MembershipUiFixtures.order(
            status = "expired", manual = true, checkout = "", review = pending)))
        text("\u8ba2\u5355\u53f7\u5f85\u5ba1\u6838").assertExists()
        text("\u63d0\u4ea4\u8ba2\u5355\u53f7").assertDoesNotExist()
        text("\u91cd\u65b0\u751f\u6210\u8ba2\u5355").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(review =
            CheckinBilling.Review("LOCAL_CLAIM", "LOCAL_REFERENCE", "approved", "")) }
        text("\u5ba1\u6838\u5df2\u901a\u8fc7").assertExists()
        text("\u91cd\u65b0\u751f\u6210\u8ba2\u5355").assertIsNotEnabled()
        text("\u670d\u52a1\u5668\u652f\u4ed8\u5df2\u786e\u8ba4").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("expired", state.value.order?.status)
            assertFalse(requireNotNull(state.value.catalog).entitled)
            assertEquals(0, claims + finishes)
            state.value = state.value.copy(review =
                CheckinBilling.Review("LOCAL_CLAIM", "LOCAL_REFERENCE", "rejected", "Server mismatch"))
        }
        text("\u5ba1\u6838\u672a\u901a\u8fc7\uff1aServer mismatch").assertExists()
        description("\u652f\u4ed8\u8ba2\u5355\u53f7").assertIsEnabled()
        text("\u91cd\u65b0\u751f\u6210\u8ba2\u5355").assertIsEnabled()
    }

    @Test fun qrErrorOffersRetryButLoadingAndInFlightStatesBlockIt() {
        show(MembershipUiFixtures.state(order = MembershipUiFixtures.order()).copy(
            message = "Offline QR unavailable"))
        text("\u4ed8\u6b3e\u7801\u6682\u4e0d\u53ef\u7528").assertExists()
        tapReachable("\u91cd\u8bd5")
        compose.runOnIdle {
            assertEquals(1, finishes)
            assertEquals("pending", state.value.order?.status)
            state.value = state.value.copy(qrLoading = true)
        }
        text("\u6b63\u5728\u52a0\u8f7d\u4ed8\u6b3e\u7801").assertExists()
        text("\u7b49\u5f85\u786e\u8ba4").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(qrLoading = false, requestInFlight = true) }
        text("\u5904\u7406\u4e2d").assertIsNotEnabled()
    }

    @Test fun expiredFailedAndUnknownOrdersDoNotRenderAConfirmationOrQr() {
        show(MembershipUiFixtures.state(order = MembershipUiFixtures.order(status = "expired")))
        listOf("expired" to "\u8ba2\u5355\u5df2\u8fc7\u671f", "failed" to "\u8ba2\u5355\u5931\u8d25", "processing" to "\u72b6\u6001\u5f85\u786e\u8ba4")
            .forEach { (status, label) ->
                compose.runOnIdle { state.value = state.value.copy(order = MembershipUiFixtures.order(status = status)) }
                text(label).assertExists()
                tag("membership-qr").assertDoesNotExist()
                text("\u670d\u52a1\u5668\u652f\u4ed8\u5df2\u786e\u8ba4").assertDoesNotExist()
                tapReachable("\u91cd\u65b0\u751f\u6210\u8ba2\u5355")
                compose.runOnIdle { assertEquals(status, state.value.order?.status) }
            }
        compose.runOnIdle { assertEquals(3, finishes) }
    }

    @Test fun aLocalPaidSnapshotStillCannotInventMembershipEntitlement() {
        // This explicitly supplied snapshot is not a poll result or a payment performed by the test.
        show(MembershipUiFixtures.state(order = MembershipUiFixtures.order(status = "paid")))
        text("\u670d\u52a1\u5668\u652f\u4ed8\u5df2\u786e\u8ba4").assertExists()
        text("\u672a\u5f00\u901a\u4f1a\u5458").assertExists()
        text("\u4f1a\u5458\u5df2\u5f00\u901a").assertDoesNotExist()
        tag("membership-qr").assertDoesNotExist()
        tapReachable("\u5b8c\u6210")
        compose.runOnIdle {
            assertEquals(1, finishes)
            assertFalse(requireNotNull(state.value.catalog).entitled)
            state.value = state.value.copy(catalog = MembershipUiFixtures.catalog(
                entitled = true, expiresAt = MembershipUiFixtures.MEMBERSHIP_EXPIRY))
        }
        text("\u4f1a\u5458\u5df2\u5f00\u901a").assertExists()
        assertLabelFits("\u5230\u671f\u65f6\u95f4 2027-02-04 12:34")
    }

    @Test fun creatingOrMissingOrderDoesNotOfferPaymentControls() {
        show(MembershipUiFixtures.state(order = null).copy(requestInFlight = true))
        text("\u6b63\u5728\u521b\u5efa\u8ba2\u5355").assertExists()
        text("\u91cd\u8bd5").assertIsNotEnabled()
        tag("membership-payable").assertDoesNotExist()
        tag("membership-qr").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(requestInFlight = false, message = "Offline create failed") }
        text("\u8ba2\u5355\u6682\u4e0d\u53ef\u7528").assertExists()
        text("Offline create failed").assertExists()
        tapReachable("\u91cd\u8bd5")
        compose.runOnIdle {
            assertEquals(1, finishes)
            assertNull(state.value.order)
        }
    }

    @Test fun squareCheckoutAndFinalRetryAreUsable() = verifyLayout()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227QrAndFinalRetryAreInsideTheCircle() = verifyLayout()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192QrAndFinalRetryAreInsideTheCircle() = verifyLayout()

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowLargeTextLightCheckoutHasNoOverlappingAmounts() = verifyLayout(largeLight = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundLargeTextLightCheckoutKeepsQrAndFinalRetryReachable() = verifyLayout(largeLight = true)

    private fun verifyLayout(largeLight: Boolean = false) {
        show(MembershipUiFixtures.state(order = MembershipUiFixtures.order()).copy(qrBitmap = MembershipUiFixtures.qr()),
            largeLight = largeLight)
        listOf("\u00a510", "123****89", MembershipUiFixtures.QUARTER_NAME, "90 \u5929", "\u7231\u53d1\u7535", "2026-10-03 15:45")
            .forEach(::assertLabelFits)
        assertLabelsSeparated("\u5e94\u4ed8\u91d1\u989d", "\u00a510")
        assertLabelsSeparated("\u5957\u9910", MembershipUiFixtures.QUARTER_NAME)
        assertQrPixels()
        assertLabelFits("\u7b49\u5f85\u786e\u8ba4")
        text("\u7b49\u5f85\u786e\u8ba4").assertIsNotEnabled()
        capture("pending-last-button")
        compose.runOnIdle { state.value = state.value.copy(
            order = MembershipUiFixtures.order(status = "expired"), qrBitmap = null) }
        text("\u8ba2\u5355\u5df2\u8fc7\u671f").assertExists()
        tapReachable("\u91cd\u65b0\u751f\u6210\u8ba2\u5355")
        capture("expired-last-button")
        assertBackReachable()
        compose.runOnIdle {
            assertEquals(1, finishes)
            assertEquals(1, backs)
            assertEquals(0, claims)
            assertEquals("expired", state.value.order?.status)
            assertFalse(requireNotNull(state.value.catalog).entitled)
        }
    }

    private fun assertQrPixels() {
        val node = reach(tag("membership-qr")).fetchSemanticsNode()
        val bounds = Rect(node.positionInWindow.x, node.positionInWindow.y,
            node.positionInWindow.x + node.size.width, node.positionInWindow.y + node.size.height)
        capture("rendered-qr")
        assertInDisplay(bounds, "full QR with its quiet zone", corners = true)
        assertEquals("QR must stay square", bounds.width, bounds.height, 1f)
        val result = compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val width = bounds.width.roundToInt()
                val height = bounds.height.roundToInt()
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, bounds.left.roundToInt(), bounds.top.roundToInt(), width, height)
                MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))).text
            } finally {
                bitmap.recycle()
            }
        }
        assertEquals("The native-rendered QR must contain only the offline fixture", "UI_FIXTURE_NOT_A_PAYMENT", result)
    }

    private fun show(
        initial: ComposeMembershipUiState = MembershipUiFixtures.state(order = MembershipUiFixtures.order()),
        account: CheckinCenterClient.Account? = MembershipUiFixtures.account(),
        largeLight: Boolean = false,
    ) {
        state = mutableStateOf(initial)
        setScreen(theme(largeLight), systemFontScale = if (largeLight) 1.25f else 1f) {
            ComposeMembershipCheckoutScreen(state.value, account, { backs++ }, { finishes++ },
                { references += it; state.value = state.value.copy(paymentReference = it) }, { claims++ })
        }
    }
}
