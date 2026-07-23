package com.ronan.heyboxlite;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.URI;

public final class CheckinCaptchaActivity extends Activity {
    static final String EXTRA_URI = "checkin_captcha_uri";
    static final String EXTRA_TICKET = "checkin_captcha_ticket";
    static final String EXTRA_RANDSTR = "checkin_captcha_randstr";
    static final String EXTRA_ERROR = "checkin_captcha_error";

    private static final long TIMEOUT_MS = 180_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> finishError("安全验证超时，请重试");
    private WebView webView;
    private TextView statusView;
    private String verificationUri = "";
    private boolean finished;

    static Intent intent(Context context, String verificationUri) {
        return new Intent(context, CheckinCaptchaActivity.class)
                .putExtra(EXTRA_URI, verificationUri);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SECURE);
        verificationUri = getIntent().getStringExtra(EXTRA_URI);
        if (!CheckinCaptchaContract.isTrustedPageUri(verificationUri)) {
            finishError("安全验证地址无效");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WebView.setDataDirectorySuffix("checkin_captcha");
            }
        } catch (Throwable error) {
            finishError("当前系统无法初始化安全验证");
            return;
        }
        buildContent();
        startWebView();
    }

    private void buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 18, 18));

        FrameLayout toolbar = new FrameLayout(this);
        toolbar.setPadding(dp(14), 0, dp(6), 0);
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        statusView = new TextView(this);
        statusView.setText("正在加载安全验证");
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(16.0f);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        statusView.setSingleLine(true);
        statusView.setPadding(0, 0, dp(48), 0);
        toolbar.addView(statusView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        ImageButton close = new ImageButton(this);
        close.setImageDrawable(Compat.tintedDrawable(this, R.drawable.ic_close, Color.WHITE));
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setContentDescription("关闭安全验证");
        close.setOnClickListener(view -> finishError("已取消安全验证"));
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(44), dp(44));
        closeParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        toolbar.addView(close, closeParams);

        FrameLayout webContainer = new FrameLayout(this);
        root.addView(webContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        setContentView(root);
        try {
            webView = new WebView(this);
            webContainer.addView(webView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Throwable error) {
            finishError("当前系统缺少可用的 WebView 组件");
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void startWebView() {
        if (webView == null || finished) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(false);
            }
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setSupportMultipleWindows(false);
            settings.setSaveFormData(false);
            settings.setSavePassword(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.setSafeBrowsingEnabled(true);
            }
            webView.setBackgroundColor(Color.WHITE);
            webView.setWebViewClient(new CaptchaWebViewClient());
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onJsPrompt(WebView view, String url, String message,
                                          String defaultValue, JsPromptResult prompt) {
                    if (message == null
                            || !message.startsWith(CheckinCaptchaContract.PROMPT_PREFIX)) {
                        return super.onJsPrompt(view, url, message, defaultValue, prompt);
                    }
                    prompt.confirm("");
                    CheckinCaptchaContract.Result result =
                            CheckinCaptchaContract.parsePrompt(url, message);
                    if (result == null || !result.successful) {
                        finishError("安全验证未完成，请重试");
                    } else {
                        finishSuccess(result.ticket, result.randstr);
                    }
                    return true;
                }
            });
            handler.postDelayed(timeout, TIMEOUT_MS);
            webView.loadUrl(verificationUri);
        } catch (Throwable error) {
            finishError("当前系统无法打开安全验证");
        }
    }

    private final class CaptchaWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return !isAllowedNavigation(url, false);
        }

        @TargetApi(Build.VERSION_CODES.N)
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !isAllowedNavigation(request.getUrl().toString(), request.isForMainFrame());
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (verificationUri.equals(url) && statusView != null) {
                statusView.setText("请完成小黑盒安全验证");
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int errorCode, String description,
                                    String failingUrl) {
            if (verificationUri.equals(failingUrl)) {
                finishError("安全验证页面加载失败，请检查网络");
            }
        }

        @TargetApi(Build.VERSION_CODES.M)
        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                        WebResourceResponse errorResponse) {
            if (request.isForMainFrame()) {
                finishError("安全验证服务暂时不可用");
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                       SslError error) {
            handler.cancel();
            finishError("安全验证证书校验失败");
        }

        @TargetApi(Build.VERSION_CODES.O)
        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            destroyWebView();
            finishError("安全验证组件已停止，请重试");
            return true;
        }
    }

    private boolean isAllowedNavigation(String value, boolean mainFrame) {
        if (verificationUri.equals(value)) return true;
        if (mainFrame) return false;
        try {
            URI uri = new URI(value == null ? "" : value);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null) return false;
            return host.equals("captcha.qq.com")
                    || host.endsWith(".captcha.qq.com")
                    || host.equals("captcha.qcloud.com")
                    || host.endsWith(".captcha.qcloud.com")
                    || host.endsWith(".gtimg.com");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void finishSuccess(String ticket, String randstr) {
        if (finished) return;
        finished = true;
        Intent data = new Intent()
                .putExtra(EXTRA_TICKET, ticket)
                .putExtra(EXTRA_RANDSTR, randstr);
        setResult(RESULT_OK, data);
        finish();
    }

    private void finishError(String message) {
        if (finished) return;
        finished = true;
        setResult(RESULT_CANCELED,
                new Intent().putExtra(EXTRA_ERROR, message == null ? "安全验证失败" : message));
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timeout);
        destroyWebView();
        super.onDestroy();
    }

    private void destroyWebView() {
        WebView current = webView;
        webView = null;
        if (current == null) return;
        try {
            ViewGroup parent = (ViewGroup) current.getParent();
            if (parent != null) parent.removeView(current);
            current.stopLoading();
            current.loadUrl("about:blank");
            current.clearHistory();
            current.destroy();
        } catch (Throwable ignored) {
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
