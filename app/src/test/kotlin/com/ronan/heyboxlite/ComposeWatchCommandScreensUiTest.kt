package com.ronan.heyboxlite

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.roundToInt
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi",
    shadows = [WatchCommandCheckinControllerShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class ComposeWatchCommandScreensUiTest : ComposeWatchActionButtonTestHarness() {
    private var api: ApiClient? = null
    private var coordinator: CheckinCenterCoordinator? = null
    private var controller: ComposeCheckinController? = null

    @Before fun clearCommands() { WatchCommandCheckinControllerShadow.commands.clear() }

    @After fun closeServices() {
        controller?.close()
        coordinator?.close()
        api?.close()
    }

    @Test fun darkSquareEmailActionHasAVisibleSurface() = verifyCodeAction(email = true, dark = true)
    @Test fun lightSquareSmsActionHasAVisibleSurface() = verifyCodeAction(email = false, dark = false)

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun darkRoundSmsActionHasAVisibleSurface() = verifyCodeAction(email = false, dark = true)

    @Test @Config(qualifiers = "w227dp-h227dp-round-mdpi")
    fun lightRoundEmailActionHasAVisibleSurface() = verifyCodeAction(email = true, dark = false)

    @Test @Config(qualifiers = "w160dp-h240dp-mdpi")
    fun narrowBigTextSmsActionStacksWithoutTruncation() =
        verifyCodeAction(email = false, dark = false, large = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun smallRoundBigTextEmailActionFitsInsideTheCircle() =
        verifyCodeAction(email = true, dark = true, large = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun darkReducedScaleSmallRoundEmailActionStacksInsideTheCircle() =
        verifyCodeAction(email = true, dark = true, large = true, uiScale = 0.5f)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun lightReducedScaleSmallRoundSmsActionStacksInsideTheCircle() =
        verifyCodeAction(email = false, dark = false, large = true, uiScale = 0.5f)

    @Test @Config(qualifiers = "w360dp-h480dp-mdpi")
    fun wideSquareEmailActionStaysBesideItsInput() =
        verifyCodeAction(email = true, dark = true, stacked = false)

    @Test @Config(qualifiers = "w360dp-h480dp-mdpi")
    fun wideSquareSmsActionStaysBesideItsInput() =
        verifyCodeAction(email = false, dark = false, stacked = false)

    private fun verifyCodeAction(email: Boolean, dark: Boolean, large: Boolean = false,
                                 uiScale: Float? = null, stacked: Boolean = true) {
        val palette = theme(dark, large, uiScale)
        val services = services(palette)
        val center = CheckinCenterCoordinator(compose.activity, services.cache)
        coordinator = center
        val owner = ComposeCheckinController(services, center)
        controller = owner
        // Render production forms without starting flows or sending credentials/requests.
        val state = mutableStateOf(ComposeCheckinUiState(
            pairing = CheckinCenterClient.PairingStart("fixture-device", "FIXTURE", 120, 5, true, true),
            serviceMode = CheckinServiceAccountFlow.Mode.REGISTER,
            pairingRemainingSeconds = 120,
        ))
        show(palette, large) {
            if (email) ComposeCheckinPairingScreen(state.value, owner, {})
            else ComposeCheckinMobileLoginScreen(state.value, owner, {})
        }
        val label = if (email) state.value.serviceEmailButton else state.value.mobileSmsButton
        val enabled = reach(label).assertIsEnabled()
        assertFits(label)
        assertSurface(enabled, palette, true, "send-enabled")
        val fieldLabel = if (email) "6 \u4f4d\u90ae\u7bb1\u9a8c\u8bc1\u7801" else "+86 \u624b\u673a\u53f7"
        assertCodeControlsSeparated(fieldLabel, label, stacked, palette)
        reach(fieldLabel)
        assertFits(fieldLabel)
        val layouts = mutableListOf<TextLayoutResult>()
        text(fieldLabel, unmerged = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("Placeholder ink", palette.subtle.toArgb(), layouts.single().layoutInput.style.color.toArgb())
        val command = if (email) "email" else "sms"
        reach(label).assertIsEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(command), WatchCommandCheckinControllerShadow.commands) }
        val retryLabel = "60 s \u540e\u91cd\u8bd5"
        compose.runOnIdle {
            state.value = if (email) state.value.copy(serviceEmailButton = retryLabel,
                serviceEmailButtonEnabled = false) else state.value.copy(mobileSmsButton = retryLabel,
                mobileSmsButtonEnabled = false)
        }
        val disabled = reach(retryLabel).assertIsNotEnabled()
        assertFits(retryLabel)
        assertSurface(disabled, palette, false, "send-countdown-disabled")
        assertCodeControlsSeparated(fieldLabel, retryLabel, stacked, palette)
        val previous = state.value
        val ownerState = owner.uiState
        disabled.performClick().performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(previous, state.value)
            assertEquals("Disabled command may not enter its controller", ownerState, owner.uiState)
            assertEquals("Disabled command must not dispatch", listOf(command), WatchCommandCheckinControllerShadow.commands)
        }
    }

    private fun codeField(label: String): SemanticsNodeInteraction {
        return compose.onNodeWithTag("checkin-field-$label", useUnmergedTree = true)
    }

    private fun assertCodeControlsSeparated(fieldLabel: String, actionLabel: String, stacked: Boolean,
                                           theme: ComposeThemeState) {
        // BasicTextField exposes its editable area, inside the painted field's padding.
        val editable = bounds(codeField(fieldLabel))
        val density = compose.activity.resources.displayMetrics.density
        val horizontalPadding = (10 * theme.uiScale * density).roundToInt()
        val verticalPadding = (9 * theme.uiScale * density).roundToInt()
        val input = Rect(editable.left - horizontalPadding, editable.top - verticalPadding,
            editable.right + horizontalPadding, editable.bottom + verticalPadding)
        val action = bounds(text(actionLabel))
        val horizontal = input.right <= action.left || action.right <= input.left
        val vertical = input.bottom <= action.top || action.bottom <= input.top
        assertTrue("Send button must leave useful input width: $editable", editable.width >= 35)
        assertTrue("Input and command may not overlap: $input / $action", horizontal || vertical)
        if (stacked) {
            assertTrue("Narrow action must follow its input vertically: $input / $action", input.bottom <= action.top)
            assertEquals("Stacked action must use the input's full width", input.left, action.left, 0.5f)
            assertEquals("Stacked action must use the input's full width", input.right, action.right, 0.5f)
        } else {
            assertTrue("Wide action must remain beside its input: $input / $action", input.right <= action.left)
        }
    }

    @Test fun watchLaterRemoveDelegatesItsEntryAndDisablesWhileLoading() = verifyRemove(dark = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun lightSmallRoundBigTextRemoveStillHasAVisibleDisabledSurface() = verifyRemove(dark = false, large = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun darkReducedScaleSmallRoundRemoveKeepsItsCallback() =
        verifyRemove(dark = true, large = true, uiScale = 0.5f)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun lightReducedScaleSmallRoundRemoveKeepsItsCallback() =
        verifyRemove(dark = false, large = true, uiScale = 0.5f)

    private fun verifyRemove(dark: Boolean, large: Boolean = false, uiScale: Float? = null) {
        val palette = theme(dark, large, uiScale)
        val services = services(palette)
        val entry = ComposeOfflineEntry(FeedItem.from(JSONObject()
            .put("linkid", "saved-command").put("title", "Saved")), 1024, 1L)
        val state = mutableStateOf(ComposeWatchLaterState(entries = listOf(entry)))
        val removed = mutableListOf<ComposeOfflineEntry>()
        var opens = 0
        show(palette, large) {
            ComposeWatchLaterScreen(state.value, services, { opens++ }, { removed += it }, {}, {},
                rememberLazyListState())
        }
        val label = "\u79fb\u9664"
        val remove = reach(label).assertIsEnabled()
        assertFits(label)
        assertSurface(remove, palette, true, "remove-enabled")
        remove.performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, removed.size)
            assertSame(entry, removed.single())
            assertEquals("Remove must not open the feed card", 0, opens)
            state.value = state.value.copy(loading = true)
        }
        val disabled = reach(label).assertIsNotEnabled()
        assertSurface(disabled, palette, false, "remove-disabled")
        disabled.performClick().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, removed.size); assertEquals(0, opens) }
    }

    @Test fun dialogConfirmAndCancelKeepTheirEnabledSemantics() = verifyDialog(dark = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun lightSmallRoundBigTextDialogActionsRemainReachable() = verifyDialog(dark = false, large = true)

    @Test fun timeDialogRejectsInvalidTimeAndConfirmsEditedTime() = verifyDialog(dark = false, time = true)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun darkReducedScaleSmallRoundOffsetDialogKeepsVisibleActions() =
        verifyDialog(dark = true, large = true, uiScale = 0.5f)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun lightReducedScaleSmallRoundTimeDialogKeepsVisibleActions() =
        verifyDialog(dark = false, large = true, uiScale = 0.5f, time = true)

    private fun verifyDialog(dark: Boolean, large: Boolean = false,
                             uiScale: Float? = null, time: Boolean = false) {
        val palette = theme(dark, large, uiScale)
        val enabled = mutableStateOf(false)
        val confirmations = mutableListOf<String>()
        var dismissals = 0
        show(palette, large) {
            if (time) CheckinTaskTimeDialog("08:30", enabled.value,
                { dismissals++ }, { confirmations += it })
            else CheckinTaskOffsetDialog(30, enabled.value,
                { dismissals++ }, { confirmations += it.toString() })
        }
        val confirmLabel = "\u786e\u8ba4"
        val cancelLabel = "\u53d6\u6d88"
        val confirm = reach(confirmLabel).assertIsNotEnabled()
        assertFits(confirmLabel)
        assertSurface(confirm, palette, false, "dialog-busy-confirm")
        confirm.performClick().performTouchInput { click() }
        compose.runOnIdle { assertTrue(confirmations.isEmpty()); enabled.value = true }
        val input = compose.onNodeWithContentDescription(if (time) "\u8f93\u5165\u6267\u884c\u65f6\u95f4"
            else "\u8f93\u5165\u968f\u673a\u504f\u79fb\u5206\u949f")
        input.performScrollTo().performTextReplacement(if (time) "24:00" else "721")
        val invalid = reach(confirmLabel).assertIsNotEnabled()
        assertSurface(invalid, palette, false, "dialog-invalid-confirm")
        invalid.performClick().performTouchInput { click() }
        compose.runOnIdle { assertTrue(confirmations.isEmpty()) }
        val value = if (time) "09:15" else "45"
        input.performScrollTo().performTextReplacement(value)
        val valid = reach(confirmLabel).assertIsEnabled()
        assertFits(confirmLabel)
        assertSurface(valid, palette, true, "dialog-valid-confirm")
        valid.performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(value), confirmations) }
        val cancel = reach(cancelLabel).assertIsEnabled()
        assertFits(cancelLabel)
        assertSurface(cancel, palette, true, "dialog-cancel")
        cancel.performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, dismissals) }
        input.performScrollTo().assertIsDisplayed()
    }

    private fun services(theme: ComposeThemeState): ComposeServices {
        val context = compose.activity
        val session = SessionStore(context)
        session.setNoImage(true)
        val client = ApiClient(session)
        api = client
        return ComposeServices(context, session, client, GameDetailClient(client), LocalCache(context),
            Handler(Looper.getMainLooper()), ReadingTimeTracker(context), null, theme, ComposeToast {})
    }
}

/** Record real form-button dispatch without sending email, SMS, or credentials. */
@Implements(value = ComposeCheckinController::class, isInAndroidSdk = false)
internal class WatchCommandCheckinControllerShadow {
    @Implementation fun sendRegistrationEmail() { commands += "email" }
    @Implementation fun sendSmsCode() { commands += "sms" }

    companion object { val commands = mutableListOf<String>() }
}
