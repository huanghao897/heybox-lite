package com.ronan.heyboxlite;

/** Write authorization and protocol bridge shared by Compose comment actions. */
final class ComposeCommentRequests {
    private final MainActivity activity;

    ComposeCommentRequests(MainActivity activity) { this.activity = activity; }

    void like(String id, boolean liked, ApiClient.Callback callback) {
        if (!allow("评论点赞", callback)) return;
        activity.writeActions.commentLike(id, activity.currentLinkHsrc, liked, callback);
    }

    void replies(String id, String cursor, ApiClient.Callback callback) {
        activity.api.get(EndpointProvider.subComments(),
                OfficialRequestParams.subComments(id, cursor, activity.currentLinkHsrc), callback);
    }

    void send(String text, String rootId, String replyId, ApiClient.Callback callback) {
        if (!allow("评论", callback)) return;
        activity.writeActions.createComment(activity.currentLinkId, activity.currentLinkHsrc,
                activity.currentAuthCode, text, rootId, replyId, callback);
    }

    private boolean allow(String action, ApiClient.Callback callback) {
        if (activity.postActions == null || !activity.postActions.requireLogin(action)
                || !activity.postActions.allowWriteAction(action)) {
            callback.onError("操作未提交，请检查登录状态或稍后再试");
            return false;
        }
        return true;
    }
}
