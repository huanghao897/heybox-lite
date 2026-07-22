package com.ronan.heyboxlite;

import android.net.Uri;
import android.os.Handler;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

final class QrLoginController {
    private static final long POLL_INTERVAL_MS = 2000L;
    private static final long ERROR_INTERVAL_MS = 5000L;
    private static final long QR_LIFETIME_MS = 5 * 60 * 1000L;

    interface Listener {
        void onQrReady(String url);

        void onStatus(String status);

        void onLogin(JSONObject result);

        void onError(String message);
    }

    private final ApiClient api;
    private final Handler handler;
    private final Runnable pollTask = this::poll;
    private Listener listener;
    private String qrKey = "";
    private int requestId;
    private boolean active;
    private boolean foreground;
    private long expiresAt;

    QrLoginController(ApiClient api, Handler handler) {
        this.api = api;
        this.handler = handler;
    }

    void start(Listener listener) {
        stop();
        this.listener = listener;
        this.active = true;
        this.foreground = true;
        this.expiresAt = System.currentTimeMillis() + QR_LIFETIME_MS;
        int currentRequest = this.requestId;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app", "web");
        params.put(SecureStrings.heyboxId(), "");
        this.api.get(EndpointProvider.qrUrl(), params, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                if (!isCurrent(currentRequest)) return;
                JSONObject result = body.optJSONObject("result");
                String url = result == null ? "" : result.optString("qr_url");
                if (url.isEmpty()) {
                    fail("二维码获取失败");
                    return;
                }
                qrKey = Uri.parse(url).getQueryParameter("qr");
                if (qrKey == null || qrKey.isEmpty()) qrKey = result.optString("qr");
                if (qrKey.isEmpty()) {
                    fail("二维码获取失败");
                    return;
                }
                listener.onQrReady(url);
                if (isCurrent(currentRequest)) schedule(POLL_INTERVAL_MS);
            }

            @Override
            public void onError(String message) {
                if (isCurrent(currentRequest)) fail(errorMessage(message));
            }
        });
    }

    void stop() {
        this.active = false;
        this.foreground = false;
        this.requestId++;
        this.qrKey = "";
        this.expiresAt = 0L;
        this.listener = null;
        this.handler.removeCallbacks(this.pollTask);
    }

    void pause() {
        this.foreground = false;
        this.handler.removeCallbacks(this.pollTask);
    }

    void resume() {
        if (!this.active || this.qrKey.isEmpty()) return;
        this.foreground = true;
        schedule(0L);
    }

    private void poll() {
        if (!this.active || !this.foreground || this.qrKey.isEmpty()) return;
        if (isExpired()) {
            expire();
            return;
        }
        int currentRequest = this.requestId;
        Map<String, String> params = new HashMap<>();
        params.put("qr", this.qrKey);
        params.put("app", "web");
        this.api.get(EndpointProvider.qrState(), params, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                if (!isCurrent(currentRequest)) return;
                JSONObject result = body.optJSONObject("result");
                String state = result == null ? "" : result.optString("error");
                if ("ok".equals(state)) {
                    Listener current = listener;
                    stop();
                    if (current != null) current.onLogin(result);
                    return;
                }
                if ("cancel".equals(state)) {
                    Listener current = listener;
                    stop();
                    if (current != null) current.onStatus("登录已取消，请重新获取");
                    return;
                }
                listener.onStatus("ready".equals(state) ? "已扫码，请在手机确认" : "等待扫码");
                schedule(POLL_INTERVAL_MS);
            }

            @Override
            public void onError(String message) {
                if (!isCurrent(currentRequest)) return;
                listener.onStatus("网络波动，正在重试");
                schedule(ERROR_INTERVAL_MS);
            }
        });
    }

    private boolean isCurrent(int id) {
        return this.active && id == this.requestId && this.listener != null;
    }

    private void schedule(long delayMs) {
        this.handler.removeCallbacks(this.pollTask);
        if (!this.active || !this.foreground) return;
        long remaining = this.expiresAt - System.currentTimeMillis();
        if (remaining <= 0L) {
            expire();
            return;
        }
        this.handler.postDelayed(this.pollTask, Math.min(delayMs, remaining));
    }

    private boolean isExpired() {
        return this.expiresAt > 0L && System.currentTimeMillis() >= this.expiresAt;
    }

    private void expire() {
        Listener current = this.listener;
        stop();
        if (current != null) current.onStatus("二维码已过期，请重新获取");
    }

    private void fail(String message) {
        Listener current = this.listener;
        stop();
        if (current != null) current.onError(message);
    }

    private static String errorMessage(String message) {
        if (message == null || message.isEmpty()) return "请稍后重试";
        if (message.contains("HTTP 403")) return "请求过于频繁，请稍后重试";
        String compact = message.replace('\n', ' ').trim();
        return compact.length() > 28 ? compact.substring(0, 28) : compact;
    }
}
