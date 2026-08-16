package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

final class CheckinCenterPage {
    interface Host {
        void closePage();
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
        TASK_SETTINGS,
        BILLING,
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
    private final SettingsUi settingsUi;
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
    private String registrationEmailChallengeAddress = "";
    private long registrationEmailRetryAtElapsed;
    private long registrationEmailExpiresAtElapsed;
    private EditText smsPhoneInput;
    private EditText smsCodeInput;
    private EditText passwordInput;
    private Button smsSendButton;
    private Button smsSubmitButton;
    private Button passwordSubmitButton;
    private TextView smsStatus;
    private View taskEnabledSwitch;
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
    private CheckinPaymentPage paymentPage;
    private boolean closed;
    private boolean contentPresented;

    CheckinCenterPage(Activity activity, SessionStore session,
                      CheckinCenterCoordinator coordinator, ThemeTokens tokens, Host host) {
        this.activity = activity;
        this.session = session;
        this.coordinator = coordinator;
        this.tokens = tokens;
        this.host = host;
        this.scale = session.uiScale() / 100.0f;
        this.roundLayout = session.usesRoundLayout();
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int roundHeaderInset = roundLayout
                ? RoundLayoutMetrics.headerInnerInset(screenWidth) : 0;
        this.settingsUi = new SettingsUi(activity, session, tokens, roundLayout,
                roundHeaderInset, handler, this::navigateBack);
        this.root = new FrameLayout(activity);
        this.root.setBackgroundColor(tokens.background);
        this.state = coordinator.paired() ? State.SYNCING : State.UNPAIRED;
        render();
        if (coordinator.paired()) loadStatus();
    }

    View view() {
        return root;
    }

    private void navigateBack() {
        if (!handleBack()) host.closePage();
    }

    void refresh() {
        if (closed) return;
        if (state == State.BILLING && paymentPage != null) {
            paymentPage.onResume();
            return;
        }
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
        if (state == State.BILLING && paymentPage != null) {
            return paymentPage.handleBack();
        }
        if (state == State.TASK_SETTINGS) {
            state = status == null ? State.SYNCING : State.CONNECTED;
            render();
            return true;
        }
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
        if (paymentPage != null && state == State.BILLING) paymentPage.onResume();
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
        if (paymentPage != null) paymentPage.onPause();
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
        Motions.resetTree(root);
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
        if (state == State.BILLING && paymentPage != null) {
            present(paymentPage.view());
            return;
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout page = column(tokens.background);
        applyPageInsets(page, false);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        page.addView(settingsUi.topCard(
                state == State.TASK_SETTINGS ? "签到设置" : "小黑盒签到"));
        if (!errorMessage.isEmpty()) addTop(page, errorBanner(errorMessage), 6);
        if (!coordinator.paired()) renderUnpaired(page);
        else if (state == State.TASK_SETTINGS && status != null) {
            renderTaskSettings(page);
        }
        else if (state == State.SYNCING || state == State.ERROR || status == null) {
            renderLoading(page);
        } else {
            renderConnected(page);
        }
        present(scroll);
    }

    private void renderUnpaired(LinearLayout page) {
        LinearLayout card = card();
        String subtitle = coordinator.supported()
                ? "连接后由服务器按计划执行"
                : "当前设备不支持安全连接";
        card.addView(statusHeader(R.drawable.il_calendar, "未连接", subtitle, tokens.text));
        addTop(card, body("登录签到服务后，再连接需要签到的小黑盒账号。",
                tokens.muted), 12);
        Button connect = primaryButton("连接签到服务");
        connect.setEnabled(coordinator.supported());
        connect.setOnClickListener(view -> {
            UiComponents.press(view);
            beginPairing();
        });
        addTop(card, connect, 13);
        page.addView(card);
    }

    private void renderLoading(LinearLayout page) {
        LinearLayout card = card();
        String title = state == State.SYNCING ? "正在连接" : "暂时无法连接";
        String message = state == State.SYNCING ? "正在读取账号与签到计划"
                : state == State.ERROR ? "可重新加载状态，或撤销此设备"
                : "正在处理签到任务";
        card.addView(statusHeader(R.drawable.il_refresh, title, message,
                state == State.ERROR ? tokens.muted : tokens.text,
                state == State.SYNCING));
        page.addView(card);
        if (state == State.ERROR) addRecoveryActions(page);
    }

    private void renderConnected(LinearLayout page) {
        boolean accountConnected = "connected".equalsIgnoreCase(status.account.state);
        LinearLayout statusCard = card();
        String stateLabel = !accountConnected ? "等待手机号登录"
                : state == State.RUNNING ? "执行中" : taskStateLabel(status.task);
        int stateColor = state == State.RUNNING || accountConnected && status.task.active()
                ? tokens.text : tokens.muted;
        statusCard.addView(statusHeader(R.drawable.il_calendar, stateLabel,
                accountConnected ? accountLabel(status.account) : "尚未连接小黑盒账号",
                stateColor, state == State.RUNNING));
        if (!accountConnected) {
            addTop(statusCard, body("自动签到需要单独使用手机号登录小黑盒。",
                    tokens.muted), 11);
            Button login = primaryButton("手机号登录");
            login.setOnClickListener(view -> {
                UiComponents.press(view);
                openMobileLogin();
            });
            addTop(statusCard, login, 12);
            page.addView(statusCard);

            CheckinBilling.Membership membership = status.membership;
            settingsUi.addSection(page, "服务");
            LinearLayout service = settingsUi.list();
            addSponsorshipEntry(service, membership);
            settingsUi.addEntry(service, "撤销此设备", null, null,
                    R.drawable.ic_logout, this::requestRevoke);
            page.addView(service);
            return;
        }
        addTop(statusCard, scheduleMetric(status.task), 13);
        Button run = primaryButton(state == State.RUNNING ? "正在签到" : "立即签到");
        run.setEnabled(state != State.RUNNING && status.task.active());
        run.setOnClickListener(view -> {
            UiComponents.press(view);
            runNow();
        });
        addTop(statusCard, run, 12);
        page.addView(statusCard);

        settingsUi.addSection(page, "管理");
        LinearLayout management = settingsUi.list();
        settingsUi.addEntry(management, "签到设置", null,
                scheduleLabel(status.task) + " · " + offsetLabel(status.task.offsetMinutes),
                R.drawable.il_settings, this::openTaskSettings);
        CheckinBilling.Membership membership = status.membership;
        addSponsorshipEntry(management, membership);
        settingsUi.addEntry(management, "更换账号", null, "手机号登录",
                R.drawable.il_person, this::openMobileLogin);
        page.addView(management);

        settingsUi.addSection(page, "最近签到");
        LinearLayout latest = settingsUi.list();
        if (status.lastRun == null) {
            settingsUi.addInfoEntry(latest, "暂无记录", null, null,
                    R.drawable.il_history);
        } else {
            String reward = rewardLabel(status.lastRun.checkIn);
            String result = runStatusLabel(status.lastRun);
            if (!reward.isEmpty()) result += " · " + reward;
            settingsUi.addInfoEntry(latest, result, runSummary(status.lastRun),
                    runTime(status.lastRun), R.drawable.il_history);
        }
        page.addView(latest);

        Button revoke = quietButton("撤销此设备");
        revoke.setEnabled(state != State.RUNNING);
        revoke.setOnClickListener(view -> requestRevoke());
        addTop(page, revoke, 8);
    }

    private void addTaskSettingsCard(LinearLayout page) {
        CheckinCenterClient.Task task = status.task;
        settingsUi.addSection(page, "计划");
        LinearLayout settings = settingsUi.list();
        taskEnabledSwitch = settingsUi.toggle("自动签到", null,
                taskEnabledLabel(task), task.enabled, checked -> {
                    if (!taskSettingsInFlight) {
                        saveTaskSettings(checked, normalizedScheduleTime(task),
                                task.offsetMinutes);
                    }
                });
        taskEnabledSwitch.setEnabled(!taskSettingsInFlight);
        settings.addView(taskEnabledSwitch);

        taskTimeButton = settingsUi.addEntry(settings, "执行时间", null,
                scheduleLabel(task), R.drawable.il_calendar,
                () -> showTaskTimePicker(task)).root;
        taskTimeButton.setEnabled(!taskSettingsInFlight);

        settingsUi.addDivider(settings);
        settings.addView(offsetSettingRow(task));
        if (task.platformBlocked || task.signBlocked) {
            TextView warning = body("服务已暂停此任务，当前设置会保留。", tokens.muted);
            warning.setPadding(dp(12), dp(8), dp(12), dp(10));
            settings.addView(warning);
        }
        page.addView(settings);
    }

    private void renderTaskSettings(LinearLayout page) {
        addTaskSettingsCard(page);
    }

    private void openTaskSettings() {
        if (status == null || state == State.RUNNING) return;
        state = State.TASK_SETTINGS;
        render();
    }

    private void addSponsorshipEntry(LinearLayout list,
                                     CheckinBilling.Membership membership) {
        if (membership == null || !membership.voluntarySponsorship) return;
        if (membership.checkoutAvailable) {
            settingsUi.addEntry(list, "赞助", null, "自愿支持",
                    R.drawable.il_qr, () -> openSponsorship(membership));
        } else {
            settingsUi.addInfoEntry(list, "赞助", null, "暂不可用", R.drawable.il_qr);
        }
    }

    private void openSponsorship(CheckinBilling.Membership membership) {
        if (membership == null || !membership.checkoutAvailable
                || state == State.RUNNING) return;
        if (paymentPage != null) paymentPage.close();
        paymentPage = new CheckinPaymentPage(activity, session, coordinator, tokens,
                roundLayout, membership, new CheckinPaymentPage.Host() {
                    @Override
                    public void closeSponsorship() {
                        if (paymentPage != null) {
                            paymentPage.close();
                            paymentPage = null;
                        }
                        state = State.CONNECTED;
                        render();
                    }

                    @Override
                    public void showMessage(String message) {
                        host.showMessage(message);
                    }
                });
        state = State.BILLING;
        render();
        paymentPage.onResume();
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
                                current.lastRun, current.membership);
                        state = State.TASK_SETTINGS;
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
        if (taskEnabledSwitch != null) setEnabledTree(taskEnabledSwitch, enabled);
        if (taskTimeButton != null) taskTimeButton.setEnabled(enabled);
        if (taskOffsetMinusButton != null) taskOffsetMinusButton.setEnabled(enabled);
        if (taskOffsetPlusButton != null) taskOffsetPlusButton.setEnabled(enabled);
    }

    private void setEnabledTree(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            setEnabledTree(group.getChildAt(index), enabled);
        }
    }

    private void addRecoveryActions(LinearLayout page) {
        settingsUi.addSection(page, "操作");
        LinearLayout actions = settingsUi.list();
        settingsUi.addEntry(actions, "登录小黑盒", null, "手机号",
                R.drawable.il_person, this::openMobileLogin);
        settingsUi.addEntry(actions, "重新加载", null, null,
                R.drawable.il_refresh, this::refresh);
        if (coordinator.paired()) {
            settingsUi.addEntry(actions, "撤销此设备", null, null,
                    R.drawable.ic_logout, this::requestRevoke);
        }
        page.addView(actions);
    }

    private void renderMobileLogin() {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout page = column(tokens.background);
        applyPageInsets(page, true);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        page.addView(settingsUi.topCard("手机号登录"));

        LinearLayout card = card();
        card.addView(statusHeader(R.drawable.il_person, "登录小黑盒",
                "仅用于服务器自动签到", tokens.text));

        LinearLayout modes = new LinearLayout(activity);
        modes.setPadding(dp(3), dp(3), dp(3), dp(3));
        Compat.setBackground(modes, UiComponents.round(
                activity, tokens.panelElevated, 10, scale));
        Button smsMode = segmentButton(roundLayout ? "验证码" : "短信验证码",
                loginMode == LoginMode.SMS);
        Button passwordMode = segmentButton("密码", loginMode == LoginMode.PASSWORD);
        smsMode.setOnClickListener(view -> switchLoginMode(LoginMode.SMS));
        passwordMode.setOnClickListener(view -> switchLoginMode(LoginMode.PASSWORD));
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(0, dp(34), 1f);
        modeParams.rightMargin = dp(2);
        modes.addView(smsMode, modeParams);
        LinearLayout.LayoutParams passwordModeParams = new LinearLayout.LayoutParams(0, dp(34), 1f);
        passwordModeParams.leftMargin = dp(2);
        modes.addView(passwordMode, passwordModeParams);
        addTop(card, modes, 12);

        smsPhoneInput = input("+86 手机号", InputType.TYPE_CLASS_PHONE);
        smsPhoneInput.setText(mobilePhone);

        if (loginMode == LoginMode.SMS) {
            LinearLayout phoneRow = new LinearLayout(activity);
            phoneRow.setGravity(Gravity.CENTER_VERTICAL);
            phoneRow.addView(smsPhoneInput, new LinearLayout.LayoutParams(
                    0, dp(roundLayout ? 38 : 42), 1f));
            smsSendButton = compactActionButton("发送验证码");
            smsSendButton.setOnClickListener(view -> {
                UiComponents.press(view);
                sendSmsCode();
            });
            LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                    dp(roundLayout ? 104 : 112), dp(roundLayout ? 38 : 42));
            sendParams.leftMargin = dp(6);
            phoneRow.addView(smsSendButton, sendParams);
            addTop(card, phoneRow, 11);

            smsCodeInput = input("短信验证码",
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            addTop(card, smsCodeInput, 7);
            smsSubmitButton = primaryButton("登录并连接");
            smsSubmitButton.setOnClickListener(view -> {
                UiComponents.press(view);
                submitSmsCode();
            });
            addTop(card, smsSubmitButton, 9);
            smsStatus = body("验证码由小黑盒发送", tokens.muted);
        } else {
            addTop(card, smsPhoneInput, 11);
            passwordInput = input("小黑盒登录密码",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            addTop(card, passwordInput, 7);
            passwordSubmitButton = primaryButton("登录并连接");
            passwordSubmitButton.setOnClickListener(view -> {
                UiComponents.press(view);
                loginWithPassword();
            });
            addTop(card, passwordSubmitButton, 9);
            smsStatus = body("密码仅用于本次登录", tokens.muted);
        }
        smsStatus.setLineSpacing(0f, 1.14f);
        addTop(card, smsStatus, 8);
        page.addView(card);
        present(scroll);
        setMobileLoginControls(!smsRequestInFlight);
        if (loginMode == LoginMode.SMS && !smsSessionId.isEmpty()) updateSmsCountdown();
    }

    private void renderPairing() {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout page = column(tokens.background);
        applyPageInsets(page, true);
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        page.addView(settingsUi.topCard("连接签到服务"));

        LinearLayout card = card();
        card.addView(statusHeader(R.drawable.il_qr,
                serviceAccountMode == ServiceAccountMode.LOGIN
                        ? "登录签到服务" : "注册签到服务",
                "配对码  " + pairing.start.userCode, tokens.text));
        pairingCountdown = body("", tokens.muted);
        addTop(card, pairingCountdown, 8);

        if (pairing.start.registrationOpen) {
            LinearLayout modes = new LinearLayout(activity);
            modes.setPadding(dp(3), dp(3), dp(3), dp(3));
            Compat.setBackground(modes, UiComponents.round(
                    activity, tokens.panelElevated, 10, scale));
            String loginLabel = roundLayout ? "登录" : "已有账号";
            String registerLabel = roundLayout ? "注册" : "注册账号";
            Button loginMode = segmentButton(loginLabel,
                    serviceAccountMode == ServiceAccountMode.LOGIN);
            Button registerMode = segmentButton(registerLabel,
                    serviceAccountMode == ServiceAccountMode.REGISTER);
            loginMode.setOnClickListener(view -> switchServiceAccountMode(
                    ServiceAccountMode.LOGIN));
            registerMode.setOnClickListener(view -> switchServiceAccountMode(
                    ServiceAccountMode.REGISTER));
            LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(34), 1f);
            left.rightMargin = dp(2);
            modes.addView(loginMode, left);
            LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(34), 1f);
            right.leftMargin = dp(2);
            modes.addView(registerMode, right);
            addTop(card, modes, 10);
        } else {
            serviceAccountMode = ServiceAccountMode.LOGIN;
        }

        serviceUsernameInput = input("签到服务账号", InputType.TYPE_CLASS_TEXT);
        serviceUsernameInput.setText(serviceUsername);
        addTop(card, serviceUsernameInput, 10);
        servicePasswordInput = input("密码",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addTop(card, servicePasswordInput, 7);

        if (serviceAccountMode == ServiceAccountMode.REGISTER) {
            serviceConfirmPasswordInput = input("再次输入密码",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            addTop(card, serviceConfirmPasswordInput, 7);
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
        pairingStatus = body("账号密码不会保存在 Lite 中", tokens.muted);
        pairingStatus.setLineSpacing(0f, 1.14f);
        addTop(card, pairingStatus, 8);
        page.addView(card);
        present(scroll);
        updatePairingCountdown();
        updateRegistrationEmailCountdown();
    }

    private void addRegistrationEmailFields(LinearLayout card) {
        TextView notice = body("邮箱验证 · 验证码 10 分钟内有效", tokens.muted);
        notice.setLineSpacing(0f, 1.12f);
        addTop(card, notice, 11);
        serviceEmailInput = input("邮箱地址",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        serviceEmailInput.setText(serviceEmail);
        addTop(card, serviceEmailInput, 7);
        registrationEmailSendButton = compactActionButton("发送邮箱验证码");
        registrationEmailSendButton.setOnClickListener(view -> {
            UiComponents.press(view);
            sendRegistrationEmail();
        });
        addTop(card, registrationEmailSendButton, 7);
        serviceEmailCodeInput = input("邮箱验证码",
                InputType.TYPE_CLASS_NUMBER);
        serviceEmailCodeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        addTop(card, serviceEmailCodeInput, 7);
    }

    private void switchServiceAccountMode(ServiceAccountMode mode) {
        if (pairingApprovalInFlight || registrationEmailInFlight || mode == serviceAccountMode) {
            return;
        }
        rememberServiceInputs();
        serviceAccountMode = mode;
        clearRegistrationEmailChallenge();
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
        if (emailRequired) {
            if (!CheckinCenterClient.validRegistrationEmail(email)) {
                setPairingStatus("请输入正确的邮箱地址", tokens.text);
                return;
            }
            if (registrationEmailChallengeId.isEmpty()
                    || SystemClock.elapsedRealtime() >= registrationEmailExpiresAtElapsed) {
                setPairingStatus("请先发送邮箱验证码", tokens.text);
                return;
            }
            if (!email.equalsIgnoreCase(registrationEmailChallengeAddress)) {
                clearRegistrationEmailChallenge();
                updateRegistrationEmailCountdown();
                setPairingStatus("邮箱已更改，请重新发送验证码", tokens.text);
                return;
            }
            if (!CheckinCenterClient.validRegistrationEmailCode(emailCode)) {
                setPairingStatus("请输入 6 位邮箱验证码", tokens.text);
                return;
            }
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
        if (current.expired()) {
            showError("配对已过期，请重新连接");
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now < registrationEmailRetryAtElapsed) return;
        serviceEmail = serviceEmailInput.getText().toString().trim();
        if (!CheckinCenterClient.validRegistrationEmail(serviceEmail)) {
            setPairingStatus("请输入正确的邮箱地址", tokens.text);
            return;
        }
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
                        registrationEmailChallengeAddress = serviceEmail;
                        long receivedAt = SystemClock.elapsedRealtime();
                        registrationEmailRetryAtElapsed = receivedAt
                                + value.retryAfterSeconds * SECOND_MS;
                        registrationEmailExpiresAtElapsed = receivedAt
                                + value.expiresInSeconds * SECOND_MS;
                        setPairingControlsEnabled(true);
                        setPairingStatus("邮箱验证码已发送", tokens.accent);
                        if (serviceEmailCodeInput != null) serviceEmailCodeInput.setText("");
                        updateRegistrationEmailCountdown();
                        if (serviceEmailCodeInput != null) serviceEmailCodeInput.requestFocus();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (closed || pairing != current || state != State.PAIRING) return;
                        registrationEmailInFlight = false;
                        if (error.retryAfterSeconds > 0) {
                            registrationEmailRetryAtElapsed = SystemClock.elapsedRealtime()
                                    + error.retryAfterSeconds * SECOND_MS;
                        }
                        setPairingControlsEnabled(true);
                        setPairingStatus(error.getMessage(), tokens.text);
                        updateRegistrationEmailCountdown();
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
        clearRegistrationEmailChallenge();
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

    private void clearRegistrationEmailChallenge() {
        registrationEmailChallengeId = "";
        registrationEmailChallengeAddress = "";
        registrationEmailRetryAtElapsed = 0L;
        registrationEmailExpiresAtElapsed = 0L;
        if (serviceEmailCodeInput != null) serviceEmailCodeInput.setText("");
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
        Compat.setBackground(view, UiComponents.round(
                activity, tokens.panelElevated, 9, scale));
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
            clearRegistrationEmailChallenge();
            setPairingStatus("邮箱验证码已过期，请重新发送", tokens.text);
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

    private LinearLayout statusHeader(int iconRes, String title, String subtitle,
                                      int titleColor) {
        return statusHeader(iconRes, title, subtitle, titleColor, false);
    }

    private LinearLayout statusHeader(int iconRes, String title, String subtitle,
                                      int titleColor, boolean showProgress) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View icon = showProgress && !Motions.off() ? progressTile() : iconTile(iconRes);
        int iconSize = dp(roundLayout ? 38 : 42);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.rightMargin = dp(10);
        row.addView(icon, iconParams);

        LinearLayout copy = column(Color.TRANSPARENT);
        TextView titleView = label(title, roundLayout ? 15f : 16f, titleColor);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setMaxLines(2);
        copy.addView(titleView);
        TextView subtitleView = body(subtitle, tokens.muted);
        subtitleView.setMaxLines(2);
        subtitleView.setPadding(0, dp(2), 0, 0);
        copy.addView(subtitleView);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private View progressTile() {
        FrameLayout tile = new FrameLayout(activity);
        Compat.setBackground(tile, UiComponents.monoChip(activity, tokens, scale));
        LoadingSpinnerView spinner = new LoadingSpinnerView(activity);
        spinner.setColor(tokens.text);
        int size = dp(roundLayout ? 19 : 21);
        tile.addView(spinner, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        return tile;
    }

    private ImageView iconTile(int iconRes) {
        ImageView icon = new ImageView(activity);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        Drawable drawable = Compat.tintedDrawable(activity, iconRes, tokens.text);
        if (drawable != null) icon.setImageDrawable(drawable);
        Compat.setBackground(icon, UiComponents.monoChip(activity, tokens, scale));
        return icon;
    }

    private void present(View view) {
        root.addView(view, match());
        if (contentPresented) Motions.enter(view, dp(roundLayout ? 6 : 10));
        contentPresented = true;
    }

    private LinearLayout scheduleMetric(CheckinCenterClient.Task task) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = column(Color.TRANSPARENT);
        copy.addView(label("下次签到", 10.5f, tokens.muted));
        String window = windowLabel(task);
        TextView detail = body(window.isEmpty() ? taskEnabledLabel(task) : window, tokens.muted);
        detail.setPadding(0, dp(2), 0, 0);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView time = label(scheduleLabel(task), roundLayout ? 20f : 22f, tokens.text);
        time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        time.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        time.setSingleLine(true);
        row.addView(time, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    private LinearLayout offsetSettingRow(CheckinCenterClient.Task task) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(roundLayout ? 10 : 12), dp(7),
                dp(roundLayout ? 10 : 12), dp(7));
        row.setMinimumHeight(dp(60));

        ImageView icon = iconTile(R.drawable.il_scroll);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.rightMargin = dp(10);
        row.addView(icon, iconParams);

        TextView title = label("随机偏移", 15f, tokens.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        taskOffsetMinusButton = compactButton("-");
        taskOffsetMinusButton.setContentDescription("减少随机偏移");
        taskOffsetMinusButton.setEnabled(!taskSettingsInFlight && task.offsetMinutes > 0);
        taskOffsetMinusButton.setOnClickListener(view -> saveTaskSettings(task.enabled,
                normalizedScheduleTime(task), Math.max(0, task.offsetMinutes - 30)));
        row.addView(taskOffsetMinusButton, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView value = body(offsetLabel(task.offsetMinutes), tokens.text);
        value.setGravity(Gravity.CENTER);
        value.setSingleLine(true);
        row.addView(value, new LinearLayout.LayoutParams(dp(roundLayout ? 58 : 66), dp(32)));

        taskOffsetPlusButton = compactButton("+");
        taskOffsetPlusButton.setContentDescription("增加随机偏移");
        taskOffsetPlusButton.setEnabled(!taskSettingsInFlight && task.offsetMinutes < 720);
        taskOffsetPlusButton.setOnClickListener(view -> saveTaskSettings(task.enabled,
                normalizedScheduleTime(task), Math.min(720, task.offsetMinutes + 30)));
        row.addView(taskOffsetPlusButton, new LinearLayout.LayoutParams(dp(32), dp(32)));
        return row;
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

    private Button segmentButton(String value, boolean selected) {
        Button button = baseButton(value);
        button.setTextColor(selected ? tokens.text : tokens.muted);
        Compat.setBackground(button, UiComponents.round(activity,
                selected ? tokens.pressedSurface() : Color.TRANSPARENT, 8, scale));
        return button;
    }

    private Button compactActionButton(String value) {
        Button button = baseButton(value);
        button.setTextSize(11f * session.textScale() / 100.0f);
        button.setTextColor(tokens.text);
        button.setPadding(dp(7), 0, dp(7), 0);
        Compat.setBackground(button, UiComponents.round(
                activity, tokens.pressedSurface(), 9, scale));
        return button;
    }

    private Button quietButton(String value) {
        Button button = baseButton(value);
        button.setTextColor(tokens.muted);
        Compat.setBackground(button, UiComponents.round(
                activity, Color.TRANSPARENT, 10, scale));
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(36)));
        return button;
    }

    private Button compactButton(String value) {
        Button button = baseButton(value);
        button.setTextColor(tokens.text);
        Compat.setBackground(button, UiComponents.round(
                activity, tokens.panelElevated, 9, scale));
        button.setTextSize(16f * session.textScale() / 100.0f);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private Button baseButton(String value) {
        Button button = UiComponents.button(activity);
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
        if (paymentPage != null) {
            paymentPage.close();
            paymentPage = null;
        }
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
