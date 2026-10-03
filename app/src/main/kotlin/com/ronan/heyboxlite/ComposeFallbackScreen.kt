package com.ronan.heyboxlite

import androidx.compose.runtime.Composable

@Composable
internal fun ComposeFallbackScreen(
    route: String,
    services: ComposeServices,
    onBack: () -> Unit,
) {
    WatchPage(route, onBack) {
        WatchCard { WatchEmptyState("该页面正在迁移中") }
    }
}
