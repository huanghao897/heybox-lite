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
        void openCaptcha(String uri);
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
    private CaptchaAction captchaAction = CaptchaAction.NONE;
    private String captchaPhone = "";
    private String captchaCode = "";
    private String captchaPassword = "";

    CheckinMobileLoginFlow(CheckinCenterCoordinator coordinator, Host host) {
        this.coordinator = coordinator;
        this.host = host;
    }

    void start() {
        reset();
    }

    void resume() {
        resumed = true;
        refreshControls();
        updateCountdown();
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
        if (requestInFlight || value == mode) return;
        rememberedPhone = clean(host.phone());
        host.clearPassword();
        mode = value;
        clearCaptcha();
        host.renderModeChanged();
    }

    void sendSmsCode() {
        if (requestInFlight) return;
        String phone = clean(host.phone());
        rememberedPhone = phone;
        requestSmsCode(phone, "", "", false);
    }

    void submitSmsCode() {
        if (requestInFlight || smsSessionId.isEmpty()) return;
        if (SystemClock.elapsedRealtime() >= expiresAtElapsed) {
            resetSmsSession("验证码已过期，请重新发送");
            return;
        }
        requestSubmitSmsCode(clean(host.code()), "", "", false);
    }

    void loginWithPassword() {
        if (requestInFlight) return;
        String phone = clean(host.phone());
        rememberedPhone = phone;
        requestPasswordLogin(phone, host.password(), "", "", false);
    }

    void onCaptchaResult(String ticket, String randstr) {
        if (!host.active() || captchaAction == CaptchaAction.NONE) return;
        CaptchaAction action = captchaAction;
        String phone = captchaPhone;
        String code = captchaCode;
        String password = captchaPassword;
        clearCaptcha();
        if (action == CaptchaAction.SEND_SMS) {
            requestSmsCode(phone, ticket, randstr, true);
        } else if (action == CaptchaAction.SUBMIT_SMS) {
            requestSubmitSmsCode(code, ticket, randstr, true);
        } else {
            requestPasswordLogin(phone, password, ticket, randstr, true);
        }
    }

    void onCaptchaCancelled(String message) {
        if (!host.active() || captchaAction == CaptchaAction.NONE) return;
        clearCaptcha();
        requestInFlight = false;
        refreshControls();
        updateCountdown();
        host.setStatus(clean(message).isEmpty() ? "安全验证已取消" : message,
                Status.ERROR);
    }

    private void requestSmsCode(String phone, String ticket, String randstr,
                                boolean captchaRetry) {
        requestInFlight = true;
        refreshControls();
        host.setStatus(captchaRetry ? "安全验证通过，正在发送验证码" : "正在发送验证码",
                Status.NORMAL);
        coordinator.sendSmsCode(phone, ticket, randstr,
                new CheckinCenterClient.Callback<CheckinCenterClient.SmsSession>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.SmsSession value) {
                        if (!host.active()) return;
                        clearCaptcha();
                        requestInFlight = false;
                        smsSessionId = value.sessionId;
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
                        if (!host.active()) return;
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
        requestInFlight = true;
        refreshControls();
        host.setStatus(captchaRetry ? "安全验证通过，正在继续登录" : "正在验证并连接账号",
                Status.NORMAL);
        coordinator.submitSmsCode(smsSessionId, code, ticket, randstr,
                new CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.ConnectedAccount value) {
                        if (!host.active()) return;
                        reset();
                        host.showMessage("手机号登录成功");
                        host.connected();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (!host.active()) return;
                        if (!captchaRetry && beginCaptcha(
                                CaptchaAction.SUBMIT_SMS, "", code, "", error)) return;
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
        requestInFlight = true;
        refreshControls();
        host.setStatus(captchaRetry ? "安全验证通过，正在继续登录" : "正在登录小黑盒",
                Status.NORMAL);
        coordinator.loginWithPassword(phone, password, ticket, randstr,
                new CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.ConnectedAccount value) {
                        if (!host.active()) return;
                        host.clearPassword();
                        reset();
                        host.showMessage("手机号密码登录成功");
                        host.connected();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (!host.active()) return;
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
        captchaPhone = clean(phone);
        captchaCode = clean(code);
        captchaPassword = password == null ? "" : password;
        host.setStatus("请完成小黑盒安全验证", Status.ACCENT);
        host.openCaptcha(error.captchaUri);
        return true;
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
            resetSmsSession("验证码已过期，请重新发送");
            return false;
        }
        long seconds = Math.max(0L, (retryAtElapsed - now + 999L) / SECOND_MS);
        host.updateSmsButton(seconds > 0L ? seconds + " 秒后可重发" : "重新发送验证码",
                !requestInFlight && seconds == 0L);
        return true;
    }

    private void resetSmsSession(String message) {
        handler.removeCallbacks(countdownTask);
        smsSessionId = "";
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
        handler.removeCallbacks(countdownTask);
        requestInFlight = false;
        mode = Mode.SMS;
        rememberedPhone = "";
        smsSessionId = "";
        retryAtElapsed = 0L;
        expiresAtElapsed = 0L;
        clearCaptcha();
    }

    private void clearCaptcha() {
        captchaAction = CaptchaAction.NONE;
        captchaPhone = "";
        captchaCode = "";
        captchaPassword = "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
