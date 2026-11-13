package com.ronan.heyboxlite

import android.app.Activity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Scheduling follows RESUMED, not the lifetime of the composition. */
internal class ComposeCheckinActivityLifecycle(
    activity: Activity,
    private val onResume: () -> Unit,
    private val onPause: () -> Unit,
    private val onDestroy: () -> Unit = {},
) : DefaultLifecycleObserver {
    private val lifecycle = (activity as LifecycleOwner).lifecycle
    private var registered = false
    private var resumed = false

    override fun onResume(owner: LifecycleOwner) {
        if (!registered || resumed) return
        resumed = true
        onResume()
    }

    override fun onPause(owner: LifecycleOwner) = pause()

    override fun onStop(owner: LifecycleOwner) = pause()

    override fun onDestroy(owner: LifecycleOwner) {
        close()
        onDestroy()
    }

    fun start() {
        if (registered) return
        registered = true
        lifecycle.addObserver(this)
    }

    private fun pause() {
        if (!resumed) return
        resumed = false
        onPause()
    }

    fun close() {
        if (!registered) return
        registered = false
        lifecycle.removeObserver(this)
        pause()
    }
}
