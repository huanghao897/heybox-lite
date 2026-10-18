package com.ronan.heyboxlite;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.List;

/** Binds native content features to the shell without owning Compose or native route state. */
final class MainContentHost implements MainContentFeatures.Host {
    private final MainActivity activity;

    MainContentHost(MainActivity activity) { this.activity = activity; }

    @Override public String screen() { return activity.screen; }
    @Override public String currentLinkId() {
        ComposeDetailContext detail = activity.composeDetailContext();
        return detail == null ? activity.currentLinkId : detail.getItem().id;
    }
    @Override public String currentLinkHsrc() {
        ComposeDetailContext detail = activity.composeDetailContext();
        return detail == null ? activity.currentLinkHsrc : detail.getHsrc();
    }
    @Override public FeedItem currentDetailItem() {
        ComposeDetailContext detail = activity.composeDetailContext();
        return detail == null ? activity.currentDetailItem : detail.getItem();
    }
    @Override public List<FeedItem> searchItems() { return activity.searchPage.items(); }
    @Override public void prepareReadingCenter() { activity.prepareReadingCenterChrome(); }
    @Override public void prepareSavedPage(String title) { activity.prepareSavedPageChrome(title); }

    @Override public void prepareProfile() {
        activity.activate("profile");
        activity.title.setText(R.string.title_profile);
        activity.action.setVisibility(View.INVISIBLE);
    }

    @Override public void prepareLogin() {
        activity.screen = "login";
        activity.shellBar.setVisibility(View.GONE);
        activity.setBottomNavVisible(false);
        activity.leading.setVisibility(View.INVISIBLE);
        activity.action.setVisibility(View.INVISIBLE);
        activity.title.setText(R.string.title_qr_login);
    }

    @Override public void prepareFeed() {
        activity.activate("feed");
        activity.title.setText(R.string.title_feed);
        activity.action.setText("");
        activity.setIcon(activity.action, R.drawable.ic_refresh, activity.TEXT, 19);
        activity.action.setVisibility(View.INVISIBLE);
        activity.action.setOnClickListener(view -> activity.feedPage.load(true));
    }

    @Override public void showContent(View view) {
        activity.content.removeAllViews();
        activity.content.addView(view, activity.match());
    }
    @Override public void showPage(View view) { activity.transitionTo(view); }
    @Override public void retainPage(String key, View view) { activity.retainedPages.put(key, view); }
    @Override public void showLoading() { activity.showLoading(); }
    @Override public void hideLoading() { activity.hideLoading(); }
    @Override public void showMessage(String message) { activity.showMessage(message); }
    @Override public void showToast(String message) { activity.toast(message); }
    @Override public void showProfile() { activity.showProfile(); }
    @Override public void showLogin() { activity.showLogin(); }
    @Override public void showFeed() { activity.showFeed(); }
    @Override public void showSearch() { activity.showSearch(); }
    @Override public void showReadingCenter() { activity.showReadingCenter(); }
    @Override public void showReadingStats() { activity.showReadingStats(); }
    @Override public void showFavorites() { activity.showFavorites(); }
    @Override public void showCheckinCenter() { activity.showCheckinCenter(); }
    @Override public void showLeaderboard() { activity.showLeaderboard(); }
    @Override public void showSettings() { activity.showSettingsHome(); }
    @Override public void showDetail(FeedItem item) { activity.showDetail(item); }
    @Override public void showUserSpace(String id, String name, String avatar) {
        activity.showUserSpace(id, name, avatar);
    }
    @Override public void openImages(ImageView source, String[] urls, int index) {
        activity.openImage(source, urls, index);
    }
    @Override public void addBottomSpace(LinearLayout page) { activity.addBottomNavSafeSpace(page); }

    @Override public void loginCompleted() {
        activity.invalidateNativeSessionContent();
        if (activity.composeAppHost != null) activity.composeAppHost.invalidateProfile();
        activity.toast("登录成功");
        EmojiStore.load(activity.api, () -> { });
        activity.showFeed();
        PresenceReporter.pingNow(activity.session, activity.readingTimeTracker, activity::applyAccessStatus);
        RemoteConfig.load(activity.session, () -> activity.applyAccessStatus(RemoteConfig.accessStatus()));
    }

    @Override public void feedChanged() {
        activity.feedPage.notifyItemsChanged();
        if (activity.composeAppHost != null) activity.composeAppHost.invalidateFeed();
    }
    @Override public void setFeedRefreshBusy(boolean busy) {
        activity.action.setEnabled(!busy);
        activity.action.setAlpha(busy ? 0.45f : 1f);
    }
    @Override public void markFeedLateralTransition() {
        if ("profile".equals(activity.screen)) {
            activity.pendingBackTransition = true;
            activity.pendingLateralPush = true;
        }
    }
    @Override public FeedAdapter createFeedAdapter(List<FeedItem> items) {
        return activity.createFeedAdapter(items);
    }
    @Override public String readingSummary() { return activity.readingEntrySummary(); }
    @Override public boolean savedScreenActive() { return "saved".equals(activity.screen); }
    @Override public int pageHorizontalPadding() { return activity.pageHorizontalPadding(); }
    @Override public int pageTopPadding() { return activity.pageTopPadding(); }
    @Override public int subpageTopPadding() { return activity.subpageTopPadding(); }
    @Override public int roundHeaderInset() {
        return activity.roundHorizontalInset(RoundLayoutMetrics.HEADER_HORIZONTAL_RATIO, 10);
    }
    @Override public int roundHeaderTopPadding() { return activity.roundHeaderTopPadding(); }
    @Override public int roundSearchInset() {
        return activity.roundHorizontalInset(RoundLayoutMetrics.SEARCH_HORIZONTAL_RATIO, 6);
    }
}
