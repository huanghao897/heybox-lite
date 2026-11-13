package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi",
    instrumentedPackages = ["com.ronan.heyboxlite"],
    shadows = [LoginLifecycleApi::class, LoginLifecycleQr::class, CheckinFlowCrypto::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ComposeLoginLifecycleRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var api: ApiClient
    private val visible = mutableStateOf(true)
    private var loggedIn = 0

    @Before fun showLogin() {
        LoginLifecycleApi.requests.clear()
        val activity = compose.activity
        val session = SessionStore(activity)
        api = ApiClient(session)
        val services = ComposeServices(activity, session, api, GameDetailClient(api), LocalCache(activity),
            Handler(Looper.getMainLooper()), ReadingTimeTracker(activity), null,
            composeThemeState(ThemeTokens.of(true, 0, 0), 1f, 1f, false), ComposeToast {})
        compose.setContent {
            HeyboxComposeTheme(services.theme) {
                if (visible.value) ComposeLoginScreen(services, {}, {}, { loggedIn++ })
            }
        }
        compose.waitForIdle()
        assertEquals(1, LoginLifecycleApi.requests.size)
    }

    @After fun close() { api.close() }

    @Test fun composeQrPollingStopsOnActualActivityPauseStopAndResumesTheSameQr() {
        ready()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        advance(10)
        assertEquals(1, LoginLifecycleApi.requests.size)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        advance(0)
        assertEquals(2, LoginLifecycleApi.requests.size)
        assertEquals(EndpointProvider.qrState(), LoginLifecycleApi.requests.last().path)
        advance(10)
        assertEquals(2, LoginLifecycleApi.requests.size)
    }

    @Test fun qrCreationCompletesDuringPauseAndResumeDoesNotFetchAReplacement() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        ready()
        advance(10)
        assertEquals(1, LoginLifecycleApi.requests.size)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        advance(0)
        assertEquals(2, LoginLifecycleApi.requests.size)
        assertEquals(EndpointProvider.qrState(), LoginLifecycleApi.requests.last().path)
    }

    @Test fun failedCredentialCompletionDoesNotNavigateAndRemovesTheQr() {
        ready()
        advance(2)
        compose.runOnIdle {
            LoginLifecycleApi.requests.last().callback.onSuccess(body(JSONObject().put("error", "ok").put("heyboxid", "42")))
        }
        compose.onNodeWithText("无法安全保存登录凭据，请重新获取二维码").assertExists()
        compose.onNodeWithContentDescription("登录二维码").assertDoesNotExist()
        assertEquals(0, loggedIn)
        advance(10)
        assertEquals(2, LoginLifecycleApi.requests.size)
    }

    @Test fun leavingCompositionRejectsLateQrLoginAndStopsAllPolling() {
        ready()
        advance(2)
        val poll = LoginLifecycleApi.requests.last().callback
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { poll.onSuccess(body(JSONObject().put("error", "ok"))) }
        advance(60)
        assertEquals(0, loggedIn)
        assertEquals(2, LoginLifecycleApi.requests.size)
    }

    @Test fun retryRejectsPreviousQrCallbacksAndKeepsOnlyTheNewRequest() {
        val old = LoginLifecycleApi.requests.single().callback
        compose.onNodeWithText("重新获取").performClick()
        compose.waitForIdle()
        assertEquals(2, LoginLifecycleApi.requests.size)
        compose.runOnIdle { old.onSuccess(body(JSONObject().put("qr_url", "https://example.invalid/?qr=old"))) }
        advance(10)
        assertEquals(2, LoginLifecycleApi.requests.size)
    }

    private fun ready() {
        compose.runOnIdle {
            LoginLifecycleApi.requests.first().callback.onSuccess(body(JSONObject()
                .put("qr_url", "https://example.invalid/?qr=fixture")))
        }
        compose.waitForIdle()
    }
    private fun advance(seconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    }
    private fun body(result: JSONObject) = JSONObject().put("result", result)
}

@Implements(value = ApiClient::class, isInAndroidSdk = false)
internal class LoginLifecycleApi {
    @Implementation fun get(path: String, params: Map<String, String>, callback: ApiClient.Callback) {
        requests += Request(path, callback)
    }
    data class Request(val path: String, val callback: ApiClient.Callback)
    companion object { val requests = mutableListOf<Request>() }
}

@Implements(value = QrCode::class, isInAndroidSdk = false)
internal class LoginLifecycleQr {
    companion object {
        @JvmStatic @Implementation fun create(url: String, size: Int): Bitmap =
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
    }
}
