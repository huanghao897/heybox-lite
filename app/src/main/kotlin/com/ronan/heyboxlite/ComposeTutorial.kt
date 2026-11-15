package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal val LocalTutorialState = staticCompositionLocalOf<TutorialState?> { null }
internal val LocalTutorialActive = staticCompositionLocalOf { true }

@Composable
internal fun ComposeTutorialTrigger(topic: TutorialTopic?, active: Boolean = true) {
    val state = LocalTutorialState.current
    val foreground = active && LocalTutorialActive.current
    DisposableEffect(state, topic, foreground) {
        if (foreground && topic != null) state?.offer(topic)
        onDispose { if (foreground && topic != null) state?.leave(topic) }
    }
}

@Composable
internal fun ComposeTutorialOverlay(state: TutorialState, doubleTapReply: Boolean = true) {
    val topic = state.topic ?: return
    key(topic) {
        ComposeTutorialDialog(topic, doubleTapReply, { state.dismiss() }, state::complete, state.rotary)
    }
}

@Composable
internal fun ComposeTutorialDialog(
    topic: TutorialTopic,
    doubleTapReply: Boolean,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    rotary: ComposeRotaryRequest? = null,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val steps = TutorialCatalog.steps(topic, doubleTapReply)
    val step = steps[index]
    val theme = LocalHeyboxTheme.current
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(theme.background).testTag("tutorial-viewport")) {
            val side = if (theme.roundScreen) maxWidth * 0.13f else 16.dp
            val top = if (theme.roundScreen) minOf(maxWidth, maxHeight) * 0.12f else 12.dp
            val bottom = if (theme.roundScreen) minOf(maxWidth, maxHeight) * 0.14f else 12.dp
            val headerInset = if (theme.roundScreen) (watchHeaderHorizontal(maxWidth, theme) - side)
                .coerceAtLeast(0.dp) else 0.dp
            Column(Modifier.fillMaxSize().padding(start = side, end = side, top = top, bottom = bottom)
                .testTag("tutorial-frame"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = headerInset), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Icon(painterResource(step.icon), null, tint = theme.muted, modifier = Modifier.size(watchDp(16)))
                    Spacer(Modifier.size(6.dp))
                    Text("使用教程 · ${index + 1}/${steps.size}", color = theme.muted,
                        fontSize = watchSp(WatchType.CAPTION), textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("tutorial-progress"))
                }
                key(index) {
                    val scroll = rememberScrollState()
                    var lastRotary by remember { mutableIntStateOf(rotary?.serial ?: 0) }
                    LaunchedEffect(rotary?.serial) {
                        if (rotary != null && rotary.serial != lastRotary) {
                            lastRotary = rotary.serial
                            scroll.scrollBy(rotary.distance.toFloat())
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().testTag("tutorial-content")
                        .verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(step.title, color = theme.text, fontSize = watchSp(WatchType.SUBTITLE),
                            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().testTag("tutorial-step-title"))
                        Spacer(Modifier.height(7.dp))
                        Text(step.body, color = theme.muted, fontSize = watchSp(WatchType.SECONDARY),
                            lineHeight = watchLineHeight(WatchType.SECONDARY),
                            modifier = Modifier.fillMaxWidth().testTag("tutorial-step-body"))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = if (theme.roundScreen) 6.dp else 0.dp)
                    .testTag("tutorial-actions"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    WatchActionButton(if (index == 0) "稍后" else "上一步",
                        { if (index == 0) onDismiss() else index-- }, Modifier.weight(1f),
                        fontSize = WatchType.CAPTION)
                    WatchActionButton(if (index == steps.lastIndex) "知道了" else "下一步",
                        { if (index == steps.lastIndex) onComplete() else index++ }, Modifier.weight(1f),
                        fontSize = WatchType.CAPTION, primary = true)
                }
            }
        }
    }
}

@Composable
internal fun ComposeTutorialLibrary(state: TutorialState, onBack: () -> Unit) {
    WatchPage("使用教程", onBack) {
        WatchSectionTitle("使用教程")
        WatchCard {
            TutorialTopic.entries.forEach { topic ->
                WatchRow(topic.title, icon = topic.icon, chevron = true,
                    modifier = Modifier.testTag("tutorial-entry-${topic.key}"), onClick = { state.replay(topic) })
            }
        }
    }
}
