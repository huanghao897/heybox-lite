package com.ronan.heyboxlite;

/** Locks a touch stream to its first decisive axis or to multi-touch. */
final class GestureAxisLock {
    static final int NONE = 0;
    static final int HORIZONTAL = 1;
    static final int VERTICAL = 2;
    static final int MULTI_TOUCH = 3;

    private GestureAxisLock() {}

    static int resolve(int current, float dx, float dy,
                       float horizontalSlop, float verticalSlop,
                       float axisRatio, boolean multiTouch) {
        if (multiTouch) return MULTI_TOUCH;
        if (current != NONE) return current;
        float absDx = Math.abs(dx);
        float absDy = Math.abs(dy);
        if (absDx > horizontalSlop && absDx > absDy * axisRatio) {
            return HORIZONTAL;
        }
        if (absDy > verticalSlop && absDy > absDx * axisRatio) {
            return VERTICAL;
        }
        return NONE;
    }
}
