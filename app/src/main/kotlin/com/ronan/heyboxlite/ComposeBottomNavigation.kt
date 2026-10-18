package com.ronan.heyboxlite

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal class ComposeBottomNavItem(
    val label: String,
    val key: String,
    @DrawableRes val icon: Int,
    val click: Runnable,
)

/** Snapshot state shared between the Java facade and the Compose capsule. */
internal class ComposeBottomNavState(
    theme: ComposeThemeState,
    surface: Color,
    selectionSurface: Color,
    dimensions: ResponsiveDock.Dimensions,
) {
    val items = mutableStateListOf<ComposeBottomNavItem>()
    var theme by mutableStateOf(theme)
    var surface by mutableStateOf(surface)
    var selectionSurface by mutableStateOf(selectionSurface)
    var dimensions by mutableStateOf(dimensions)
    val selection = mutableStateOf<String?>(null)

    fun apply(tokens: ThemeTokens, round: Boolean, uiScale: Float, textScale: Float) {
        theme = composeThemeState(tokens, uiScale, textScale, round)
        surface = tokens.dockSurface().asComposeColor()
        selectionSurface = tokens.navSelection().asComposeColor()
    }
}

@Composable
internal fun ComposeBottomNavigation(
    state: ComposeBottomNavState,
    motion: Boolean,
) {
    val theme = state.theme
    val dimensions = state.dimensions
    val density = LocalDensity.current
    val paddingH = with(density) { dimensions.paddingHorizontal.toDp() }
    val paddingV = with(density) { dimensions.paddingVertical.toDp() }
    val itemMargin = with(density) { dimensions.itemMargin.toDp() }
    val iconSize = with(density) { dimensions.iconSize.toDp() }
    val capsuleRadius = (28 * theme.uiScale).dp
    val selectionRadius = (24 * theme.uiScale).dp
    val selected = state.selection.value

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(capsuleRadius))
            .background(state.surface),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = paddingH, vertical = paddingV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.items.forEach { item ->
                key(item.key) {
                    DockItem(
                        item = item,
                        active = item.key == selected,
                        iconSize = iconSize,
                        itemMargin = itemMargin,
                        selectionRadius = selectionRadius,
                        selectionSurface = state.selectionSurface,
                        theme = theme,
                        motion = motion,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.DockItem(
    item: ComposeBottomNavItem,
    active: Boolean,
    iconSize: androidx.compose.ui.unit.Dp,
    itemMargin: androidx.compose.ui.unit.Dp,
    selectionRadius: androidx.compose.ui.unit.Dp,
    selectionSurface: Color,
    theme: ComposeThemeState,
    motion: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (motion && pressed) 0.975f else 1f,
        animationSpec = tween(durationMillis = if (pressed && motion) 90 else 160),
        label = "dockPress",
    )
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = itemMargin),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .scale(scale)
                .clip(RoundedCornerShape(selectionRadius))
                .background(if (active) selectionSurface else Color.Transparent)
                .testTag("bottom-nav-${item.key}")
                .semantics { contentDescription = item.label }
                .selectable(
                    selected = active,
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Tab,
                    onClick = { item.click.run() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.icon),
                contentDescription = null,
                tint = if (active) theme.text else theme.subtle,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
