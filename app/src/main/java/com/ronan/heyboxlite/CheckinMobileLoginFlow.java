package com.ronan.heyboxlite;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

final class CheckinMobileLoginFlow {
    enum Mode { SMS, PASSWORD }
    enum Status { NORMAL, ACCENT, ERROR }

    interface Host {
        boolean active();
        String phone();
        String code();
        String password();
        void renderModeChanged();
        void setControls(boolean enabled, Mode mode, boolean hasSession,
                         long retryAtElapsed);
        void setStatus(String message, Status status);
        void updateSmsButton(String label, boolean enabled);
        void clearCode();
        void clearPassword();
        void focusCode();
        void openCaptcha(String uri, int attempt);
        void showMessage(String message);
        void connected();
    }

    private enum CaptchaAction { NONE, SEND_SMS, SUBMIT_SMS, PASSWORD_LOGIN }

    private static final long SECOND_MS = 1_000L;

    private final CheckinCenterCoordinator coordinator;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable countdownTask = this::updateCountdown;
    private Mode mode = Mode.SMS;
    private String rememberedPhone = "";
    private String smsSessionId = "";
    private long retryAtElapsed;
    private long expiresAtElapsed;
    private boolean requestInFlight;
    private boolean resumed;
    private boolean closed;
    private int generation;
    private int captchaGeneration;
    private String sessionPhone = "";
    private String captchaSessionId = "";
    private String pendingCaptchaUri = "";
    private CaptchaAction captchaAction = CaptchaAction.NONE;
    private String captchaPhone = "";
    private String captchaCode = "";
    private String captchaPassword = "";

    CheckinMobileLoginFlow(CheckinCenterCoordinator coordinator, Host host) {
        this.coordinator = coordinator;
        this.host = host;
    }

    void start() {
        if (closed) return;
        reset();
    }

    void resume() {
        if (closed) return;
        resumed = true;
        refreshControls();
        updateCountdown();
        launchPendingCaptcha();
    }

    void bindView() {
        refreshControls();
        updateCountdownView();
    }

    void pause() {
        resumed = false;
        handler.removeCallbacks(countdownTask);
    }

    void close() {
        closed = true;
        resumed = false;
        handler.removeCallbacksAndMessages(null);
        reset();
    }

    Mode mode() {
        return mode;
    }

    String rememberedPhone() {
        return rememberedPhone;
    }

    boolean requestInFlight() {
        return requestInFlight;
    }

    void switchMode(Mode value) {
        if (closed || requestInFlight || value == mode) return;
        generation++;
        rememberedPhone = clean(host.phone());
        host.clearPassword();
        mode = value;
        clearCaptcha();
        host.renderModeChanged();
    }

    void sendSmsCode() {
        if (closed || requestInFlight || !host.active()) return;
        String phone = clean(host.phone());
        if (!phone.equals(sessionPhone)) reset();
        rememberedPhone = phone;
        requestSmsCode(phone, "", "", false);
    }

    void submitSmsCode() {
        if (closed || requestInFlight || smsSessionId.isEmpty() || !host.active()) return;
        if (!sessionPhone.equals(clean(host.phone()))) {
            resetSmsSession("手机号已更改，请重新发送验证码");
            return;
        }
        if (SystemClock.elapsedRealtime() >= expiresAtElapsed) {
            resetSmsSession("验证码已过期，请重新发送");
            return;
        }
        requestSubmitSmsCode(clean(host.code()), "", "", false);
    }

    void loginWithPassword() {
        if (closed || requestInFlight || !host.active()) return;
        String phone = clean(host.phone());
        rememberedPhone = phone;
        requestPasswordLogin(phone, host.password(), "", "", false);
    }

    void onCaptchaResult(int attempt, String ticket, String randstr) {
        if (attempt != captchaGeneration || !accept(attempt, captchaPhone)
                || captchaAction == CaptchaAction.NONE) return;
        CaptchaAction action = captchaAction;
        String phone = captchaPhone;
        String code = captchaCode;
        String password = captchaPassword;
        String session = captchaSessionId;
        clearCaptcha();
        if (action == CaptchaAction.SEND_SMS) {
            requestSmsCode(phone, ticket, randstr, true);
        } else if (action == CaptchaAction.SUBMIT_SMS) {
            if (!session.equals(smsSessionId) || !phone.equals(sessionPhone)) return;
            requestSubmitSmsCode(code, ticket, randstr, true);
        } else {
            requestPasswordLogin(phone, password, ticket, randstr, true);
        }
    }

    void onCaptchaCancelled(int attempt, String message) {
        if (attempt != captchaGeneration || !accept(attempt, captchaPhone)
                || captchaAction == CaptchaAction.NONE) return;
        clearCaptcha();
        requestInFlight = false;
        refreshControls();
        updateCountdown();
        host.setStatus(clean(message).isEmpty() ? "安全验证已取消" : message,
                Status.ERROR);
    }

    private void requestSmsCode(String phone, String ticket, String randstr,
                                boolean captchaRetry) {
        final int attempt = ++generation;
        requestInFlight = true;
        refreshControls();
        host.setStatus(captchaRetry ? "安全验证通过，正在发送验证码" : "正在发送验证码",
                Status.NORMAL);
        coordinator.sendSmsCode(phone, ticket, randstr,
                new CheckinCenterClient.Callback<CheckinCenterClient.SmsSession>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.SmsSession value) {
                        if (!accept(attempt, phone)) return;
                        clearCaptcha();
                        requestInFlight = false;
                        smsSessionId = value.sessionId;
                        sessionPhone = phone;
                        long now = SystemClock.elapsedRealtime();
                        retryAtElapsed = now + value.retryAfterSeconds * SECOND_MS;
                        expiresAtElapsed = now + value.expiresInSeconds * SECOND_MS;
                        refreshControls();
                        host.setStatus("验证码已发送，10 分钟内有效", Status.ACCENT);
                        host.focusCode();
                        updateCountdown();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (!accept(attempt, phone)) return;
                        if (!captchaRetry && beginCaptcha(
                                CaptchaAction.SEND_SMS, phone, "", "", error)) return;
                        clearCaptcha();
                        requestInFlight = false;
                        refreshControls();
                        host.setStatus(captchaRetry && error.captchaRequired()
                                ? "安全验证未通过，请重新发送验证码"
                                : error.getMessage(), Status.ERROR);
                    }
                });
    }

    private void requestSubmitSmsCode(String code, String ticket, String randstr,
                                      boolean captchaRetry) {
        final int attempt = ++generation;
        final String phone = sessionPhone;
        final String session = smsSessionId;
        requestInFlight = true;
        refreshControls();
        host.setStatus(captchaRetry ? "安全验证通过，正在继续登录" : "正在验证并连接账号",
                Status.NORMAL);
        coordinator.submitSmsCode(session, code, ticket, randstr,
                new CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.ConnectedAccount value) {
                        if (!accept(attempt, phone) || !session.equals(smsSessionId)) return;
                        reset();
                        host.showMessage("手机号登录成功");
                        host.connected();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (!accept(attempt, phone) || !session.equals(smsSessionId)) return;
                        if (!captchaRetry && beginCaptcha(
                                CaptchaAction.SUBMIT_SMS, phone, code, "", error)) return;
                        clearCaptcha();
                        requestInFlight = false;
                        refreshControls();
                        updateCountdown();
                        host.setStatus(captchaRetry && error.captchaRequired()
                                ? "安全验证未通过，请重新提交验证码"
                                : error.getMessage(), Status.ERROR);
                    }
                });
    }

    private void requestPasswordLogin(String phone, String password, String ticket,
                                      String randstr, boolean captchaRetry) {
        final int attempt = ++generation;
        requestInFlight = true;
        refreshControls();
        host.setStatus(captchaRetry ? "安全验证通过，正在继续登录" : "正在登录小黑盒",
                Status.NORMAL);
        coordinator.loginWithPassword(phone, password, ticket, randstr,
                new CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.ConnectedAccount value) {
                        if (!accept(attempt, phone)) return;
                        host.clearPassword();
                        reset();
                        host.showMessage("手机号密码登录成功");
                        host.connected();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (!accept(attempt, phone)) return;
                        if (!captchaRetry && beginCaptcha(
                                CaptchaAction.PASSWORD_LOGIN, phone, "", password, error)) {
                            return;
                        }
                        clearCaptcha();
                        requestInFlight = false;
                        host.clearPassword();
                        refreshControls();
                        host.setStatus(captchaRetry && error.captchaRequired()
                                ? "安全验证未通过，请重新登录"
                                : error.getMessage(), Status.ERROR);
                    }
                });
    }

    private boolean beginCaptcha(CaptchaAction action, String phone, String code,
                                 String password, CheckinCenterClient.ApiError error) {
        if (!error.captchaRequired()) return false;
        captchaAction = action;
        captchaGeneration = generation;
        captchaPhone = clean(phone);
        captchaCode = clean(code);
        captchaPassword = password == null ? "" : password;
        captchaSessionId = smsSessionId;
        pendingCaptchaUri = error.captchaUri;
        host.setStatus("请完成小黑盒安全验证", Status.ACCENT);
        launchPendingCaptcha();
        return true;
    }

    private void launchPendingCaptcha() {
        if (!resumed || closed || !host.active() || pendingCaptchaUri.isEmpty()) return;
        String uri = pendingCaptchaUri;
        pendingCaptchaUri = "";
        host.openCaptcha(uri, captchaGeneration);
    }

    private boolean accept(int attempt, String phone) {
        if (closed || generation != attempt || !host.active()) return false;
        if (phone.equals(clean(host.phone()))) return true;
        resetSmsSession("手机号已更改，请重新登录");
        return false;
    }

    private void updateCountdown() {
        handler.removeCallbacks(countdownTask);
        if (!resumed || !host.active() || smsSessionId.isEmpty()) return;
        if (!updateCountdownView()) return;
        handler.postDelayed(countdownTask, SECOND_MS);
    }

    private boolean updateCountdownView() {
        if (smsSessionId.isEmpty()) return false;
        long now = SystemClock.elapsedRealtime();
        if (now >= expiresAtElapsed) {
            if (requestInFlight) {
                host.updateSmsButton("验证码已过期", false);
                return false;
            }
            resetSmsSession("验证码已过期，请重新发送");
            return false;
        }
        long seconds = Math.max(0L, (retryAtElapsed - now + 999L) / SECOND_MS);
        host.updateSmsButton(seconds > 0L ? seconds + " 秒后可重发" : "重新发送验证码",
                !requestInFlight && seconds == 0L);
        return true;
    }

    private void resetSmsSession(String message) {
        generation++;
        handler.removeCallbacks(countdownTask);
        smsSessionId = "";
        sessionPhone = "";
        retryAtElapsed = 0L;
        expiresAtElapsed = 0L;
        requestInFlight = false;
        clearCaptcha();
        host.clearCode();
        refreshControls();
        host.updateSmsButton("发送验证码", true);
        host.setStatus(message, Status.ERROR);
    }

    private void refreshControls() {
        host.setControls(!requestInFlight, mode, !smsSessionId.isEmpty(), retryAtElapsed);
    }

    private void reset() {
        generation++;
        handler.removeCallbacks(countdownTask);
        requestInFlight = false;
        mode = Mode.SMS;
        rememberedPhone = "";
        smsSessionId = "";
        sessionPhone = "";
        retryAtElapsed = 0L;
        expiresAtElapsed = 0L;
        clearCaptcha();
    }

    private void clearCaptcha() {
        captchaAction = CaptchaAction.NONE;
        captchaPhone = "";
        captchaCode = "";
        captchaPassword = "";
        captchaSessionId = "";
        pendingCaptchaUri = "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
