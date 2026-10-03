package com.ronan.heyboxlite

import android.app.Activity
import android.os.Handler

/** Java-friendly callback used by Compose screens for short user messages. */
internal fun interface ComposeToast {
    fun show(message: String)
}

/** Shared bridge for Compose screens; it contains services, not mutable UI state. */
internal data class ComposeServices(
    val activity: Activity,
    val session: SessionStore,
    val api: ApiClient,
    val gameDetails: GameDetailClient,
    val cache: LocalCache,
    val handler: Handler,
    val reading: ReadingTimeTracker,
    val checkin: CheckinCenterCoordinator?,
    val theme: ComposeThemeState,
    val toast: ComposeToast,
)
