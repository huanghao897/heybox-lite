package com.ronan.heyboxlite;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.json.JSONObject;

final class MainDetailFeatures {
    interface Host {
        FeedItem currentItem();
        JSONObject currentBody();
        String currentLinkId();
        String currentAuthCode();
        DetailPager currentPager();
        boolean detailActive(FeedItem item);
        int dp(int value);
        int pageHorizontalPadding();
        int subpageTopPadding();
        int roundHeaderInnerInset();
        boolean watchLayout();
        boolean roundLayout();
        void reloadDetail();
        void showUserSpace(String userId, String name, String avatar);
        void openImage(ImageView source, String url);
        void hideLoading();
        void renderDetail(JSONObject body, FeedItem fallback);
        void showMessage(String message);
        void showToast(String message);
        LinearLayout articleSurface();
        View detailReturnPreview();
        View detailBackButton();
    }

    final DetailHeaderRenderer headerRenderer;
    final CommentController commentController;
    final CommentRenderer commentRenderer;
    final DetailCommentsSection commentsSection;
    final DetailActionBar actionBar;
    final DetailLoadCoordinator loader;
    final DetailPageAssembler pageAssembler;

    MainDetailFeatures(Activity activity, SessionStore session, ApiClient api,
                       WriteActionClient writeActions, LocalCache cache,
                       ThemeTokens tokens, PostActionController postActions,
                       DetailContentRenderer contentRenderer,
                       ReadingTimeTracker readingTimeTracker, Handler handler, Host host) {
        boolean roundLayout = host.roundLayout();
        headerRenderer = new DetailHeaderRenderer(activity, session, tokens, postActions,
                roundLayout, host::showUserSpace);
        commentController = new CommentController(activity, session, api, writeActions,
                cache, tokens, roundLayout, new CommentController.PageHost() {
                    @Override public FeedItem currentItem() { return host.currentItem(); }
                    @Override public JSONObject currentBody() { return host.currentBody(); }
                    @Override public String currentLinkId() { return host.currentLinkId(); }
                    @Override public String currentHsrc() {
                        return postActions.hsrcFor(host.currentItem());
                    }
                    @Override public String currentAuthCode() {
                        return host.currentAuthCode();
                    }
                    @Override public boolean requireLogin(String actionName) {
                        return postActions.requireLogin(actionName);
                    }
                    @Override public boolean allowWriteAction(String actionName) {
                        return postActions.allowWriteAction(actionName);
                    }
                    @Override public String writeErrorMessage(String actionName,
                                                              String message) {
                        return postActions.writeErrorMessage(actionName, message);
                    }
                    @Override public void reloadDetail() { host.reloadDetail(); }
                    @Override public void showToast(String message) {
                        host.showToast(message);
                    }
                    @Override public void openImage(ImageView source, String url) {
                        host.openImage(source, url);
                    }
                });
        commentRenderer = commentController.renderer();
        commentsSection = new DetailCommentsSection(activity, session, tokens,
                commentRenderer, handler, pager -> host.currentPager() == pager);
        actionBar = new DetailActionBar(activity, session, cache, tokens, postActions,
                commentController, commentRenderer, contentRenderer, roundLayout,
                new DetailActionBar.Host() {
                    @Override public JSONObject currentDetailBody() {
                        return host.currentBody();
                    }
                    @Override public void showToast(String message) {
                        host.showToast(message);
                    }
                });
        loader = new DetailLoadCoordinator(activity, api, cache, handler,
                new DetailLoadCoordinator.Host() {
                    @Override public boolean active(FeedItem item) {
                        return host.detailActive(item);
                    }
                    @Override public void hideLoading() { host.hideLoading(); }
                    @Override public void renderDetail(JSONObject body, FeedItem fallback) {
                        host.renderDetail(body, fallback);
                    }
                    @Override public void refreshOffline(FeedItem item, JSONObject body) {
                        actionBar.refreshOffline(item, body);
                    }
                    @Override public void showMessage(String message) {
                        host.showMessage(message);
                    }
                    @Override public void toast(String message) { host.showToast(message); }
                });
        pageAssembler = new DetailPageAssembler(activity, session, tokens, cache,
                postActions, headerRenderer, contentRenderer, actionBar, commentsSection,
                readingTimeTracker, new DetailPageAssembler.LayoutHost() {
                    @Override public int dp(int value) { return host.dp(value); }
                    @Override public boolean watchLayout() { return host.watchLayout(); }
                    @Override public boolean roundLayout() { return host.roundLayout(); }
                    @Override public int pageHorizontalPadding() {
                        return host.pageHorizontalPadding();
                    }
                    @Override public int subpageTopPadding() {
                        return host.subpageTopPadding();
                    }
                    @Override public int roundHeaderInnerInset() {
                        return host.roundHeaderInnerInset();
                    }
                    @Override public LinearLayout articleSurface() {
                        return host.articleSurface();
                    }
                    @Override public View returnPreview() {
                        return host.detailReturnPreview();
                    }
                    @Override public View backButton() { return host.detailBackButton(); }
                });
    }
}
