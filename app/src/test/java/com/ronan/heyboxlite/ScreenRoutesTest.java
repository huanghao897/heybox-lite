package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScreenRoutesTest {
    @Test
    public void staticScreensKeepTheirExpectedParents() {
        assertParent("search", "feed");
        assertParent("reading_stats", "reading_center");
        assertParent("reading_center", "profile");
        assertParent("checkin_center", "profile");
        assertParent("announcement_board", "about");
        assertParent("display_preview", "display_settings");
        assertParent("display_settings", "settings_home");
        assertParent("startup_settings", "settings_home");
        assertParent("app_settings", "settings_home");
        assertParent("about", "settings_home");
        assertParent("settings_home", "profile");
    }

    @Test
    public void dynamicScreensAllowBackWithoutInventingAStaticParent() {
        assertTrue(ScreenRoutes.canNavigateBack("detail"));
        assertTrue(ScreenRoutes.canNavigateBack("user_space"));
        assertTrue(ScreenRoutes.canNavigateBack("saved"));
        assertEquals("fallback", ScreenRoutes.staticParentOrDefault("detail", "fallback"));
        assertEquals("fallback", ScreenRoutes.staticParentOrDefault("user_space", "fallback"));
        assertEquals("fallback", ScreenRoutes.staticParentOrDefault("saved", "fallback"));
    }

    @Test
    public void topLevelAndUnknownScreensDoNotExposeBack() {
        assertFalse(ScreenRoutes.canNavigateBack("feed"));
        assertFalse(ScreenRoutes.canNavigateBack("profile"));
        assertFalse(ScreenRoutes.canNavigateBack("login"));
        assertFalse(ScreenRoutes.canNavigateBack("unknown"));
    }

    private static void assertParent(String screen, String expectedParent) {
        assertTrue(ScreenRoutes.canNavigateBack(screen));
        assertEquals(expectedParent, ScreenRoutes.staticParentOrDefault(screen, "fallback"));
    }
}
