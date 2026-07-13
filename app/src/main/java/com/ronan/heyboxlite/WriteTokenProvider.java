package com.ronan.heyboxlite;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JsPromptResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class WriteTokenProvider {
    interface Callback {
        void onReady();
        void onError(String message);
    }

    private static final int TIMEOUT_MS = 25000;
    private static final String TOKEN_BASE_URL = "https://www.xiaoheihe.cn/";

    private final Activity activity;
    private final SessionStore session;
    private final ApiClient api;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Callback> pending = new ArrayList<>();

    private WebView webView;
    private boolean fetching;
    private Runnable timeoutTask;
    private String promptNonce = "";

    WriteTokenProvider(Activity activity, SessionStore session, ApiClient api) {
        this.activity = activity;
        this.session = session;
        this.api = api;
    }

    void ensure(Callback callback) {
        if (session.hasCookieValue(SecureStrings.xXhhTokenId())) {
            callback.onReady();
            return;
        }
        fetch(false, callback);
    }

    void refresh(Callback callback) {
        fetch(true, callback);
    }

    private void fetch(boolean force, Callback callback) {
        main.post(() -> {
            if (!force && session.hasCookieValue(SecureStrings.xXhhTokenId())) {
                callback.onReady();
                return;
            }
            pending.add(callback);
            if (fetching) return;
            fetching = true;
            startTimeout();
            startWebView();
        });
    }

    private void startTimeout() {
        timeoutTask = () -> finishError("写入验证 token 获取超时");
        main.postDelayed(timeoutTask, TIMEOUT_MS);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void startWebView() {
        destroyWebView();
        promptNonce = UUID.randomUUID().toString();
        webView = new WebView(activity);
        webView.setVisibility(View.GONE);
        webView.setBackgroundColor(Color.TRANSPARENT);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isTrustedPromptUrl(url);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsPrompt(WebView view, String url, String message,
                                      String defaultValue, JsPromptResult result) {
                if (message != null && message.startsWith("heyboxlite:")) {
                    result.confirm("");
                    handlePrompt(message);
                    return true;
                }
                return super.onJsPrompt(view, url, message, defaultValue, result);
            }
        });
        View decor = activity.getWindow().getDecorView();
        if (decor instanceof ViewGroup) {
            ((ViewGroup) decor).addView(webView, new ViewGroup.LayoutParams(1, 1));
        }
        webView.loadDataWithBaseURL(TOKEN_BASE_URL, tokenHtml(),
                "text/html", "UTF-8", null);
    }

    private String tokenHtml() {
        return "<!doctype html><html><head><meta charset='utf-8'></head><body>"
                + "<script>"
                + "var NONCE='" + promptNonce + "';"
                + "function send(t,v){prompt('heyboxlite:'+NONCE+':'+t+':'+encodeURIComponent(String(v||'')),'');}"
                + "function fail(v){send('error',v&&v.message?v.message:v);}"
                + "window._smConf={organization:'0yD85BjYvGFAvHaSQ1mc',appId:'heybox_website',"
                + "publicKey:'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCXj9exmI4nQjmT52iwr+yf7hAQ06bfSZHTAHUfRBYiagCf/whhd8es0R79wBigpiHLd28TKA8b8mGR8OiiI1hV+qfynCWihvp3mdj8MiiH6SU3lhro2hkfYzImZB0RmWr2zE4Xt1+A6Oyp6bf+W7JSxYUXHw3nNv7Td4jw4jEFKQIDAQAB',"
                + "staticHost:'static.portal101.cn',protocol:'https'};"
                + "window._smReadyFuncs=[function(){try{var s=window.SMSdk;"
                + "send('device',s&&s.getDeviceId?s.getDeviceId():'');}catch(e){fail(e);}}];"
                + "window.SMSdk={};"
                + "var sc=document.createElement('script');"
                + "sc.src='https://static.portal101.cn/dist/web/v3.0.0/fp.min.js?t='+Math.floor(Date.now()/21600000);"
                + "sc.onerror=function(){fail('SMSdk 加载失败');};"
                + "document.head.appendChild(sc);"
                + "setTimeout(function(){fail('SMSdk 超时');},8000);"
                + "</script></body></html>";
    }

    private void handlePrompt(String message) {
        String[] parts = message.split(":", 4);
        if (parts.length < 4 || !promptNonce.equals(parts[1])) return;
        String type = parts[2];
        String value = decode(parts[3]);
        if ("device".equals(type)) handleDeviceId(value);
        else if ("error".equals(type)) finishError(value.isEmpty() ? "SMSdk 获取失败" : value);
    }

    private boolean isTrustedPromptUrl(String url) {
        return TOKEN_BASE_URL.equals(url);
    }

    private void handleDeviceId(String value) {
        if (value == null || value.isEmpty()) {
            finishError("SMSdk 未返回设备数据");
            return;
        }
        if (value.startsWith("B") && value.length() == 89) {
            saveToken(value);
            finishSuccess();
            return;
        }
        if (value.startsWith("D")) {
            requestBoxToken(value);
            return;
        }
        finishError("SMSdk 返回了无法识别的设备数据");
    }

    private void requestBoxToken(String boxData) {
        Map<String, String> body = new HashMap<>();
        body.put("box_data", boxData);
        api.postForm(EndpointProvider.boxDataCallback(), Collections.emptyMap(),
                body, new ApiClient.Callback() {
                    @Override public void onSuccess(JSONObject response) {
                        String token = tokenFrom(response);
                        if (token.isEmpty()) {
                            finishError("服务器未返回写入验证 token");
                            return;
                        }
                        saveToken(token);
                        finishSuccess();
                    }

                    @Override public void onError(String message) {
                        finishError("写入验证 token 获取失败：" + message);
                    }
                });
    }

    private String tokenFrom(JSONObject response) {
        if (response == null) return "";
        JSONObject result = response.optJSONObject("result");
        if (result != null) {
            String token = first(result.optString("heybox_token"),
                    result.optString(SecureStrings.xXhhTokenId()));
            if (!token.isEmpty()) return token;
        }
        Object value = response.opt("result");
        if (value instanceof String) return (String) value;
        return first(response.optString("heybox_token"),
                response.optString(SecureStrings.xXhhTokenId()));
    }

    private void saveToken(String token) {
        session.putCookieValue(SecureStrings.xXhhTokenId(), token);
    }

    private void finishSuccess() {
        main.post(() -> {
            if (!fetching) return;
            fetching = false;
            cancelTimeout();
            destroyWebView();
            List<Callback> callbacks = drain();
            for (Callback callback : callbacks) callback.onReady();
        });
    }

    private void finishError(String message) {
        main.post(() -> {
            if (!fetching) return;
            fetching = false;
            cancelTimeout();
            destroyWebView();
            List<Callback> callbacks = drain();
            String value = message == null || message.isEmpty()
                    ? "写入验证 token 获取失败" : message;
            for (Callback callback : callbacks) callback.onError(value);
        });
    }

    private List<Callback> drain() {
        List<Callback> callbacks = new ArrayList<>(pending);
        pending.clear();
        return callbacks;
    }

    private void cancelTimeout() {
        if (timeoutTask != null) main.removeCallbacks(timeoutTask);
        timeoutTask = null;
    }

    void close() {
        main.post(() -> {
            fetching = false;
            cancelTimeout();
            pending.clear();
            destroyWebView();
        });
    }

    private void destroyWebView() {
        if (webView == null) return;
        try {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
        } catch (Exception ignored) {
        }
        try {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
        } catch (Exception ignored) {
        }
        webView = null;
        promptNonce = "";
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String first(String first, String second) {
        if (first != null && !first.isEmpty()) return first;
        return second == null ? "" : second;
    }
}
