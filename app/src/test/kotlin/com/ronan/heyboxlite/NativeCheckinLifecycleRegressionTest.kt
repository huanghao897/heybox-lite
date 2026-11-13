package com.ronan.heyboxlite

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Looper
import android.widget.EditText
import androidx.activity.ComponentActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.Closeable
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class,
    instrumentedPackages = ["com.ronan.heyboxlite"],
    shadows = [CheckinFlowRequests::class, CheckinFlowCrypto::class])
@LooperMode(LooperMode.Mode.PAUSED)
class NativeCheckinLifecycleRegressionTest {
    @Test fun oldCaptchaActivityCannotRetryANewNativeLoginAttempt() {
        Fixture().use { f ->
            f.mobile("13800000001")
            f.mobile.sendSmsCode()
            CheckinFlowRequests.sms.single().callback.onError(captcha())
            val old = shadowOf(f.activity).nextStartedActivityForResult
            assertNotNull(old)
            assertTrue(f.page.handleBack())
            f.mobile("13800000002")
            f.mobile.sendSmsCode()
            CheckinFlowRequests.sms.last().callback.onError(captcha())
            val current = shadowOf(f.activity).nextStartedActivityForResult
            assertNotEquals(old.requestCode, current.requestCode)
            deliver(old.requestCode)
            assertEquals(2, CheckinFlowRequests.sms.size)
            assertTrue(f.mobile.requestInFlight())
            deliver(current.requestCode)
            assertEquals(3, CheckinFlowRequests.sms.size)
            assertEquals("13800000002", CheckinFlowRequests.sms.last().phone)
            assertEquals("ticket", CheckinFlowRequests.sms.last().ticket)
        }
    }

    @Test fun nativePagePauseStopsPairingAndResumeKeepsASinglePollInFlight() {
        Fixture(paired = false).use { f ->
            f.call("beginPairing")
            CheckinFlowRequests.starts.single().onSuccess(CheckinCenterClient.PairingStart(
                "device", "USER", 600, 2, true, true))
            f.page.onPause()
            f.advance(5)
            assertTrue(CheckinFlowRequests.polls.isEmpty())
            f.page.onResume()
            f.advance(0)
            assertEquals(1, CheckinFlowRequests.polls.size)
            f.page.onPause()
            f.page.onResume()
            f.advance(5)
            assertEquals(1, CheckinFlowRequests.polls.size)
            CheckinFlowRequests.polls.single().onSuccess(CheckinCenterClient.PairingPoll(
                "authorization_pending", ""))
            f.advance(2)
            assertEquals(2, CheckinFlowRequests.polls.size)
        }
    }

    @Test fun closingNativePageDropsCaptchaResultAndDoesNotRetainItsCallback() {
        Fixture().use { f ->
            f.mobile("13800000001")
            f.mobile.sendSmsCode()
            CheckinFlowRequests.sms.single().callback.onError(captcha())
            val launched = shadowOf(f.activity).nextStartedActivityForResult
            f.page.close()
            deliver(launched.requestCode)
            assertEquals(1, CheckinFlowRequests.sms.size)
            assertNull(shadowOf(f.activity).nextStartedActivityForResult)
        }
    }

    private class Fixture(paired: Boolean = true) : Closeable {
        val lifecycle = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = lifecycle.get()
        private val session = SessionStore(activity)
        private val coordinator = CheckinCenterCoordinator(activity, LocalCache(activity))
        val page: CheckinCenterPage
        val mobile: CheckinMobileLoginFlow

        init {
            CheckinFlowRequests.clear()
            if (paired) assertTrue(coordinator.authorize("ccdevice1_" + "A".repeat(43)))
            page = CheckinCenterPage(activity, session, coordinator, ThemeTokens.of(true, 0, 0),
                object : CheckinCenterPage.Host {
                    override fun closePage() = Unit
                    override fun confirmRevoke(confirmed: Runnable) = Unit
                    override fun showMessage(message: String) = Unit
                })
            activity.setContentView(page.view())
            page.onResume()
            if (paired) CheckinFlowRequests.statuses.single().onSuccess(CheckinFlowFixture.status())
            mobile = field(page, "mobileLogin") as CheckinMobileLoginFlow
        }

        fun mobile(phone: String) {
            call("openMobileLogin")
            val forms = field(page, "accountForms")!!
            (field(forms, "mobilePhone") as EditText).setText(phone)
        }
        fun call(method: String) {
            CheckinCenterPage::class.java.getDeclaredMethod(method).apply { isAccessible = true }.invoke(page)
        }
        fun advance(seconds: Long) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds)) }
        override fun close() {
            page.close()
            coordinator.close()
            lifecycle.pause().stop().destroy()
            ComposeCheckinCaptchaController.clear()
        }
        private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }.get(owner)
    }

    private fun deliver(request: Int) {
        assertTrue(ComposeCheckinCaptchaController.handleActivityResult(request, Activity.RESULT_OK,
            Intent().putExtra(CheckinCaptchaActivity.EXTRA_TICKET, "ticket")
                .putExtra(CheckinCaptchaActivity.EXTRA_RANDSTR, "randstr")))
    }
    private fun captcha() = CheckinCenterClient.ApiError(CheckinCenterClient.Operation.SMS_SEND,
        428, "fixture captcha", CheckinCenterClient.ErrorKind.HTTP, "captcha_required",
        "https://captcha.example.invalid/verify")
}
