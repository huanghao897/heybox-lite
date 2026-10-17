package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeRoundLayoutFormsUiTest : ComposeRoundLayoutHarness() {
    private var coordinator: CheckinCenterCoordinator? = null
    private var controller: ComposeCheckinController? = null

    @After fun closeFormOwners() {
        controller?.close()
        coordinator?.close()
    }

    @Test fun squareRegistrationKeepsEmailAndBottomActionsReachable() = verifyRegistration()
    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192RegistrationKeepsEmailAndBottomActionsReachable() = verifyRegistration()
    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227RegistrationKeepsEmailAndBottomActionsReachable() = verifyRegistration()
    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundRegistrationUsesTheCircleForItsLastAction() = verifyRegistration(forceRound = true)

    private fun verifyRegistration(forceRound: Boolean = false) {
        val services = layoutServices(forceRound)
        val owner = formOwner(services)
        val state = ComposeCheckinUiState(
            pairing = CheckinCenterClient.PairingStart("fixture-device", "FIXTURE", 120, 5, true, true),
            pairingRemainingSeconds = 120,
            serviceMode = CheckinServiceAccountFlow.Mode.REGISTER,
        )
        var backs = 0
        showLayout(services) { ComposeCheckinPairingScreen(state, owner) { backs++ } }
        assertNoTopToolbar()
        listOf("\u7b7e\u5230\u670d\u52a1\u8d26\u53f7", "\u6ce8\u518c\u90ae\u7bb1",
            state.serviceEmailButton, "\u6ce8\u518c\u5e76\u8fde\u63a5").forEach { label ->
            val target = circleReach(label)
            assertTouchable(target, label)
            assertPaintedLabelInsideDisplay(label)
        }
        // Inspect authentication actions without invoking a live flow or sending credentials.
        circleReach("\u6ce8\u518c\u5e76\u8fde\u63a5").assertIsEnabled()
        touchReachable("\u53d6\u6d88\u8fde\u63a5")
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun squareSmsFormKeepsCodeAndLastActionReachable() = verifyMobile()
    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192SmsFormKeepsCodeAndLastActionReachable() = verifyMobile()
    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227PasswordFormKeepsItsLastActionReachable() = verifyMobile(password = true)
    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundPasswordFormUsesCircularTouchCenters() = verifyMobile(forceRound = true, password = true)

    private fun verifyMobile(forceRound: Boolean = false, password: Boolean = false) {
        val services = layoutServices(forceRound)
        val owner = formOwner(services)
        val state = ComposeCheckinUiState(
            mobileMode = if (password) CheckinMobileLoginFlow.Mode.PASSWORD else CheckinMobileLoginFlow.Mode.SMS,
            mobileHasSession = !password,
        )
        var backs = 0
        showLayout(services) { ComposeCheckinMobileLoginScreen(state, owner) { backs++ } }
        assertNoTopToolbar()
        val labels = if (password) listOf("+86 \u624b\u673a\u53f7", "\u5c0f\u9ed1\u76d2\u767b\u5f55\u5bc6\u7801")
            else listOf("+86 \u624b\u673a\u53f7", state.mobileSmsButton)
        (labels + "\u767b\u5f55\u5e76\u8fde\u63a5").forEach { label ->
            assertTouchable(circleReach(label), label)
            assertPaintedLabelInsideDisplay(label)
        }
        circleReach("\u767b\u5f55\u5e76\u8fde\u63a5").assertIsEnabled()
        touchReachable("\u8fd4\u56de\u7b7e\u5230\u4e2d\u5fc3")
        compose.runOnIdle { assertEquals(1, backs) }
    }

    private fun formOwner(services: ComposeServices): ComposeCheckinController {
        val center = CheckinCenterCoordinator(compose.activity, services.cache)
        coordinator = center
        return ComposeCheckinController(services, center).also { controller = it }
    }
}
