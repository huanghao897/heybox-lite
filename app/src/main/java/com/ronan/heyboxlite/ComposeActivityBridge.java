package com.ronan.heyboxlite;

import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
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

    boolean isLegacyDetailActive() { return legacyDetailActive; }

    void clearLegacyDetailOwnership() { legacyDetailActive = false; }

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
        if (legacyDetailActive && "detail".equals(activity.screen)) {
            if (activity.detailPager != null && activity.detailPager.showingComments()) {
                activity.detailPager.showArticle(true);
                return true;
            }
            returnFromLegacyDetail();
            return true;
        }
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
        // The native detail surface owns the complete content, comment, emoji and
        // image pipeline. Prepare that surface for every caller, including the
        // legacy reading-center and favorites callbacks, but let MainActivity run
        // its normal detail setup exactly once.
        if (openingLegacyDetail || activity.composeAppHost == null
                || !activity.composeAppHost.isMounted()) return false;
        if (!isComposeSurfaceVisible()) {
            // A post opened from a native user page/detail page must remain in
            // the native history. Clear the root marker so its back gesture
            // returns to that native page instead of skipping it.
            legacyDetailActive = false;
            return false;
        }
        if (!legacyDetailActive) {
            legacyDetailActive = true;
            legacyReturnRoute = nativeReturnRoute();
            captureReturnFallback(legacyReturnRoute);
            hideComposeSurface();
        }
        return false;
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
        activity.composeAppHost.setSurfaceActive(true);
        boolean topLevel = "feed".equals(key) || "profile".equals(key);
        activity.setBottomNavVisible(topLevel, false);
        if (topLevel) activity.bottomNavigation.select(key);
        activity.composeAppHost.setRoute(route == null ? "feed" : route);
    }

    void onNativeDetailShown() {
        if (!legacyDetailActive || activity.leading == null) return;
        activity.leading.setOnClickListener(view -> returnFromLegacyDetail());
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
        if (!legacyDetailActive && isComposeSurfaceVisible()) {
            captureReturnFallback(nativeReturnRoute());
        }
        legacyDetailActive = true;
        legacyReturnRoute = nativeReturnRoute();
        openingLegacyDetail = true;
        hideComposeSurface();
        try {
            activity.showDetail(item);
        } finally {
            openingLegacyDetail = false;
        }
    }

    @Override public void requestImage(String url) {
        activity.openImage(null, url);
    }

    private void returnFromLegacyDetail() {
        returnFromLegacyDetail(false);
    }

    void returnFromNativeDetail(boolean gestureOwned) {
        returnFromLegacyDetail(gestureOwned);
    }

    View createDetailReturnPreview() {
        return legacyDetailActive && activity.composeAppHost != null
                ? activity.composeAppHost.createReturnPreview(legacyReturnRoute) : null;
    }

    private void returnFromLegacyDetail(boolean gestureOwned) {
        String route = legacyReturnRoute;
        View handoff = gestureOwned ? createDetailReturnPreview() : null;
        ViewGroup root = handoff == null ? null : (ViewGroup) activity.findViewById(android.R.id.content);
        if (root != null) root.addView(handoff, new ViewGroup.LayoutParams(-1, -1));
        try {
            activity.returnFromDetailForCompose(gestureOwned);
            // Returning a nested native detail restored a native user page. Keep
            // that page visible; the root Compose route is restored only after the
            // user backs out of the native chain.
            if ("user_space".equals(activity.screen) && activity.composeLayer != null
                    && activity.composeLayer.getVisibility() != View.VISIBLE) return;
            boolean composeVisible = isComposeSurfaceVisible();
            if (route != null && !route.isEmpty()
                    && (!route.equals(activity.composeAppHost == null
                    ? "" : activity.composeAppHost.currentRouteSpec()) || !composeVisible)) {
                showRoute(route);
            }
        } finally {
            if (handoff != null && root != null) {
                handoff.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override public boolean onPreDraw() {
                        handoff.getViewTreeObserver().removeOnPreDrawListener(this);
                        handoff.postOnAnimation(() -> root.removeView(handoff));
                        return true;
                    }
                });
            }
        }
    }

    private boolean isComposeSurfaceVisible() {
        return activity.composeLayer != null
                && activity.composeLayer.getVisibility() == View.VISIBLE;
    }

    private String nativeReturnRoute() {
        if (activity.composeAppHost == null) return activity.screen;
        String route = activity.composeAppHost.currentRouteSpec();
        if ("detail".equals(route)) route = activity.composeAppHost.detailReturnRouteSpec();
        return route == null || route.isEmpty() ? activity.screen : route;
    }

    private void hideComposeSurface() {
        if (activity.composeAppHost != null) activity.composeAppHost.setSurfaceActive(false);
        if (activity.composeLayer != null) activity.composeLayer.setVisibility(View.INVISIBLE);
        if (activity.content != null) {
            activity.pageTransitions.finishNow();
            activity.content.removeAllViews();
            activity.content.setVisibility(View.VISIBLE);
        }
    }

    private void captureReturnFallback(String route) {
        if (activity.composeAppHost == null || !activity.composeAppHost.hasLiveReturnPreview(route)) {
            activity.captureComposeReturnSnapshot();
        }
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
        if (activity.composeAppHost != null) activity.composeAppHost.invalidateProfile();
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
    }
}
