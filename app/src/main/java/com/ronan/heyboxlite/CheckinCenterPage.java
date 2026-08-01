package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.TimePickerDialog;
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
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

final class CheckinCenterPage {
    interface Host {
        void openCaptcha(String verificationUri);
        void confirmRevoke(Runnable confirmed);
        void showMessage(String message);
    }

    private enum CaptchaAction {
        NONE,
        SEND_SMS,
        SUBMIT_SMS,
        PASSWORD_LOGIN
    }

    private enum LoginMode {
        SMS,
        PASSWORD
    }

    private enum ServiceAccountMode {
        LOGIN,
        REGISTER
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
    private final Runnable registrationEmailCountdownTask =
            this::updateRegistrationEmailCountdown;
    private final boolean roundLayout;
    private State state;
    private PairingSession pairing;
    private CheckinCenterClient.Status status;
    private String errorMessage = "";
    private TextView pairingCountdown;
    private EditText serviceUsernameInput;
    private EditText servicePasswordInput;
    private EditText serviceConfirmPasswordInput;
    private EditText serviceEmailInput;
    private EditText serviceEmailCodeInput;
    private Button pairingApproveButton;
    private Button registrationEmailSendButton;
    private TextView pairingStatus;
    private boolean pairingApprovalInFlight;
    private boolean registrationEmailInFlight;
    private ServiceAccountMode serviceAccountMode = ServiceAccountMode.LOGIN;
    private String serviceUsername = "";
    private String serviceEmail = "";
    private String registrationEmailChallengeId = "";
    private long registrationEmailRetryAtElapsed;
    private long registrationEmailExpiresAtElapsed;
    private EditText smsPhoneInput;
    private EditText smsCodeInput;
    private EditText passwordInput;
    private Button smsSendButton;
    private Button smsSubmitButton;
    private Button passwordSubmitButton;
    private TextView smsStatus;
    private Switch taskEnabledSwitch;
    private View taskTimeButton;
    private Button taskOffsetMinusButton;
    private Button taskOffsetPlusButton;
    private LoginMode loginMode = LoginMode.SMS;
    private String mobilePhone = "";
    private String smsSessionId = "";
    private long smsRetryAtElapsed;
    private long smsExpiresAtElapsed;
    private boolean smsRequestInFlight;
    private CaptchaAction captchaAction = CaptchaAction.NONE;
    private String captchaPhone = "";
    private String captchaCode = "";
    private String captchaPassword = "";
    private boolean taskSettingsInFlight;
    private boolean closed;

    CheckinCenterPage(Activity activity, SessionStore session,
                      CheckinCenterCoordinator coordinator, ThemeTokens tokens, Host host) {
        this.activity = activity;
        this.session = session;
        this.coordinator = coordinator;
        this.tokens = tokens;
        this.host = host;
        this.scale = session.uiScale() / 100.0f;
        this.roundLayout = session.roundScreen()
                || activity.getResources().getConfiguration().isScreenRound();
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
        loadStatus();
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
        if (state == State.PAIRING && !registrationEmailChallengeId.isEmpty()) {
            handler.removeCallbacks(registrationEmailCountdownTask);
            handler.post(registrationEmailCountdownTask);
        }
    }

    void onPause() {
        handler.removeCallbacks(pollTask);
        handler.removeCallbacks(countdownTask);
        handler.removeCallbacks(smsCountdownTask);
        handler.removeCallbacks(registrationEmailCountdownTask);
    }

    private void beginPairing() {
        if (!coordinator.supported()) {
            showError("小黑盒自动签到需要 Android 7.0 或更高版本");
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
                            clearPairingSessionState();
                            showError("无法安全保存签到服务连接，请检查系统安全组件");
                            return;
                        }
                        clearPairingSessionState();
                        state = State.SYNCING;
                        render();
                        host.showMessage("签到服务已连接");
                        loadStatus();
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
                    clearPairingSessionState();
                    state = State.UNPAIRED;
                    errorMessage = "";
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
        loginMode = LoginMode.SMS;
        mobilePhone = "";
        clearCaptchaRequest();
        errorMessage = "";
        state = State.MOBILE_LOGIN;
        render();
    }

    private void sendSmsCode() {
        if (smsRequestInFlight || smsPhoneInput == null) return;
        String phone = smsPhoneInput.getText().toString().trim();
        mobilePhone = phone;
        requestSmsCode(phone, "", "", false);
    }

    private void requestSmsCode(String phone, String captchaTicket, String captchaRandstr,
                                boolean captchaRetry) {
        smsRequestInFlight = true;
        setMobileLoginControls(false);
        setMobileLoginStatus(captchaRetry ? "安全验证通过，正在发送验证码" : "正在发送验证码",
                tokens.muted);
        coordinator.sendSmsCode(phone, captchaTicket, captchaRandstr,
                new CheckinCenterClient.Callback<CheckinCenterClient.SmsSession>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.SmsSession value) {
                        if (closed || state != State.MOBILE_LOGIN) return;
                        clearCaptchaRequest();
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
                        if (!captchaRetry && beginCaptcha(
                                CaptchaAction.SEND_SMS, phone, "", error)) {
                            return;
                        }
                        clearCaptchaRequest();
                        smsRequestInFlight = false;
                        setMobileLoginControls(true);
                        setMobileLoginStatus(captchaRetry && error.captchaRequired()
                                ? "安全验证未通过，请重新发送验证码"
                                : error.getMessage(), tokens.text);
                    }
                });
    }

    private void submitSmsCode() {
        if (smsRequestInFlight || smsCodeInput == null || smsSessionId.isEmpty()) return;
        if (SystemClock.elapsedRealtime() >= smsExpiresAtElapsed) {
            resetSmsSession("验证码已过期，请重新发送");
            return;
        }
        requestSubmitSmsCode(smsCodeInput.getText().toString().trim(), "", "", false);
    }

    private void requestSubmitSmsCode(String code, String captchaTicket, String captchaRandstr,
                                      boolean captchaRetry) {
        smsRequestInFlight = true;
        setMobileLoginControls(false);
        setMobileLoginStatus(captchaRetry ? "安全验证通过，正在继续登录" : "正在验证并连接账号",
                tokens.muted);
        coordinator.submitSmsCode(smsSessionId, code, captchaTicket, captchaRandstr,
                new CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.ConnectedAccount value) {
                        if (closed || state != State.MOBILE_LOGIN) return;
                        clearCaptchaRequest();
                        smsRequestInFlight = false;
                        smsSessionId = "";
                        host.showMessage("手机号登录成功");
                        finishMobileLogin();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (closed || state != State.MOBILE_LOGIN) return;
                        if (!captchaRetry && beginCaptcha(
                                CaptchaAction.SUBMIT_SMS, "", code, error)) {
                            return;
                        }
                        clearCaptchaRequest();
                        smsRequestInFlight = false;
                        smsCodeInput.setEnabled(true);
                        smsSubmitButton.setEnabled(true);
                        updateSmsCountdown();
                        setMobileLoginStatus(captchaRetry && error.captchaRequired()
                                ? "安全验证未通过，请重新提交验证码"
                                : error.getMessage(), tokens.text);
                    }
                });
    }

    private void switchLoginMode(LoginMode mode) {
        if (smsRequestInFlight || mode == loginMode) return;
        if (smsPhoneInput != null) {
            mobilePhone = smsPhoneInput.getText().toString().trim();
        }
        if (passwordInput != null) passwordInput.setText("");
        loginMode = mode;
        clearCaptchaRequest();
        clearSmsViews();
        render();
    }

    private void loginWithPassword() {
        if (smsRequestInFlight || smsPhoneInput == null || passwordInput == null) return;
        String phone = smsPhoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        mobilePhone = phone;
        requestPasswordLogin(phone, password, "", "", false);
    }

    private void requestPasswordLogin(String phone, String password, String captchaTicket,
                                      String captchaRandstr, boolean captchaRetry) {
        smsRequestInFlight = true;
        setMobileLoginControls(false);
        setMobileLoginStatus(captchaRetry ? "安全验证通过，正在继续登录" : "正在登录小黑盒",
                tokens.muted);
        coordinator.loginWithPassword(phone, password, captchaTicket, captchaRandstr,
                new CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.ConnectedAccount value) {
                        if (closed || state != State.MOBILE_LOGIN) return;
                        clearCaptchaRequest();
                        smsRequestInFlight = false;
                        if (passwordInput != null) passwordInput.setText("");
                        host.showMessage("手机号密码登录成功");
                        finishMobileLogin();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (closed || state != State.MOBILE_LOGIN) return;
                        if (!captchaRetry && beginCaptcha(CaptchaAction.PASSWORD_LOGIN,
                                phone, "", password, error)) {
                            return;
                        }
                        clearCaptchaRequest();
                        smsRequestInFlight = false;
                        if (passwordInput != null) passwordInput.setText("");
                        setMobileLoginControls(true);
                        setMobileLoginStatus(captchaRetry && error.captchaRequired()
                                ? "安全验证未通过，请重新登录"
                                : error.getMessage(), tokens.text);
                    }
                });
    }

    private boolean beginCaptcha(CaptchaAction action, String phone, String code,
                                 CheckinCenterClient.ApiError error) {
        return beginCaptcha(action, phone, code, "", error);
    }

    private boolean beginCaptcha(CaptchaAction action, String phone, String code,
                                 String password, CheckinCenterClient.ApiError error) {
        if (!error.captchaRequired()) return false;
        captchaAction = action;
        captchaPhone = phone == null ? "" : phone;
        captchaCode = code == null ? "" : code;
        captchaPassword = password == null ? "" : password;
        setMobileLoginStatus("请完成小黑盒安全验证", tokens.accent);
        host.openCaptcha(error.captchaUri);
        return true;
    }

    void onCaptchaResult(String ticket, String randstr) {
        if (closed || state != State.MOBILE_LOGIN || captchaAction == CaptchaAction.NONE) return;
        CaptchaAction action = captchaAction;
        String phone = captchaPhone;
        String code = captchaCode;
        String password = captchaPassword;
        clearCaptchaRequest();
        if (action == CaptchaAction.SEND_SMS) {
            requestSmsCode(phone, ticket, randstr, true);
        } else if (action == CaptchaAction.SUBMIT_SMS) {
            requestSubmitSmsCode(code, ticket, randstr, true);
        } else {
            requestPasswordLogin(phone, password, ticket, randstr, true);
        }
    }

    void onCaptchaCancelled(String message) {
        if (closed || state != State.MOBILE_LOGIN || captchaAction == CaptchaAction.NONE) return;
        clearCaptchaRequest();
        smsRequestInFlight = false;
        setMobileLoginControls(true);
        updateSmsCountdown();
        setMobileLoginStatus(message == null || message.trim().isEmpty()
                ? "安全验证已取消" : message, tokens.text);
    }

    private void clearCaptchaRequest() {
        captchaAction = CaptchaAction.NONE;
        captchaPhone = "";
        captchaCode = "";
        captchaPassword = "";
    }

    private void finishMobileLogin() {
        handler.removeCallbacks(smsCountdownTask);
        smsRequestInFlight = false;
        mobilePhone = "";
        smsSessionId = "";
        smsRetryAtElapsed = 0L;
        smsExpiresAtElapsed = 0L;
        clearCaptchaRequest();
        clearSmsViews();
        state = State.SYNCING;
        errorMessage = "";
        render();
        loadStatus();
    }

    private void showError(String message) {
        clearPairingSessionState();
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
        applyPageInsets(page, false);
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
        addTop(card, body("可直接登录或注册签到服务账号，无需打开浏览器。", tokens.muted), 8);
        Button connect = primaryButton("连接签到服务");
        connect.setEnabled(coordinator.supported());
        connect.setOnClickListener(view -> {
            UiComponents.press(view);
            beginPairing();
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
        boolean accountConnected = "connected".equalsIgnoreCase(status.account.state);
        LinearLayout card = card();
        card.addView(heading("小黑盒自动签到"));
        String stateLabel = !accountConnected ? "等待手机号登录"
                : state == State.RUNNING ? "执行中" : taskStateLabel(status.task);
        addTop(card, body(stateLabel, state == State.RUNNING ? tokens.accent
                : accountConnected && status.task.active() ? tokens.text : tokens.muted), 5);
        addTop(card, infoRow("小黑盒账号", accountConnected
                ? accountLabel(status.account) : "尚未登录"), 14);
        if (!accountConnected) {
            addTop(card, body("自动签到必须使用手机号验证码或密码登录小黑盒。Lite 的二维码登录仅用于浏览，不会上传到签到服务。",
                    tokens.text), 10);
            page.addView(card);
            addMobileLoginCard(page, false);
            Button revoke = ghostButton("撤销此设备");
            revoke.setOnClickListener(view -> requestRevoke());
            addTop(page, revoke, 9);
            return;
        }
        addTop(card, infoRow("服务器签到", taskEnabledLabel(status.task)), 2);
        addTop(card, infoRow("计划时间", scheduleLabel(status.task)), 2);
        addTop(card, infoRow("随机偏移", offsetLabel(status.task.offsetMinutes)), 2);
        if (!status.task.windowStart.isEmpty() || !status.task.windowEnd.isEmpty()) {
            addTop(card, infoRow("执行区间", windowLabel(status.task)), 2);
        }
        page.addView(card);

        addTaskSettingsCard(page);

        LinearLayout latest = card();
        latest.addView(sectionTitle("最近一次签到"));
        if (status.lastRun == null) {
            addTop(latest, body("暂无执行记录", tokens.muted), 8);
        } else {
            addTop(latest, infoRow("结果", runStatusLabel(status.lastRun)), 8);
            addTop(latest, infoRow("时间", runTime(status.lastRun)), 2);
            String reward = rewardLabel(status.lastRun.checkIn);
            if (!reward.isEmpty()) {
                addTop(latest, infoRow("本次获得", reward), 2);
            }
            String summaryText = runSummary(status.lastRun);
            if (!summaryText.isEmpty()) {
                TextView summary = body(summaryText, tokens.text);
                summary.setLineSpacing(0f, 1.15f);
                addTop(latest, summary, 9);
            }
        }
        addTop(page, latest, 9);

        addMobileLoginCard(page, true);

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

    private void addTaskSettingsCard(LinearLayout page) {
        CheckinCenterClient.Task task = status.task;
        LinearLayout settings = card();
        settings.addView(sectionTitle("自动签到设置"));

        LinearLayout enabledRow = new LinearLayout(activity);
        enabledRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView enabledLabel = body("自动签到", tokens.text);
        enabledRow.addView(enabledLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        taskEnabledSwitch = new Switch(activity);
        taskEnabledSwitch.setChecked(task.enabled);
        taskEnabledSwitch.setEnabled(!taskSettingsInFlight);
        enabledRow.addView(taskEnabledSwitch, new LinearLayout.LayoutParams(-2, dp(36)));
        taskEnabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!taskSettingsInFlight) {
                saveTaskSettings(checked, normalizedScheduleTime(task), task.offsetMinutes);
            }
        });
        addTop(settings, enabledRow, 10);

        taskTimeButton = settingRow("执行时间", scheduleLabel(task));
        taskTimeButton.setEnabled(!taskSettingsInFlight);
        taskTimeButton.setOnClickListener(view -> showTaskTimePicker(task));
        addTop(settings, taskTimeButton, 4);

        LinearLayout offsetRow = new LinearLayout(activity);
        offsetRow.setOrientation(roundLayout ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        offsetRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView offsetTitle = body("随机偏移", tokens.text);
        offsetRow.addView(offsetTitle, roundLayout
                ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout offsetControls = roundLayout ? new LinearLayout(activity) : offsetRow;
        offsetControls.setGravity(Gravity.CENTER);
        taskOffsetMinusButton = compactButton("-");
        taskOffsetMinusButton.setContentDescription("减少随机偏移");
        taskOffsetMinusButton.setEnabled(!taskSettingsInFlight && task.offsetMinutes > 0);
        taskOffsetMinusButton.setOnClickListener(view -> saveTaskSettings(task.enabled,
                normalizedScheduleTime(task), Math.max(0, task.offsetMinutes - 30)));
        offsetControls.addView(taskOffsetMinusButton,
                new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView offsetValue = body(offsetLabel(task.offsetMinutes), tokens.text);
        offsetValue.setGravity(Gravity.CENTER);
        offsetValue.setSingleLine(true);
        offsetControls.addView(offsetValue,
                new LinearLayout.LayoutParams(dp(roundLayout ? 70 : 76), dp(36)));
        taskOffsetPlusButton = compactButton("+");
        taskOffsetPlusButton.setContentDescription("增加随机偏移");
        taskOffsetPlusButton.setEnabled(!taskSettingsInFlight && task.offsetMinutes < 720);
        taskOffsetPlusButton.setOnClickListener(view -> saveTaskSettings(task.enabled,
                normalizedScheduleTime(task), Math.min(720, task.offsetMinutes + 30)));
        offsetControls.addView(taskOffsetPlusButton,
                new LinearLayout.LayoutParams(dp(36), dp(36)));
        if (roundLayout) {
            LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(-1, -2);
            controlsParams.topMargin = dp(5);
            offsetRow.addView(offsetControls, controlsParams);
        }
        addTop(settings, offsetRow, 4);

        if (task.platformBlocked || task.signBlocked) {
            addTop(settings, body("签到服务当前已暂停此任务，设置会保留。", tokens.muted), 7);
        }
        addTop(page, settings, 9);
    }

    private void showTaskTimePicker(CheckinCenterClient.Task task) {
        if (taskSettingsInFlight) return;
        String[] parts = normalizedScheduleTime(task).split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        TimePickerDialog dialog = new TimePickerDialog(activity,
                (view, selectedHour, selectedMinute) -> saveTaskSettings(task.enabled,
                        String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute),
                        task.offsetMinutes), hour, minute, true);
        dialog.setTitle("执行时间");
        dialog.show();
    }

    private void saveTaskSettings(boolean enabled, String scheduleTime, int offsetMinutes) {
        if (taskSettingsInFlight || status == null) return;
        taskSettingsInFlight = true;
        setTaskSettingsControls(false);
        coordinator.updateTaskSettings(enabled, scheduleTime, offsetMinutes,
                new CheckinCenterClient.Callback<CheckinCenterClient.Task>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.Task value) {
                        if (closed) return;
                        taskSettingsInFlight = false;
                        CheckinCenterClient.Status current = status;
                        if (current == null || !coordinator.paired()) {
                            loadStatus();
                            return;
                        }
                        status = new CheckinCenterClient.Status(current.account, value,
                                current.lastRun);
                        state = State.CONNECTED;
                        errorMessage = "";
                        render();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (closed) return;
                        taskSettingsInFlight = false;
                        if (error.authorizationInvalid()) {
                            showError(error.getMessage());
                            return;
                        }
                        host.showMessage(error.getMessage());
                        render();
                    }
                });
    }

    private void setTaskSettingsControls(boolean enabled) {
        if (taskEnabledSwitch != null) taskEnabledSwitch.setEnabled(enabled);
        if (taskTimeButton != null) taskTimeButton.setEnabled(enabled);
        if (taskOffsetMinusButton != null) taskOffsetMinusButton.setEnabled(enabled);
        if (taskOffsetPlusButton != null) taskOffsetPlusButton.setEnabled(enabled);
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
        login.addView(sectionTitle(connected ? "小黑盒账号" : "登录小黑盒"));
        TextView description = body(
                connected
                        ? "需要更换签到账号时，可重新使用手机号验证码或密码登录。"
                        : "自动签到只支持手机号验证码或密码登录。手机号和凭据由签到服务处理，Lite 不会保存。",
                tokens.muted);
        description.setLineSpacing(0f, 1.15f);
        addTop(login, description, 7);
        Button open = connected ? ghostButton("重新登录") : primaryButton("手机号登录");
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
        applyPageInsets(page, true);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        LinearLayout card = card();
        card.addView(heading("手机号登录小黑盒"));
        TextView description = body(
                "登录成功后，服务器会加密保存移动端凭据并用于自动签到。Lite 不保存手机号、验证码或登录凭据。",
                tokens.muted);
        description.setLineSpacing(0f, 1.16f);
        addTop(card, description, 7);

        LinearLayout modes = new LinearLayout(activity);
        Button smsMode = loginMode == LoginMode.SMS
                ? primaryButton(roundLayout ? "验证码" : "短信验证码")
                : ghostButton(roundLayout ? "验证码" : "短信验证码");
        Button passwordMode = loginMode == LoginMode.PASSWORD
                ? primaryButton("密码登录") : ghostButton("密码登录");
        smsMode.setOnClickListener(view -> switchLoginMode(LoginMode.SMS));
        passwordMode.setOnClickListener(view -> switchLoginMode(LoginMode.PASSWORD));
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
        modeParams.rightMargin = dp(4);
        modes.addView(smsMode, modeParams);
        LinearLayout.LayoutParams passwordModeParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
        passwordModeParams.leftMargin = dp(4);
        modes.addView(passwordMode, passwordModeParams);
        addTop(card, modes, 13);

        addTop(card, body("手机号", tokens.text), 14);
        smsPhoneInput = input("+86 13800000000", InputType.TYPE_CLASS_PHONE);
        smsPhoneInput.setText(mobilePhone);
        addTop(card, smsPhoneInput, 6);

        if (loginMode == LoginMode.SMS) {
            smsSendButton = ghostButton("发送验证码");
            smsSendButton.setOnClickListener(view -> {
                UiComponents.press(view);
                sendSmsCode();
            });
            addTop(card, smsSendButton, 8);

            addTop(card, body("短信验证码", tokens.text), 13);
            smsCodeInput = input("4-8 位验证码",
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            addTop(card, smsCodeInput, 6);
            smsSubmitButton = primaryButton("登录并连接");
            smsSubmitButton.setOnClickListener(view -> {
                UiComponents.press(view);
                submitSmsCode();
            });
            addTop(card, smsSubmitButton, 8);
            smsStatus = body("验证码由小黑盒发送，发送操作不会自动重试", tokens.muted);
        } else {
            addTop(card, body("密码", tokens.text), 13);
            passwordInput = input("小黑盒登录密码",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            addTop(card, passwordInput, 6);
            passwordSubmitButton = primaryButton("登录并连接");
            passwordSubmitButton.setOnClickListener(view -> {
                UiComponents.press(view);
                loginWithPassword();
            });
            addTop(card, passwordSubmitButton, 8);
            smsStatus = body("密码只用于本次登录，不会保存在 Lite 或签到服务中", tokens.muted);
        }
        smsStatus.setLineSpacing(0f, 1.14f);
        addTop(card, smsStatus, 10);
        page.addView(card);

        Button back = ghostButton("返回签到状态");
        back.setOnClickListener(view -> finishMobileLogin());
        addTop(page, back, 9);
        root.addView(scroll, match());
        setMobileLoginControls(!smsRequestInFlight);
        if (loginMode == LoginMode.SMS && !smsSessionId.isEmpty()) updateSmsCountdown();
    }

    private void renderPairing() {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout page = column(tokens.background);
        applyPageInsets(page, true);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        LinearLayout card = card();
        card.addView(heading("连接签到服务"));
        TextView description = body(
                serviceAccountMode == ServiceAccountMode.LOGIN
                        ? "登录签到服务账号并连接此设备。这里不是小黑盒手机号登录。"
                        : "创建签到服务账号并连接此设备，全程无需打开浏览器。",
                tokens.muted);
        description.setLineSpacing(0f, 1.16f);
        addTop(card, description, 7);
        addTop(card, body("配对码  " + pairing.start.userCode, tokens.muted), 9);
        pairingCountdown = body("", tokens.muted);
        addTop(card, pairingCountdown, 3);

        if (pairing.start.registrationOpen) {
            LinearLayout modes = new LinearLayout(activity);
            String loginLabel = roundLayout ? "登录" : "已有账号";
            String registerLabel = roundLayout ? "注册" : "注册账号";
            Button loginMode = serviceAccountMode == ServiceAccountMode.LOGIN
                    ? primaryButton(loginLabel) : ghostButton(loginLabel);
            Button registerMode = serviceAccountMode == ServiceAccountMode.REGISTER
                    ? primaryButton(registerLabel) : ghostButton(registerLabel);
            loginMode.setOnClickListener(view -> switchServiceAccountMode(
                    ServiceAccountMode.LOGIN));
            registerMode.setOnClickListener(view -> switchServiceAccountMode(
                    ServiceAccountMode.REGISTER));
            LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(38), 1f);
            left.rightMargin = dp(4);
            modes.addView(loginMode, left);
            LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(38), 1f);
            right.leftMargin = dp(4);
            modes.addView(registerMode, right);
            addTop(card, modes, 12);
        } else {
            serviceAccountMode = ServiceAccountMode.LOGIN;
        }

        addTop(card, body("签到服务账号", tokens.text), 14);
        serviceUsernameInput = input("账号", InputType.TYPE_CLASS_TEXT);
        serviceUsernameInput.setText(serviceUsername);
        addTop(card, serviceUsernameInput, 6);
        addTop(card, body("密码", tokens.text), 13);
        servicePasswordInput = input("密码",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addTop(card, servicePasswordInput, 6);

        if (serviceAccountMode == ServiceAccountMode.REGISTER) {
            addTop(card, body("确认密码", tokens.text), 13);
            serviceConfirmPasswordInput = input("再次输入密码",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            addTop(card, serviceConfirmPasswordInput, 6);
            TextView passwordRule = body(
                    "至少 12 位，并包含大小写字母、数字、符号中的三类", tokens.muted);
            passwordRule.setLineSpacing(0f, 1.12f);
            addTop(card, passwordRule, 6);
            if (pairing.start.registrationEmailRequired) {
                addRegistrationEmailFields(card);
            }
        }

        pairingApproveButton = primaryButton(serviceAccountMode == ServiceAccountMode.LOGIN
                ? "登录并连接" : "注册并连接");
        pairingApproveButton.setOnClickListener(view -> {
            UiComponents.press(view);
            submitServiceAccount();
        });
        addTop(card, pairingApproveButton, 10);
        pairingStatus = body("账号密码只用于本次请求，不会保存在 Lite 中", tokens.muted);
        pairingStatus.setLineSpacing(0f, 1.14f);
        addTop(card, pairingStatus, 9);
        page.addView(card);

        Button cancel = ghostButton("取消");
        cancel.setOnClickListener(view -> cancelPairing());
        addTop(page, cancel, 9);
        root.addView(scroll, match());
        updatePairingCountdown();
        updateRegistrationEmailCountdown();
    }

    private void addRegistrationEmailFields(LinearLayout card) {
        addTop(card, body("邮箱", tokens.text), 13);
        serviceEmailInput = input("用于接收验证码",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        serviceEmailInput.setText(serviceEmail);
        addTop(card, serviceEmailInput, 6);
        registrationEmailSendButton = ghostButton("发送邮箱验证码");
        registrationEmailSendButton.setOnClickListener(view -> {
            UiComponents.press(view);
            sendRegistrationEmail();
        });
        addTop(card, registrationEmailSendButton, 7);
        addTop(card, body("邮箱验证码", tokens.text), 13);
        serviceEmailCodeInput = input("6 位验证码",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        addTop(card, serviceEmailCodeInput, 6);
    }

    private void switchServiceAccountMode(ServiceAccountMode mode) {
        if (pairingApprovalInFlight || registrationEmailInFlight || mode == serviceAccountMode) {
            return;
        }
        rememberServiceInputs();
        serviceAccountMode = mode;
        registrationEmailChallengeId = "";
        registrationEmailRetryAtElapsed = 0L;
        registrationEmailExpiresAtElapsed = 0L;
        handler.removeCallbacks(registrationEmailCountdownTask);
        clearPairingViews();
        render();
    }

    private void submitServiceAccount() {
        if (serviceAccountMode == ServiceAccountMode.REGISTER) registerPairing();
        else approvePairing();
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

    private void registerPairing() {
        PairingSession current = pairing;
        if (pairingApprovalInFlight || current == null || serviceUsernameInput == null
                || servicePasswordInput == null || serviceConfirmPasswordInput == null) return;
        if (current.expired()) {
            showError("配对已过期，请重新连接");
            return;
        }
        String username = serviceUsernameInput.getText().toString().trim();
        String password = servicePasswordInput.getText().toString();
        String confirmation = serviceConfirmPasswordInput.getText().toString();
        if (!password.equals(confirmation)) {
            clearServicePasswords();
            setPairingStatus("两次输入的密码不一致", tokens.text);
            return;
        }
        boolean emailRequired = current.start.registrationEmailRequired;
        String email = serviceEmailInput == null ? ""
                : serviceEmailInput.getText().toString().trim();
        String emailCode = serviceEmailCodeInput == null ? ""
                : serviceEmailCodeInput.getText().toString().trim();
        if (emailRequired && (registrationEmailChallengeId.isEmpty()
                || SystemClock.elapsedRealtime() >= registrationEmailExpiresAtElapsed)) {
            setPairingStatus("请先发送并填写有效的邮箱验证码", tokens.text);
            return;
        }
        serviceUsername = username;
        serviceEmail = email;
        pairingApprovalInFlight = true;
        setPairingControlsEnabled(false);
        setPairingStatus("正在创建签到服务账号", tokens.muted);
        coordinator.registerPairing(current.start.userCode, username, password, email,
                registrationEmailChallengeId, emailCode, emailRequired,
                new CheckinCenterClient.Callback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean value) {
                        if (closed || pairing != current || state != State.PAIRING) return;
                        pairingApprovalInFlight = false;
                        clearServicePasswords();
                        setPairingStatus("注册成功，正在完成设备连接", tokens.accent);
                        schedulePoll(0L);
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (closed || pairing != current || state != State.PAIRING) return;
                        pairingApprovalInFlight = false;
                        clearServicePasswords();
                        setPairingControlsEnabled(true);
                        setPairingStatus(error.getMessage(), tokens.text);
                    }
                });
    }

    private void sendRegistrationEmail() {
        PairingSession current = pairing;
        if (registrationEmailInFlight || current == null || serviceEmailInput == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now < registrationEmailRetryAtElapsed) return;
        serviceEmail = serviceEmailInput.getText().toString().trim();
        registrationEmailInFlight = true;
        setPairingControlsEnabled(false);
        setPairingStatus("正在发送邮箱验证码", tokens.muted);
        coordinator.sendRegistrationEmail(current.start.userCode, serviceEmail,
                new CheckinCenterClient.Callback<CheckinCenterClient.RegistrationEmailSession>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.RegistrationEmailSession value) {
                        if (closed || pairing != current || state != State.PAIRING) return;
                        registrationEmailInFlight = false;
                        registrationEmailChallengeId = value.challengeId;
                        long receivedAt = SystemClock.elapsedRealtime();
                        registrationEmailRetryAtElapsed = receivedAt
                                + value.retryAfterSeconds * SECOND_MS;
                        registrationEmailExpiresAtElapsed = receivedAt
                                + value.expiresInSeconds * SECOND_MS;
                        setPairingControlsEnabled(true);
                        setPairingStatus("邮箱验证码已发送", tokens.accent);
                        updateRegistrationEmailCountdown();
                        if (serviceEmailCodeInput != null) serviceEmailCodeInput.requestFocus();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (closed || pairing != current || state != State.PAIRING) return;
                        registrationEmailInFlight = false;
                        setPairingControlsEnabled(true);
                        setPairingStatus(error.getMessage(), tokens.text);
                    }
                });
    }

    private void cancelPairing() {
        clearPairingSessionState();
        state = State.UNPAIRED;
        errorMessage = "";
        render();
    }

    private void setPairingControlsEnabled(boolean enabled) {
        if (serviceUsernameInput != null) serviceUsernameInput.setEnabled(enabled);
        if (servicePasswordInput != null) servicePasswordInput.setEnabled(enabled);
        if (serviceConfirmPasswordInput != null) serviceConfirmPasswordInput.setEnabled(enabled);
        if (serviceEmailInput != null) serviceEmailInput.setEnabled(enabled);
        if (serviceEmailCodeInput != null) serviceEmailCodeInput.setEnabled(enabled);
        if (registrationEmailSendButton != null) {
            registrationEmailSendButton.setEnabled(enabled
                    && SystemClock.elapsedRealtime() >= registrationEmailRetryAtElapsed);
        }
        if (pairingApproveButton != null) pairingApproveButton.setEnabled(enabled);
    }

    private void setPairingStatus(String message, int color) {
        if (pairingStatus == null) return;
        pairingStatus.setText(message == null ? "连接失败，请稍后重试" : message);
        pairingStatus.setTextColor(color);
    }

    private void clearPairingViews() {
        clearServicePasswords();
        serviceUsernameInput = null;
        servicePasswordInput = null;
        serviceConfirmPasswordInput = null;
        serviceEmailInput = null;
        serviceEmailCodeInput = null;
        pairingApproveButton = null;
        registrationEmailSendButton = null;
        pairingStatus = null;
    }

    private void clearPairingSessionState() {
        handler.removeCallbacks(pollTask);
        handler.removeCallbacks(countdownTask);
        handler.removeCallbacks(registrationEmailCountdownTask);
        pairing = null;
        pairingApprovalInFlight = false;
        registrationEmailInFlight = false;
        serviceAccountMode = ServiceAccountMode.LOGIN;
        serviceUsername = "";
        serviceEmail = "";
        registrationEmailChallengeId = "";
        registrationEmailRetryAtElapsed = 0L;
        registrationEmailExpiresAtElapsed = 0L;
        clearPairingViews();
    }

    private void rememberServiceInputs() {
        if (serviceUsernameInput != null) {
            serviceUsername = serviceUsernameInput.getText().toString().trim();
        }
        if (serviceEmailInput != null) {
            serviceEmail = serviceEmailInput.getText().toString().trim();
        }
    }

    private void clearServicePasswords() {
        if (servicePasswordInput != null) servicePasswordInput.setText("");
        if (serviceConfirmPasswordInput != null) serviceConfirmPasswordInput.setText("");
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
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(roundLayout ? 40 : 42)));
        return view;
    }

    private void setMobileLoginControls(boolean enabled) {
        if (smsPhoneInput == null) return;
        if (loginMode == LoginMode.PASSWORD) {
            smsPhoneInput.setEnabled(enabled);
            if (passwordInput != null) passwordInput.setEnabled(enabled);
            if (passwordSubmitButton != null) passwordSubmitButton.setEnabled(enabled);
            return;
        }
        if (smsCodeInput == null || smsSendButton == null || smsSubmitButton == null) return;
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

    private void updateRegistrationEmailCountdown() {
        handler.removeCallbacks(registrationEmailCountdownTask);
        if (closed || state != State.PAIRING || registrationEmailSendButton == null) return;
        long now = SystemClock.elapsedRealtime();
        if (!registrationEmailChallengeId.isEmpty()
                && now >= registrationEmailExpiresAtElapsed) {
            registrationEmailChallengeId = "";
            registrationEmailRetryAtElapsed = 0L;
            registrationEmailExpiresAtElapsed = 0L;
        }
        long retrySeconds = Math.max(0L,
                (registrationEmailRetryAtElapsed - now + 999L) / SECOND_MS);
        registrationEmailSendButton.setText(retrySeconds > 0L
                ? retrySeconds + " 秒后可重发" : "发送邮箱验证码");
        registrationEmailSendButton.setEnabled(!pairingApprovalInFlight
                && !registrationEmailInFlight && retrySeconds == 0L);
        if (retrySeconds > 0L || !registrationEmailChallengeId.isEmpty()) {
            handler.postDelayed(registrationEmailCountdownTask, SECOND_MS);
        }
    }

    private void resetSmsSession(String message) {
        handler.removeCallbacks(smsCountdownTask);
        smsSessionId = "";
        smsRetryAtElapsed = 0L;
        smsExpiresAtElapsed = 0L;
        smsRequestInFlight = false;
        clearCaptchaRequest();
        if (smsCodeInput != null) smsCodeInput.setText("");
        setMobileLoginControls(true);
        if (smsSendButton != null) smsSendButton.setText("发送验证码");
        setMobileLoginStatus(message, tokens.text);
    }

    private void clearSmsViews() {
        if (smsPhoneInput != null) smsPhoneInput.setText("");
        if (smsCodeInput != null) smsCodeInput.setText("");
        if (passwordInput != null) passwordInput.setText("");
        smsPhoneInput = null;
        smsCodeInput = null;
        passwordInput = null;
        smsSendButton = null;
        smsSubmitButton = null;
        passwordSubmitButton = null;
        smsStatus = null;
    }

    private LinearLayout infoRow(String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(roundLayout ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        TextView left = body(label, tokens.muted);
        row.addView(left, roundLayout
                ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 0.44f));
        TextView right = body(value, tokens.text);
        right.setGravity(roundLayout ? Gravity.START : Gravity.END);
        right.setMaxLines(3);
        LinearLayout.LayoutParams rightParams = roundLayout
                ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 0.56f);
        if (roundLayout) rightParams.topMargin = dp(2);
        else rightParams.leftMargin = dp(8);
        row.addView(right, rightParams);
        return row;
    }

    private LinearLayout card() {
        LinearLayout card = column(Color.TRANSPARENT);
        int horizontal = roundLayout ? dp(12) : dp(14);
        int vertical = roundLayout ? dp(11) : dp(13);
        card.setPadding(horizontal, vertical, horizontal, vertical);
        Compat.setBackground(card, UiComponents.card(activity, tokens, scale));
        return card;
    }

    private void applyPageInsets(LinearLayout page, boolean subpage) {
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int height = activity.getResources().getDisplayMetrics().heightPixels;
        int horizontal = roundLayout
                ? RoundLayoutMetrics.componentInset(width,
                RoundLayoutMetrics.PAGE_HORIZONTAL_RATIO, dp(10))
                : dp(10);
        int top = roundLayout
                ? RoundLayoutMetrics.componentInset(Math.min(width, height),
                subpage ? RoundLayoutMetrics.SUBPAGE_TOP_RATIO
                        : RoundLayoutMetrics.PAGE_TOP_RATIO, dp(8))
                : dp(8);
        page.setPadding(horizontal, top, horizontal,
                roundLayout ? Math.max(top, dp(18)) : dp(18));
    }

    private LinearLayout column(int color) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    private TextView heading(String value) {
        TextView view = label(value, roundLayout ? 17f : 18f, tokens.text);
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

    private LinearLayout settingRow(String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(11), 0, dp(11), 0);
        row.setMinimumHeight(dp(42));
        Compat.setBackground(row, UiComponents.ghostButton(activity, tokens, scale));
        row.addView(body(label, tokens.text), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView current = body(value, tokens.muted);
        current.setGravity(Gravity.END);
        current.setSingleLine(true);
        row.addView(current, new LinearLayout.LayoutParams(-2, -2));
        row.setContentDescription(label + "，当前" + value);
        return row;
    }

    private Button compactButton(String value) {
        Button button = ghostButton(value);
        button.setTextSize(16f * session.textScale() / 100.0f);
        button.setPadding(0, 0, 0, 0);
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
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(roundLayout ? 38 : 42)));
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

    private String normalizedScheduleTime(CheckinCenterClient.Task task) {
        return task.scheduleTime.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
                ? task.scheduleTime : "08:30";
    }

    private String runStatusLabel(CheckinCenterClient.LastRun run) {
        if (run.checkIn.checkedIn) return "已签到";
        String value = run.status;
        if ("ok".equalsIgnoreCase(value) || "completed".equalsIgnoreCase(value)) return "成功";
        if ("running".equalsIgnoreCase(value)) return "执行中";
        if ("skipped".equalsIgnoreCase(value)) return "未执行";
        if ("failed".equalsIgnoreCase(value) || "error".equalsIgnoreCase(value)) return "失败";
        return value.isEmpty() ? "未知" : value;
    }

    private String runMessage(CheckinCenterClient.RunResult result) {
        if (result.checkIn.checkedIn) {
            String reward = rewardLabel(result.checkIn);
            return reward.isEmpty() ? "已签到" : "已签到，获得 " + reward;
        }
        if ("ok".equalsIgnoreCase(result.status)) return "小黑盒签到任务已完成";
        if ("skipped".equalsIgnoreCase(result.status)) return "今日没有需要执行的签到任务";
        return "签到任务已返回结果";
    }

    private String runSummary(CheckinCenterClient.LastRun run) {
        if (run.checkIn.checkedIn) {
            return run.checkIn.newlySigned ? "今日签到已完成" : "今日已签到";
        }
        return run.summary;
    }

    private String rewardLabel(CheckinCenterClient.CheckinResult result) {
        if (result == null) return "";
        StringBuilder value = new StringBuilder();
        if (result.coinDelta >= 0) value.append(result.coinDelta).append(" 盒币");
        if (result.experienceDelta >= 0) {
            if (value.length() > 0) value.append("、");
            value.append(result.experienceDelta).append(" 经验");
        }
        return value.toString();
    }

    private int dp(int value) {
        return UiComponents.dp(activity, value, scale);
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    public void close() {
        closed = true;
        clearCaptchaRequest();
        mobilePhone = "";
        handler.removeCallbacksAndMessages(null);
        clearPairingSessionState();
        clearSmsViews();
        taskEnabledSwitch = null;
        taskTimeButton = null;
        taskOffsetMinusButton = null;
        taskOffsetPlusButton = null;
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
