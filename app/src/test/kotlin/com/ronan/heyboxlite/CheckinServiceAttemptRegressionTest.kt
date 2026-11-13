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
class CheckinServiceAttemptRegressionTest {
    @Test fun emailResultDuringPauseIsRetainedAndCountdownResumesWithoutResending() {
        CheckinFlowFixture(false).use { f ->
            f.pairing()
            registration(f)
            f.owner.sendRegistrationEmail()
            f.background()
            CheckinFlowRequests.emails.single().onSuccess(
                CheckinCenterClient.RegistrationEmailSession("email-A", 60, 600))
            assertFalse(f.owner.serviceAccount.busy())
            val label = f.owner.uiState.serviceEmailButton
            f.advance(10)
            assertEquals(label, f.owner.uiState.serviceEmailButton)
            f.foreground()
            assertNotEquals(label, f.owner.uiState.serviceEmailButton)
            assertEquals(1, CheckinFlowRequests.emails.size)
        }
    }

    @Test fun previousEmailAndApprovalCannotChangeReenteredPairingOrReleaseBusy() {
        CheckinFlowFixture(false).use { f ->
            f.pairing()
            registration(f)
            f.owner.sendRegistrationEmail()
            val oldEmail = CheckinFlowRequests.emails.single()
            f.owner.cancelPairing()
            f.pairing()
            f.owner.submitServiceAccount()
            val oldApproval = CheckinFlowRequests.approvals.single()
            f.owner.cancelPairing()
            f.pairing()
            registration(f)
            f.owner.sendRegistrationEmail()
            val before = f.owner.uiState
            oldEmail.onSuccess(CheckinCenterClient.RegistrationEmailSession("old-A", 60, 600))
            oldEmail.onError(CheckinFlowFixture.error(CheckinCenterClient.Operation.REGISTRATION_EMAIL))
            oldApproval.onSuccess(true)
            oldApproval.onError(CheckinFlowFixture.error(CheckinCenterClient.Operation.PAIR_APPROVE))
            assertEquals(before, f.owner.uiState)
            assertTrue(f.owner.serviceAccount.busy())
        }
    }

    @Test fun approvedResultWhileStoppedIsKeptButPollWaitsForResumeAndNeverReapproves() {
        CheckinFlowFixture(false).use { f ->
            f.pairing()
            f.owner.submitServiceAccount()
            f.background()
            CheckinFlowRequests.approvals.single().onSuccess(true)
            assertFalse(f.owner.serviceAccount.busy())
            f.advance(30)
            assertTrue(CheckinFlowRequests.polls.isEmpty())
            f.foreground()
            f.advance(0)
            assertEquals(1, CheckinFlowRequests.polls.size)
            assertEquals(1, CheckinFlowRequests.approvals.size)
        }
    }

    @Test fun pairingStartAndPollFromPreviousSessionCannotAuthorizeOrReplaceNewSession() {
        CheckinFlowFixture(false).use { f ->
            f.owner.openPairing()
            val start = CheckinFlowRequests.starts.single()
            f.owner.cancelPairing()
            f.pairing()
            f.advance(2)
            val poll = CheckinFlowRequests.polls.single()
            f.owner.cancelPairing()
            f.pairing()
            val current = f.owner.pairingFlow.startValue()
            start.onSuccess(CheckinCenterClient.PairingStart("old", "OLD", 600, 2, true, true))
            poll.onSuccess(CheckinCenterClient.PairingPoll("authorized", "ccdevice1_" + "B".repeat(43)))
            assertSame(current, f.owner.pairingFlow.startValue())
            assertFalse(f.coordinator.paired())
        }
    }

    @Test fun closeRejectsEmailAndApprovalCallbacksAndClearsTimers() {
        CheckinFlowFixture(false).use { f ->
            f.pairing()
            registration(f)
            f.owner.sendRegistrationEmail()
            f.owner.close()
            val state = f.owner.uiState
            CheckinFlowRequests.emails.single().onSuccess(
                CheckinCenterClient.RegistrationEmailSession("late", 60, 600))
            f.advance(60)
            assertEquals(state, f.owner.uiState)
            assertFalse(f.owner.serviceAccount.busy())
            assertTrue(CheckinFlowRequests.polls.isEmpty())
        }
    }

    private fun registration(f: CheckinFlowFixture) {
        f.owner.switchServiceMode(CheckinServiceAccountFlow.Mode.REGISTER)
        f.owner.setServiceEmail("fixture@example.invalid")
    }
}
