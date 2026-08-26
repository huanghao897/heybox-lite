package com.ronan.heyboxlite;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

final class DetailLoadCoordinator {
    interface Host {
        boolean active(FeedItem item);
        void hideLoading();
        void renderDetail(JSONObject body, FeedItem fallback);
        void refreshOffline(FeedItem item, JSONObject body);
        void showMessage(String message);
        void toast(String message);
    }

    static final class State {
        final JSONObject pendingBody;
        final boolean rendered;
        final long loadStartedAt;

        State(JSONObject pendingBody, boolean rendered, long loadStartedAt) {
            this.pendingBody = pendingBody;
            this.rendered = rendered;
            this.loadStartedAt = loadStartedAt;
        }
    }

    private static final String OFFLINE_CACHE_MESSAGE = "已显示离线缓存";

    private final Context context;
    private final ApiClient api;
    private final LocalCache cache;
    private final Handler handler;
    private final Host host;
    private int requestToken;
    private JSONObject pendingBody;
    private boolean rendered;
    private long loadStartedAt;

    DetailLoadCoordinator(Context context, ApiClient api, LocalCache cache,
                          Handler handler, Host host) {
        this.context = context;
        this.api = api;
        this.cache = cache;
        this.handler = handler;
        this.host = host;
    }

    void load(FeedItem item) {
        pendingBody = null;
        rendered = false;
        loadStartedAt = SystemClock.elapsedRealtime();
        int token = ++requestToken;
        renderAfterEntry(item, token);
        if (!networkConnected()) {
            afterEntry(() -> {
                if (current(item, token)) {
                    host.hideLoading();
                    handleFailure(item, "当前无网络");
                }
            });
            return;
        }
        api.get(EndpointProvider.linkTreeV2(),
                OfficialRequestParams.detail(item.id, item.hsrc, item.video),
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        if (!current(item, token)) return;
                        host.hideLoading();
                        String blocked = blockedMessage(body);
                        if (!blocked.isEmpty()) {
                            handleFailureAfterEntry(item, token, blocked);
                        } else if (!hasLink(body)) {
                            handleFailureAfterEntry(item, token, "详情数据为空");
                        } else {
                            cacheAndRender(item, body, token);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!current(item, token)) return;
                        host.hideLoading();
                        handleFailureAfterEntry(item, token, message);
                    }
                });
    }

    void markRendered() {
        rendered = true;
    }

    boolean rendered() {
        return rendered;
    }

    long loadStartedAt() {
        return loadStartedAt;
    }

    void cancel() {
        requestToken++;
        pendingBody = null;
        rendered = false;
    }

    State suspend() {
        requestToken++;
        State state = new State(pendingBody, rendered, loadStartedAt);
        pendingBody = null;
        rendered = false;
        return state;
    }

    void restore(State state) {
        requestToken++;
        pendingBody = state.pendingBody;
        rendered = state.rendered;
        loadStartedAt = state.loadStartedAt;
    }

    void close() {
        cancel();
    }

    private void cacheAndRender(FeedItem item, JSONObject body, int token) {
        JSONObject normalized = DetailResponseNormalizer.normalize(body);
        DetailResponseNormalizer.mergeVideoFallback(normalized, item.toJson());
        cache.saveDetail(item.id, normalized);
        if (cache.isWatchLater(item.id)) {
            host.refreshOffline(item, normalized);
        }
        pendingBody = normalized;
        renderAfterEntry(item, token);
    }

    private void renderAfterEntry(FeedItem item, int token) {
        afterEntry(() -> {
            if (!current(item, token)) return;
            JSONObject body = pendingBody;
            pendingBody = null;
            if (body == null && !rendered) body = initialBody(item);
            if (body != null) host.renderDetail(body, item);
        });
    }

    private void afterEntry(Runnable action) {
        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - loadStartedAt);
        long delay = Math.max(0L, MotionSpec.TRANSITION_FULL_MS - elapsed);
        handler.postDelayed(action, delay);
    }

    private JSONObject initialBody(FeedItem item) {
        JSONObject cached = cache.detail(item.id);
        if (cached != null && blockedMessage(cached).isEmpty() && hasLink(cached)) {
            cache.log("perf app stage=detail-cache-hit link=true");
            return cached;
        }
        try {
            JSONObject result = new JSONObject();
            result.put("link", item.toJson());
            result.put("comments", new JSONArray());
            JSONObject body = new JSONObject();
            body.put("result", result);
            body.put("_progressive_preview", true);
            return body;
        } catch (JSONException error) {
            return null;
        }
    }

    private void handleFailure(FeedItem item, String message) {
        cache.log("detail failed " + item.id + ": " + message);
        if (rendered) {
            host.toast("详情更新失败，已保留当前内容");
            return;
        }
        JSONObject cached = cache.detail(item.id);
        if (cached != null && blockedMessage(cached).isEmpty() && hasLink(cached)) {
            host.toast(OFFLINE_CACHE_MESSAGE);
            host.renderDetail(cached, item);
        } else if (!renderFallback(item, message)) {
            host.showMessage("详情加载失败\n" + message);
        }
    }

    private void handleFailureAfterEntry(FeedItem item, int token, String message) {
        afterEntry(() -> {
            if (current(item, token)) handleFailure(item, message);
        });
    }

    private boolean renderFallback(FeedItem item, String reason) {
        try {
            String notice = fallbackNotice(reason);
            JSONObject link = item.toJson();
            if (link.optString("title").isEmpty()) link.put("title", "帖子摘要暂不可用");
            if (link.optString("description").isEmpty()
                    && link.optString("text").isEmpty()) {
                link.put("description", notice + " 当前列表没有返回正文摘要，登录后可查看完整详情");
            }
            JSONObject result = new JSONObject();
            result.put("link", link);
            result.put("comments", new JSONArray());
            JSONObject body = new JSONObject();
            body.put("result", result);
            body.put("_fallback_notice", notice);
            host.renderDetail(body, item);
            return true;
        } catch (JSONException error) {
            return false;
        }
    }

    private boolean current(FeedItem item, int token) {
        return token == requestToken && host.active(item);
    }

    private boolean networkConnected() {
        try {
            ConnectivityManager manager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (SecurityException ignored) {
            return true;
        }
    }

    private static boolean hasLink(JSONObject body) {
        JSONObject result = body == null ? null : body.optJSONObject("result");
        return result != null && result.optJSONObject("link") != null;
    }

    private static String blockedMessage(JSONObject body) {
        if (body == null) return "详情数据为空";
        String direct = blockedMessageFrom(body);
        if (!direct.isEmpty()) return direct;
        JSONObject result = body.optJSONObject("result");
        return result == null ? "" : blockedMessageFrom(result);
    }

    private static String blockedMessageFrom(JSONObject object) {
        String status = object.optString("status");
        String code = object.optString("code");
        String message = Json.first(object.optString("msg"), object.optString("message"));
        if (verificationStatus(status) || verificationStatus(code)) {
            return Json.first(message, status, code, "需要完成验证后才能继续");
        }
        return verificationText(message) ? message : "";
    }

    private static boolean verificationStatus(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.contains("captcha") || lower.contains("verify")
                || lower.contains("verification") || lower.contains("name_verify")
                || lower.contains("need_alipay_verify")
                || lower.contains("need_bind_phone")
                || lower.contains("need_phone_code");
    }

    private static boolean verificationText(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.contains("captcha") || lower.contains("verify")
                || value.contains("需要完成验证") || value.contains("验证")
                || value.contains("接口限制") || value.contains("请求过于频繁");
    }

    private static String fallbackNotice(String reason) {
        String value = reason == null ? "" : reason;
        if (value.contains("验证") || value.contains("captcha")
                || value.contains("403") || value.contains("限制")) {
            return "游客模式：完整详情需要验证，已显示首页摘要";
        }
        return "详情接口暂不可用，已显示首页摘要";
    }
}
