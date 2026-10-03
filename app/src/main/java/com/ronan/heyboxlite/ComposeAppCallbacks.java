package com.ronan.heyboxlite;

import org.json.JSONObject;

/**
 * Small Java bridge for the Compose shell.  Network, login, cache and write
 * operations stay in their existing controllers; Compose only emits intent.
 */
interface ComposeAppCallbacks {
    int ACTION_LIKE = 1;
    int ACTION_FAVORITE = 2;
    int ACTION_CACHE = 3;
    int ACTION_FOLLOW = 4;
    int ACTION_COMMENT = 5;

    void navigate(String route);

    void back();

    void backTo(String route);

    void openDetail(FeedItem item);

    void requestImage(String url);

    void requestVideo(VideoData video);

    void feedAction(FeedItem item, int action);

    void likeComment(JSONObject comment);

    void replyComment(JSONObject comment);

    void loadReplies(JSONObject comment);

    void writeComment(FeedItem item);

    void loginCompleted();

    void settingsChanged();

    void runSettingsAction(String action);
}
