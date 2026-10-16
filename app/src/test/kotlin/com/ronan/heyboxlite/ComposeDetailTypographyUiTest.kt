package com.ronan.heyboxlite

import android.app.Application
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeDetailTypographyUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var api: ApiClient? = null

    @After fun close() { api?.close() }

    @Test fun squareBodyHeadingsQuotesAndCaptionsMatchTheLegacyTextViews() = verify()

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi")
    fun roundBodyHeadingsQuotesAndCaptionsMatchTheLegacyTextViews() = verify()

    @Test @Config(fontScale = 1.3f)
    fun uiTextBodyAndSystemScalesAreEachAppliedExactlyOnce() =
        verify(uiScale = 0.7f, textScale = 1.2f, bodyScale = 125)

    @Test @Config(qualifiers = "w192dp-h192dp-round-mdpi", fontScale = 1.3f)
    fun roundLargeTextAndCustomLineSpacingUseTheNativeFontMetrics() =
        verify(uiScale = 0.5f, textScale = 1.4f, bodyScale = 120, lineSpacing = 160)

    private fun verify(uiScale: Float = 1f, textScale: Float = 1f, bodyScale: Int = 100,
        lineSpacing: Int = 122) {
        val activity = compose.activity
        val round = activity.resources.configuration.isScreenRound
        val session = SessionStore(activity).apply {
            setBodyTextScale(bodyScale)
            setBodyLineSpacing(lineSpacing)
        }
        val theme = composePreviewTheme(round).copy(uiScale = uiScale, textScale = textScale)
        val client = ApiClient(session).also { api = it }
        val services = ComposeServices(activity, session, client, GameDetailClient(client),
            LocalCache(activity), Handler(Looper.getMainLooper()), ReadingTimeTracker(activity),
            null, theme, ComposeToast {})
        val blocks = RichContent.parse("<h2>Heading</h2><p>Body sample</p>" +
            "<blockquote>Quote sample</blockquote><figcaption>Caption</figcaption>", null)
        compose.setContent {
            HeyboxComposeTheme(theme) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    blocks.forEach { ComposeDetailBlock(it, ComposeMediaSettings(enabled = false), services, {}) }
                }
            }
        }
        val cases = listOf(
            Triple("Heading", if (round) 15f else 16f, 1.16f),
            Triple("Body sample", if (round) 13f else 14.5f, maxOf(1.18f, lineSpacing / 100f)),
            Triple("Quote sample", if (round) 12.5f else 13.5f, maxOf(1.14f, lineSpacing / 100f)),
            Triple("Caption", 11f, 1.3f),
        )
        for ((label, baseSize, spacing) in cases) {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(results)
            }
            val layout = paintedTextLayout(results.single())
            val native = UiComponents.label(activity, label, baseSize, android.graphics.Color.BLACK,
                textScale * bodyScale / 100f).apply {
                typeface = if (label == "Heading") Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setLineSpacing(UiComponents.dp(activity, 1, uiScale).toFloat(), spacing)
            }
            val expectedSp = baseSize * textScale * bodyScale / 100f
            assertEquals("$label must retain the native base size and apply app scales once",
                expectedSp, layout.layoutInput.style.fontSize.value, 0.001f)
            val expectedPx = with(layout.layoutInput.density) { expectedSp.sp.toPx() }
            if (layout.layoutInput.density.fontScale == 1f) {
                assertEquals("$label default size must match the old renderer", native.textSize, expectedPx, 0.05f)
            }
            // Compose backports nonlinear font scaling, unlike pre-34 TextView.
            // Compare native line metrics at the actual scaled size, not scaledDensity * sp.
            native.setTextSize(TypedValue.COMPLEX_UNIT_PX, expectedPx)
            assertEquals("$label must follow Compose system font scaling", expectedPx,
                with(layout.layoutInput.density) { layout.layoutInput.style.fontSize.toPx() }, 0.05f)
            assertEquals("$label line height must use native font metrics", native.lineHeight.toFloat(),
                with(layout.layoutInput.density) { layout.layoutInput.style.lineHeight.toPx() }, 0.05f)
        }
    }
}
