package com.ronan.heyboxlite

/** Keeps shell back gestures away from horizontal content gestures. */
internal object ComposeSwipePolicy {
    @JvmStatic
    fun canArm(route: String, startX: Float, edgePx: Float): Boolean {
        // Compose screens contain sliders, text fields and horizontal media
        // controls. Starting only from the leading edge keeps those controls
        // from being mistaken for a back gesture. The native shell applies
        // the same ownership rule through its child disallow-intercept path.
        return route.isNotBlank() && (edgePx <= 0f || startX <= edgePx)
    }
}
