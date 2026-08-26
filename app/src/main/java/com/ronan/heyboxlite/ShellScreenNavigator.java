package com.ronan.heyboxlite;

/** Applies shell chrome after a real view has been adopted by a swipe transition. */
final class ShellScreenNavigator {
    interface Host {
        String screen();

        void pauseCheckinPage();

        void pauseLeaderboardPage();

        void activateTopLevel(String key);

        void restoreFeedScroll();

        void updateProfileSummary();

        void showProfileRefreshAction();

        void showSubpage(String key, int titleRes, Runnable backAction);

        void navigateToProfile();

        void navigateToReadingCenter();

        void navigateToSettingsHome();

        void navigateToDisplaySettings();

        void navigateToAbout();

        void navigateToFeed();
    }

    private final Host host;

    ShellScreenNavigator(Host host) {
        this.host = host;
    }

    void adopt(String key) {
        pauseTransientPage();
        if ("feed".equals(key)) {
            host.activateTopLevel("feed");
            host.restoreFeedScroll();
            return;
        }
        if ("profile".equals(key)) {
            host.activateTopLevel("profile");
            host.updateProfileSummary();
            host.showProfileRefreshAction();
            return;
        }
        if ("reading_stats".equals(key)) {
            host.showSubpage(key, R.string.title_reading_time, host::navigateToReadingCenter);
            return;
        }
        if ("reading_center".equals(key)) {
            host.showSubpage(key, R.string.title_reading_center, host::navigateToProfile);
            return;
        }
        if ("leaderboard".equals(key)) {
            host.showSubpage(key, R.string.title_leaderboard, host::navigateToProfile);
            return;
        }
        if ("settings_home".equals(key)) {
            host.showSubpage(key, R.string.title_settings, host::navigateToProfile);
            return;
        }
        if ("display_settings".equals(key)) {
            host.showSubpage(key, R.string.title_display, host::navigateToSettingsHome);
            return;
        }
        if ("display_preview".equals(key)) {
            host.showSubpage(key, R.string.title_ui_preview, host::navigateToDisplaySettings);
            return;
        }
        if ("startup_settings".equals(key)) {
            host.showSubpage(key, R.string.title_startup_update, host::navigateToSettingsHome);
            return;
        }
        if ("app_settings".equals(key)) {
            host.showSubpage(key, R.string.title_content_cache, host::navigateToSettingsHome);
            return;
        }
        if ("video_settings".equals(key)) {
            host.showSubpage(key, R.string.title_video_playback, host::navigateToSettingsHome);
            return;
        }
        if ("about".equals(key)) {
            host.showSubpage(key, R.string.title_about, host::navigateToSettingsHome);
        }
    }

    void navigateToParent(String screen) {
        String parent = ScreenRoutes.staticParentOrDefault(screen, "feed");
        if ("profile".equals(parent)) {
            host.navigateToProfile();
        } else if ("reading_center".equals(parent)) {
            host.navigateToReadingCenter();
        } else if ("display_settings".equals(parent)) {
            host.navigateToDisplaySettings();
        } else if ("about".equals(parent)) {
            host.navigateToAbout();
        } else if ("settings_home".equals(parent)) {
            host.navigateToSettingsHome();
        } else {
            host.navigateToFeed();
        }
    }

    private void pauseTransientPage() {
        if ("checkin_center".equals(host.screen())) host.pauseCheckinPage();
        if ("leaderboard".equals(host.screen())) host.pauseLeaderboardPage();
    }
}
