package com.ronan.heyboxlite;

import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

final class WriteActionClient {
    private static final long ACTION_INTERVAL_MS = 2000L;
    private static final long RISK_BLOCK_MS = 10 * 60 * 1000L;

    private interface Request {
        void start(ApiClient.Callback callback);
    }

    private final ApiClient api;
    private final SessionStore session;
    private final WriteTokenProvider tokenProvider;
    private final ApiClient.Logger logger;
    private long lastSubmitAt;
    private long blockedUntilAt;
    private boolean closed;

    WriteActionClient(ApiClient api, SessionStore session, WriteTokenProvider tokenProvider,
                      ApiClient.Logger logger) {
        this.api = api;
        this.session = session;
        this.tokenProvider = tokenProvider;
        this.logger = logger;
    }

    String begin(String actionName) {
        long now = System.currentTimeMillis();
        if (now < this.blockedUntilAt) return "官方风控暂时限制" + actionName + "，稍后再试";
        if (now - this.lastSubmitAt < ACTION_INTERVAL_MS) return "操作太频繁了，稍等一下";
        this.lastSubmitAt = now;
        return null;
    }

    String errorMessage(String actionName, String message) {
        return isRiskControlError(message)
                ? "官方风控暂时限制" + actionName + "，先停一会儿再试"
                : message;
    }

    void like(String linkId, String hsrc, boolean liked, ApiClient.Callback callback) {
        Map<String, String> query = OfficialRequestParams.hsrcQuery(hsrc);
        Map<String, String> body = OfficialRequestParams.linkLike(linkId, liked);
        run("like", cb -> this.api.postForm(
                EndpointProvider.awardLink(), query, body, cb), callback);
    }

    void favorite(String linkId, String hsrc, boolean favored, ApiClient.Callback callback) {
        Map<String, String> query = OfficialRequestParams.hsrcQuery(hsrc);
        Map<String, String> body = OfficialRequestParams.favorite(linkId, favored);
        run("favorite", cb -> this.api.postForm(
                EndpointProvider.favourLink(), query, body, cb), callback);
    }

    void follow(String userId, String hsrc, boolean following, ApiClient.Callback callback) {
        String path = following ? EndpointProvider.followUser() : EndpointProvider.unfollowUser();
        Map<String, String> query = OfficialRequestParams.hsrcQuery(hsrc);
        Map<String, String> body = OfficialRequestParams.follow(userId);
        run(following ? "follow" : "unfollow",
                cb -> this.api.postForm(path, query, body, cb), callback);
    }

    void commentLike(String commentId, String hsrc, boolean liked,
                     ApiClient.Callback callback) {
        Map<String, String> query = OfficialRequestParams.hsrcQuery(hsrc);
        Map<String, String> body = OfficialRequestParams.commentLike(commentId, liked);
        run("comment-like", cb -> this.api.postForm(
                EndpointProvider.supportComment(), query, body, cb), callback);
    }

    void createComment(String linkId, String hsrc, String authCode, String text,
                       String rootId, String replyId, ApiClient.Callback callback) {
        Map<String, String> query = OfficialRequestParams.hsrcQuery(hsrc);
        if (authCode != null && !authCode.isEmpty()) query.put("auth_code", authCode);
        Map<String, String> body = OfficialRequestParams.createComment(
                linkId, text, rootId, replyId);
        run("comment-create", cb -> this.api.postForm(
                EndpointProvider.createComment(), query, body, cb), callback);
    }

    void close() {
        this.closed = true;
    }

    static boolean isRiskControlError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.US);
        return message.contains("无法使用该功能") || message.contains("有风险")
                || message.contains("风险") || message.contains("风控")
                || message.contains("请求过于频繁") || message.contains("完成验证")
                || message.contains("验证码") || message.contains("HTTP 429")
                || lower.contains("show_captcha") || lower.contains("captcha")
                || lower.contains("frequent") || lower.contains("risk_control");
    }

    private void run(String name, Request request, ApiClient.Callback callback) {
        log("write request start: " + name + " cookieKeys="
                + this.session.authCookieKeysForLog());
        execute(name, request, false, callback);
    }

    private void execute(String name, Request request, boolean tokenRetried,
                         ApiClient.Callback callback) {
        if (this.closed) return;
        request.start(new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                if (closed) return;
                log("write request ok: " + name);
                callback.onSuccess(body);
            }

            @Override
            public void onError(String message) {
                if (closed) return;
                log("write request failed: " + name + ": " + message);
                if (isRiskControlError(message)) {
                    blockedUntilAt = Math.max(blockedUntilAt,
                            System.currentTimeMillis() + RISK_BLOCK_MS);
                    callback.onError(message);
                    return;
                }
                if (!tokenRetried && isTokenError(message) && tokenProvider != null) {
                    log("write token missing, refreshing once: " + name);
                    tokenProvider.refresh(new WriteTokenProvider.Callback() {
                        @Override
                        public void onReady() {
                            execute(name, request, true, callback);
                        }

                        @Override
                        public void onError(String tokenMessage) {
                            log("write token refresh failed: " + tokenMessage);
                            callback.onError(message);
                        }
                    });
                    return;
                }
                callback.onError(message);
            }
        });
    }

    private void log(String message) {
        if (this.logger != null) this.logger.log(message);
    }

    private static boolean isTokenError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.US);
        return lower.contains("lack_token") || lower.contains("x_xhh_tokenid");
    }
}
