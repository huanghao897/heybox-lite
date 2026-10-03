package com.ronan.heyboxlite

/** Keeps shell back gestures away from horizontal content gestures. */
internal object ComposeSwipePolicy {
    @JvmStatic
    fun canArm(route: String, startX: Float, edgePx: Float): Boolean {
        // Sub-pages in the native shell accept a watch-style back swipe from
        // anywhere. The gesture modifier runs in Main pass, so a real child
        // horizontal control can consume the gesture before we take it.
        return route.isNotBlank()
    }
}
