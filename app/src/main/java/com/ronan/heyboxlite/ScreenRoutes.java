package com.ronan.heyboxlite;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class ScreenRoutes {
    private static final Set<String> DYNAMIC_BACK_SCREENS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("detail", "user_space", "saved")));
    private static final Map<String, String> STATIC_PARENTS;

    static {
        Map<String, String> parents = new HashMap<>();
        parents.put("search", "feed");
        parents.put("reading_stats", "reading_center");
        parents.put("reading_center", "profile");
        parents.put("leaderboard", "profile");
        parents.put("checkin_center", "profile");
        parents.put("announcement_board", "about");
        parents.put("display_preview", "display_settings");
        parents.put("display_settings", "settings_home");
        parents.put("startup_settings", "settings_home");
        parents.put("app_settings", "settings_home");
        parents.put("about", "settings_home");
        parents.put("settings_home", "profile");
        STATIC_PARENTS = Collections.unmodifiableMap(parents);
    }

    private ScreenRoutes() {
    }

    static boolean canNavigateBack(String screen) {
        return DYNAMIC_BACK_SCREENS.contains(screen) || STATIC_PARENTS.containsKey(screen);
    }

    static String staticParentOrDefault(String screen, String defaultScreen) {
        String parent = STATIC_PARENTS.get(screen);
        return parent == null ? defaultScreen : parent;
    }
}
