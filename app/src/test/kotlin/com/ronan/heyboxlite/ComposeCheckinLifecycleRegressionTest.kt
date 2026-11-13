package com.ronan.heyboxlite

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class,
    instrumentedPackages = ["com.ronan.heyboxlite"],
    shadows = [CheckinFlowRequests::class, CheckinFlowCrypto::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeCheckinLifecycleRegressionTest {
    @Test fun pairingAndEmailCountdownStopOnActivityPauseStopAndCatchUpOnResume() {
        CheckinFlowFixture(false).use { f ->
            f.pairing()
            f.owner.switchServiceMode(CheckinServiceAccountFlow.Mode.REGISTER)
            f.owner.setServiceEmail("fixture@example.invalid")
            f.owner.sendRegistrationEmail()
            CheckinFlowRequests.emails.single().onSuccess(
                CheckinCenterClient.RegistrationEmailSession("email-A", 60, 600))
            val remaining = f.owner.uiState.pairingRemainingSeconds
            val email = f.owner.uiState.serviceEmailButton
            f.background()
            f.advance(10)
            assertTrue(CheckinFlowRequests.polls.isEmpty())
            assertEquals(remaining, f.owner.uiState.pairingRemainingSeconds)
            assertEquals(email, f.owner.uiState.serviceEmailButton)
            f.foreground()
            f.advance(0)
            assertEquals(1, CheckinFlowRequests.polls.size)
            assertTrue(f.owner.uiState.pairingRemainingSeconds < remaining)
            assertNotEquals(email, f.owner.uiState.serviceEmailButton)
        }
    }

    @Test fun inFlightPairingPollIsNotReplayedByResumeAndResultIsRetainedWhilePaused() {
        CheckinFlowFixture(false).use { f ->
            f.pairing()
            f.advance(2)
            val poll = CheckinFlowRequests.polls.single()
            f.background()
            f.foreground()
            f.advance(10)
            assertEquals(1, CheckinFlowRequests.polls.size)
            f.background()
            poll.onSuccess(CheckinCenterClient.PairingPoll("authorized", "ccdevice1_" + "B".repeat(43)))
            assertTrue(f.coordinator.paired())
            assertEquals(ComposeCheckinRoute.CENTER, f.owner.uiState.route)
            f.foreground()
            f.advance(10)
            assertEquals(1, CheckinFlowRequests.polls.size)
        }
    }

    @Test fun smsResultArrivingWhilePausedIsKeptWithoutSchedulingCountdownOrSendingAgain() {
        CheckinFlowFixture().use { f ->
            f.mobile()
            f.owner.sendSmsCode()
            f.background()
            CheckinFlowRequests.sms.single().callback.onSuccess(CheckinCenterClient.SmsSession("session-A", 60, 600))
            assertFalse(f.owner.mobileLogin.requestInFlight())
            assertTrue(f.owner.uiState.mobileHasSession)
            val button = f.owner.uiState.mobileSmsButton
            f.advance(10)
            assertEquals(button, f.owner.uiState.mobileSmsButton)
            f.foreground()
            assertNotEquals(button, f.owner.uiState.mobileSmsButton)
            assertEquals(1, CheckinFlowRequests.sms.size)
            f.owner.setMobileCode("123456")
            f.owner.submitSmsCode()
            assertEquals("session-A", CheckinFlowRequests.submits.single().identity)
        }
    }

    @Test fun taskFailureOffPageAlwaysClearsBusyAndReentryCanSaveAgain() {
        CheckinFlowFixture().use { f ->
            f.owner.openTaskSettings()
            f.owner.setTaskEnabled(false)
            assertTrue(f.owner.uiState.taskSaving)
            f.owner.navigateBack { fail("must remain inside checkin") }
            f.background()
            CheckinFlowRequests.tasks.single().onError(CheckinFlowFixture.error())
            assertFalse(f.owner.taskSettings.requestInFlight())
            assertFalse(f.owner.uiState.taskSaving)
            f.foreground()
            f.owner.openTaskSettings()
            f.owner.setTaskEnabled(false)
            assertEquals(2, CheckinFlowRequests.tasks.size)
        }
    }

    @Test fun taskSuccessWhileHiddenUpdatesOriginalStatusWithoutReopeningSettingsOrReplayingWrite() {
        CheckinFlowFixture().use { f ->
            f.owner.openTaskSettings()
            f.owner.setTaskEnabled(false)
            f.owner.navigateBack {}
            f.background()
            CheckinFlowRequests.tasks.single().onSuccess(CheckinFlowFixture.task(false))
            assertFalse(f.owner.uiState.status!!.task.enabled)
            assertFalse(f.owner.uiState.taskSaving)
            assertEquals(ComposeCheckinRoute.CENTER, f.owner.uiState.route)
            f.foreground()
            f.advance(60)
            assertEquals(1, CheckinFlowRequests.tasks.size)
        }
    }

    @Test fun closeRejectsLatePairingEmailSmsAndTaskCallbacks() {
        CheckinFlowFixture().use { f ->
            f.mobile()
            f.owner.sendSmsCode()
            f.owner.close()
            val state = f.owner.uiState
            CheckinFlowRequests.sms.single().callback.onSuccess(CheckinCenterClient.SmsSession("late", 60, 600))
            f.advance(60)
            assertEquals(state, f.owner.uiState)
            assertFalse(f.owner.mobileLogin.requestInFlight())
        }
    }
}
