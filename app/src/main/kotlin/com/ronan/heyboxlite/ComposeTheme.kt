package com.ronan.heyboxlite

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal data class ComposeRotaryRequest(val serial: Int, val distance: Int)

internal data class ComposeThemeState(
    val dark: Boolean,
    val background: Color,
    val panel: Color,
    val panelElevated: Color,
    val text: Color,
    val muted: Color,
    val subtle: Color,
    val hairline: Color,
    val accent: Color,
    val link: Color,
    val onAccent: Color,
    val uiScale: Float,
    val textScale: Float,
    val roundScreen: Boolean,
    val rotaryRequest: ComposeRotaryRequest? = null,
    val motionLevel: Int = MotionLevel.REDUCED,
)

internal val LocalHeyboxTheme = staticCompositionLocalOf<ComposeThemeState> {
    error("Heybox Compose theme is not installed")
}

internal val LocalHeyboxRotaryRequest = compositionLocalOf<ComposeRotaryRequest?> { null }

internal fun composeThemeState(
    tokens: ThemeTokens,
    uiScale: Float,
    textScale: Float,
    roundScreen: Boolean,
): ComposeThemeState = ComposeThemeState(
    dark = tokens.dark,
    background = tokens.background.asComposeColor(),
    panel = tokens.panel.asComposeColor(),
    panelElevated = tokens.panelElevated.asComposeColor(),
    text = tokens.text.asComposeColor(),
    muted = tokens.muted.asComposeColor(),
    subtle = tokens.subtle.asComposeColor(),
    hairline = tokens.hairline.asComposeColor(),
    accent = tokens.accent.asComposeColor(),
    link = tokens.link.asComposeColor(),
    onAccent = tokens.onPrimary.asComposeColor(),
    uiScale = uiScale.coerceIn(0.5f, 1.6f),
    textScale = textScale.coerceIn(0.75f, 1.8f),
    roundScreen = roundScreen,
    motionLevel = Motions.level(),
)

@Composable
internal fun HeyboxComposeTheme(
    state: ComposeThemeState,
    content: @Composable () -> Unit,
) {
    // Scroll input changes much more often than the palette. Do not invalidate
    // every themed image and text view on each crown event.
    val visualState = state.copy(rotaryRequest = null)
    val colors: ColorScheme = remember(visualState) {
        if (visualState.dark) {
            darkColorScheme(
                primary = visualState.accent,
                onPrimary = visualState.onAccent,
                background = visualState.background,
                onBackground = visualState.text,
                surface = visualState.panel,
                onSurface = visualState.text,
                surfaceVariant = visualState.panelElevated,
                onSurfaceVariant = visualState.muted,
                outline = visualState.hairline,
                primaryContainer = visualState.panelElevated,
                onPrimaryContainer = visualState.text,
            )
        } else {
            lightColorScheme(
                primary = visualState.accent,
                onPrimary = visualState.onAccent,
                background = visualState.background,
                onBackground = visualState.text,
                surface = visualState.panel,
                onSurface = visualState.text,
                surfaceVariant = visualState.panelElevated,
                onSurfaceVariant = visualState.muted,
                outline = visualState.hairline,
                primaryContainer = visualState.panelElevated,
                onPrimaryContainer = visualState.text,
            )
        }
    }
    val typography = remember { Typography() }
    CompositionLocalProvider(
        LocalHeyboxTheme provides visualState,
        LocalHeyboxRotaryRequest provides state.rotaryRequest,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            shapes = MaterialTheme.shapes.copy(
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp),
            ),
            content = content,
        )
    }
}

internal fun Int.asComposeColor(): Color = Color(
    red = android.graphics.Color.red(this) / 255f,
    green = android.graphics.Color.green(this) / 255f,
    blue = android.graphics.Color.blue(this) / 255f,
    alpha = android.graphics.Color.alpha(this) / 255f,
)
