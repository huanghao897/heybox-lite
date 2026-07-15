package com.ronan.heyboxlite;

import android.os.Handler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

final class WriteActionClient {
    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_DELAY_MS = 1300L;
    private static final long ACTION_INTERVAL_MS = 2000L;
    private static final long RISK_BLOCK_MS = 10 * 60 * 1000L;

    private interface Request {
        void start(ApiClient.Callback callback);
    }

    private static final class Step {
        final String name;
        final Request request;

        Step(String name, Request request) {
            this.name = name;
            this.request = request;
        }
    }

    private final ApiClient api;
    private final SessionStore session;
    private final WriteTokenProvider tokenProvider;
    private final Handler handler;
    private final ApiClient.Logger logger;
    private long lastSubmitAt;
    private long blockedUntilAt;
    private Runnable retryTask;
    private boolean closed;

    WriteActionClient(ApiClient api, SessionStore session, WriteTokenProvider tokenProvider,
                      Handler handler, ApiClient.Logger logger) {
        this.api = api;
        this.session = session;
        this.tokenProvider = tokenProvider;
        this.handler = handler;
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
        return isRiskControlError(message) ? "官方风控暂时限制" + actionName + "，先停一会儿再试" : message;
    }

    void like(String linkId, String hsrc, boolean liked, ApiClient.Callback callback) {
        Map<String, String> body = new HashMap<>();
        body.put("link_id", linkId);
        body.put("award_type", liked ? "1" : "0");
        run(new Step[]{
                new Step("like-official", cb -> this.api.postForm(EndpointProvider.awardLink(), query(hsrc), body, cb)),
                new Step("like-compat", cb -> this.api.postForm(EndpointProvider.awardLink(), Collections.emptyMap(), body, cb))
        }, callback);
    }

    void favorite(String linkId, String hsrc, boolean favored, ApiClient.Callback callback) {
        Map<String, String> official = favoriteBody(linkId, favored, false);
        Map<String, String> compat = favoriteBody(linkId, favored, true);
        run(new Step[]{
                new Step("favorite-official", cb -> this.api.postForm(EndpointProvider.favourLink(), query(hsrc), official, cb)),
                new Step("favorite-folder-compat", cb -> this.api.postForm(EndpointProvider.favourLink(), query(hsrc), compat, cb))
        }, callback);
    }

    void follow(String userId, String hsrc, boolean following, ApiClient.Callback callback) {
        String path = following ? EndpointProvider.followUser() : EndpointProvider.unfollowUser();
        Map<String, String> body = new HashMap<>();
        body.put("following_id", userId);
        if (following) body.put("follows", "1");
        run(new Step[]{
                new Step("follow-official", cb -> this.api.postForm(path, query(hsrc), body, cb)),
                new Step("follow-compat", cb -> this.api.postForm(path, Collections.emptyMap(), body, cb))
        }, callback);
    }

    void commentLike(String commentId, String hsrc, boolean liked, ApiClient.Callback callback) {
        Map<String, String> body = new HashMap<>();
        body.put("comment_id", commentId);
        body.put("support_type", liked ? "1" : "2");
        run(new Step[]{
                new Step("comment-like-official", cb -> this.api.postForm(EndpointProvider.supportComment(), query(hsrc), body, cb)),
                new Step("comment-like-compat", cb -> this.api.postForm(EndpointProvider.supportComment(), Collections.emptyMap(), body, cb))
        }, callback);
    }

    void createComment(String linkId, String hsrc, String authCode, String text,
                       String rootId, String replyId, ApiClient.Callback callback) {
        Map<String, String> body = new HashMap<>();
        body.put("link_id", linkId);
        body.put("text", text);
        body.put("root_id", rootId == null ? "" : rootId);
        body.put("reply_id", replyId == null ? "" : replyId);
        body.put("imgs", "");
        body.put("is_cy", "0");
        body.put("recommend_state", "0");
        Map<String, String> officialQuery = query(hsrc);
        if (authCode != null && !authCode.isEmpty()) officialQuery.put("auth_code", authCode);
        run(new Step[]{
                new Step("comment-create-official", cb -> this.api.postForm(EndpointProvider.createComment(), officialQuery, body, cb)),
                new Step("comment-create-compat", cb -> this.api.postForm(EndpointProvider.createComment(), Collections.emptyMap(), body, cb))
        }, callback);
    }

    void close() {
        this.closed = true;
        if (this.retryTask != null) this.handler.removeCallbacks(this.retryTask);
    }

    static boolean isLoginError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.US);
        return lower.contains("login") || lower.contains("relogin") || message.contains("登录") || message.contains("重新登录");
    }

    static boolean isParameterError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.US);
        return message.contains("验证参数") || message.contains("参数") || message.contains("非法请求") || lower.contains("param");
    }

    static boolean isRiskControlError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.US);
        return message.contains("无法使用该功能") || message.contains("有风险") || message.contains("风险")
                || message.contains("风控") || message.contains("请求过于频繁") || message.contains("完成验证")
                || message.contains("验证码") || message.contains("HTTP 429") || lower.contains("show_captcha")
                || lower.contains("captcha") || lower.contains("frequent") || lower.contains("risk_control");
    }

    private void run(Step[] steps, ApiClient.Callback callback) {
        log("write cookie keys: " + this.session.authCookieKeysForLog());
        run(steps, 0, "", "", false, callback);
    }

    private void run(Step[] steps, int index, String lastError, String importantError,
                     boolean tokenRetried, ApiClient.Callback callback) {
        if (this.closed) return;
        if (index >= steps.length || index >= MAX_ATTEMPTS) {
            callback.onError(resultError(lastError, importantError));
            return;
        }
        Step step = steps[index];
        log("write fallback " + index + " start: " + step.name);
        step.request.start(new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                if (closed) return;
                log("write fallback " + index + " ok: " + step.name);
                callback.onSuccess(body);
            }

            @Override
            public void onError(String message) {
                if (closed) return;
                log("write fallback " + index + " failed: " + step.name + ": " + message);
                if (isRiskControlError(message)) {
                    blockedUntilAt = Math.max(blockedUntilAt, System.currentTimeMillis() + RISK_BLOCK_MS);
                    log("write risk control stop fallback: " + step.name);
                    callback.onError(message);
                    return;
                }
                boolean tokenError = isTokenError(message);
                boolean loginError = isLoginError(message);
                String nextImportant = isParameterError(message) || loginError ? message : importantError;
                if ((tokenError || loginError) && !tokenRetried && tokenProvider != null) {
                    log("write auth failed, refreshing token then retrying chain");
                    tokenProvider.refresh(new WriteTokenProvider.Callback() {
                        @Override
                        public void onReady() {
                            schedule(steps, index, message, nextImportant, true, callback);
                        }

                        @Override
                        public void onError(String tokenMessage) {
                            log("write token refresh failed: " + tokenMessage);
                            callback.onError(message);
                        }
                    });
                } else if (tokenError || loginError) {
                    callback.onError(message);
                } else if (isParameterError(message)) {
                    schedule(steps, index + 1, message, nextImportant, tokenRetried, callback);
                } else {
                    callback.onError(message);
                }
            }
        });
    }

    private void schedule(Step[] steps, int index, String lastError, String importantError,
                          boolean tokenRetried, ApiClient.Callback callback) {
        if (index >= steps.length || index >= MAX_ATTEMPTS) {
            callback.onError(resultError(lastError, importantError));
            return;
        }
        log("write fallback delay next=" + index + " ms=" + RETRY_DELAY_MS);
        this.retryTask = () -> run(steps, index, lastError, importantError, tokenRetried, callback);
        this.handler.postDelayed(this.retryTask, RETRY_DELAY_MS);
    }

    private void log(String message) {
        if (this.logger != null) this.logger.log(message);
    }

    private static Map<String, String> query(String hsrc) {
        Map<String, String> query = new HashMap<>();
        if (hsrc != null && !hsrc.isEmpty()) query.put("h_src", hsrc);
        return query;
    }

    private static Map<String, String> favoriteBody(String linkId, boolean favored, boolean includeFolder) {
        Map<String, String> body = new HashMap<>();
        body.put("link_id", linkId);
        body.put("favour_type", favored ? "1" : "2");
        if (includeFolder) body.put("folder_id", "");
        return body;
    }

    private static boolean isTokenError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.US);
        return lower.contains("lack_token") || lower.contains("x_xhh_tokenid") || lower.contains("tokenid")
                || lower.contains("token") || message.contains("令牌");
    }

    private static String resultError(String lastError, String importantError) {
        String message = importantError == null || importantError.isEmpty() ? lastError : importantError;
        return message == null || message.isEmpty() ? "接口未返回可用结果" : message;
    }
}
