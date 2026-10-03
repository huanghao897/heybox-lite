@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComposeCrashRecoveryScreen(
    state: ComposeCrashRecoveryState,
    onRestart: () -> Unit,
    onExit: () -> Unit,
    onSave: () -> Unit,
) {
    val theme = LocalHeyboxTheme.current
    BoxWithConstraints(Modifier.fillMaxSize().background(theme.background).testTag("crash-screen")) {
        val diameter = minOf(maxWidth, maxHeight)
        // The entire rectangle, including controls at both scroll limits, is inside the circle.
        val width = minOf(340.dp, if (theme.roundScreen) diameter * 0.68f else maxWidth - 24.dp)
            .coerceAtLeast(1.dp)
        val height = minOf(270.dp, if (theme.roundScreen) diameter * 0.68f else maxHeight - 20.dp)
            .coerceAtLeast(1.dp)
        Column(Modifier.align(Alignment.Center).width(width).height(height).testTag("crash-content")) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .testTag("crash-report"),
                verticalArrangement = Arrangement.spacedBy(watchDp(5)),
            ) {
                Text("\u5e94\u7528\u51fa\u73b0\u5f02\u5e38", color = theme.text, fontSize = watchSp(17f),
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(), letterSpacing = 0.sp)
                Text(state.summary, color = theme.muted, fontSize = watchSp(11f),
                    textAlign = TextAlign.Center, letterSpacing = 0.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().testTag("crash-summary"))
                if (state.stack.isNotEmpty()) {
                    Text(state.stack, color = theme.muted, fontSize = watchSp(10f),
                        fontFamily = FontFamily.Monospace, letterSpacing = 0.sp,
                        modifier = Modifier.fillMaxWidth().testTag("crash-stack"))
                }
            }
            // Keep long stack text out of the action scroller. Large labels may wrap naturally.
            Column(
                Modifier.fillMaxWidth().heightIn(max = height * 0.78f)
                    .verticalScroll(rememberScrollState()).padding(top = watchDp(4))
                    .testTag("crash-actions"),
                verticalArrangement = Arrangement.spacedBy(watchDp(5)),
            ) {
                Text(state.status, color = theme.muted, fontSize = watchSp(10f),
                    textAlign = TextAlign.Center, letterSpacing = 0.sp,
                    modifier = Modifier.fillMaxWidth().testTag("crash-status")
                        .semantics { liveRegion = LiveRegionMode.Polite })
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    CrashRecoveryButton("\u91cd\u542f\u5e94\u7528", "crash-restart", onRestart, primary = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(watchDp(6)),
                        verticalAlignment = Alignment.CenterVertically) {
                        CrashRecoveryButton("\u9000\u51fa", "crash-exit", onExit, Modifier.weight(1f))
                        IconButton(
                            onClick = onSave,
                            enabled = !state.saving,
                            modifier = Modifier.size(watchDp(38).coerceAtLeast(32.dp))
                                .clip(RoundedCornerShape(8.dp)).background(theme.panel)
                                .testTag("crash-save").semantics { stateDescription = state.status },
                        ) {
                            Icon(painterResource(R.drawable.il_scroll),
                                contentDescription = "\u4fdd\u5b58\u5b8c\u6574\u65e5\u5fd7",
                                tint = if (state.saving) theme.subtle else theme.text,
                                modifier = Modifier.size(watchDp(20)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashRecoveryButton(
    label: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val theme = LocalHeyboxTheme.current
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = watchDp(38).coerceAtLeast(32.dp)).testTag(tag),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) theme.accent else theme.panel,
            contentColor = if (primary) theme.onAccent else theme.text,
        ),
        contentPadding = PaddingValues(horizontal = watchDp(6), vertical = watchDp(5)),
    ) {
        Text(label, fontSize = watchSp(13f), textAlign = TextAlign.Center,
            letterSpacing = 0.sp, modifier = Modifier.fillMaxWidth())
    }
}
