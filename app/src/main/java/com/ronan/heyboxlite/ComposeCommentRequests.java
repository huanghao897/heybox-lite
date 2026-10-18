package com.ronan.heyboxlite;

/** Write authorization and protocol bridge shared by Compose comment actions. */
final class ComposeCommentRequests {
    private final MainActivity activity;

    ComposeCommentRequests(MainActivity activity) { this.activity = activity; }

    void like(String id, boolean liked, ApiClient.Callback callback) {
        ComposeDetailContext detail = current(callback);
        if (detail == null) return;
        if (!allow("评论点赞", callback)) return;
        activity.writeActions.commentLike(id, detail.getHsrc(), liked, callback);
    }

    void replies(String id, String cursor, ApiClient.Callback callback) {
        ComposeDetailContext detail = current(callback);
        if (detail == null) return;
        activity.api.get(EndpointProvider.subComments(),
                OfficialRequestParams.subComments(id, cursor, detail.getHsrc()), callback);
    }

    void send(String text, String rootId, String replyId, ApiClient.Callback callback) {
        ComposeDetailContext detail = current(callback);
        if (detail == null) return;
        if (!allow("评论", callback)) return;
        activity.writeActions.createComment(detail.getItem().id, detail.getHsrc(),
                detail.getAuthCode(), text, rootId, replyId, callback);
    }

    private ComposeDetailContext current(ApiClient.Callback callback) {
        ComposeDetailContext detail = activity.composeDetailContext();
        if (detail == null) callback.onError("当前帖子已关闭，请重新打开");
        return detail;
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
