package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class CheckinCenterPage {
    interface Host {
        void closePage();
        void openCaptcha(String verificationUri);
        void confirmRevoke(Runnable confirmed);
        void showMessage(String message);
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

    private final Activity activity;
    private final SessionStore session;
    private final CheckinCenterCoordinator coordinator;
    private final ThemeTokens tokens;
    private final Host host;
    private final CheckinCenterUi ui;
    private final CheckinMobileLoginFlow mobileLogin;
    private final CheckinPairingFlow pairingFlow;
    private final CheckinServiceAccountFlow serviceAccountFlow;
    private final CheckinTaskSettingsFlow taskSettingsFlow;
    private final CheckinTaskSettingsView taskSettingsView;
    private final CheckinAccountForms accountForms;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float scale;
    private final FrameLayout root;
    private final SettingsUi settingsUi;
    private final CheckinHistoryView historyView;
    private final boolean roundLayout;
    private State state;
    private CheckinCenterClient.Status status;
    private CheckinHistory history;
    private boolean historyLoading;
    private String historyError = "";
    private String errorMessage = "";
    private CheckinPaymentPage paymentPage;
    private boolean closed;
    private boolean resumed;
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
        this.ui = new CheckinCenterUi(activity, session, tokens, roundLayout);
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int roundHeaderInset = roundLayout
                ? RoundLayoutMetrics.headerInnerInset(screenWidth) : 0;
        this.settingsUi = new SettingsUi(activity, session, tokens, roundLayout,
                roundHeaderInset, handler, this::navigateBack);
        this.historyView = new CheckinHistoryView(settingsUi);
        this.accountForms = new CheckinAccountForms(activity, session, tokens, ui,
                settingsUi, roundLayout);
        this.mobileLogin = new CheckinMobileLoginFlow(coordinator,
                new CheckinMobileLoginFlow.Host() {
                    @Override public boolean active() {
                        return !closed && state == State.MOBILE_LOGIN;
                    }
                    @Override public String phone() {
                        return accountForms.mobilePhone();
                    }
                    @Override public String code() {
                        return accountForms.mobileCode();
                    }
                    @Override public String password() {
                        return accountForms.mobilePassword();
                    }
                    @Override public void renderModeChanged() {
                        clearSmsViews();
                        render();
                    }
                    @Override public void setControls(boolean enabled,
                                                      CheckinMobileLoginFlow.Mode mode,
                                                      boolean hasSession,
                                                      long retryAtElapsed) {
                        setMobileLoginControls(enabled, mode, hasSession, retryAtElapsed);
                    }
                    @Override public void setStatus(String message,
                                                    CheckinMobileLoginFlow.Status status) {
                        int color = status == CheckinMobileLoginFlow.Status.ACCENT
                                ? tokens.accent : status == CheckinMobileLoginFlow.Status.NORMAL
                                ? tokens.muted : tokens.text;
                        setMobileLoginStatus(message, color);
                    }
                    @Override public void updateSmsButton(String label, boolean enabled) {
                        accountForms.updateSmsButton(label, enabled);
                    }
                    @Override public void clearCode() {
                        accountForms.clearMobileCode();
                    }
                    @Override public void clearPassword() {
                        accountForms.clearMobilePassword();
                    }
                    @Override public void focusCode() {
                        accountForms.focusMobileCode();
                    }
                    @Override public void openCaptcha(String uri) {
                        host.openCaptcha(uri);
                    }
                    @Override public void showMessage(String message) {
                        host.showMessage(message);
                    }
                    @Override public void connected() {
                        finishMobileLogin();
                    }
                });
        this.pairingFlow = new CheckinPairingFlow(coordinator,
                new CheckinPairingFlow.Host() {
                    @Override public boolean pairingVisible() {
                        return !closed && state == State.PAIRING;
                    }
                    @Override public void pairingStarting() {
                        state = State.SYNCING;
                        errorMessage = "";
                        render();
                    }
                    @Override public void pairingStarted() {
                        resetServiceAccountFlow();
                        state = State.PAIRING;
                        render();
                    }
                    @Override public void pairingConnected() {
                        resetServiceAccountFlow();
                        clearPairingViews();
                        state = State.SYNCING;
                        render();
                        loadStatus();
                    }
                    @Override public void updatePairingCountdown(long remainingSeconds) {
                        accountForms.updatePairingCountdown(remainingSeconds);
                    }
                    @Override public void showError(String message) {
                        CheckinCenterPage.this.showError(message);
                    }
                    @Override public void showMessage(String message) {
                        host.showMessage(message);
                    }
                });
        this.serviceAccountFlow = new CheckinServiceAccountFlow(coordinator, pairingFlow,
                new CheckinServiceAccountFlow.Host() {
                    @Override public boolean pairingVisible() {
                        return !closed && state == State.PAIRING;
                    }
                    @Override public String username() {
                        return accountForms.serviceUsername();
                    }
                    @Override public String password() {
                        return accountForms.servicePassword();
                    }
                    @Override public String passwordConfirmation() {
                        return accountForms.servicePasswordConfirmation();
                    }
                    @Override public String email() {
                        return accountForms.serviceEmail();
                    }
                    @Override public String emailCode() {
                        return accountForms.serviceEmailCode();
                    }
                    @Override public void renderModeChanged() {
                        clearPairingViews();
                        render();
                    }
                    @Override public void setControlsEnabled(boolean enabled,
                                                             long emailRetryAtElapsed) {
                        setPairingControlsEnabled(enabled, emailRetryAtElapsed);
                    }
                    @Override public void setStatus(String message,
                                                    CheckinServiceAccountFlow.Status status) {
                        int color = status == CheckinServiceAccountFlow.Status.ACCENT
                                ? tokens.accent : status == CheckinServiceAccountFlow.Status.NORMAL
                                ? tokens.muted : tokens.text;
                        setPairingStatus(message, color);
                    }
                    @Override public void updateEmailButton(String label, boolean enabled) {
                        accountForms.updateEmailButton(label, enabled);
                    }
                    @Override public void clearPasswords() {
                        clearServicePasswords();
                    }
                    @Override public void clearEmailCode() {
                        accountForms.clearEmailCode();
                    }
                    @Override public void focusEmailCode() {
                        accountForms.focusEmailCode();
                    }
                    @Override public void focusPassword() {
                        accountForms.focusServicePassword();
                    }
                    @Override public void showError(String message) {
                        CheckinCenterPage.this.showError(message);
                    }
                    @Override public void pairingApproved() {
                        pairingFlow.pollNow();
                    }
                });
        this.taskSettingsFlow = new CheckinTaskSettingsFlow(coordinator,
                new CheckinTaskSettingsFlow.Host() {
                    @Override public boolean active() {
                        return !closed && state == State.TASK_SETTINGS;
                    }
                    @Override public boolean paired() {
                        return coordinator.paired();
                    }
                    @Override public CheckinCenterClient.Status status() {
                        return CheckinCenterPage.this.status;
                    }
                    @Override public void setControlsEnabled(boolean enabled) {
                        taskSettingsView.setControlsEnabled(enabled);
                    }
                    @Override public void taskUpdated(CheckinCenterClient.Task task,
                                                      boolean renderPage) {
                        CheckinCenterClient.Status current = status;
                        if (current == null) {
                            if (renderPage) loadStatus();
                            return;
                        }
                        status = new CheckinCenterClient.Status(current.account, task,
                                current.lastRun, current.membership);
                        errorMessage = "";
                        if (renderPage) render();
                    }
                    @Override public void reloadStatus() {
                        loadStatus();
                    }
                    @Override public void authorizationFailed(String message) {
                        showError(message);
                    }
                    @Override public void showMessage(String message) {
                        host.showMessage(message);
                    }
                    @Override public void render() {
                        CheckinCenterPage.this.render();
                    }
                });
        this.taskSettingsView = new CheckinTaskSettingsView(activity, session, tokens,
                settingsUi, ui, taskSettingsFlow, roundLayout);
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
            clearPairingState();
            state = State.UNPAIRED;
            status = null;
            history = null;
            historyLoading = false;
            historyError = "";
            errorMessage = "";
            render();
            return;
        }
        state = State.SYNCING;
        history = null;
        historyLoading = false;
        historyError = "";
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
        if (state == State.SYNCING && !coordinator.paired()) clearPairingState();
        return false;
    }

    void onResume() {
        resumed = true;
        if (paymentPage != null && state == State.BILLING) paymentPage.onResume();
        pairingFlow.resume();
        if (state == State.MOBILE_LOGIN) mobileLogin.resume();
        serviceAccountFlow.resume();
    }

    void onPause() {
        resumed = false;
        if (paymentPage != null) paymentPage.onPause();
        pairingFlow.pause();
        mobileLogin.pause();
        serviceAccountFlow.pause();
    }

    private void beginPairing() {
        pairingFlow.start();
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
                history = null;
                historyError = "";
                historyLoading = "connected".equalsIgnoreCase(value.account.state);
                errorMessage = warning == null ? "" : warning;
                render();
                if (historyLoading) loadHistory();
            }

            @Override
            public void onError(CheckinCenterClient.ApiError error) {
                showError(error.getMessage());
            }
        });
    }

    private void loadHistory() {
        if (closed || !coordinator.paired() || status == null
                || !"connected".equalsIgnoreCase(status.account.state)) {
            historyLoading = false;
            return;
        }
        coordinator.getHistory(new CheckinCenterClient.Callback<CheckinHistory>() {
            @Override
            public void onSuccess(CheckinHistory value) {
                if (closed || status == null) return;
                history = value;
                historyLoading = false;
                historyError = "";
                render(false);
            }

            @Override
            public void onError(CheckinCenterClient.ApiError error) {
                if (closed || status == null) return;
                if (error.authorizationInvalid()) {
                    showError(error.getMessage());
                    return;
                }
                if (error.operation == CheckinCenterClient.Operation.HISTORY
                        && error.statusCode == 404 && status.lastRun != null) {
                    // Older CheckinCenter deployments do not expose the history route
                    // yet, but status still carries the latest real execution result.
                    history = CheckinHistory.fromLastRun(status.lastRun);
                    historyLoading = false;
                    historyError = "";
                    render(false);
                    return;
                }
                historyLoading = false;
                historyError = error.getMessage();
                render(false);
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
                    history = null;
                    historyLoading = false;
                    historyError = "";
                    clearPairingState();
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
        mobileLogin.start();
        errorMessage = "";
        state = State.MOBILE_LOGIN;
        render();
        if (resumed) mobileLogin.resume();
    }

    private void sendSmsCode() {
        mobileLogin.sendSmsCode();
    }

    private void submitSmsCode() {
        mobileLogin.submitSmsCode();
    }

    private void switchLoginMode(CheckinMobileLoginFlow.Mode mode) {
        mobileLogin.switchMode(mode);
    }

    private void loginWithPassword() {
        mobileLogin.loginWithPassword();
    }

    void onCaptchaResult(String ticket, String randstr) {
        mobileLogin.onCaptchaResult(ticket, randstr);
    }

    void onCaptchaCancelled(String message) {
        mobileLogin.onCaptchaCancelled(message);
    }

    private void finishMobileLogin() {
        mobileLogin.pause();
        clearSmsViews();
        state = State.SYNCING;
        errorMessage = "";
        render();
        loadStatus();
    }

    private void showError(String message) {
        if (state == State.PAIRING) clearPairingState();
        state = coordinator.paired() ? State.ERROR : State.UNPAIRED;
        errorMessage = message == null ? "签到服务请求失败" : message;
        render();
    }

    private void render() {
        render(true);
    }

    private void render(boolean animate) {
        if (closed) return;
        Motions.resetTree(root);
        root.removeAllViews();
        if (state == State.PAIRING && pairingFlow.startValue() != null) {
            renderPairing();
            return;
        }
        if (state == State.MOBILE_LOGIN) {
            present(accountForms.mobile(mobileLogin, accountActions()), animate);
            mobileLogin.bindView();
            return;
        }
        if (state == State.BILLING && paymentPage != null) {
            present(paymentPage.view(), animate);
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
        present(scroll, animate);
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
                CheckinTaskSettingsView.scheduleLabel(status.task) + " · "
                        + CheckinTaskSettingsView.offsetLabel(status.task.offsetMinutes),
                R.drawable.il_settings, this::openTaskSettings);
        CheckinBilling.Membership membership = status.membership;
        addSponsorshipEntry(management, membership);
        settingsUi.addEntry(management, "更换账号", null, "手机号登录",
                R.drawable.il_person, this::openMobileLogin);
        page.addView(management);

        historyView.addTo(page, history, historyLoading, historyError, this::loadHistory);

        Button revoke = quietButton("撤销此设备");
        revoke.setEnabled(state != State.RUNNING);
        revoke.setOnClickListener(view -> requestRevoke());
        addTop(page, revoke, 8);
    }

    private void renderTaskSettings(LinearLayout page) {
        taskSettingsView.addTo(page, status.task);
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

    private void renderPairing() {
        CheckinCenterClient.PairingStart pairing = pairingFlow.startValue();
        if (pairing == null) {
            showError("配对会话已失效，请重新连接");
            return;
        }
        present(accountForms.pairing(pairing, serviceAccountFlow, accountActions()));
        pairingFlow.bindCountdown();
        serviceAccountFlow.bindView();
    }

    private CheckinAccountForms.Actions accountActions() {
        return new CheckinAccountForms.Actions() {
            @Override public void selectMobileMode(CheckinMobileLoginFlow.Mode mode) {
                switchLoginMode(mode);
            }
            @Override public void sendSms() { sendSmsCode(); }
            @Override public void submitSms() { submitSmsCode(); }
            @Override public void submitPassword() { loginWithPassword(); }
            @Override public void selectServiceMode(CheckinServiceAccountFlow.Mode mode) {
                switchServiceAccountMode(mode);
            }
            @Override public void sendRegistrationEmail() {
                CheckinCenterPage.this.sendRegistrationEmail();
            }
            @Override public void submitServiceAccount() {
                CheckinCenterPage.this.submitServiceAccount();
            }
        };
    }

    private void switchServiceAccountMode(CheckinServiceAccountFlow.Mode mode) {
        serviceAccountFlow.switchMode(mode);
    }

    private void submitServiceAccount() {
        serviceAccountFlow.submit();
    }

    private void sendRegistrationEmail() {
        serviceAccountFlow.sendRegistrationEmail();
    }

    private void cancelPairing() {
        clearPairingState();
        state = State.UNPAIRED;
        errorMessage = "";
        render();
    }

    private void setPairingControlsEnabled(boolean enabled, long emailRetryAtElapsed) {
        accountForms.setPairingControls(enabled, emailRetryAtElapsed);
    }

    private void setPairingStatus(String message, int color) {
        accountForms.setPairingStatus(message, color);
    }

    private void clearPairingViews() {
        accountForms.clearPairing();
    }

    private void clearPairingState() {
        pairingFlow.cancel();
        resetServiceAccountFlow();
        clearPairingViews();
    }

    private void resetServiceAccountFlow() {
        serviceAccountFlow.reset();
    }

    private void clearServicePasswords() {
        accountForms.clearServicePasswords();
    }

    private View errorBanner(String message) {
        return ui.errorBanner(message);
    }

    private void setMobileLoginControls(boolean enabled, CheckinMobileLoginFlow.Mode mode,
                                        boolean hasSession, long retryAtElapsed) {
        accountForms.setMobileControls(enabled, mode, hasSession, retryAtElapsed);
    }

    private void setMobileLoginStatus(String message, int color) {
        accountForms.setMobileStatus(message, color);
    }

    private void clearSmsViews() {
        accountForms.clearMobile();
    }

    private LinearLayout statusHeader(int iconRes, String title, String subtitle,
                                      int titleColor) {
        return ui.statusHeader(iconRes, title, subtitle, titleColor, false);
    }

    private LinearLayout statusHeader(int iconRes, String title, String subtitle,
                                      int titleColor, boolean showProgress) {
        return ui.statusHeader(iconRes, title, subtitle, titleColor, showProgress);
    }

    private void present(View view) {
        present(view, true);
    }

    private void present(View view, boolean animate) {
        root.addView(view, match());
        if (contentPresented && animate) Motions.enter(view, dp(roundLayout ? 6 : 10));
        contentPresented = true;
    }

    private LinearLayout scheduleMetric(CheckinCenterClient.Task task) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = column(Color.TRANSPARENT);
        copy.addView(label("下次签到", 10.5f, tokens.muted));
        String window = windowLabel(task);
        TextView detail = body(window.isEmpty()
                ? CheckinTaskSettingsView.enabledLabel(task) : window, tokens.muted);
        detail.setPadding(0, dp(2), 0, 0);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView time = label(CheckinTaskSettingsView.scheduleLabel(task),
                roundLayout ? 20f : 22f, tokens.text);
        time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        time.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        time.setSingleLine(true);
        row.addView(time, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    private LinearLayout card() {
        return ui.card();
    }

    private void applyPageInsets(LinearLayout page, boolean subpage) {
        ui.applyPageInsets(page, subpage);
    }

    private LinearLayout column(int color) {
        return ui.column(color);
    }

    private TextView body(String value, int color) {
        return ui.body(value, color);
    }

    private TextView label(String value, float size, int color) {
        return ui.label(value, size, color);
    }

    private Button primaryButton(String value) {
        return ui.primaryButton(value);
    }

    private Button quietButton(String value) {
        return ui.quietButton(value);
    }

    private void addTop(ViewGroup parent, View child, int marginDp) {
        ui.addTop(parent, child, marginDp);
    }

    private String accountLabel(CheckinCenterClient.Account account) {
        String name = account.displayName.isEmpty() ? "小黑盒账号" : account.displayName;
        return account.externalIdMasked.isEmpty() ? name : name + "  " + account.externalIdMasked;
    }

    private String taskStateLabel(CheckinCenterClient.Task task) {
        if (task.platformBlocked || task.signBlocked) return "自动签到已暂停";
        return task.active() ? "自动签到已启用" : "自动签到未启用";
    }

    private String windowLabel(CheckinCenterClient.Task task) {
        if (task.windowStart.isEmpty()) return task.windowEnd;
        if (task.windowEnd.isEmpty()) return task.windowStart;
        return task.windowStart + " - " + task.windowEnd;
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
        resumed = false;
        if (paymentPage != null) {
            paymentPage.close();
            paymentPage = null;
        }
        mobileLogin.close();
        pairingFlow.close();
        serviceAccountFlow.close();
        taskSettingsFlow.close();
        handler.removeCallbacksAndMessages(null);
        clearPairingViews();
        clearSmsViews();
        taskSettingsView.clear();
        root.removeAllViews();
    }

}
