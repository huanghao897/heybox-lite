package com.ronan.heyboxlite

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/** A per-Activity surface: no app host, service container or main-process initialization. */
internal class ComposeCrashRecoveryHost(
    activity: ComponentActivity,
    session: SessionStore,
    report: String,
    restart: Runnable,
    exit: Runnable,
    save: Runnable,
) {
    private val state = mutableStateOf(ComposeCrashRecoveryState.fromReport(report))
    private var closed = false
    private var restartAction: Runnable? = restart
    private var exitAction: Runnable? = exit
    private var saveAction: Runnable? = save
    private val surface: ComposeView

    init {
        val theme = crashRecoveryTheme(session.darkMode(), session.uiScale() / 100f,
            session.textScale() / 100f, session.usesRoundLayout())
        surface = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HeyboxComposeTheme(theme) {
                    ComposeCrashRecoveryScreen(state.value, { restartAction?.run() },
                        { exitAction?.run() }, { saveAction?.run() })
                }
            }
        }
        activity.setContentView(surface)
    }

    fun status(value: String, saving: Boolean) {
        if (closed) return
        state.value = state.value.copy(status = value, saving = saving)
    }

    fun close() {
        if (closed) return
        closed = true
        restartAction = null
        exitAction = null
        saveAction = null
        surface.disposeComposition()
    }
}

internal fun crashRecoveryTheme(
    dark: Boolean,
    uiScale: Float,
    textScale: Float,
    round: Boolean,
): ComposeThemeState {
    val tokens = ThemeTokens.of(dark, 0, 0)
    return ComposeThemeState(
        dark = dark,
        background = tokens.background.asComposeColor(),
        panel = tokens.panel.asComposeColor(),
        panelElevated = tokens.panelElevated.asComposeColor(),
        text = tokens.text.asComposeColor(),
        muted = tokens.muted.asComposeColor(),
        subtle = tokens.subtle.asComposeColor(),
        hairline = tokens.hairline.asComposeColor(),
        accent = tokens.primary.asComposeColor(),
        link = tokens.link.asComposeColor(),
        onAccent = tokens.onPrimary.asComposeColor(),
        uiScale = uiScale.coerceIn(0.6f, 1f),
        textScale = textScale.coerceIn(0.5f, 1.8f),
        roundScreen = round,
        motionLevel = MotionLevel.OFF,
    )
}
