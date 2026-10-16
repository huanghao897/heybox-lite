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

    default void requestImages(String[] urls, int index) {
        if (urls != null && index >= 0 && index < urls.length) requestImage(urls[index]);
    }

    void requestVideo(VideoData video);

    void feedAction(FeedItem item, int action);

    void likeComment(JSONObject comment);

    void replyComment(JSONObject comment);

    void loadReplies(JSONObject comment);

    void writeComment(FeedItem item);

    default void setCommentLike(String id, boolean liked, ApiClient.Callback callback) {
        callback.onError("当前页面暂不支持评论点赞");
    }

    default void requestReplies(String id, String cursor, ApiClient.Callback callback) {
        callback.onError("当前页面暂不支持读取回复");
    }

    default void createComment(String text, String rootId, String replyId, ApiClient.Callback callback) {
        callback.onError("当前页面暂不支持发送评论");
    }

    void loginCompleted();

    void settingsChanged();

    void runSettingsAction(String action);
}
