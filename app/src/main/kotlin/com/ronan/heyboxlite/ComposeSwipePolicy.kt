package com.ronan.heyboxlite

/** Keeps shell back gestures away from horizontal content gestures such as image paging. */
internal object ComposeSwipePolicy {
    @JvmStatic
    fun canArm(route: String, startX: Float, edgePx: Float): Boolean {
        return route == "feed" || startX <= edgePx
    }
}
