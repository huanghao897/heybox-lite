package com.ronan.heyboxlite;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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

    enum State {
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
    private final CheckinCenterPageRenderer contentRenderer;
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
        this.contentRenderer = new CheckinCenterPageRenderer(activity, session, tokens, ui,
                settingsUi, historyView, taskSettingsView, roundLayout,
                new CheckinCenterPageRenderer.Actions() {
                    @Override public void beginPairing() { CheckinCenterPage.this.beginPairing(); }
                    @Override public void openMobileLogin() { CheckinCenterPage.this.openMobileLogin(); }
                    @Override public void runNow() { CheckinCenterPage.this.runNow(); }
                    @Override public void openTaskSettings() { CheckinCenterPage.this.openTaskSettings(); }
                    @Override public void openSponsorship(CheckinBilling.Membership membership) {
                        CheckinCenterPage.this.openSponsorship(membership);
                    }
                    @Override public void requestRevoke() { CheckinCenterPage.this.requestRevoke(); }
                    @Override public void refresh() { CheckinCenterPage.this.refresh(); }
                    @Override public void loadHistory() { CheckinCenterPage.this.loadHistory(); }
                });
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
                host.showMessage(CheckinResultText.runMessage(value));
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
        contentRenderer.render(page, state, status, history, historyLoading, historyError,
                errorMessage, coordinator.paired(), coordinator.supported());
        present(scroll, animate);
    }

    private void openTaskSettings() {
        if (status == null || state == State.RUNNING) return;
        state = State.TASK_SETTINGS;
        render();
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

    private void present(View view) {
        present(view, true);
    }

    private void present(View view, boolean animate) {
        root.addView(view, match());
        if (contentPresented && animate) Motions.enter(view, dp(roundLayout ? 6 : 10));
        contentPresented = true;
    }

    private void applyPageInsets(LinearLayout page, boolean subpage) {
        ui.applyPageInsets(page, subpage);
    }

    private LinearLayout column(int color) {
        return ui.column(color);
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
