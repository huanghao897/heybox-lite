package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import org.junit.Assert.assertEquals
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
internal class ComposeCheckinCenterUiTest : ComposeMembershipUiRegressionHarness() {
    private lateinit var state: MutableState<ComposeCheckinUiState>
    private val actions = mutableListOf<String>()

    @Test fun disconnectedAndUnsupportedStatesDoNotExposeMemberOrRunActions() {
        show(ComposeCheckinUiState(supported = false))
        text("\u672a\u8fde\u63a5\u7b7e\u5230\u670d\u52a1").assertExists()
        text("\u8fde\u63a5\u7b7e\u5230\u670d\u52a1").assertIsNotEnabled()
        text("\u7acb\u5373\u7b7e\u5230").assertDoesNotExist()
        text("\u4f1a\u5458\u670d\u52a1").assertDoesNotExist()
        text("\u7b7e\u5230\u8bb0\u5f55").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(supported = true) }
        tapReachable("\u8fde\u63a5\u7b7e\u5230\u670d\u52a1")
        compose.runOnIdle {
            assertEquals(listOf("connect"), actions)
            assertEquals(false, state.value.paired)
            assertNull(state.value.status)
        }
    }

    @Test fun missingStatusHasLoadingAndErrorRetryWithoutInventingAnAccount() {
        show(ComposeCheckinUiState(paired = true, stage = ComposeCheckinStage.SYNCING))
        text("\u6b63\u5728\u8bfb\u53d6\u8d26\u53f7").assertExists()
        text("\u7acb\u5373\u7b7e\u5230").assertDoesNotExist()
        text("\u7b7e\u5230\u8bb0\u5f55").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(stage = ComposeCheckinStage.ERROR,
            errorMessage = "Offline status unavailable") }
        text("\u6682\u65f6\u65e0\u6cd5\u8fde\u63a5").assertExists()
        assertLabelFits("Offline status unavailable")
        tapReachable("\u91cd\u65b0\u8bfb\u53d6")
        compose.runOnIdle {
            assertEquals(listOf("refresh"), actions)
            assertNull(state.value.status)
        }
    }

    @Test fun requiredMembershipBlocksRunUntilAnEntitledSnapshotArrives() {
        show()
        text("\u7b49\u5f85\u5f00\u901a\u4f1a\u5458").assertExists()
        text("\u7acb\u5373\u7b7e\u5230").assertIsNotEnabled()
        tapReachable("\u4f1a\u5458\u670d\u52a1")
        compose.runOnIdle {
            assertEquals(listOf("membership"), actions)
            assertEquals(ComposeCheckinRoute.CENTER, state.value.route)
            assertNull(state.value.status?.lastRun)
            state.value = MembershipUiFixtures.center(MembershipUiFixtures.catalog(entitled = true))
        }
        text("\u7acb\u5373\u7b7e\u5230").assertIsEnabled()
        tapReachable("\u7acb\u5373\u7b7e\u5230")
        compose.runOnIdle {
            assertEquals(listOf("membership", "run"), actions)
            assertEquals(ComposeCheckinStage.CONNECTED, state.value.stage)
            assertNull(state.value.status?.lastRun)
        }
        text("\u7b7e\u5230\u6210\u529f").assertDoesNotExist()
        text("\u7b7e\u5230\u6267\u884c\u4e2d").assertDoesNotExist()
    }

    @Test fun blockedDisabledAndRunningTasksDisableRunAndRunningDisablesRevoke() {
        show(MembershipUiFixtures.center(MembershipUiFixtures.catalog(entitled = true),
            task = MembershipUiFixtures.task(platformBlocked = true)))
        text("\u81ea\u52a8\u7b7e\u5230\u5df2\u6682\u505c").assertExists()
        text("\u7acb\u5373\u7b7e\u5230").assertIsNotEnabled()
        compose.runOnIdle {
            state.value = MembershipUiFixtures.center(MembershipUiFixtures.catalog(entitled = true),
                task = MembershipUiFixtures.task(signBlocked = true))
        }
        text("\u81ea\u52a8\u7b7e\u5230\u5df2\u6682\u505c").assertExists()
        text("\u7acb\u5373\u7b7e\u5230").assertIsNotEnabled()
        compose.runOnIdle {
            state.value = MembershipUiFixtures.center(MembershipUiFixtures.catalog(entitled = true),
                task = MembershipUiFixtures.task(enabled = false))
        }
        text("\u81ea\u52a8\u7b7e\u5230\u672a\u5f00\u542f").assertExists()
        text("\u7acb\u5373\u7b7e\u5230").assertIsNotEnabled()
        compose.runOnIdle {
            state.value = MembershipUiFixtures.center(MembershipUiFixtures.catalog(entitled = true),
                stage = ComposeCheckinStage.RUNNING)
        }
        text("\u6b63\u5728\u7b7e\u5230").assertIsNotEnabled()
        text("\u64a4\u9500\u6b64\u8bbe\u5907").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }

    @Test fun loggedOutAccountOffersLoginInsteadOfTaskControlsOrHistory() {
        show(MembershipUiFixtures.center(account = CheckinCenterClient.Account("logged_out", "Fixture account", "")))
        text("\u7b49\u5f85\u624b\u673a\u53f7\u767b\u5f55").assertExists()
        text("\u624b\u673a\u53f7\u767b\u5f55").assertIsEnabled()
        text("\u7acb\u5373\u7b7e\u5230").assertDoesNotExist()
        text("\u7b7e\u5230\u8bbe\u7f6e").assertDoesNotExist()
        text("\u7b7e\u5230\u8bb0\u5f55").assertDoesNotExist()
        tapReachable("\u624b\u673a\u53f7\u767b\u5f55")
        compose.runOnIdle {
            assertEquals(listOf("login"), actions)
            assertEquals("logged_out", state.value.status?.account?.state)
        }
    }

    @Test fun loggedOutAccountSummaryAlsoOpensMobileLogin() {
        show(MembershipUiFixtures.center(account = CheckinCenterClient.Account("logged_out", "Fixture account", "")))
        tapReachable("Fixture account")
        compose.runOnIdle { assertEquals(listOf("login"), actions) }
    }

    @Test fun scheduleWindowAndMissingScheduleComeFromTheServerNotTheReferenceDesign() {
        show()
        assertLabelFits("23:40 - 00:10")
        assertLabelFits("09:17")
        text("08:30").assertDoesNotExist()
        compose.runOnIdle {
            state.value = MembershipUiFixtures.center(task = MembershipUiFixtures.task(
                schedule = "", windowStart = "", windowEnd = "00:42"))
        }
        text("00:42").assertExists()
        text("\u672a\u8bbe\u7f6e").assertExists()
        text("23:40 - 00:10").assertDoesNotExist()
        text("09:17").assertDoesNotExist()
        text("08:30").assertDoesNotExist()
    }

    @Test fun statusErrorsDoNotRemoveNavigationAndRetryOnlyDelegates() {
        show(MembershipUiFixtures.center().copy(errorMessage = "Offline status failed"))
        assertLabelFits("Offline status failed")
        tapReachable("\u91cd\u65b0\u8bfb\u53d6\u72b6\u6001")
        tapReachable("\u7b7e\u5230\u8bb0\u5f55")
        compose.runOnIdle {
            assertEquals(listOf("refresh", "history"), actions)
            assertEquals("Offline status failed", state.value.errorMessage)
            assertNull(state.value.history)
        }
    }

    @Test fun squareCenterScheduleAndLastRevokeAreUsable() = verifyLayout()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun round227CenterAndLastRevokeAreInsideTheCircle() = verifyLayout()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun round192CenterAndLastRevokeAreInsideTheCircle() = verifyLayout()

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowLargeTextLightCenterHasNoOverlappingSchedule() = verifyLayout(largeLight = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundLargeTextLightCenterKeepsTheLastRevokeReachable() = verifyLayout(largeLight = true)

    private fun verifyLayout(largeLight: Boolean = false) {
        show(MembershipUiFixtures.center(MembershipUiFixtures.catalog(entitled = true)),
            largeLight = largeLight)
        tag("checkin-account-card").fetchSemanticsNode()
        tag("checkin-home-entry-group").fetchSemanticsNode()
        tag("checkin-danger-row").fetchSemanticsNode()
        assertLabelFits("123****89")
        assertLabelFits("23:40 - 00:10")
        assertLabelFits("09:17")
        assertLabelsSeparated("\u4e0b\u6b21\u7b7e\u5230\u65f6\u6bb5", "\u9884\u8ba1\u6267\u884c")
        capture("server-schedule")
        listOf("\u7acb\u5373\u7b7e\u5230", "\u7b7e\u5230\u8bbe\u7f6e", "\u4f1a\u5458\u670d\u52a1", "\u8fde\u7eed\u7b7e\u5230\u6392\u884c\u699c", "\u7b7e\u5230\u8bb0\u5f55", "\u64a4\u9500\u6b64\u8bbe\u5907")
            .forEach(::tapReachable)
        capture("last-revoke")
        assertBackReachable()
        compose.runOnIdle {
            assertEquals(listOf("run", "settings", "membership", "leaderboard", "history", "revoke", "back"), actions)
            assertTrue(state.value.paired)
            assertEquals(false, state.value.showRevokeConfirm)
            assertNull(state.value.status?.lastRun)
            assertEquals(ComposeCheckinStage.CONNECTED, state.value.stage)
        }
    }

    private fun show(
        initial: ComposeCheckinUiState = MembershipUiFixtures.center(),
        largeLight: Boolean = false,
    ) {
        state = mutableStateOf(initial)
        setScreen(theme(largeLight), systemFontScale = if (largeLight) 1.25f else 1f) {
            ComposeCheckinCenterContent(state.value, { actions += "back" }, { actions += "connect" },
                { actions += "refresh" }, { actions += "login" }, { actions += "run" },
                { actions += "settings" }, { actions += "membership" }, { actions += "leaderboard" },
                { actions += "revoke" }, { actions += "history" })
        }
    }
}
