package com.ronan.heyboxlite

import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.Closeable
import java.time.Duration

internal class CheckinFlowFixture(paired: Boolean = true) : Closeable {
    val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = activityController.get()
    val session = SessionStore(activity)
    val api = ApiClient(session)
    val cache = LocalCache(activity)
    val coordinator = CheckinCenterCoordinator(activity, cache)
    val services = ComposeServices(activity, session, api, GameDetailClient(api), cache,
        Handler(Looper.getMainLooper()), ReadingTimeTracker(activity), coordinator,
        composeThemeState(ThemeTokens.of(true, 0, 0), 1f, 1f, false), ComposeToast {})
    val owner: ComposeCheckinController

    init {
        CheckinFlowRequests.clear()
        if (paired) assertTrue(coordinator.authorize("ccdevice1_" + "A".repeat(43)))
        owner = ComposeCheckinController(services, coordinator)
        owner.start()
        if (paired) CheckinFlowRequests.statuses.single().onSuccess(status())
    }

    fun background() { activityController.pause().stop() }
    fun foreground() { activityController.start().resume() }
    fun advance(seconds: Long) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds)) }
    fun mobile(phone: String = "13800000001") {
        owner.openMobileLogin()
        owner.setMobilePhone(phone)
    }
    fun pairing() {
        owner.openPairing()
        CheckinFlowRequests.starts.last().onSuccess(CheckinCenterClient.PairingStart(
            "device", "USER", 600, 2, true, true))
        advance(0)
    }

    override fun close() {
        owner.close()
        coordinator.close()
        api.close()
        activityController.pause().stop().destroy()
        ComposeCheckinCaptchaController.clear()
    }

    companion object {
        fun status() = CheckinCenterClient.Status(CheckinCenterClient.Account("logged_out", "fixture", ""),
            task(), null, null)
        fun task(enabled: Boolean = true) = CheckinCenterClient.Task(enabled, true, "09:00", 0,
            "", "", false, false, CheckinSharing.parse(JSONObject()))
        fun error(operation: CheckinCenterClient.Operation = CheckinCenterClient.Operation.TASK_SETTINGS,
                  status: Int = 500) = CheckinCenterClient.ApiError(operation, status, "fixture failure")
    }
}

@Implements(value = CheckinCenterClient::class, isInAndroidSdk = false)
internal class CheckinFlowRequests {
    @Implementation fun startPairing(name: String, version: String, code: Int,
        callback: CheckinCenterClient.Callback<CheckinCenterClient.PairingStart>) { starts += callback }
    @Implementation fun pollPairing(code: String,
        callback: CheckinCenterClient.Callback<CheckinCenterClient.PairingPoll>) { polls += callback }
    @Implementation fun getStatus(token: String,
        callback: CheckinCenterClient.Callback<CheckinCenterClient.Status>) { statuses += callback }
    @Implementation fun sendSmsCode(token: String, phone: String, ticket: String, randstr: String,
        callback: CheckinCenterClient.Callback<CheckinCenterClient.SmsSession>) { sms += Sms(phone, ticket, callback) }
    @Implementation fun submitSmsCode(token: String, session: String, code: String, ticket: String, randstr: String,
        callback: CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>) { submits += Login(session, ticket, callback) }
    @Implementation fun loginWithPassword(token: String, phone: String, password: String, ticket: String, randstr: String,
        callback: CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>) { passwords += Login(phone, ticket, callback) }
    @Implementation fun sendRegistrationEmail(code: String, email: String,
        callback: CheckinCenterClient.Callback<CheckinCenterClient.RegistrationEmailSession>) { emails += callback }
    @Implementation fun approvePairing(code: String, username: String, password: String,
        callback: CheckinCenterClient.Callback<Boolean>) { approvals += callback }
    @Implementation fun registerPairing(code: String, username: String, password: String, email: String,
        challenge: String, emailCode: String, required: Boolean,
        callback: CheckinCenterClient.Callback<Boolean>) { approvals += callback }
    @Implementation fun updateTaskSettings(token: String, enabled: Boolean, time: String, offset: Int,
        action: String?, share: Boolean?, callback: CheckinCenterClient.Callback<CheckinCenterClient.Task>) { tasks += callback }
    @Implementation fun runNow(token: String,
        callback: CheckinCenterClient.Callback<CheckinCenterClient.RunResult>) { runs += callback }
    @Implementation fun revokeDevice(token: String, callback: CheckinCenterClient.Callback<Boolean>) { revokes += callback }
    @Implementation fun close() = Unit

    data class Sms(val phone: String, val ticket: String, val callback: CheckinCenterClient.Callback<CheckinCenterClient.SmsSession>)
    data class Login(val identity: String, val ticket: String, val callback: CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>)
    companion object {
        val starts = mutableListOf<CheckinCenterClient.Callback<CheckinCenterClient.PairingStart>>()
        val polls = mutableListOf<CheckinCenterClient.Callback<CheckinCenterClient.PairingPoll>>()
        val statuses = mutableListOf<CheckinCenterClient.Callback<CheckinCenterClient.Status>>()
        val sms = mutableListOf<Sms>()
        val submits = mutableListOf<Login>()
        val passwords = mutableListOf<Login>()
        val emails = mutableListOf<CheckinCenterClient.Callback<CheckinCenterClient.RegistrationEmailSession>>()
        val approvals = mutableListOf<CheckinCenterClient.Callback<Boolean>>()
        val tasks = mutableListOf<CheckinCenterClient.Callback<CheckinCenterClient.Task>>()
        val runs = mutableListOf<CheckinCenterClient.Callback<CheckinCenterClient.RunResult>>()
        val revokes = mutableListOf<CheckinCenterClient.Callback<Boolean>>()
        fun clear() {
            starts.clear(); polls.clear(); statuses.clear(); sms.clear(); submits.clear(); passwords.clear()
            emails.clear(); approvals.clear(); tasks.clear(); runs.clear(); revokes.clear()
        }
    }
}

@Implements(value = ModernCookieCrypto::class, isInAndroidSdk = false)
internal class CheckinFlowCrypto {
    companion object {
        @JvmStatic @Implementation fun encrypt(value: String) = "fixture:$value"
        @JvmStatic @Implementation fun decrypt(value: String) = value.removePrefix("fixture:")
    }
}
