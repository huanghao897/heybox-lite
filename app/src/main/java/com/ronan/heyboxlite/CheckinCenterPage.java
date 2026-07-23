package com.ronan.heyboxlite;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.util.Locale;

final class CheckinCenterPage {
    interface Host {
        void openLogin();
        void confirmRevoke(Runnable confirmed);
        void showMessage(String message);
    }

    private enum State {
        UNPAIRED,
        PAIRING,
        SYNCING,
        CONNECTED,
        RUNNING,
        ERROR
    }

    private static final long SECOND_MS = 1_000L;

    private final Activity activity;
    private final SessionStore session;
    private final CheckinCenterCoordinator coordinator;
    private final ThemeTokens tokens;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float scale;
    private final FrameLayout root;
    private final Runnable pollTask = this::pollPairing;
    private final Runnable countdownTask = this::updatePairingCountdown;
    private State state;
    private PairingSession pairing;
    private CheckinCenterClient.Status status;
    private String errorMessage = "";
    private WebView webView;
    private TextView pairingCountdown;
    private boolean closed;

    CheckinCenterPage(Activity activity, SessionStore session,
                      CheckinCenterCoordinator coordinator, ThemeTokens tokens, Host host) {
        this.activity = activity;
        this.session = session;
        this.coordinator = coordinator;
        this.tokens = tokens;
        this.host = host;
        this.scale = session.uiScale() / 100.0f;
        this.root = new FrameLayout(activity);
        this.root.setBackgroundColor(tokens.background);
        this.state = coordinator.paired() ? State.SYNCING : State.UNPAIRED;
        render();
        if (coordinator.paired()) refresh();
    }

    View view() {
        return root;
    }

    void refresh() {
        if (closed) return;
        if (!coordinator.paired()) {
            state = State.UNPAIRED;
            status = null;
            errorMessage = "";
            render();
            return;
        }
        state = State.SYNCING;
        errorMessage = "";
        render();
        coordinator.syncCredentials(false, new CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>() {
            @Override
            public void onSuccess(CheckinCenterClient.ConnectedAccount value) {
                loadStatus();
            }

            @Override
            public void onError(CheckinCenterClient.ApiError error) {
                if (error.authorizationInvalid()) {
                    showError(error.getMessage());
                } else {
                    loadStatus(error.getMessage());
                }
            }
        });
    }

    boolean handleBack() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return false;
    }

    void onResume() {
        if (webView != null) webView.onResume();
        if (pairing != null) {
            schedulePoll(0L);
            handler.removeCallbacks(countdownTask);
            handler.post(countdownTask);
        }
    }

    void onPause() {
        handler.removeCallbacks(pollTask);
        handler.removeCallbacks(countdownTask);
        if (webView != null) webView.onPause();
    }

    private void beginPairing() {
        if (!coordinator.supported()) {
            showError("小黑盒自动签到需要 Android 7.0 或更高版本");
            return;
        }
        if (!session.isLoggedIn()) {
            host.openLogin();
            return;
        }
        state = State.SYNCING;
        errorMessage = "";
        render();
        coordinator.startPairing(new CheckinCenterClient.Callback<CheckinCenterClient.PairingStart>() {
            @Override
            public void onSuccess(CheckinCenterClient.PairingStart value) {
                long lifetimeSeconds = Math.min(600L, value.expiresInSeconds);
                pairing = new PairingSession(value,
                        SystemClock.elapsedRealtime() + lifetimeSeconds * SECOND_MS);
                state = State.PAIRING;
                render();
                schedulePoll(value.intervalSeconds * SECOND_MS);
                handler.post(countdownTask);
            }

            @Override
            public void onError(CheckinCenterClient.ApiError error) {
                showError(error.getMessage());
            }
        });
    }

    private void pollPairing() {
        PairingSession current = pairing;
        if (closed || current == null || state != State.PAIRING) return;
        if (current.expired()) {
            pairing = null;
            showError("配对已过期，请重新连接");
            return;
        }
        coordinator.pollPairing(current.start.deviceCode,
                new CheckinCenterClient.Callback<CheckinCenterClient.PairingPoll>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.PairingPoll value) {
                        current.retries = 0;
                        if (!value.authorized()) {
                            schedulePoll(current.start.intervalSeconds * SECOND_MS);
                            return;
                        }
                        if (!coordinator.authorize(value.deviceToken)) {
                            pairing = null;
                            showError("无法安全保存签到服务连接，请检查系统安全组件");
                            return;
                        }
                        pairing = null;
                        state = State.SYNCING;
                        destroyWebView();
                        render();
                        coordinator.syncCredentials(true,
                                new CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>() {
                                    @Override
                                    public void onSuccess(CheckinCenterClient.ConnectedAccount account) {
                                        host.showMessage("小黑盒自动签到已连接");
                                        loadStatus();
                                    }

                                    @Override
                                    public void onError(CheckinCenterClient.ApiError error) {
                                        showError(error.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (CheckinRetryPolicy.shouldRetry(error, current.retries)
                                && !current.expired()) {
                            long delay = CheckinRetryPolicy.delayMillis(error,
                                    current.retries++, current.start.intervalSeconds * SECOND_MS);
                            schedulePoll(delay);
                            return;
                        }
                        pairing = null;
                        showError(error.getMessage());
                    }
                });
    }

    private void loadStatus() {
        loadStatus("");
    }

    private void loadStatus(String warning) {
        if (closed || !coordinator.paired()) {
            state = State.UNPAIRED;
            render();
            return;
        }
        coordinator.getStatus(new CheckinCenterClient.Callback<CheckinCenterClient.Status>() {
            @Override
            public void onSuccess(CheckinCenterClient.Status value) {
                status = value;
                state = State.CONNECTED;
                errorMessage = warning == null ? "" : warning;
                render();
            }

            @Override
            public void onError(CheckinCenterClient.ApiError error) {
                showError(error.getMessage());
            }
        });
    }

    private void runNow() {
        if (state == State.RUNNING) return;
        state = State.RUNNING;
        errorMessage = "";
        render();
        coordinator.runNow(new CheckinCenterClient.Callback<CheckinCenterClient.RunResult>() {
            @Override
            public void onSuccess(CheckinCenterClient.RunResult value) {
                host.showMessage(runMessage(value));
                loadStatus();
            }

            @Override
            public void onError(CheckinCenterClient.ApiError error) {
                showError(error.getMessage());
            }
        });
    }

    private void requestRevoke() {
        host.confirmRevoke(() -> {
            state = State.SYNCING;
            render();
            coordinator.revokeDevice(new CheckinCenterClient.Callback<Boolean>() {
                @Override
                public void onSuccess(Boolean value) {
                    status = null;
                    pairing = null;
                    state = State.UNPAIRED;
                    errorMessage = "";
                    destroyWebView();
                    render();
                    host.showMessage("已撤销此设备");
                }

                @Override
                public void onError(CheckinCenterClient.ApiError error) {
                    showError(error.getMessage());
                }
            });
        });
    }

    private void showError(String message) {
        state = coordinator.paired() ? State.ERROR : State.UNPAIRED;
        errorMessage = message == null ? "签到服务请求失败" : message;
        render();
    }

    private void render() {
        if (closed) return;
        root.removeAllViews();
        pairingCountdown = null;
        if (state == State.PAIRING && pairing != null) {
            renderPairing();
            return;
        }
        destroyWebView();
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout page = column(tokens.background);
        page.setPadding(dp(10), dp(8), dp(10), dp(18));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        if (!errorMessage.isEmpty()) page.addView(errorBanner(errorMessage));
        if (!coordinator.paired()) renderUnpaired(page);
        else if (state == State.SYNCING || state == State.ERROR || status == null) {
            renderLoading(page);
        } else {
            renderConnected(page);
        }
        root.addView(scroll, match());
    }

    private void renderUnpaired(LinearLayout page) {
        LinearLayout card = card();
        card.addView(heading("小黑盒自动签到"));
        TextView stateText = body(coordinator.supported() ? "未连接签到服务"
                : "当前系统不支持安全配对", tokens.muted);
        addTop(card, stateText, 5);
        TextView description = body("连接后，服务器会按照设定时间完成每日小黑盒签到。",
                tokens.text);
        description.setLineSpacing(0f, 1.16f);
        addTop(card, description, 12);
        if (!session.isLoggedIn()) {
            addTop(card, body("连接前需要先在 Lite 登录小黑盒账号。", tokens.muted), 8);
        }
        Button connect = primaryButton(session.isLoggedIn() ? "连接签到服务" : "登录小黑盒");
        connect.setEnabled(coordinator.supported());
        connect.setOnClickListener(view -> {
            UiComponents.press(view);
            if (session.isLoggedIn()) beginPairing();
            else host.openLogin();
        });
        addTop(card, connect, 16);
        page.addView(card);
    }

    private void renderLoading(LinearLayout page) {
        LinearLayout card = card();
        card.addView(heading(state == State.SYNCING ? "正在连接签到服务" : "小黑盒自动签到"));
        String message = state == State.SYNCING ? "正在读取账号和签到计划"
                : state == State.ERROR ? "可重新加载状态，或撤销此设备"
                : "正在处理签到任务";
        addTop(card, body(message, tokens.muted), 6);
        page.addView(card);
        if (state == State.ERROR) addRecoveryActions(page);
    }

    private void renderConnected(LinearLayout page) {
        LinearLayout card = card();
        card.addView(heading("小黑盒自动签到"));
        String stateLabel = state == State.RUNNING ? "执行中" : taskStateLabel(status.task);
        addTop(card, body(stateLabel, state == State.RUNNING ? tokens.accent
                : status.task.active() ? tokens.text : tokens.muted), 5);
        addTop(card, infoRow("账号", accountLabel(status.account)), 14);
        addTop(card, infoRow("服务器签到", taskEnabledLabel(status.task)), 2);
        addTop(card, infoRow("计划时间", scheduleLabel(status.task)), 2);
        addTop(card, infoRow("随机偏移", offsetLabel(status.task.offsetMinutes)), 2);
        if (!status.task.windowStart.isEmpty() || !status.task.windowEnd.isEmpty()) {
            addTop(card, infoRow("执行区间", windowLabel(status.task)), 2);
        }
        page.addView(card);

        LinearLayout latest = card();
        latest.addView(sectionTitle("最近一次签到"));
        if (status.lastRun == null) {
            addTop(latest, body("暂无执行记录", tokens.muted), 8);
        } else {
            addTop(latest, infoRow("结果", runStatusLabel(status.lastRun.status)), 8);
            addTop(latest, infoRow("时间", runTime(status.lastRun)), 2);
            if (!status.lastRun.summary.isEmpty()) {
                TextView summary = body(status.lastRun.summary, tokens.text);
                summary.setLineSpacing(0f, 1.15f);
                addTop(latest, summary, 9);
            }
        }
        addTop(page, latest, 9);

        Button run = primaryButton(state == State.RUNNING ? "正在签到" : "立即签到");
        run.setEnabled(state != State.RUNNING && status.task.active());
        run.setOnClickListener(view -> {
            UiComponents.press(view);
            runNow();
        });
        addTop(page, run, 12);
        Button revoke = ghostButton("撤销此设备");
        revoke.setEnabled(state != State.RUNNING);
        revoke.setOnClickListener(view -> {
            UiComponents.press(view);
            requestRevoke();
        });
        addTop(page, revoke, 7);
    }

    private void addRecoveryActions(LinearLayout page) {
        Button retry = primaryButton("重新加载");
        retry.setOnClickListener(view -> refresh());
        addTop(page, retry, 10);
        if (coordinator.paired()) {
            Button revoke = ghostButton("撤销此设备");
            revoke.setOnClickListener(view -> requestRevoke());
            addTop(page, revoke, 7);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void renderPairing() {
        LinearLayout page = column(tokens.background);
        page.setPadding(dp(8), dp(7), dp(8), dp(7));
        LinearLayout card = card();
        card.addView(sectionTitle("确认此设备"));
        addTop(card, body("配对码 " + pairing.start.userCode, tokens.text), 5);
        pairingCountdown = body("", tokens.muted);
        addTop(card, pairingCountdown, 3);
        page.addView(card, new LinearLayout.LayoutParams(-1, -2));

        webView = new WebView(activity);
        webView.setBackgroundColor(tokens.background);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSaveFormData(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        }
        CookieManager.getInstance().setAcceptCookie(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (CheckinCenterClient.isTrustedWebUri(url)) return false;
                host.showMessage("已阻止不受信任的页面");
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url,
                                      android.graphics.Bitmap favicon) {
                if (!CheckinCenterClient.isTrustedWebUri(url)) view.stopLoading();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (CheckinCenterClient.isTrustedWebUri(url)) return null;
                return new WebResourceResponse("text/plain", "UTF-8",
                        new ByteArrayInputStream(new byte[0]));
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           SslError error) {
                handler.cancel();
                CheckinCenterPage.this.handler.post(() ->
                        showError("签到服务证书校验失败"));
            }
        });
        webView.loadUrl(pairing.start.verificationUri);
        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        webParams.topMargin = dp(7);
        page.addView(webView, webParams);
        root.addView(page, match());
        updatePairingCountdown();
    }

    private View errorBanner(String message) {
        TextView view = body(message, tokens.text);
        view.setPadding(dp(11), dp(9), dp(11), dp(9));
        view.setLineSpacing(0f, 1.12f);
        Compat.setBackground(view, UiComponents.round(activity,
                ThemeTokens.blend(tokens.panel, Color.rgb(190, 55, 55),
                        tokens.dark ? 0.22f : 0.10f), 10, scale));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(9);
        view.setLayoutParams(params);
        return view;
    }

    private LinearLayout infoRow(String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.TOP);
        TextView left = body(label, tokens.muted);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 0.44f));
        TextView right = body(value, tokens.text);
        right.setGravity(Gravity.END);
        right.setMaxLines(3);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -2, 0.56f);
        rightParams.leftMargin = dp(8);
        row.addView(right, rightParams);
        return row;
    }

    private LinearLayout card() {
        LinearLayout card = column(Color.TRANSPARENT);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        Compat.setBackground(card, UiComponents.card(activity, tokens, scale));
        return card;
    }

    private LinearLayout column(int color) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    private TextView heading(String value) {
        TextView view = label(value, 18f, tokens.text);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private TextView sectionTitle(String value) {
        TextView view = label(value, 15f, tokens.text);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private TextView body(String value, int color) {
        return label(value, 12f, color);
    }

    private TextView label(String value, float size, int color) {
        TextView view = UiComponents.label(activity, value, size, color,
                session.textScale() / 100.0f);
        view.setIncludeFontPadding(false);
        return view;
    }

    private Button primaryButton(String value) {
        Button button = baseButton(value);
        button.setTextColor(tokens.onPrimary);
        Compat.setBackground(button, UiComponents.primaryButton(activity, tokens, scale));
        return button;
    }

    private Button ghostButton(String value) {
        Button button = baseButton(value);
        button.setTextColor(tokens.text);
        Compat.setBackground(button, UiComponents.ghostButton(activity, tokens, scale));
        return button;
    }

    private Button baseButton(String value) {
        Button button = new Button(activity);
        button.setText(value);
        button.setTextSize(12f * session.textScale() / 100.0f);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(42)));
        return button;
    }

    private void addTop(ViewGroup parent, View child, int marginDp) {
        LinearLayout.LayoutParams params = child.getLayoutParams() instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) child.getLayoutParams()
                : new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(marginDp);
        parent.addView(child, params);
    }

    private void schedulePoll(long delayMillis) {
        handler.removeCallbacks(pollTask);
        if (!closed && pairing != null) handler.postDelayed(pollTask, Math.max(0L, delayMillis));
    }

    private void updatePairingCountdown() {
        if (pairing == null || pairingCountdown == null || closed) return;
        long remaining = Math.max(0L, pairing.expiresAtElapsed - SystemClock.elapsedRealtime());
        long seconds = (remaining + 999L) / SECOND_MS;
        pairingCountdown.setText(String.format(Locale.getDefault(), "有效时间 %d:%02d",
                seconds / 60L, seconds % 60L));
        if (remaining > 0L) handler.postDelayed(countdownTask, SECOND_MS);
    }

    private void destroyWebView() {
        if (webView == null) return;
        webView.stopLoading();
        webView.setWebViewClient(null);
        if (webView.getParent() instanceof ViewGroup) {
            ((ViewGroup) webView.getParent()).removeView(webView);
        }
        webView.destroy();
        webView = null;
    }

    private String accountLabel(CheckinCenterClient.Account account) {
        String name = account.displayName.isEmpty() ? "小黑盒账号" : account.displayName;
        return account.externalIdMasked.isEmpty() ? name : name + "  " + account.externalIdMasked;
    }

    private String scheduleLabel(CheckinCenterClient.Task task) {
        return task.scheduleTime.isEmpty() ? "未设置" : task.scheduleTime;
    }

    private String taskStateLabel(CheckinCenterClient.Task task) {
        if (task.platformBlocked || task.signBlocked) return "自动签到已暂停";
        return task.active() ? "自动签到已启用" : "自动签到未启用";
    }

    private String taskEnabledLabel(CheckinCenterClient.Task task) {
        if (task.platformBlocked || task.signBlocked) return "已暂停";
        return task.enabled && task.sign ? "已启用" : "未启用";
    }

    private String offsetLabel(int minutes) {
        int value = Math.max(0, minutes);
        return value == 0 ? "无" : value + " 分钟";
    }

    private String windowLabel(CheckinCenterClient.Task task) {
        if (task.windowStart.isEmpty()) return task.windowEnd;
        if (task.windowEnd.isEmpty()) return task.windowStart;
        return task.windowStart + " - " + task.windowEnd;
    }

    private String runTime(CheckinCenterClient.LastRun run) {
        String value = run.finishedAt.isEmpty() ? run.startedAt : run.finishedAt;
        if (value.length() >= 16 && value.charAt(10) == 'T') {
            return value.substring(5, 10) + " " + value.substring(11, 16);
        }
        return value.isEmpty() ? "未知" : value;
    }

    private String runStatusLabel(String value) {
        if ("ok".equalsIgnoreCase(value) || "completed".equalsIgnoreCase(value)) return "成功";
        if ("running".equalsIgnoreCase(value)) return "执行中";
        if ("skipped".equalsIgnoreCase(value)) return "已跳过";
        if ("failed".equalsIgnoreCase(value) || "error".equalsIgnoreCase(value)) return "失败";
        return value.isEmpty() ? "未知" : value;
    }

    private String runMessage(CheckinCenterClient.RunResult result) {
        if ("ok".equalsIgnoreCase(result.status)) return "小黑盒签到任务已完成";
        if ("skipped".equalsIgnoreCase(result.status)) return "今日签到任务已处理";
        return "签到任务已返回结果";
    }

    private int dp(int value) {
        return UiComponents.dp(activity, value, scale);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    public void close() {
        closed = true;
        pairing = null;
        handler.removeCallbacksAndMessages(null);
        destroyWebView();
        root.removeAllViews();
    }

    private static final class PairingSession {
        final CheckinCenterClient.PairingStart start;
        final long expiresAtElapsed;
        int retries;

        PairingSession(CheckinCenterClient.PairingStart start, long expiresAtElapsed) {
            this.start = start;
            this.expiresAtElapsed = expiresAtElapsed;
        }

        boolean expired() {
            return SystemClock.elapsedRealtime() >= expiresAtElapsed;
        }
    }
}
