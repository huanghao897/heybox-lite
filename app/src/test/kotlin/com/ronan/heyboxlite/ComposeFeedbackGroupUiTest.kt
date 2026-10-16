package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeFeedbackGroupUiTest : ComposeWatchActionButtonTestHarness() {
    private var api: ApiClient? = null

    @After fun closeServices() { api?.close() }

    @Test fun squareDarkGroupOpensAsDialogWithoutNavigation() = verifyDialog()
    @Test fun squareLightGroupOpensAsDialogWithoutNavigation() = verifyDialog(dark = false)
    @Test fun backClosesOnlyTheDialog() = verifyDialog(back = true)

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun roundGroupKeepsTheQrAndConfirmationReachable() = verifyDialog()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallLightRoundLargeTextKeepsConfirmationVisible() = verifyDialog(dark = false, large = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun reducedScaleRoundKeepsConfirmationTouchable() = verifyDialog(large = true, uiScale = 0.5f)

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowLargeTextGroupRemainsADialog() = verifyDialog(large = true)

    @Test fun pendingUpdateWaitsUntilTheFeedbackDialogCloses() {
        val feedback = mutableStateOf(true)
        val update = mutableStateOf<UpdateChecker.Result?>(null)
        var feedbackDismissals = 0
        var updateDismissals = 0
        var downloads = 0
        show(theme()) {
            ComposeAboutDialogs(feedback.value, update.value,
                { feedbackDismissals++; feedback.value = false },
                { updateDismissals++; update.value = null }, { downloads++ })
        }
        val feedbackDialog = ShadowDialog.getLatestDialog()
        compose.runOnIdle {
            update.value = UpdateChecker.Result(true, "9.0", "", "Fixture notes",
                "https://heyboxlite.xyz/", "", 999, "test")
        }
        compose.onNodeWithTag("feedback-group-dialog").assertIsDisplayed()
        text("\u53d1\u73b0\u65b0\u7248\u672c 9.0").assertDoesNotExist()
        assertSame(feedbackDialog, ShadowDialog.getLatestDialog())

        text("\u786e\u5b9a").performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithTag("feedback-group-dialog").assertDoesNotExist()
        assertFalse(feedbackDialog.isShowing)
        text("\u53d1\u73b0\u65b0\u7248\u672c 9.0").assertIsDisplayed()
        assertTrue(ShadowDialog.getLatestDialog().isShowing)
        text("\u7a0d\u540e").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(1, feedbackDismissals)
        assertEquals(1, updateDismissals)
        assertEquals(0, downloads)
        assertFalse(ShadowDialog.getLatestDialog().isShowing)
    }

    private fun verifyDialog(dark: Boolean = true, large: Boolean = false,
                             uiScale: Float? = null, back: Boolean = false) {
        val palette = theme(dark, large, uiScale)
        val activity = compose.activity
        val session = SessionStore(activity)
        val client = ApiClient(session)
        api = client
        val services = ComposeServices(activity, session, client, GameDetailClient(client),
            LocalCache(activity), Handler(Looper.getMainLooper()), ReadingTimeTracker(activity),
            null, palette, ComposeToast {})
        val navigation = mutableListOf<String>()
        var pageBacks = 0
        show(palette, large) { ComposeAboutScreen(services, navigation::add) { pageBacks++ } }

        reach("\u4ea4\u6d41\u7fa4").performTouchInput { click() }
        compose.waitForIdle()
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog.isShowing)
        assertTrue(navigation.isEmpty())
        compose.onNodeWithTag("feedback-group-dialog").assertIsDisplayed()
        val confirm = text("\u786e\u5b9a").assertIsDisplayed()
        assertFits("\u786e\u5b9a")
        assertSurface(confirm, palette, true, "feedback-confirm")

        val qr = compose.onNodeWithContentDescription("QQ\u7fa4 781941517 \u4e8c\u7ef4\u7801")
            .performScrollTo().assertIsDisplayed().fetchSemanticsNode()
        assertTrue("QR must have a useful visible size", qr.size.width >= 48 && qr.size.height >= 48)
        assertEquals("QR may not be vertically clipped", qr.size.height.toFloat(), qr.boundsInRoot.height, 0.5f)
        assertEquals("QR may not be horizontally clipped", qr.size.width.toFloat(), qr.boundsInRoot.width, 0.5f)
        reach("QQ\u7fa4\uff1a781941517")
        assertFits("QQ\u7fa4\uff1a781941517")
        confirm.assertIsDisplayed()
        if (back) compose.runOnIdle {
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        } else confirm.performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithTag("feedback-group-dialog").assertDoesNotExist()
        assertFalse(dialog.isShowing)
        assertTrue(navigation.isEmpty())
        assertEquals(0, pageBacks)
        text("\u4ea4\u6d41\u7fa4").assertIsDisplayed()
    }
}
