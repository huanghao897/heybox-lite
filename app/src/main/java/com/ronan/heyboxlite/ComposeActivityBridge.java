package com.ronan.heyboxlite;

import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONObject;

/** Route and action adapter kept outside MainActivity so the Compose shell does not grow it again. */
final class ComposeActivityBridge implements ComposeAppCallbacks {
    private final MainActivity activity;
    private boolean openingLegacyDetail;
    private boolean legacyDetailActive;
    private String legacyReturnRoute = "feed";

    ComposeActivityBridge(MainActivity activity) {
        this.activity = activity;
    }

    void mount(FrameLayout body) {
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
                activity.themeTokens, activity.usesRoundLayout(), body,
                activity::runWithPressFeedback);
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
        return showRouteIfMounted("user_space?user=" + Uri.encode(userId)
                + "&name=" + Uri.encode(name == null ? "" : name)
                + "&avatar=" + Uri.encode(avatar == null ? "" : avatar));
    }

    boolean handleBack() {
        if (legacyDetailActive) {
            returnFromLegacyDetail();
            return true;
        }
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
        // The native detail surface still owns the complete comment/media pipeline.
        // Keep Compose from stealing the async result until its renderer reaches parity.
        if (openingLegacyDetail || legacyDetailActive) return false;
        if (activity.composeAppHost == null || !activity.composeAppHost.isMounted()) return false;
        activity.pendingBackTransition = false;
        activity.pageTransitions.finishNow();
        activity.saveCurrentDetailProgress();
        activity.stopQrPolling();
        activity.ensureEmojiCatalog(() -> { });
        if ("feed".equals(activity.screen)) activity.feedPage.saveScroll();
        if ("search".equals(activity.screen)) activity.searchPage.saveListPosition();
        if (!"detail".equals(activity.screen)) {
            activity.detailReturn = activity.screen;
            activity.detailReturnTitle = "";
        }
        activity.screen = "detail";
        activity.currentLinkId = item.id;
        activity.currentLinkHsrc = item.hsrc;
        activity.currentAuthCode = "";
        activity.currentDetailItem = item;
        activity.currentDetailBody = null;
        activity.commentController.reset();
        activity.localCache.rememberRecent(item);
        activity.composeAppHost.showDetailLoading(item);
        activity.detailLoader.load(item);
        if (activity.activityResumed && activity.readingTimeTracker != null) {
            activity.readingTimeTracker.start(item.article, item.id);
        }
        return true;
    }

    boolean showComposeDetailResult(DetailPageAssembler.Result detail, FeedItem fallback) {
        if (legacyDetailActive) return false;
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
        String key = route == null ? "feed" : route.split("\\?", 2)[0];
        if (!"detail".equals(key)) legacyDetailActive = false;
        activity.screen = key;
        activity.shellBar.setVisibility(View.GONE);
        activity.content.setVisibility(View.INVISIBLE);
        activity.composeLayer.setVisibility(View.VISIBLE);
        boolean topLevel = "feed".equals(key) || "profile".equals(key);
        activity.setBottomNavVisible(topLevel, false);
        if (topLevel) activity.bottomNavigation.select(key);
        activity.composeAppHost.setRoute(route == null ? "feed" : route);
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
            activity.currentDetailBody = null;
        }
        showRoute(route);
    }

    @Override public void openDetail(FeedItem item) {
        if (item == null) return;
        // Compose cards remain the entry point, but comments, emoji, thumbnails,
        // original-image opening and reply actions use the stable native detail host.
        legacyDetailActive = true;
        legacyReturnRoute = activity.composeAppHost != null
                ? activity.composeAppHost.currentRoute() : activity.screen;
        openingLegacyDetail = true;
        if (activity.composeLayer != null) activity.composeLayer.setVisibility(View.INVISIBLE);
        if (activity.content != null) activity.content.setVisibility(View.VISIBLE);
        try {
            activity.showDetail(item);
            activity.leading.setOnClickListener(view -> returnFromLegacyDetail());
        } finally {
            openingLegacyDetail = false;
        }
    }

    @Override public void requestImage(String url) {
        activity.openImage(null, url);
    }

    private void returnFromLegacyDetail() {
        String route = legacyReturnRoute;
        activity.returnFromDetail();
        if (route != null && !route.isEmpty()) showRoute(route);
    }

    @Override public void requestVideo(VideoData video) {
        activity.openVideo(video);
    }

    @Override public void feedAction(FeedItem item, int action) {
        if (item == null || activity.postActions == null) return;
        if (action == ComposeAppCallbacks.ACTION_LIKE) {
            activity.postActions.toggleFeedLike(item);
        } else if (action == ComposeAppCallbacks.ACTION_FOLLOW) {
            activity.postActions.toggleFeedFollow(item, !item.following, success -> { });
        } else if (action == ComposeAppCallbacks.ACTION_FAVORITE) {
            activity.postActions.toggleFeedFavorite(item);
        } else if (action == ComposeAppCallbacks.ACTION_CACHE) {
            activity.postActions.toggleFeedCache(item);
        } else if (action == ComposeAppCallbacks.ACTION_COMMENT) {
            openDetail(item);
        }
        if (activity.composeAppHost != null) activity.composeAppHost.invalidateFeed();
    }

    @Override public void likeComment(JSONObject comment) {
        if (activity.commentController == null || activity.commentRenderer == null) return;
        activity.commentController.toggleLike(comment,
                activity.commentRenderer.createLikeControl());
    }

    @Override public void replyComment(JSONObject comment) {
        if (activity.commentController != null) activity.commentController.reply(comment);
    }

    @Override public void loadReplies(JSONObject comment) {
        if (comment == null || activity.api == null || activity.composeAppHost == null) return;
        String rootId = CommentData.commentId(comment);
        if (rootId.isEmpty()) return;
        activity.api.get(EndpointProvider.subComments(),
                OfficialRequestParams.subComments(rootId, "", activity.currentLinkHsrc),
                new ApiClient.Callback() {
                    @Override public void onSuccess(JSONObject body) {
                        activity.composeAppHost.appendDetailReplies(rootId, extractReplies(body, rootId));
                    }

                    @Override public void onError(String message) {
                        activity.toast("回复加载失败：" + message);
                    }
                });
    }

    @Override public void writeComment(FeedItem item) {
        if (activity.commentController != null) activity.commentController.showDialog(null);
    }

    @Override public void loginCompleted() {
        if (activity.feedPage != null) activity.feedPage.clearItems();
        activity.toast("登录成功");
        if (activity.api != null) EmojiStore.load(activity.api, () -> { });
        activity.showTopLevel(0);
        PresenceReporter.pingNow(activity.session, activity.readingTimeTracker, status -> { });
        RemoteConfig.load(activity.session, () -> { });
    }

    private static java.util.List<JSONObject> extractReplies(JSONObject body, String rootId) {
        java.util.ArrayList<JSONObject> values = new java.util.ArrayList<>();
        JSONObject result = body == null ? null : body.optJSONObject("result");
        org.json.JSONArray array = result == null ? null
                : Json.firstArray(result, "comments", "comment", "list", "sub_comments");
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            org.json.JSONArray nested = item.optJSONArray("comment");
            if (nested == null) {
                if (!rootId.equals(CommentData.commentId(item))) values.add(item);
            } else {
                for (int j = 0; j < nested.length(); j++) {
                    JSONObject reply = nested.optJSONObject(j);
                    if (reply != null && !rootId.equals(CommentData.commentId(reply))) values.add(reply);
                }
            }
        }
        return values;
    }

    @Override public void settingsChanged() {
        activity.applyPalette();
        if (activity.composeAppHost != null) {
            activity.composeAppHost.updateTheme(activity.themeTokens,
                    activity.usesRoundLayout(), activity.session.uiScale() / 100.0f,
                    activity.session.textScale() / 100.0f);
        }
        syncChrome(null);
    }

    @Override public void runSettingsAction(String action) {
        String key = action == null ? "" : action.split("\\?", 2)[0];
        if ("login".equals(key)) {
            activity.showLogin();
        } else if ("logout".equals(key)) {
            activity.session.clearSession();
            activity.toast("已退出登录");
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
    }
}
