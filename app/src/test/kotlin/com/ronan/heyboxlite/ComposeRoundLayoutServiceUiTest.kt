package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeRoundLayoutServiceUiTest : ComposeRoundLayoutHarness() {
    @Test fun squareCheckinCenterKeepsItsFinalCommandReachable() = verifyCenter()
    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192CheckinCenterKeepsItsFinalCommandReachable() = verifyCenter()
    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227CheckinCenterKeepsItsFinalCommandReachable() = verifyCenter()
    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundCheckinCenterKeepsItsFinalCommandReachable() = verifyCenter(forceRound = true)

    private fun verifyCenter(forceRound: Boolean = false) {
        val services = layoutServices(forceRound)
        val state = MembershipUiFixtures.center()
        var history = 0
        var revoked = 0
        showLayout(services) {
            ComposeCheckinCenterContent(state, {}, {}, {}, {}, {}, {}, {}, {}, { revoked++ }, { history++ })
        }
        assertBackAnchor()
        touchReachable("\u7b7e\u5230\u8bb0\u5f55")
        touchReachable("\u64a4\u9500\u6b64\u8bbe\u5907")
        compose.runOnIdle {
            assertEquals(1, history)
            assertEquals(1, revoked)
        }
    }

    @Test fun squareCheckoutKeepsQrAndTerminalActionReachable() = verifyCheckout()
    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192CheckoutCanShowTheWholeQrAndTerminalAction() = verifyCheckout()
    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227CheckoutCanShowTheWholeQrAndTerminalAction() = verifyCheckout()
    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundCheckoutDoesNotClipTheQrCorners() = verifyCheckout(forceRound = true)

    private fun verifyCheckout(forceRound: Boolean = false) {
        val services = layoutServices(forceRound)
        val bitmap = MembershipUiFixtures.qr()
        val state = mutableStateOf(MembershipUiFixtures.state(order = MembershipUiFixtures.order())
            .copy(qrBitmap = bitmap))
        var finishes = 0
        try {
            showLayout(services) {
                ComposeMembershipCheckoutScreen(state.value, MembershipUiFixtures.account(), {},
                    { finishes++ }, {}, {})
            }
            assertBackAnchor()
            val qr = circleReach(compose.onNodeWithTag("membership-qr"))
            assertControlInsideDisplay(qr, "complete offline payment QR")
            val waiting = circleReach("\u7b49\u5f85\u786e\u8ba4").assertIsNotEnabled()
            assertPaintedLabelInsideDisplay("\u7b49\u5f85\u786e\u8ba4")
            waiting.performTouchInput { click() }
            compose.runOnIdle {
                assertEquals(0, finishes)
                state.value = state.value.copy(order = MembershipUiFixtures.order(status = "paid"))
            }
            touchReachable("\u5b8c\u6210")
            compose.runOnIdle { assertEquals(1, finishes) }
        } finally {
            // Remove the real Image before releasing its local fixture bitmap.
            compose.runOnIdle { state.value = state.value.copy(qrBitmap = null) }
            compose.waitForIdle()
            bitmap.recycle()
        }
    }

    @Test fun squareHistoryCanOpenItsLastRecordAndReadTheLastDetail() = verifyHistory()
    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192HistoryCanOpenItsLastRecordAndReadTheLastDetail() = verifyHistory()
    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227HistoryCanOpenItsLastRecordAndReadTheLastDetail() = verifyHistory()
    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundHistoryCanOpenItsLastRecordAndReadTheLastDetail() = verifyHistory(forceRound = true)

    private fun verifyHistory(forceRound: Boolean = false) {
        val services = layoutServices(forceRound)
        val history = MembershipUiFixtures.history()
        val selected = mutableStateOf<CheckinHistory.Entry?>(null)
        val opened = mutableListOf<Long>()
        var backs = 0
        showLayout(services) {
            val detail = selected.value
            if (detail == null) {
                ComposeCheckinHistoryScreen(ComposeCheckinUiState(history = history), {}, {}, {
                    opened += it.id
                    selected.value = it
                })
            } else ComposeCheckinHistoryDetailScreen(detail) {
                backs++
                selected.value = null
            }
        }
        assertBackAnchor()
        touchReachable(history.entries.last().displayTime())
        compose.runOnIdle { assertEquals(listOf(history.entries.last().id), opened) }
        assertBackAnchor()
        val lastField = "Server detail \u00b7 Archived failure field"
        circleReach(lastField)
        assertPaintedLabelInsideDisplay(lastField)
        val back = compose.onNodeWithContentDescription("\u8fd4\u56de", useUnmergedTree = true)
        back.performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, backs) }
        assertBackAnchor()
    }

    @Test fun squareTaskSettingsKeepsItsLastSharingSwitchTouchable() = verifyTaskSettings()
    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192TaskSettingsKeepsItsLastSharingSwitchTouchable() = verifyTaskSettings()
    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227TaskSettingsKeepsItsLastSharingSwitchTouchable() = verifyTaskSettings()
    @Test @Config(qualifiers = "w192dp-h192dp-mdpi")
    fun forcedRoundTaskSettingsKeepsItsLastSharingSwitchTouchable() = verifyTaskSettings(forceRound = true)

    private fun verifyTaskSettings(forceRound: Boolean = false) {
        val services = layoutServices(forceRound)
        val fixture = MembershipUiFixtures.task()
        val task = CheckinCenterClient.Task(fixture.enabled, fixture.sign, fixture.scheduleTime,
            fixture.offsetMinutes, fixture.windowStart, fixture.windowEnd, fixture.platformBlocked,
            fixture.signBlocked, CheckinSharing.parse(JSONObject().put("share_actions_available", true)))
        val offsets = mutableListOf<Int>()
        val shares = mutableListOf<Pair<String, Boolean>>()
        showLayout(services) {
            ComposeCheckinTaskSettingsContent(MembershipUiFixtures.center(task = task), {}, {}, {},
                { offsets += it }, { action, enabled -> shares += action to enabled })
        }
        assertBackAnchor()
        val increase = circleReach(compose.onNodeWithContentDescription("\u589e\u52a0\u968f\u673a\u504f\u79fb"))
            .assertIsEnabled()
        assertTouchable(increase, "increase random offset")
        increase.performTouchInput { click() }
        touchReachable("\u5206\u4eab\u8bc4\u4ef7")
        compose.runOnIdle {
            assertEquals(listOf((task.offsetMinutes + 30).coerceAtMost(720)), offsets)
            assertEquals(listOf("share_review" to true), shares)
        }
    }
}
