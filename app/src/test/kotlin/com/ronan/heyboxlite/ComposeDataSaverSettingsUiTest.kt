package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
class ComposeDataSaverSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var api: ApiClient? = null
    private val title = "\u7701\u6d41\u6a21\u5f0f"

    @After fun close() { api?.close() }

    @Test fun saverIsOffByDefaultAndStoredIndependentlyFromBothNoImageSettingsAndLogout() {
        val session = SessionStore(compose.activity)
        assertFalse(session.isDataSaverEnabled())
        session.setNoImage(true)
        session.setGameCardNoImage(true)
        session.setDataSaverEnabled(true)
        assertTrue(SessionStore(compose.activity).isDataSaverEnabled())
        session.setNoImage(false)
        session.setGameCardNoImage(false)
        assertTrue(session.isDataSaverEnabled())
        session.clearSession()
        assertTrue(SessionStore(compose.activity).isDataSaverEnabled())
        session.setDataSaverEnabled(false)
        assertFalse(session.noImage())
        assertFalse(session.gameCardNoImage())
    }

    @Test fun contentSettingsTogglePersistsAndSurvivesAFreshSession() = verifySetting()

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun roundContentSettingsToggleRemainsReachableAndPersists() = verifySetting()

    private fun verifySetting() {
        val services = services()
        val session = mutableStateOf(services.session)
        var changes = 0
        compose.setContent {
            key(session.value) {
                HeyboxComposeTheme(services.theme) {
                    ComposeContentCacheScreen(services.copy(session = session.value), {}, {}, { changes++ })
                }
            }
        }
        compose.onNodeWithText(title).performScrollTo()
        toggle().assertIsOff().performClick()
        toggle().assertIsOn()
        compose.runOnIdle {
            assertEquals(1, changes)
            assertTrue(SessionStore(compose.activity).isDataSaverEnabled())
            assertFalse(session.value.noImage())
            assertFalse(session.value.gameCardNoImage())
            session.value = SessionStore(compose.activity)
        }
        compose.onNodeWithText(title).performScrollTo()
        toggle().assertIsOn().performClick()
        compose.runOnIdle { assertFalse(SessionStore(compose.activity).isDataSaverEnabled()); assertEquals(2, changes) }
    }

    @Test fun preferenceChangesReachImageGatesWithoutAThemeChange() {
        val session = SessionStore(compose.activity)
        session.setDataSaverEnabled(true)
        val images = ComposeInlineTextTestImages()
        compose.setContent {
            ProvideComposeDataSaver(session) {
                ComposeRemoteImage("content", composePreviewTheme(false), false, 64.dp,
                    RoundedCornerShape(4.dp), "content", Modifier.size(64.dp).testTag("image"),
                    images, dataSaver = LocalComposeDataSaver.current)
            }
        }
        compose.runOnIdle { assertTrue(images.requests.isEmpty()); session.setDataSaverEnabled(false) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("content"), images.requests.map { it.url }) }
        compose.runOnIdle { session.setDataSaverEnabled(true) }
        compose.waitForIdle()
        compose.onNodeWithTag("image").performClick()
        compose.runOnIdle { assertEquals(listOf("content", "content"), images.requests.map { it.url }) }
    }

    private fun toggle() = compose.onNode(isToggleable() and hasText(title))

    private fun services(): ComposeServices {
        val context = compose.activity
        val session = SessionStore(context)
        val client = ApiClient(session).also { api = it }
        return ComposeServices(context, session, client, GameDetailClient(client), LocalCache(context),
            Handler(Looper.getMainLooper()), ReadingTimeTracker(context), null,
            composePreviewTheme(RoundLayoutMetrics.isRoundDisplay(context)), ComposeToast {})
    }
}
