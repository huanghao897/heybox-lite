package com.ronan.heyboxlite;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;

import java.util.List;

final class MainContentFeatures {
    interface Host {
        String screen();
        String currentLinkId();
        String currentLinkHsrc();
        FeedItem currentDetailItem();
        List<FeedItem> searchItems();
        void prepareReadingCenter();
        void prepareSavedPage(String title);
        void prepareProfile();
        void prepareLogin();
        void prepareFeed();
        void showContent(View view);
        void showPage(View view);
        void retainPage(String key, View view);
        void showLoading();
        void showProfileLoading();
        void hideLoading();
        void showMessage(String message);
        void showToast(String message);
        void showProfile();
        void showLogin();
        void showFeed();
        void showSearch();
        void showReadingCenter();
        void showReadingStats();
        void showFavorites();
        void showCheckinCenter();
        void showLeaderboard();
        void showSettings();
        void showDetail(FeedItem item);
        void showUserSpace(String userId, String name, String avatar);
        void openImage(android.widget.ImageView source, String url);
        void openImages(android.widget.ImageView source, String[] urls, int index);
        void addBottomSpace(LinearLayout page);
        void loginCompleted();
        void feedChanged();
        void setFeedRefreshBusy(boolean busy);
        void markFeedLateralTransition();
        FeedAdapter createFeedAdapter(List<FeedItem> items);
        String readingSummary();
        boolean savedScreenActive();
        int pageHorizontalPadding();
        int pageTopPadding();
        int subpageTopPadding();
        int roundHeaderInset();
        int roundHeaderTopPadding();
        int roundSearchInset();
    }

    final DetailContentRenderer detailContentRenderer;
    final UserSpacePage userSpacePage;
    final SavedContentController savedContentController;
    final ProfilePage profilePage;
    final QrLoginPage qrLoginPage;
    final PostActionController postActions;
    final FeedPage feedPage;

    MainContentFeatures(Activity activity, SessionStore session, ApiClient api,
                        WriteActionClient writeActions, LocalCache cache,
                        ThemeTokens tokens, SearchBarController searchBars,
                        SettingsUi settingsUi, CheckinCenterCoordinator checkinCoordinator,
                        Handler handler, Host host) {
        boolean roundLayout = session.usesRoundLayout();
        detailContentRenderer = new DetailContentRenderer(activity, session, tokens,
                roundLayout, host::openImages);
        userSpacePage = new UserSpacePage(activity, session, api, cache, tokens,
                new UserSpacePage.Host() {
                    @Override public boolean isActive() {
                        return "user_space".equals(host.screen());
                    }
                    @Override public void openPost(FeedItem item) { host.showDetail(item); }
                });
        savedContentController = new SavedContentController(activity, session, api, cache,
                searchBars, settingsUi, userSpacePage, tokens, roundLayout,
                new SavedContentController.Host() {
                    @Override public void prepareReadingCenter() {
                        host.prepareReadingCenter();
                    }
                    @Override public boolean isReadingCenterScreen() {
                        return "reading_center".equals(host.screen());
                    }
                    @Override public void prepareSavedPage(String title) {
                        host.prepareSavedPage(title);
                    }
                    @Override public void showContent(View view) { host.showContent(view); }
                    @Override public void retainPage(String key, View view) {
                        host.retainPage(key, view);
                    }
                    @Override public void showLoading() { host.showLoading(); }
                    @Override public void hideLoading() { host.hideLoading(); }
                    @Override public void showMessage(String message) {
                        host.showMessage(message);
                    }
                    @Override public void showToast(String message) {
                        host.showToast(message);
                    }
                    @Override public void showProfile() { host.showProfile(); }
                    @Override public void showLogin() { host.showLogin(); }
                    @Override public void showReadingStats() { host.showReadingStats(); }
                    @Override public void showDetail(FeedItem item) { host.showDetail(item); }
                    @Override public FeedAdapter createFeedAdapter(List<FeedItem> items) {
                        return host.createFeedAdapter(items);
                    }
                    @Override public void addBottomNavSafeSpace(LinearLayout page) {
                        host.addBottomSpace(page);
                    }
                    @Override public String readingSummary() {
                        return host.readingSummary();
                    }
                    @Override public boolean isSavedScreen() {
                        return host.savedScreenActive();
                    }
                    @Override public int pageHorizontalPadding() {
                        return host.pageHorizontalPadding();
                    }
                    @Override public int subpageTopPadding() {
                        return host.subpageTopPadding();
                    }
                    @Override public int roundSearchInset() {
                        return host.roundSearchInset();
                    }
                });
        profilePage = new ProfilePage(activity, session, api, cache, settingsUi, tokens,
                checkinCoordinator, roundLayout, new ProfilePage.Host() {
                    @Override public void prepareProfileChrome() { host.prepareProfile(); }
                    @Override public boolean isProfileActive() {
                        return "profile".equals(host.screen());
                    }
                    @Override public void showPage(View page) { host.showPage(page); }
                    @Override public void showLoading() { host.showProfileLoading(); }
                    @Override public void hideLoading() { host.hideLoading(); }
                    @Override public void showLogin() { host.showLogin(); }
                    @Override public void showReadingCenter() { host.showReadingCenter(); }
                    @Override public void showFavorites() { host.showFavorites(); }
                    @Override public void showCheckinCenter() { host.showCheckinCenter(); }
                    @Override public void showLeaderboard() { host.showLeaderboard(); }
                    @Override public void showSettings() { host.showSettings(); }
                    @Override public void showUserSpace(String id, String name, String avatar) {
                        host.showUserSpace(id, name, avatar);
                    }
                    @Override public void showToast(String message) {
                        host.showToast(message);
                    }
                    @Override public void addBottomSafeSpace(LinearLayout page) {
                        host.addBottomSpace(page);
                    }
                    @Override public String readingSummary() {
                        return host.readingSummary();
                    }
                    @Override public int pageHorizontalPadding() {
                        return host.pageHorizontalPadding();
                    }
                    @Override public int pageTopPadding() { return host.pageTopPadding(); }
                    @Override public int roundHeaderInset() {
                        return host.roundHeaderInset();
                    }
                });
        qrLoginPage = new QrLoginPage(activity, session, api, handler, tokens, roundLayout,
                new QrLoginPage.Host() {
                    @Override public void prepareLoginChrome() { host.prepareLogin(); }
                    @Override public void showPage(View page) { host.showContent(page); }
                    @Override public void onLoginComplete() { host.loginCompleted(); }
                    @Override public void showFeed() { host.showFeed(); }
                    @Override public int pageHorizontalPadding() {
                        return host.pageHorizontalPadding();
                    }
                    @Override public int pageTopPadding() { return host.pageTopPadding(); }
                });
        postActions = new PostActionController(activity, session, writeActions, cache,
                tokens, roundLayout, new PostActionController.Host() {
                    @Override public String currentLinkId() { return host.currentLinkId(); }
                    @Override public String currentLinkHsrc() { return host.currentLinkHsrc(); }
                    @Override public FeedItem currentDetailItem() {
                        return host.currentDetailItem();
                    }
                    @Override public List<FeedItem> feedItems() { return feedPage.items(); }
                    @Override public List<FeedItem> searchItems() { return host.searchItems(); }
                    @Override public void feedChanged() { host.feedChanged(); }
                    @Override public void showToast(String message) {
                        host.showToast(message);
                    }
                });
        feedPage = new FeedPage(activity, session, api, cache, tokens, postActions,
                roundLayout, new FeedPage.Host() {
                    @Override public void prepareFeedChrome() {
                        host.markFeedLateralTransition();
                        host.prepareFeed();
                    }
                    @Override public boolean isFeedActive() {
                        return "feed".equals(host.screen());
                    }
                    @Override public void showPage(View page) { host.showPage(page); }
                    @Override public void showSearch() { host.showSearch(); }
                    @Override public void openDetail(FeedItem item) { host.showDetail(item); }
                    @Override public void showLoading() { host.showLoading(); }
                    @Override public void hideLoading() { host.hideLoading(); }
                    @Override public void showMessage(String message) {
                        host.showMessage(message);
                    }
                    @Override public void showToast(String message) {
                        host.showToast(message);
                    }
                    @Override public void setRefreshBusy(boolean busy) {
                        host.setFeedRefreshBusy(busy);
                    }
                    @Override public int pageTopPadding() { return host.pageTopPadding(); }
                    @Override public int roundHeaderTopPadding() {
                        return host.roundHeaderTopPadding();
                    }
                    @Override public int roundHeaderInset() {
                        return host.roundHeaderInset();
                    }
                    @Override public int roundCardInset() {
                        return host.pageHorizontalPadding();
                    }
                    @Override public int roundSearchInset() {
                        return host.roundSearchInset();
                    }
                });
    }
}
