package com.ronan.heyboxlite;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reuses detail loading and parsing services without constructing the legacy UI tree. */
final class ComposeDetailRuntime implements AutoCloseable {
    private final MainActivity activity;
    private final DetailLoadCoordinator loader;
    private final ExecutorService parser = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "heybox-compose-detail");
        thread.setDaemon(true);
        return thread;
    });
    private long boundEntry = -1;
    private String boundAccount = "";
    private boolean networkFinished;
    private boolean parseInFlight;
    private volatile int generation;
    private volatile boolean closed;

    ComposeDetailRuntime(MainActivity activity) {
        this.activity = activity;
        loader = new DetailLoadCoordinator(activity, activity.api, activity.localCache,
                activity.handler, new DetailLoadCoordinator.Host() {
            @Override public boolean active(FeedItem item) {
                if (!current()) return false;
                FeedItem active = activity.composeAppHost.currentDetailItem();
                return active != null && item.id.equals(active.id);
            }
            @Override public void hideLoading() { }
            @Override public void requestFinished() {
                if (!current()) return;
                networkFinished = true;
                finishRequestIfPublished();
            }
            @Override public void renderDetail(JSONObject body, FeedItem fallback) {
                parse(body, fallback);
            }
            @Override public void refreshOffline(FeedItem item, JSONObject body) {
                activity.detailActionBar.refreshOffline(item, body);
            }
            @Override public void showMessage(String message) {
                if (current()) activity.composeAppHost.showDetailError(message);
            }
            @Override public void toast(String message) {
                if (current()) {
                    activity.composeAppHost.finishDetailLoading();
                    activity.toast(message);
                }
            }
        });
    }

    void open(FeedItem item) {
        if (closed || item == null || activity.composeAppHost == null) return;
        activity.saveCurrentDetailProgress();
        if (activity.readingTimeTracker != null) activity.readingTimeTracker.pause();
        if (activity.detailLoader != null) activity.detailLoader.cancel();
        activity.composeAppHost.showDetailLoading(item);
        activate();
    }

    void routeChanged() {
        if (closed) return;
        if (activity.composeAppHost == null || !activity.composeAppHost.isRoute("detail")) {
            if (boundEntry != -1 && activity.readingTimeTracker != null) activity.readingTimeTracker.pause();
            generation++;
            loader.cancel();
            boundEntry = -1;
            return;
        }
        if (boundEntry != activity.composeAppHost.currentDetailEntryKey()) activate();
    }

    private void activate() {
        generation++;
        loader.cancel();
        networkFinished = false;
        parseInFlight = false;
        boundEntry = activity.composeAppHost.currentDetailEntryKey();
        boundAccount = activity.session.userId();
        FeedItem item = activity.composeAppHost.currentDetailItem();
        if (item == null) return;
        activity.screen = "detail";
        activity.detailPager = null;
        activity.detailScroll = null;
        activity.detailCommentScroll = null;
        activity.localCache.rememberRecent(item);
        if (activity.composeAppHost.detailNeedsLoad()) loader.load(item);
        else resumeReading(item);
    }

    private void parse(JSONObject source, FeedItem fallback) {
        if (!current()) return;
        int request = ++generation;
        boolean playGif = activity.session.playGif();
        // The loader may merge a later web response into the same JSON tree.
        String snapshot = source.toString();
        parseInFlight = true;
        parser.execute(() -> {
            if (closed || generation != request) return;
            try {
                ComposeDetailDocument value = ComposeDetailDocument.parse(new JSONObject(snapshot), fallback, playGif);
                activity.handler.post(() -> {
                    if (!current() || generation != request) return;
                    PostActionController.LikeState likes = activity.postActions.linkLikeState(value.getLink(), fallback);
                    value.getItem().liked = likes.liked;
                    value.getItem().likes = likes.likes;
                    activity.composeAppHost.showDetailDocument(value);
                    loader.markRendered();
                    parseInFlight = false;
                    finishRequestIfPublished();
                    resumeReading(value.getItem());
                });
            } catch (org.json.JSONException | RuntimeException error) {
                activity.handler.post(() -> {
                    if (!current() || generation != request) return;
                    parseInFlight = false;
                    activity.localCache.log("compose detail parse failed type=" + error.getClass().getSimpleName());
                    activity.composeAppHost.showDetailError("详情内容暂时无法显示，请重试");
                    finishRequestIfPublished();
                });
            }
        });
    }

    private void finishRequestIfPublished() {
        // An HTTP response can finish before delayed rendering and background parsing.
        if (current() && networkFinished && !parseInFlight && !loader.hasPendingRender()) {
            activity.composeAppHost.finishDetailRequest();
        }
    }

    private boolean current() {
        return !closed && activity.composeAppHost != null && activity.composeAppHost.isMounted()
                && activity.composeAppHost.isRoute("detail")
                && boundAccount.equals(activity.session.userId())
                && boundEntry == activity.composeAppHost.currentDetailEntryKey();
    }

    private void resumeReading(FeedItem item) {
        if (activity.activityResumed && activity.readingTimeTracker != null) {
            activity.readingTimeTracker.start(item.article, item.id);
            activity.readingTimeTracker.tagTopic(item.topicName);
        }
    }

    @Override public void close() {
        closed = true;
        generation++;
        loader.close();
        parser.shutdownNow();
    }
}
