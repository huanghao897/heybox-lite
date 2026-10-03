package com.ronan.heyboxlite;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ComposeSwipePolicyTest {
    @Test public void feedCanStartSwipeAwayFromEdge() {
        assertTrue(ComposeSwipePolicy.canArm("feed", 120f, 28f));
    }

    @Test public void detailRequiresEdgeStart() {
        assertTrue(ComposeSwipePolicy.canArm("detail", 20f, 28f));
        assertFalse(ComposeSwipePolicy.canArm("detail", 40f, 28f));
    }

    @Test public void leftDragCancelsArmedBackGesture() {
        assertTrue(ComposeSwipePolicy.shouldCancelForLeftDrag(true, 0f, -3f));
        assertFalse(ComposeSwipePolicy.shouldCancelForLeftDrag(true, 4f, -3f));
        assertFalse(ComposeSwipePolicy.shouldCancelForLeftDrag(false, 0f, -3f));
    }
}
