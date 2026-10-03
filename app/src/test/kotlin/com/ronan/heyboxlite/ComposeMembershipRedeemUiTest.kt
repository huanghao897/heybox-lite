package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
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
internal class ComposeMembershipRedeemUiTest : ComposeMembershipUiRegressionHarness() {
    private lateinit var state: MutableState<ComposeMembershipUiState>
    private val codes = mutableListOf<String>()
    private val submits = mutableListOf<String>()
    private var backs = 0

    @Test fun blankCodeCannotSubmitAndEditingDoesNotRedeemAutomatically() {
        show()
        text("\u786e\u8ba4\u5151\u6362").assertIsNotEnabled()
        val input = description("\u4f1a\u5458\u5151\u6362\u7801")
        input.performTextReplacement("   ")
        text("\u786e\u8ba4\u5151\u6362").assertIsNotEnabled()
        input.performTextReplacement("LOCAL-CODE-123")
        text("\u786e\u8ba4\u5151\u6362").assertIsEnabled()
        compose.runOnIdle {
            assertEquals("LOCAL-CODE-123", codes.last())
            assertTrue(submits.isEmpty())
            assertFalse(requireNotNull(state.value.catalog).entitled)
        }
        tapReachable("\u786e\u8ba4\u5151\u6362")
        compose.runOnIdle {
            assertEquals(listOf("LOCAL-CODE-123"), submits)
            assertFalse(requireNotNull(state.value.catalog).entitled)
            assertNull(state.value.order)
            assertEquals("", state.value.message)
        }
        text("\u4f1a\u5458\u5df2\u5f00\u901a").assertDoesNotExist()
    }

    @Test fun requestInFlightLocksTheDraftAndConfirmationUntilTheSnapshotChanges() {
        show(MembershipUiFixtures.state().copy(redeemCode = "LOCAL-CODE", requestInFlight = true))
        description("\u4f1a\u5458\u5151\u6362\u7801").assertIsNotEnabled().assertTextContains("LOCAL-CODE")
        text("\u63d0\u4ea4\u4e2d").assertIsNotEnabled()
        compose.runOnIdle {
            assertTrue(submits.isEmpty() && codes.isEmpty())
            state.value = state.value.copy(requestInFlight = false, message = "Offline redeem rejected")
        }
        description("\u4f1a\u5458\u5151\u6362\u7801").assertIsEnabled().assertTextContains("LOCAL-CODE")
        text("\u786e\u8ba4\u5151\u6362").assertIsEnabled()
        assertLabelFits("Offline redeem rejected")
        text("\u672a\u5f00\u901a\u4f1a\u5458").assertExists()
        text("\u4f1a\u5458\u5df2\u5f00\u901a").assertDoesNotExist()
    }

    @Test fun errorsAndExpiredMembershipRemainVisibleWithoutAClientSuccess() {
        show(MembershipUiFixtures.state(MembershipUiFixtures.catalog(
            expiresAt = MembershipUiFixtures.MEMBERSHIP_EXPIRY)).copy(
            redeemCode = "LOCAL-REJECTED", message = "This offline code is not valid"))
        assertLabelFits("This offline code is not valid")
        assertLabelFits("\u5df2\u5230\u671f 2027-02-04 12:34")
        tapReachable("\u786e\u8ba4\u5151\u6362")
        compose.runOnIdle {
            assertEquals(listOf("LOCAL-REJECTED"), submits)
            assertFalse(requireNotNull(state.value.catalog).entitled)
            assertEquals("This offline code is not valid", state.value.message)
        }
        text("\u4f1a\u5458\u5df2\u5230\u671f").assertExists()
    }

    @Test fun missingCatalogDoesNotMakeEditingOrSubmissionCreateEntitlement() {
        show(MembershipUiFixtures.state(catalog = null).copy(redeemCode = "LOCAL-CODE"))
        tapReachable("\u786e\u8ba4\u5151\u6362")
        text("\u4f1a\u5458\u5df2\u5f00\u901a").assertDoesNotExist()
        text("\u5f53\u524d\u514d\u8d39\u5f00\u653e").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("LOCAL-CODE"), submits)
            assertNull(state.value.catalog)
        }
    }

    @Test fun restoringTheRealInputWithItsExternalDraftDoesNotResubmit() {
        val restoration = StateRestorationTester(compose)
        show(restoration = restoration)
        description("\u4f1a\u5458\u5151\u6362\u7801").performTextReplacement("LOCAL-RESTORED-DRAFT")
        restoration.emulateSavedInstanceStateRestore()
        description("\u4f1a\u5458\u5151\u6362\u7801").assertTextContains("LOCAL-RESTORED-DRAFT")
        text("\u786e\u8ba4\u5151\u6362").assertIsEnabled()
        compose.runOnIdle { assertTrue(submits.isEmpty()) }
        tapReachable("\u786e\u8ba4\u5151\u6362")
        compose.runOnIdle { assertEquals(listOf("LOCAL-RESTORED-DRAFT"), submits) }
    }

    @Test fun squareRedeemInputAndConfirmationAreUsable() = verifyLayout()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227RedeemInputAndConfirmationAreInsideTheCircle() = verifyLayout()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192RedeemInputAndConfirmationAreInsideTheCircle() = verifyLayout()

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowLargeTextLightRedeemHasNoOverlappingText() = verifyLayout(largeLight = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundLargeTextLightRedeemKeepsConfirmationReachable() = verifyLayout(largeLight = true)

    private fun verifyLayout(largeLight: Boolean = false) {
        show(largeLight = largeLight)
        val input = reach(description("\u4f1a\u5458\u5151\u6362\u7801"))
        val node = input.fetchSemanticsNode()
        val bounds = Rect(node.positionInWindow.x, node.positionInWindow.y,
            node.positionInWindow.x + node.size.width, node.positionInWindow.y + node.size.height)
        capture("redeem-input")
        assertInDisplay(bounds, "redeem input", corners = true)
        input.performTextReplacement("LOCAL-CODE-123")
        tapReachable("\u786e\u8ba4\u5151\u6362")
        capture("redeem-confirmation")
        assertLabelFits("\u672a\u5f00\u901a\u4f1a\u5458")
        assertBackReachable()
        compose.runOnIdle {
            assertEquals(listOf("LOCAL-CODE-123"), submits)
            assertEquals(1, backs)
            assertFalse(requireNotNull(state.value.catalog).entitled)
            assertNull(state.value.order)
        }
    }

    private fun show(
        initial: ComposeMembershipUiState = MembershipUiFixtures.state(),
        largeLight: Boolean = false,
        restoration: StateRestorationTester? = null,
    ) {
        state = mutableStateOf(initial)
        setScreen(theme(largeLight), systemFontScale = if (largeLight) 1.25f else 1f,
            restoration = restoration) {
            ComposeMembershipRedeemScreen(state.value, { backs++ },
                { codes += it; state.value = state.value.copy(redeemCode = it) },
                { submits += state.value.redeemCode })
        }
    }
}
