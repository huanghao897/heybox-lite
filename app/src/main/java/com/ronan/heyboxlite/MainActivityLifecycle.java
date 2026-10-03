package com.ronan.heyboxlite;

import android.content.ComponentCallbacks2;

/** Owns Activity foreground/background transitions and resource cleanup. */
final class MainActivityLifecycle {
    private static final long PRESENCE_INTERVAL_MS = 600_000L;

    private final MainActivity activity;
    private final Runnable presenceTick = new Runnable() {
        @Override
        public void run() {
            PresenceReporter.ping(activity.session, activity.readingTimeTracker,
                    activity::applyAccessStatus);
            activity.handler.postDelayed(this, PRESENCE_INTERVAL_MS);
        }
    };

    MainActivityLifecycle(MainActivity activity) {
        this.activity = activity;
    }

    void onResume() {
        activity.activityResumed = true;
        if (activity.checkinCenterPage != null && "checkin_center".equals(activity.screen)) {
            activity.checkinCenterPage.onResume();
        }
        if (activity.checkinLeaderboardController != null
                && "leaderboard".equals(activity.screen)) {
            activity.checkinLeaderboardController.onResume();
        }
        if ("login".equals(activity.screen) && activity.qrLoginPage != null) {
            activity.qrLoginPage.resume();
        }
        if (activity.accountBlockedScreen) {
            PresenceReporter.pingNow(activity.session, activity.readingTimeTracker,
                    activity::applyAccessStatus);
            return;
        }
        if ("detail".equals(activity.screen) && activity.currentDetailBody != null
                && activity.currentDetailItem != null && activity.readingTimeTracker != null) {
            activity.readingTimeTracker.start(activity.currentDetailItem.article,
                    activity.currentDetailItem.id);
        }
        PresenceReporter.ping(activity.session, activity.readingTimeTracker,
                activity::applyAccessStatus);
        activity.handler.removeCallbacks(presenceTick);
        activity.handler.postDelayed(presenceTick, PRESENCE_INTERVAL_MS);
    }

    void onPause() {
        activity.activityResumed = false;
        if (activity.crownInput != null) activity.crownInput.cancel();
        if (activity.checkinCenterPage != null) activity.checkinCenterPage.onPause();
        if (activity.checkinLeaderboardController != null) {
            activity.checkinLeaderboardController.onPause();
        }
        if (activity.qrLoginPage != null) activity.qrLoginPage.pause();
        if (activity.readingTimeTracker != null) activity.readingTimeTracker.pause();
        activity.handler.removeCallbacks(presenceTick);
        activity.saveCurrentDetailProgress();
    }

    void onTrimMemory(int level) {
        CrashBreadcrumbs.record("memory trim level=" + level);
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return;
        clearVisualResources();
    }

    void onLowMemory() {
        clearVisualResources();
    }

    void onDestroy() {
        if (activity.composeAppHost != null) activity.composeAppHost.close();
        if (activity.crownInput != null) activity.crownInput.cancel();
        if (activity.readingTimeTracker != null) activity.readingTimeTracker.pause();
        if (activity.checkinCenterPage != null) {
            activity.checkinCenterPage.close();
            activity.checkinCenterPage = null;
        }
        if (activity.checkinLeaderboardController != null) {
            activity.checkinLeaderboardController.close();
            activity.checkinLeaderboardController = null;
        }
        if (activity.checkinCenterCoordinator != null) {
            activity.checkinCenterCoordinator.close();
            activity.checkinCenterCoordinator = null;
        }
        activity.saveCurrentDetailProgress();
        activity.stopQrPolling();
        if (activity.qrLoginPage != null) {
            activity.qrLoginPage.close();
            activity.qrLoginPage = null;
        }
        activity.pageTransitions.cancelNow();
        activity.discardDetailHistory();
        if (activity.feedPage != null) activity.feedPage.close();
        if (activity.savedContentController != null) activity.savedContentController.close();
        if (activity.detailPager != null) activity.detailPager.cancelMotion();
        if (activity.content instanceof BackSwipeFrameLayout) {
            ((BackSwipeFrameLayout) activity.content).cancelMotion();
        }
        ImageLoader.cancelTree(activity.content);
        activity.screenSnapshots.releaseAll();
        activity.fullScreenSnapshots.releaseAll();
        activity.retainedPages.clear();
        if (activity.searchPage != null) activity.searchPage.close();
        if (activity.searchBars != null) activity.searchBars.clear();
        if (activity.writeActions != null) activity.writeActions.close();
        if (activity.detailLoader != null) activity.detailLoader.close();
        activity.handler.removeCallbacksAndMessages(null);
        if (activity.writeTokenProvider != null) activity.writeTokenProvider.close();
        if (activity.api != null) activity.api.close();
        if (activity.cacheMaintenance != null) activity.cacheMaintenance.close();
        if (activity.diagnosticsController != null) activity.diagnosticsController.close();
    }

    private void clearVisualResources() {
        ImageLoader.clear();
        activity.screenSnapshots.clear();
        activity.fullScreenSnapshots.clear();
    }
}
