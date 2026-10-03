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

    @JvmStatic
    fun dragOffset(distance: Float, direction: Int, width: Float): Float =
        if (direction > 0) distance.coerceIn(0f, width) else distance.coerceIn(-width, 0f)
}
