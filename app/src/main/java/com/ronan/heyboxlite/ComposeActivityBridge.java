package com.ronan.heyboxlite;

import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONObject;

/** Route and action adapter kept outside MainActivity so the Compose shell does not grow it again. */
final class ComposeActivityBridge implements ComposeAppCallbacks {
    private final MainActivity activity;
    private final ComposeCommentRequests commentRequests;
    private ComposeDetailRuntime composeDetail;

    ComposeActivityBridge(MainActivity activity) {
        this.activity = activity;
        this.commentRequests = new ComposeCommentRequests(activity);
    }

    void mount(FrameLayout body) {
        if (activity.composeAppHost != null) activity.composeAppHost.close();
        activity.content = new BackSwipeFrameLayout(activity, activity);
        body.addView(activity.content, new FrameLayout.LayoutParams(-1, -1));
        activity.composeLayer = new FrameLayout(activity);
        body.addView(activity.composeLayer, new FrameLayout.LayoutParams(-1, -1));
        activity.composeAppHost = new ComposeAppHost(activity, activity.composeLayer,
                activity.session, activity.api, activity.localCache, activity.handler,
                activity.readingTimeTracker, activity.checkinCenterCoordinator,
                activity.themeTokens, activity.usesRoundLayout(),
                activity.session.uiScale() / 100.0f,
                activity.session.textScale() / 100.0f,
                this,
                message -> activity.toast(message));
        activity.bottomNavigation = new BottomNavigationController(activity, activity.session,
                activity.themeTokens, activity.usesRoundLayout(), body);
        activity.bottomNavigation.addItem(activity.getString(R.string.title_feed), "feed",
                R.drawable.ic_nav_home, activity::onFeedNavClick);
        activity.bottomNavigation.addItem(activity.getString(R.string.title_profile), "profile",
                R.drawable.ic_nav_profile, () -> activity.showTopLevel(1));
    }

    boolean showRouteIfMounted(String route) {
        if (activity.composeAppHost == null || !activity.composeAppHost.isMounted()) return false;
        showRoute(route);
        return true;
    }

    boolean showUserSpace(String userId, String name, String avatar) {
        // A native fallback page owns its own history. Do not replace it with
        // a Compose route while the Compose layer is hidden.
        if (!isComposeSurfaceVisible()) return false;
        if (activity.composeAppHost == null || !activity.composeAppHost.isMounted()) return false;
        String route = "user_space?user=" + Uri.encode(userId)
                + "&name=" + Uri.encode(name == null ? "" : name)
                + "&avatar=" + Uri.encode(avatar == null ? "" : avatar);
        activity.composeAppHost.showExternalUserSpace(route);
        syncChrome(route);
        return true;
    }

    boolean handleBack() {
        // A native fallback page can coexist with this host while the Compose
        // layer is hidden. Do not consume its back event with a stale route.
        if (activity.composeLayer == null
                || activity.composeLayer.getVisibility() != View.VISIBLE) return false;
        return activity.composeAppHost != null && activity.composeAppHost.isMounted()
                && activity.composeAppHost.handleBack();
    }

    void showReadingStats() {
        activity.screen = "reading_stats";
        activity.setBottomNavVisible(false);
        activity.leading.setVisibility(View.VISIBLE);
        activity.leading.setOnClickListener(view -> {
            activity.pendingBackTransition = true;
            activity.savedContentController.showReadingCenter();
        });
        activity.title.setText(R.string.title_reading_time);
        activity.action.setVisibility(View.GONE);
        ReadingStatsPage page = new ReadingStatsPage(activity, activity.themeTokens,
                activity.usesRoundLayout(), activity.session.uiScale() / 100.0f,
                activity.session.textScale() / 100.0f);
        android.widget.ScrollView scroll = page.build(activity.readingTimeTracker.stats(),
                activity.settingsTopCard(activity.getString(R.string.title_reading_time)),
                activity.pageHorizontalPadding(), activity.subpageTopPadding());
        activity.retainedPages.put("reading_stats", scroll);
        activity.transitionTo(scroll);
    }

    boolean openComposeDetail(FeedItem item) {
        if (activity.composeAppHost == null
                || !activity.composeAppHost.isMounted()) return false;
        if (!isComposeSurfaceVisible()) {
            // A post opened from a native user page/detail page must remain in
            // the native history so its back gesture returns to that page.
            return false;
        }
        if (composeDetail == null) composeDetail = new ComposeDetailRuntime(activity);
        composeDetail.open(item);
        syncChrome("detail");
        EmojiStore.load(activity.api, () -> { });
        return true;
    }

    boolean showComposeDetailResult(DetailPageAssembler.Result detail, FeedItem fallback) {
        if (!isComposeSurfaceVisible()) return false;
        if (activity.composeAppHost == null || !activity.composeAppHost.isMounted()
                || !"detail".equals(activity.screen)) return false;
        activity.composeAppHost.showDetailResult(detail, fallback);
        activity.detailLoader.markRendered();
        if (activity.activityResumed && activity.readingTimeTracker != null) {
            activity.readingTimeTracker.start(fallback.article, fallback.id);
        }
        return true;
    }

    void showRoute(String route) {
        if (activity.composeAppHost == null || !activity.composeAppHost.isMounted()) return;
        String routeSpec = route == null ? "feed" : route;
        String key = route == null ? "feed" : route.split("\\?", 2)[0];
        activity.screen = key;
        activity.shellBar.setVisibility(View.GONE);
        activity.content.setVisibility(View.INVISIBLE);
        activity.composeLayer.setVisibility(View.VISIBLE);
        activity.composeAppHost.setSurfaceActive(true);
        boolean topLevel = "feed".equals(key) || "profile".equals(key);
        activity.setBottomNavVisible(topLevel, false);
        if (topLevel) activity.bottomNavigation.select(key);
        activity.composeAppHost.setRoute(routeSpec);
    }

    @Override public void navigate(String route) {
        syncChrome(route);
    }

    @Override public void back() {
        activity.onBackPressed();
    }

    @Override public void backTo(String route) {
        if ("detail".equals(activity.screen)) {
            if (activity.readingTimeTracker != null) {
                activity.readingTimeTracker.pause();
                activity.updateReadingTimeEntry();
            }
            if (activity.detailLoader != null) activity.detailLoader.cancel();
            activity.detailPager = null;
            activity.detailScroll = null;
            activity.detailCommentScroll = null;
        }
        showRoute(route);
        if (composeDetail != null) composeDetail.routeChanged();
    }

    @Override public void openDetail(FeedItem item) {
        if (item == null) return;
        activity.showDetail(item);
    }

    @Override public void requestImage(String url) {
        activity.openImage(null, url);
    }

    @Override public void requestImages(String[] urls, int index) {
        activity.openImage(null, urls, index);
    }

    @Override public void requestImages(android.widget.ImageView source, String[] urls, int index) {
        activity.openImage(source, urls, index);
    }

    private boolean isComposeSurfaceVisible() {
        return activity.composeLayer != null
                && activity.composeLayer.getVisibility() == View.VISIBLE;
    }

    @Override public void requestVideo(VideoData video) {
        activity.openVideo(video);
    }

    @Override public void feedAction(FeedItem item, int action) {
        if (item == null || activity.postActions == null) return;
        if (action == ComposeAppCallbacks.ACTION_LIKE) {
            activity.postActions.toggleFeedLike(item);
        } else if (action == ComposeAppCallbacks.ACTION_FOLLOW) {
            boolean following = !item.following;
            activity.postActions.toggleFeedFollow(item, following, success -> {
                if (success) item.following = following;
                if (activity.composeAppHost != null) activity.composeAppHost.invalidateFeed();
            });
        } else if (action == ComposeAppCallbacks.ACTION_FAVORITE) {
            activity.postActions.toggleFeedFavorite(item);
        } else if (action == ComposeAppCallbacks.ACTION_CACHE) {
            activity.postActions.toggleFeedCache(item);
            ComposeDetailContext detail = activity.composeDetailContext();
            if (activity.localCache.isWatchLater(item.id) && detail != null && detail.getBody() != null
                    && item.id.equals(detail.getItem().id)) {
                activity.detailActionBar.refreshOffline(item, detail.getBody());
            }
        } else if (action == ComposeAppCallbacks.ACTION_COMMENT) {
            openDetail(item);
        }
        if (activity.composeAppHost != null) activity.composeAppHost.invalidateFeed();
    }

    @Override public void likeComment(JSONObject comment) {
        if (activity.composeAppHost != null) activity.composeAppHost.likeDetailComment(comment);
    }

    @Override public void replyComment(JSONObject comment) {
        if (activity.composeAppHost != null) activity.composeAppHost.editDetailComment(comment, "");
    }

    @Override public void loadReplies(JSONObject comment) {
        if (comment != null && activity.composeAppHost != null) {
            activity.composeAppHost.loadDetailReplies(comment);
        }
    }

    @Override public void writeComment(FeedItem item) {
        if (activity.composeAppHost != null) activity.composeAppHost.editDetailComment(null, "");
    }

    @Override public void setCommentLike(String id, boolean liked, ApiClient.Callback callback) {
        commentRequests.like(id, liked, callback);
    }

    @Override public void requestReplies(String id, String cursor, ApiClient.Callback callback) {
        commentRequests.replies(id, cursor, callback);
    }

    @Override public void createComment(String text, String rootId, String replyId, ApiClient.Callback callback) {
        commentRequests.send(text, rootId, replyId, callback);
    }

    @Override public void loginCompleted() {
        activity.invalidateNativeSessionContent();
        activity.toast("登录成功");
        if (activity.composeAppHost != null) activity.composeAppHost.invalidateProfile();
        if (activity.api != null) EmojiStore.load(activity.api, () -> { });
        activity.showTopLevel(0);
        PresenceReporter.pingNow(activity.session, activity.readingTimeTracker,
                activity::applyAccessStatus);
        RemoteConfig.load(activity.session,
                () -> activity.applyAccessStatus(RemoteConfig.accessStatus()));
    }

    @Override public void settingsChanged() {
        activity.applyPalette();
        if (activity.composeAppHost != null) {
            activity.composeAppHost.updateTheme(activity.themeTokens,
                    activity.usesRoundLayout(), activity.session.uiScale() / 100.0f,
                    activity.session.textScale() / 100.0f);
        }
        if (activity.bottomNavigation != null) {
            activity.bottomNavigation.applyTheme(activity.themeTokens,
                    activity.usesRoundLayout());
        }
        syncChrome(null);
    }

    @Override public void runSettingsAction(String action) {
        String key = action == null ? "" : action.split("\\?", 2)[0];
        if ("login".equals(key)) {
            activity.showLogin();
        } else if ("logout".equals(key)) {
            activity.session.clearSession();
            activity.invalidateNativeSessionContent();
            activity.toast("已退出登录");
            if (activity.composeAppHost != null) activity.composeAppHost.invalidateProfile();
            showRoute("profile");
        } else if ("cache_prune".equals(key)) {
            activity.cacheMaintenance.pruneOffline(() -> activity.toast("已清理过期离线内容"));
        } else if ("cache_clear".equals(key)) {
            activity.cacheMaintenance.clearTemporaryCache(
                    bytes -> activity.toast("已清除缓存 " + Format.cacheMb(bytes)));
        } else if ("diagnostics_export".equals(key)) {
            activity.exportDiagnostics();
        } else if ("diagnostics_upload".equals(key)) {
            activity.uploadDiagnostics();
        } else if ("crash_test".equals(key)) {
            CrashTestController.confirm(activity, activity.session, activity.themeTokens);
        } else if ("open_url".equals(key)) {
            activity.openUrl(Uri.parse(action).getQueryParameter("url"));
        } else if ("update_download".equals(key)) {
            activity.openUpdateUrl(Uri.parse(action).getQueryParameter("url"));
        }
    }

    private void syncChrome(String route) {
        if (activity.composeAppHost == null || !activity.composeAppHost.isMounted()) return;
        String key = route == null ? activity.composeAppHost.currentRoute()
                : route.split("\\?", 2)[0];
        activity.screen = key;
        boolean topLevel = "feed".equals(key) || "profile".equals(key);
        activity.shellBar.setVisibility(View.GONE);
        activity.content.setVisibility(View.INVISIBLE);
        activity.composeLayer.setVisibility(View.VISIBLE);
        activity.setBottomNavVisible(topLevel, false);
        if (topLevel) activity.bottomNavigation.select(key);
        if (composeDetail != null) composeDetail.routeChanged();
    }

    void saveDetailProgress() {
        if (activity.composeAppHost != null && isComposeSurfaceVisible()) {
            activity.composeAppHost.saveDetailProgress();
        }
    }

    void close() {
        if (composeDetail != null) composeDetail.close();
    }
}
