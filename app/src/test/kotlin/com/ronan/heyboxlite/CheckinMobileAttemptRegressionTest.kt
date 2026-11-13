package com.ronan.heyboxlite

import android.app.Application
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class,
    instrumentedPackages = ["com.ronan.heyboxlite"],
    shadows = [CheckinFlowRequests::class, CheckinFlowCrypto::class])
@LooperMode(LooperMode.Mode.PAUSED)
class CheckinMobileAttemptRegressionTest {
    @Test fun oldSmsSuccessOrFailureCannotInstallASessionOrReleaseNewAttemptBusy() {
        CheckinFlowFixture().use { f ->
            f.mobile()
            f.owner.sendSmsCode()
            val old = CheckinFlowRequests.sms.single().callback
            f.owner.navigateBack {}
            f.mobile("13800000002")
            f.owner.sendSmsCode()
            old.onSuccess(CheckinCenterClient.SmsSession("old-A", 60, 600))
            old.onError(CheckinFlowFixture.error(CheckinCenterClient.Operation.SMS_SEND))
            assertTrue(f.owner.mobileLogin.requestInFlight())
            assertFalse(f.owner.uiState.mobileHasSession)
            CheckinFlowRequests.sms.last().callback.onSuccess(CheckinCenterClient.SmsSession("new-B", 60, 600))
            f.owner.setMobileCode("123456")
            f.owner.submitSmsCode()
            assertEquals("new-B", CheckinFlowRequests.submits.single().identity)
        }
    }

    @Test fun oldSubmitAndPasswordCallbacksCannotConnectOrClearNewPassword() {
        CheckinFlowFixture().use { f ->
            f.mobile()
            f.owner.sendSmsCode()
            CheckinFlowRequests.sms.single().callback.onSuccess(CheckinCenterClient.SmsSession("A", 60, 600))
            f.owner.setMobileCode("123456")
            f.owner.submitSmsCode()
            val oldSubmit = CheckinFlowRequests.submits.single().callback
            f.owner.navigateBack {}
            f.mobile()
            f.owner.switchMobileMode(CheckinMobileLoginFlow.Mode.PASSWORD)
            f.owner.setMobilePassword("password-A")
            f.owner.submitPasswordLogin()
            val oldPassword = CheckinFlowRequests.passwords.single().callback
            f.owner.navigateBack {}
            f.mobile("13800000002")
            f.owner.switchMobileMode(CheckinMobileLoginFlow.Mode.PASSWORD)
            f.owner.setMobilePassword("password-B")
            f.owner.submitPasswordLogin()
            oldSubmit.onSuccess(connected())
            oldSubmit.onError(captcha(CheckinCenterClient.Operation.SMS_SUBMIT))
            oldPassword.onSuccess(connected())
            oldPassword.onError(captcha(CheckinCenterClient.Operation.PASSWORD_LOGIN))
            assertEquals(ComposeCheckinRoute.MOBILE_LOGIN, f.owner.uiState.route)
            assertEquals("password-B", f.owner.uiState.mobilePassword)
            assertTrue(f.owner.mobileLogin.requestInFlight())
            assertNull(shadowOf(f.activity).nextStartedActivityForResult)
        }
    }

    @Test fun captchaResultFromOldActivityCannotRetryTheNewPhoneAttempt() {
        CheckinFlowFixture().use { f ->
            f.mobile()
            f.owner.sendSmsCode()
            CheckinFlowRequests.sms.single().callback.onError(captcha(CheckinCenterClient.Operation.SMS_SEND))
            val first = shadowOf(f.activity).nextStartedActivityForResult
            f.owner.navigateBack {}
            f.mobile("13800000002")
            f.owner.sendSmsCode()
            CheckinFlowRequests.sms.last().callback.onError(captcha(CheckinCenterClient.Operation.SMS_SEND))
            val second = shadowOf(f.activity).nextStartedActivityForResult
            assertNotEquals(first.requestCode, second.requestCode)
            deliverCaptcha(first.requestCode)
            assertEquals(2, CheckinFlowRequests.sms.size)
            assertTrue(f.owner.mobileLogin.requestInFlight())
            deliverCaptcha(second.requestCode)
            assertEquals(3, CheckinFlowRequests.sms.size)
            assertEquals("13800000002", CheckinFlowRequests.sms.last().phone)
            assertEquals("ticket", CheckinFlowRequests.sms.last().ticket)
        }
    }

    @Test fun pauseKeepsPasswordResultAndDoesNotReplayItOnResume() {
        CheckinFlowFixture().use { f ->
            f.mobile()
            f.owner.switchMobileMode(CheckinMobileLoginFlow.Mode.PASSWORD)
            f.owner.setMobilePassword("password")
            f.owner.submitPasswordLogin()
            f.background()
            CheckinFlowRequests.passwords.single().callback.onSuccess(connected())
            assertEquals(ComposeCheckinRoute.CENTER, f.owner.uiState.route)
            f.foreground()
            f.advance(60)
            assertEquals(1, CheckinFlowRequests.passwords.size)
        }
    }

    @Test fun changedPhoneCannotUsePreviousSmsSession() {
        CheckinFlowFixture().use { f ->
            f.mobile()
            f.owner.sendSmsCode()
            CheckinFlowRequests.sms.single().callback.onSuccess(CheckinCenterClient.SmsSession("A", 60, 600))
            f.owner.setMobilePhone("13800000002")
            f.owner.setMobileCode("123456")
            f.owner.submitSmsCode()
            assertTrue(CheckinFlowRequests.submits.isEmpty())
            assertFalse(f.owner.uiState.mobileHasSession)
            assertFalse(f.owner.mobileLogin.requestInFlight())
        }
    }

    @Test fun closingOwnerWithoutACaptchaCannotClearAnotherOwnersListener() {
        CheckinFlowFixture().use { f ->
            var delivered = false
            val listener = object : ComposeCheckinCaptchaCallbacks {
                override fun onCaptchaResult(ticket: String, randstr: String) { delivered = true }
                override fun onCaptchaCancelled(message: String) = fail(message)
            }
            ComposeCheckinCaptchaController.launch(f.activity,
                "https://captcha.example.invalid/verify", false, listener)
            val other = shadowOf(f.activity).nextStartedActivityForResult
            f.owner.close()
            deliverCaptcha(other.requestCode)
            assertTrue(delivered)
        }
    }

    private fun deliverCaptcha(code: Int) {
        assertTrue(ComposeCheckinCaptchaController.handleActivityResult(code, android.app.Activity.RESULT_OK,
            Intent().putExtra(CheckinCaptchaActivity.EXTRA_TICKET, "ticket")
                .putExtra(CheckinCaptchaActivity.EXTRA_RANDSTR, "randstr")))
    }
    private fun connected() = CheckinCenterClient.ConnectedAccount("fixture", "***", true)
    private fun captcha(operation: CheckinCenterClient.Operation) = CheckinCenterClient.ApiError(
        operation, 428, "fixture captcha", CheckinCenterClient.ErrorKind.HTTP, "captcha_required",
        "https://captcha.example.invalid/verify")
}
