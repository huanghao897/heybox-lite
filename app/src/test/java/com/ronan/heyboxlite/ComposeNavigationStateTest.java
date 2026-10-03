package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

/** Pure route-stack checks kept independent from Activity and Compose rendering. */
public class ComposeNavigationStateTest {
    @Test public void backTargetsPreserveTheComposeShellHierarchy() {
        ComposeNavigationState state = new ComposeNavigationState();

        state.setRoute("search?q=watch");
        assertEquals("feed", state.backTarget());
        state.setRoute("reading_history");
        assertEquals("reading_center", state.backTarget());
        state.setRoute("display_settings");
        assertEquals("settings_home", state.backTarget());
        state.setRoute("settings_home");
        assertEquals("profile", state.backTarget());
    }

    @Test public void externalUserSpaceKeepsTheRouteItWasOpenedFrom() {
        ComposeNavigationState state = new ComposeNavigationState();
        state.setRoute("search?q=watch");

        state.showExternalUserSpace("user_space?user=42");

        assertEquals("search?q=watch", state.backTarget());
        assertEquals("user_space?user=42", state.currentRouteSpec());
    }
    @Test public void detailReturnKeepsTheSearchRouteSpec() throws Exception {
        ComposeNavigationState state = new ComposeNavigationState();
        state.setRoute("search?q=watch");
        state.showDetailLoading(FeedItem.from(new JSONObject().put("linkid", "post-1")));

        assertEquals("search?q=watch", state.detailReturnRouteSpec());
    }
}
