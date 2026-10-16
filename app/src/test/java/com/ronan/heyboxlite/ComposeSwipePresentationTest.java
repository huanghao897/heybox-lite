package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ComposeSwipePresentationTest {
    @Test public void homeAndProfileMoveTogetherWithoutAGap() {
        assertEquals(-96f, position("feed", "profile", -1, -96f), 0f);
        assertEquals(224f, position("profile", "profile", -1, -96f), 0f);
        assertEquals(96f, position("profile", "feed", 1, 96f), 0f);
        assertEquals(-224f, position("feed", "feed", 1, 96f), 0f);
    }

    @Test public void targetDoesNotJumpAtRouteCommit() {
        assertEquals(0f, position("feed", "feed", 1, 320f), 0f);
        assertEquals(0f, position("feed", null, 0, 0f), 0f);
        assertEquals(0f, position("profile", "profile", -1, -320f), 0f);
        assertEquals(0f, position("profile", null, 0, 0f), 0f);
    }

    @Test public void liveTargetKeepsSameRouteIdentityAcrossHandoff() {
        assertEquals(Arrays.asList("profile", "feed"),
                ComposeSwipePresentation.routes("profile", "feed"));
        assertEquals(Collections.singletonList("feed"),
                ComposeSwipePresentation.routes("feed", "feed"));
        assertTrue(ComposeSwipePresentation.isLiveRoute("feed"));
        assertTrue(ComposeSwipePresentation.isLiveRoute("profile"));
        assertTrue(ComposeSwipePresentation.isLiveRoute("settings_home"));
        assertTrue(ComposeSwipePresentation.isLiveRoute("reading_center"));
        assertFalse(ComposeSwipePresentation.isLiveRoute("leaderboard"));
        assertFalse(ComposeSwipePresentation.isLiveRoute(null));
    }

    @Test public void reversingPastGestureOriginNeverCompletesOppositeTarget() {
        assertEquals(0f, ComposeSwipePolicy.dragOffset(-130f, 1, 320f), 0f);
        assertEquals(0f, ComposeSwipePolicy.dragOffset(130f, -1, 320f), 0f);
        assertEquals(96f, ComposeSwipePolicy.dragOffset(96f, 1, 320f), 0f);
        assertEquals(-96f, ComposeSwipePolicy.dragOffset(-96f, -1, 320f), 0f);
    }

    @Test public void draggingCanTravelTheFullWidth() {
        assertEquals(260f, ComposeSwipePolicy.dragOffset(260f, 1, 320f), 0f);
        assertEquals(320f, ComposeSwipePolicy.dragOffset(800f, 1, 320f), 0f);
        assertEquals(-320f, ComposeSwipePolicy.dragOffset(-800f, -1, 320f), 0f);
    }

    @Test public void parameterizedTargetsKeepTheirIdentityDuringLiveHandoff() {
        assertEquals(Arrays.asList("profile", "favorite_folder?folder=42&name=games"),
                ComposeSwipePresentation.routes("profile", "favorite_folder?folder=42&name=games"));
        assertEquals(-224f, position("favorite_folder?folder=42", "favorite_folder?folder=42", 1, 96f), 0f);
        assertEquals(Collections.singletonList("favorite_folder?folder=42"),
                ComposeSwipePresentation.routes("favorite_folder?folder=42", "favorite_folder?folder=42"));
    }

    @Test public void nestedDetailsHaveSeparateLiveLayersAndMeetAtTheEdge() {
        String child = "detail?entry=2";
        String parent = "detail?entry=1";
        assertEquals(Arrays.asList(child, parent), ComposeSwipePresentation.routes(child, parent));
        assertEquals(96f, position(child, parent, 1, 96f), 0f);
        assertEquals(-224f, position(parent, parent, 1, 96f), 0f);
        assertEquals(0f, position(parent, parent, 1, 320f), 0f);
        assertEquals(Collections.singletonList(parent), ComposeSwipePresentation.routes(parent, parent));
    }

    private static float position(String page, String target, int direction, float drag) {
        return ComposeSwipePresentation.translation(page, target, direction, drag, 320f);
    }
}
