package com.ronan.heyboxlite

import android.app.Activity
import android.app.Application

/** Observes the plain Activity used by the app without requiring a lifecycle bridge. */
internal class ComposeCheckinActivityLifecycle(
    private val activity: Activity,
    private val onResume: () -> Unit,
    private val onPause: () -> Unit,
) {
    private var registered = false
    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) {
            if (activity === this@ComposeCheckinActivityLifecycle.activity) onResume()
        }

        override fun onActivityPaused(activity: Activity) {
            if (activity === this@ComposeCheckinActivityLifecycle.activity) onPause()
        }

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(
            activity: Activity,
            state: android.os.Bundle,
        ) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            if (activity === this@ComposeCheckinActivityLifecycle.activity) onPause()
        }
    }

    fun start() {
        if (registered) return
        registered = true
        activity.application.registerActivityLifecycleCallbacks(callbacks)
        onResume()
    }

    fun close() {
        if (!registered) return
        registered = false
        activity.application.unregisterActivityLifecycleCallbacks(callbacks)
        onPause()
    }
}
