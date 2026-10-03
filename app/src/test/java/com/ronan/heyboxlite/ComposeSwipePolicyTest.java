package com.ronan.heyboxlite;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ComposeSwipePolicyTest {
    @Test public void feedCanStartSwipeAwayFromEdge() {
        assertTrue(ComposeSwipePolicy.canArm("feed", 120f, 28f));
    }

    @Test public void subpagesCanStartFromTheWholeWatchSurface() {
        assertTrue(ComposeSwipePolicy.canArm("detail", 20f, 28f));
        assertTrue(ComposeSwipePolicy.canArm("detail", 40f, 28f));
        assertTrue(ComposeSwipePolicy.canArm("settings_home", 96f, 28f));
        assertFalse(ComposeSwipePolicy.canArm("", 0f, 28f));
    }
}
