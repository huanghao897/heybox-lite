package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
        MOBILE_LOGIN,
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
    private final Runnable smsCountdownTask = this::updateSmsCountdown;
    private State state;
    private PairingSession pairing;
    private CheckinCenterClient.Status status;
    private String errorMessage = "";
    private TextView pairingCountdown;
    private EditText serviceUsernameInput;
    private EditText servicePasswordInput;
    private Button pairingApproveButton;
    private TextView pairingStatus;
    private boolean pairingApprovalInFlight;
    private EditText smsPhoneInput;
    private EditText smsCodeInput;
    private Button smsSendButton;
    private Button smsSubmitButton;
    private TextView smsStatus;
    private String smsSessionId = "";
    private long smsRetryAtElapsed;
    private long smsExpiresAtElapsed;
    private boolean smsRequestInFlight;
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
        if (state == State.MOBILE_LOGIN) {
            finishMobileLogin();
            return true;
        }
        if (state == State.PAIRING) {
            cancelPairing();
            return true;
        }
        return false;
    }

    void onResume() {
        if (pairing != null) {
            schedulePoll(0L);
            handler.removeCallbacks(countdownTask);
            handler.post(countdownTask);
        }
        if (state == State.MOBILE_LOGIN && !smsSessionId.isEmpty()) {
            handler.removeCallbacks(smsCountdownTask);
            handler.post(smsCountdownTask);
        }
    }

    void onPause() {
        handler.removeCallbacks(pollTask);
        handler.removeCallbacks(countdownTask);
        handler.removeCallbacks(smsCountdownTask);
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
                        clearPairingViews();
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
                    clearPairingViews();
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

    private void openMobileLogin() {
        if (!coordinator.paired()) {
            showError("请先连接签到服务");
            return;
        }
        smsSessionId = "";
        smsRetryAtElapsed = 0L;
        smsExpiresAtElapsed = 0L;
        smsRequestInFlight = false;
        errorMessage = "";
        state = State.MOBILE_LOGIN;
        render();
    }

    private void sendSmsCode() {
        if (smsRequestInFlight || smsPhoneInput == null) return;
        String phone = smsPhoneInput.getText().toString().trim();
        smsRequestInFlight = true;
        setMobileLoginControls(false);
        setMobileLoginStatus("正在发送验证码", tokens.muted);
        coordinator.sendSmsCode(phone,
                new CheckinCenterClient.Callback<CheckinCenterClient.SmsSession>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.SmsSession value) {
                        if (closed || state != State.MOBILE_LOGIN) return;
                        smsRequestInFlight = false;
                        smsSessionId = value.sessionId;
                        long now = SystemClock.elapsedRealtime();
                        smsRetryAtElapsed = now + value.retryAfterSeconds * SECOND_MS;
                        smsExpiresAtElapsed = now + value.expiresInSeconds * SECOND_MS;
                        smsPhoneInput.setEnabled(false);
                        smsCodeInput.setEnabled(true);
                        smsSubmitButton.setEnabled(true);
                        setMobileLoginStatus("验证码已发送，10 分钟内有效", tokens.accent);
                        handler.removeCallbacks(smsCountdownTask);
                        handler.post(smsCountdownTask);
                        smsCodeInput.requestFocus();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (closed || state != State.MOBILE_LOGIN) return;
                        smsRequestInFlight = false;
                        setMobileLoginControls(true);
                        setMobileLoginStatus(error.getMessage(), tokens.text);
                    }
                });
    }

    private void submitSmsCode() {
        if (smsRequestInFlight || smsCodeInput == null || smsSessionId.isEmpty()) return;
        if (SystemClock.elapsedRealtime() >= smsExpiresAtElapsed) {
            resetSmsSession("验证码已过期，请重新发送");
            return;
        }
        smsRequestInFlight = true;
        setMobileLoginControls(false);
        setMobileLoginStatus("正在验证并连接账号", tokens.muted);
        coordinator.submitSmsCode(smsSessionId, smsCodeInput.getText().toString(),
                new CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.ConnectedAccount value) {
                        if (closed || state != State.MOBILE_LOGIN) return;
                        smsRequestInFlight = false;
                        smsSessionId = "";
                        host.showMessage("手机号登录成功");
                        finishMobileLogin();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (closed || state != State.MOBILE_LOGIN) return;
                        smsRequestInFlight = false;
                        smsCodeInput.setEnabled(true);
                        smsSubmitButton.setEnabled(true);
                        updateSmsCountdown();
                        setMobileLoginStatus(error.getMessage(), tokens.text);
                    }
                });
    }

    private void finishMobileLogin() {
        handler.removeCallbacks(smsCountdownTask);
        smsRequestInFlight = false;
        smsSessionId = "";
        smsRetryAtElapsed = 0L;
        smsExpiresAtElapsed = 0L;
        clearSmsViews();
        state = State.SYNCING;
        errorMessage = "";
        render();
        loadStatus();
    }

    private void showError(String message) {
        pairing = null;
        pairingApprovalInFlight = false;
        clearPairingViews();
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
        if (state == State.MOBILE_LOGIN) {
            renderMobileLogin();
            return;
        }
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

        addMobileLoginCard(page, "connected".equalsIgnoreCase(status.account.state));

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
        addMobileLoginCard(page, false);
        Button retry = primaryButton("重新加载");
        retry.setOnClickListener(view -> refresh());
        addTop(page, retry, 10);
        if (coordinator.paired()) {
            Button revoke = ghostButton("撤销此设备");
            revoke.setOnClickListener(view -> requestRevoke());
            addTop(page, revoke, 7);
        }
    }

    private void addMobileLoginCard(LinearLayout page, boolean connected) {
        LinearLayout login = card();
        login.addView(sectionTitle("手机号登录小黑盒"));
        TextView description = body(
                "用于生成服务器自动签到所需的移动端凭据。手机号和短信验证码仅由签到服务处理，Lite 不会保存。",
                tokens.muted);
        description.setLineSpacing(0f, 1.15f);
        addTop(login, description, 7);
        Button open = ghostButton(connected ? "重新登录" : "手机号登录");
        open.setOnClickListener(view -> {
            UiComponents.press(view);
            openMobileLogin();
        });
        addTop(login, open, 11);
        addTop(page, login, 9);
    }

    private void renderMobileLogin() {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout page = column(tokens.background);
        page.setPadding(dp(10), dp(8), dp(10), dp(18));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        LinearLayout card = card();
        card.addView(heading("手机号登录小黑盒"));
        TextView description = body(
                "登录成功后，服务器会加密保存移动端凭据并用于自动签到。Lite 不保存手机号、验证码或登录凭据。",
                tokens.muted);
        description.setLineSpacing(0f, 1.16f);
        addTop(card, description, 7);

        addTop(card, body("手机号", tokens.text), 14);
        smsPhoneInput = input("+86 13800000000", InputType.TYPE_CLASS_PHONE);
        addTop(card, smsPhoneInput, 6);
        smsSendButton = ghostButton("发送验证码");
        smsSendButton.setOnClickListener(view -> {
            UiComponents.press(view);
            sendSmsCode();
        });
        addTop(card, smsSendButton, 8);

        addTop(card, body("短信验证码", tokens.text), 13);
        smsCodeInput = input("4-8 位验证码",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        smsCodeInput.setEnabled(false);
        addTop(card, smsCodeInput, 6);
        smsSubmitButton = primaryButton("登录并连接");
        smsSubmitButton.setEnabled(false);
        smsSubmitButton.setOnClickListener(view -> {
            UiComponents.press(view);
            submitSmsCode();
        });
        addTop(card, smsSubmitButton, 8);

        smsStatus = body("验证码由小黑盒发送，发送操作不会自动重试", tokens.muted);
        smsStatus.setLineSpacing(0f, 1.14f);
        addTop(card, smsStatus, 10);
        page.addView(card);

        Button back = ghostButton("返回签到状态");
        back.setOnClickListener(view -> finishMobileLogin());
        addTop(page, back, 9);
        root.addView(scroll, match());
    }

    private void renderPairing() {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout page = column(tokens.background);
        page.setPadding(dp(10), dp(8), dp(10), dp(18));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        LinearLayout card = card();
        card.addView(heading("连接签到服务"));
        TextView description = body(
                "输入签到服务账号完成设备连接。这里不是小黑盒手机号登录，账号和密码不会保存在 Lite 中。",
                tokens.muted);
        description.setLineSpacing(0f, 1.16f);
        addTop(card, description, 7);
        addTop(card, body("配对码  " + pairing.start.userCode, tokens.text), 12);
        pairingCountdown = body("", tokens.muted);
        addTop(card, pairingCountdown, 3);

        addTop(card, body("签到服务账号", tokens.text), 14);
        serviceUsernameInput = input("账号", InputType.TYPE_CLASS_TEXT);
        addTop(card, serviceUsernameInput, 6);
        addTop(card, body("密码", tokens.text), 13);
        servicePasswordInput = input("密码",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addTop(card, servicePasswordInput, 6);

        pairingApproveButton = primaryButton("确认连接");
        pairingApproveButton.setOnClickListener(view -> {
            UiComponents.press(view);
            approvePairing();
        });
        addTop(card, pairingApproveButton, 10);
        pairingStatus = body("连接过程使用固定证书加密，不会打开网页", tokens.muted);
        pairingStatus.setLineSpacing(0f, 1.14f);
        addTop(card, pairingStatus, 9);
        page.addView(card);

        Button cancel = ghostButton("取消");
        cancel.setOnClickListener(view -> cancelPairing());
        addTop(page, cancel, 9);
        root.addView(scroll, match());
        updatePairingCountdown();
    }

    private void approvePairing() {
        PairingSession current = pairing;
        if (pairingApprovalInFlight || current == null
                || serviceUsernameInput == null || servicePasswordInput == null) return;
        if (current.expired()) {
            showError("配对已过期，请重新连接");
            return;
        }
        String username = serviceUsernameInput.getText().toString();
        String password = servicePasswordInput.getText().toString();
        pairingApprovalInFlight = true;
        setPairingControlsEnabled(false);
        setPairingStatus("正在验证签到服务账号", tokens.muted);
        coordinator.approvePairing(current.start.userCode, username, password,
                new CheckinCenterClient.Callback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean value) {
                        if (closed || pairing != current || state != State.PAIRING) return;
                        pairingApprovalInFlight = false;
                        if (servicePasswordInput != null) servicePasswordInput.setText("");
                        setPairingStatus("验证成功，正在完成设备连接", tokens.accent);
                        schedulePoll(0L);
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (closed || pairing != current || state != State.PAIRING) return;
                        pairingApprovalInFlight = false;
                        if (servicePasswordInput != null) {
                            servicePasswordInput.setText("");
                            servicePasswordInput.requestFocus();
                        }
                        setPairingControlsEnabled(true);
                        setPairingStatus(error.getMessage(), tokens.text);
                    }
                });
    }

    private void cancelPairing() {
        handler.removeCallbacks(pollTask);
        handler.removeCallbacks(countdownTask);
        pairing = null;
        pairingApprovalInFlight = false;
        clearPairingViews();
        state = State.UNPAIRED;
        errorMessage = "";
        render();
    }

    private void setPairingControlsEnabled(boolean enabled) {
        if (serviceUsernameInput != null) serviceUsernameInput.setEnabled(enabled);
        if (servicePasswordInput != null) servicePasswordInput.setEnabled(enabled);
        if (pairingApproveButton != null) pairingApproveButton.setEnabled(enabled);
    }

    private void setPairingStatus(String message, int color) {
        if (pairingStatus == null) return;
        pairingStatus.setText(message == null ? "连接失败，请稍后重试" : message);
        pairingStatus.setTextColor(color);
    }

    private void clearPairingViews() {
        if (serviceUsernameInput != null) serviceUsernameInput.setText("");
        if (servicePasswordInput != null) servicePasswordInput.setText("");
        serviceUsernameInput = null;
        servicePasswordInput = null;
        pairingApproveButton = null;
        pairingStatus = null;
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

    private EditText input(String hint, int inputType) {
        EditText view = new EditText(activity);
        view.setSingleLine(true);
        view.setTextSize(13f * session.textScale() / 100.0f);
        view.setTextColor(tokens.text);
        view.setHintTextColor(tokens.muted);
        view.setHint(hint);
        view.setInputType(inputType);
        view.setSaveEnabled(false);
        view.setMinHeight(0);
        view.setMinimumHeight(0);
        view.setPadding(dp(11), 0, dp(11), 0);
        Compat.setBackground(view, UiComponents.outlinedTextField(activity, tokens, scale));
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(42)));
        return view;
    }

    private void setMobileLoginControls(boolean enabled) {
        if (smsPhoneInput == null || smsCodeInput == null
                || smsSendButton == null || smsSubmitButton == null) return;
        if (!enabled) {
            smsPhoneInput.setEnabled(false);
            smsCodeInput.setEnabled(false);
            smsSendButton.setEnabled(false);
            smsSubmitButton.setEnabled(false);
            return;
        }
        boolean hasSession = !smsSessionId.isEmpty();
        smsPhoneInput.setEnabled(!hasSession);
        smsCodeInput.setEnabled(hasSession);
        smsSubmitButton.setEnabled(hasSession);
        smsSendButton.setEnabled(!hasSession
                || SystemClock.elapsedRealtime() >= smsRetryAtElapsed);
    }

    private void setMobileLoginStatus(String message, int color) {
        if (smsStatus == null) return;
        smsStatus.setText(message == null ? "请求失败，请稍后重试" : message);
        smsStatus.setTextColor(color);
    }

    private void updateSmsCountdown() {
        handler.removeCallbacks(smsCountdownTask);
        if (closed || state != State.MOBILE_LOGIN || smsSessionId.isEmpty()
                || smsSendButton == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now >= smsExpiresAtElapsed) {
            resetSmsSession("验证码已过期，请重新发送");
            return;
        }
        long retrySeconds = Math.max(0L, (smsRetryAtElapsed - now + 999L) / SECOND_MS);
        smsSendButton.setText(retrySeconds > 0L
                ? retrySeconds + " 秒后可重发" : "重新发送验证码");
        smsSendButton.setEnabled(!smsRequestInFlight && retrySeconds == 0L);
        handler.postDelayed(smsCountdownTask, SECOND_MS);
    }

    private void resetSmsSession(String message) {
        handler.removeCallbacks(smsCountdownTask);
        smsSessionId = "";
        smsRetryAtElapsed = 0L;
        smsExpiresAtElapsed = 0L;
        smsRequestInFlight = false;
        if (smsCodeInput != null) smsCodeInput.setText("");
        setMobileLoginControls(true);
        if (smsSendButton != null) smsSendButton.setText("发送验证码");
        setMobileLoginStatus(message, tokens.text);
    }

    private void clearSmsViews() {
        if (smsPhoneInput != null) smsPhoneInput.setText("");
        if (smsCodeInput != null) smsCodeInput.setText("");
        smsPhoneInput = null;
        smsCodeInput = null;
        smsSendButton = null;
        smsSubmitButton = null;
        smsStatus = null;
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
        clearPairingViews();
        clearSmsViews();
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
