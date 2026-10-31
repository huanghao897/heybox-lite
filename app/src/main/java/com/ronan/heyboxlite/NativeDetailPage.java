package com.ronan.heyboxlite;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.json.JSONObject;

/** Owns the document and views of the native fallback, never Compose route state. */
final class NativeDetailPage implements MainDetailFeatures.Host {
    private final MainActivity activity;
    private MainDetailFeatures features;
    DetailLoadCoordinator loader;
    FeedItem item;
    JSONObject body;
    String linkId = "";
    String hsrc = "";
    String authCode = "";
    String diagnostics = "";
    DetailPager pager;
    ScrollView articleScroll;
    ScrollView commentScroll;

    NativeDetailPage(MainActivity activity) {
        this.activity = activity;
    }

    MainDetailFeatures initialize(DetailContentRenderer renderer) {
        close();
        features = new MainDetailFeatures(activity, activity.session, activity.api,
                activity.writeActions, activity.localCache, activity.themeTokens,
                activity.postActions, renderer, activity.readingTimeTracker,
                activity.handler, this);
        loader = features.loader;
        return features;
    }

    void load(FeedItem next) {
        reset();
        linkId = next.id;
        hsrc = next.hsrc;
        item = next;
        activity.commentController.reset();
        activity.localCache.rememberRecent(next);
        activity.shellBar.setVisibility(View.GONE);
        activity.setBottomNavVisible(false);
        activity.leading.setVisibility(View.VISIBLE);
        activity.leading.setOnClickListener(view -> activity.returnFromDetail());
        activity.title.setText(R.string.title_post_body);
        activity.action.setVisibility(View.INVISIBLE);
        activity.showDetailLoadingOverlay();
        loader.load(next);
    }

    @Override public void renderDetail(JSONObject source, FeedItem fallback) {
        boolean replacing = loader.rendered();
        boolean previousComments = replacing && pager != null && pager.showingComments();
        int articleY = replacing && articleScroll != null ? articleScroll.getScrollY() : 0;
        int commentY = replacing && commentScroll != null ? commentScroll.getScrollY() : 0;
        DetailPageAssembler.Result detail = features.pageAssembler.assemble(
                source, fallback, hsrc, activity.screen, linkId, new DetailPager.Listener() {
                    @Override public boolean canSwipeBack() {
                        return "detail".equals(activity.screen);
                    }
                    @Override public void onPageChanged() { updateTitle(); }
                    @Override public void onReturn() {
                        activity.detailNavigation.returnFromDetail(true);
                    }
                });
        body = detail.body;
        hsrc = detail.hsrc;
        if (!detail.authCode.isEmpty()) authCode = detail.authCode;
        diagnostics = detail.diagnostics;
        pager = detail.pager;
        int savedY = activity.session.rememberDetailScroll()
                ? activity.localCache.scroll(linkId) : 0;
        install(detail, replacing, articleY, commentY, previousComments);
        features.commentsSection.populate(detail.articleCommentHost, detail.comments,
                detail.pager, 72L, detail.articleScroll, articleY);
        features.commentsSection.populate(detail.commentPageHost, detail.comments,
                detail.pager, 140L, detail.commentScroll, commentY);
        if (!replacing && savedY > 0) {
            DetailScrollRestorer.beforeFirstDraw(detail.articleScroll, savedY);
        }
        loader.markRendered();
        articleScroll = detail.articleScroll;
        commentScroll = detail.commentScroll;
        resumeReading();
    }

    private void install(DetailPageAssembler.Result detail, boolean replacing,
                         int articleY, int commentY, boolean commentsVisible) {
        activity.pageTransitions.finishNow();
        View returnView = activity.detailNavigation.returnView();
        if (returnView != null) detail.pager.setReturnView(returnView);
        FrameLayout root = detail.root;
        if (!replacing) {
            activity.removeViewWithTag("detail_loading");
            activity.transitionTo(root);
        } else {
            View previous = activity.content.getChildCount() == 0 ? null
                    : activity.content.getChildAt(activity.content.getChildCount() - 1);
            root.setVisibility(View.INVISIBLE);
            activity.content.addView(root, activity.match());
            root.post(() -> {
                if (pager != detail.pager || activity.isFinishing()) return;
                if (articleY > 0) detail.articleScroll.scrollTo(0, articleY);
                if (commentsVisible) detail.pager.showComments(false);
                if (commentY > 0) detail.commentScroll.scrollTo(0, commentY);
                root.setVisibility(View.VISIBLE);
                if (previous != null && previous.getParent() == activity.content) {
                    activity.content.removeView(previous);
                }
            });
        }
        root.post(() -> {
            if (pager == detail.pager && !activity.isFinishing()) {
                activity.localCache.log("perf app stage=detail-first-frame totalMs="
                        + Math.max(0L, SystemClock.elapsedRealtime() - loader.loadStartedAt())
                        + " progressiveUpdate=" + replacing);
            }
        });
    }

    View scrollTarget() {
        return pager != null && pager.showingComments() ? commentScroll : articleScroll;
    }

    boolean backToArticle() {
        if (pager == null || !pager.showingComments()) return false;
        activity.pendingBackTransition = false;
        pager.showArticle(true);
        return true;
    }

    DetailNavigationState suspend(View root, String returnScreen, View returnView,
                                  String returnTitle) {
        if (root == null || pager == null || item == null) return null;
        pager.cancelMotion();
        DetailNavigationState state = new DetailNavigationState(root, pager,
                articleScroll, commentScroll, item, returnScreen, returnView, returnTitle,
                linkId, hsrc, authCode, diagnostics, body, loader.suspend());
        clearDocument();
        return state;
    }

    void restore(DetailNavigationState state) {
        loader.restore(state.loadState);
        item = state.item;
        body = state.body;
        linkId = state.linkId;
        hsrc = state.linkHsrc;
        authCode = state.authCode;
        diagnostics = state.diagnostics;
        pager = state.pager;
        articleScroll = state.articleScroll;
        commentScroll = state.commentScroll;
    }

    View releaseForReturn(View fallback) {
        loader.cancel();
        View target = pager == null ? fallback : pager.takeReturnView();
        clearDocument();
        return target;
    }

    void saveProgress() {
        if (!"detail".equals(activity.screen) || articleScroll == null
                || linkId.isEmpty() || activity.localCache == null
                || !activity.session.rememberDetailScroll()) return;
        activity.localCache.saveScroll(linkId, articleScroll.getScrollY());
    }

    void resumeReading() {
        if (activity.activityResumed && activity.readingTimeTracker != null
                && item != null && body != null) {
            activity.readingTimeTracker.start(item.article, item.id);
        }
    }

    void reset() {
        if (loader != null) loader.cancel();
        if (pager != null) {
            pager.cancelMotion();
            pager.takeReturnView();
        }
        clearDocument();
    }

    void close() {
        reset();
        if (loader != null) loader.close();
    }

    void cancelMotion() {
        if (pager != null) pager.cancelMotion();
    }

    private void clearDocument() {
        item = null;
        body = null;
        linkId = "";
        hsrc = "";
        authCode = "";
        diagnostics = "";
        pager = null;
        articleScroll = null;
        commentScroll = null;
    }

    void updateTitle() {
        if (!"detail".equals(activity.screen) || activity.title == null || pager == null) return;
        activity.title.setText(pager.showingComments()
                ? R.string.title_comments : R.string.title_post_body);
    }

    @Override public FeedItem currentItem() { return item; }
    @Override public JSONObject currentBody() { return body; }
    @Override public String currentLinkId() { return linkId; }
    @Override public String currentAuthCode() { return authCode; }
    @Override public DetailPager currentPager() { return pager; }
    @Override public boolean detailActive(FeedItem next) {
        return "detail".equals(activity.screen) && next != null && next.id.equals(linkId);
    }
    @Override public int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density
                * activity.session.uiScale() / 100f);
    }
    @Override public int pageHorizontalPadding() { return activity.pageHorizontalPadding(); }
    @Override public int subpageTopPadding() { return activity.subpageTopPadding(); }
    @Override public int roundHeaderInnerInset() { return activity.roundHeaderInnerInset(); }
    @Override public boolean watchLayout() { return activity.usesWatchLayout(); }
    @Override public boolean roundLayout() { return activity.usesRoundLayout(); }
    @Override public void reloadDetail() {
        if (item != null) activity.showDetail(item);
    }
    @Override public void showUserSpace(String id, String name, String avatar) {
        activity.showUserSpace(id, name, avatar);
    }
    @Override public void hideLoading() { activity.hideLoading(); }
    @Override public void showMessage(String message) { activity.showMessage(message); }
    @Override public void showToast(String message) { activity.toast(message); }
    @Override public LinearLayout articleSurface() {
        LinearLayout article = new LinearLayout(activity);
        article.setOrientation(LinearLayout.VERTICAL);
        article.setBackgroundColor(activity.BG);
        int horizontal = dp(roundLayout() ? 8 : 4);
        article.setPadding(horizontal, dp(8), horizontal, dp(12));
        return article;
    }
    @Override public View detailReturnPreview() {
        return DetailReturnPreviewFactory.create(activity, activity.themeTokens,
                activity.detailNavigation.backTarget(), activity.screenSnapshots,
                activity.fullScreenSnapshots);
    }
    @Override public View detailBackButton() {
        ImageView back = new ImageView(activity);
        back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        back.setPadding(dp(9), dp(9), dp(9), dp(9));
        Drawable icon = Compat.tintedDrawable(activity, R.drawable.ic_arrow_back, activity.TEXT);
        if (icon != null) back.setImageDrawable(icon);
        Compat.setBackground(back, UiComponents.round(activity, activity.themeTokens.dockSurface(),
                18, activity.session.uiScale() / 100f));
        if (Build.VERSION.SDK_INT >= 21) back.setElevation(dp(6));
        back.setContentDescription(activity.getString(R.string.action_back));
        back.setOnClickListener(view -> activity.runWithPressFeedback(back, activity::returnFromDetail));
        return back;
    }
}
