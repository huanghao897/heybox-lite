package com.ronan.heyboxlite;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

final class CheckinServiceAccountFlow {
    enum Mode { LOGIN, REGISTER }
    enum Status { NORMAL, ACCENT, ERROR }

    interface Host {
        boolean pairingVisible();
        String username();
        String password();
        String passwordConfirmation();
        String email();
        String emailCode();
        void renderModeChanged();
        void setControlsEnabled(boolean enabled, long emailRetryAtElapsed);
        void setStatus(String message, Status status);
        void updateEmailButton(String label, boolean enabled);
        void clearPasswords();
        void clearEmailCode();
        void focusEmailCode();
        void focusPassword();
        void showError(String message);
        void pairingApproved();
    }

    private static final long SECOND_MS = 1_000L;

    private final CheckinCenterCoordinator coordinator;
    private final CheckinPairingFlow pairing;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable emailCountdownTask = this::updateEmailCountdown;
    private Mode mode = Mode.LOGIN;
    private String rememberedUsername = "";
    private String rememberedEmail = "";
    private String emailChallengeId = "";
    private String emailChallengeAddress = "";
    private long emailRetryAtElapsed;
    private long emailExpiresAtElapsed;
    private boolean approvalInFlight;
    private boolean emailInFlight;
    private boolean resumed;
    private boolean closed;

    CheckinServiceAccountFlow(CheckinCenterCoordinator coordinator,
                              CheckinPairingFlow pairing, Host host) {
        this.coordinator = coordinator;
        this.pairing = pairing;
        this.host = host;
    }

    Mode mode() {
        return mode;
    }

    String rememberedUsername() {
        return rememberedUsername;
    }

    String rememberedEmail() {
        return rememberedEmail;
    }

    boolean busy() {
        return approvalInFlight || emailInFlight;
    }

    void switchMode(Mode value) {
        if (busy() || value == mode) return;
        rememberInputs();
        mode = value;
        clearEmailChallenge();
        host.clearPasswords();
        host.renderModeChanged();
    }

    void submit() {
        if (mode == Mode.REGISTER) register();
        else approve();
    }

    void sendRegistrationEmail() {
        CheckinCenterClient.PairingStart start = activeStart();
        if (emailInFlight || start == null) return;
        long now = SystemClock.elapsedRealtime();
        if (now < emailRetryAtElapsed) return;
        rememberedEmail = clean(host.email());
        if (!CheckinCenterClient.validRegistrationEmail(rememberedEmail)) {
            host.setStatus("请输入正确的邮箱地址", Status.ERROR);
            return;
        }
        emailInFlight = true;
        host.setControlsEnabled(false, emailRetryAtElapsed);
        host.setStatus("正在发送邮箱验证码", Status.NORMAL);
        coordinator.sendRegistrationEmail(start.userCode, rememberedEmail,
                new CheckinCenterClient.Callback<CheckinCenterClient.RegistrationEmailSession>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.RegistrationEmailSession value) {
                        if (!callbackActive(start)) return;
                        emailInFlight = false;
                        emailChallengeId = value.challengeId;
                        emailChallengeAddress = rememberedEmail;
                        long receivedAt = SystemClock.elapsedRealtime();
                        emailRetryAtElapsed = receivedAt
                                + value.retryAfterSeconds * SECOND_MS;
                        emailExpiresAtElapsed = receivedAt
                                + value.expiresInSeconds * SECOND_MS;
                        host.setControlsEnabled(true, emailRetryAtElapsed);
                        host.setStatus("邮箱验证码已发送", Status.ACCENT);
                        host.clearEmailCode();
                        bindView();
                        host.focusEmailCode();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (!callbackActive(start)) return;
                        emailInFlight = false;
                        if (error.retryAfterSeconds > 0) {
                            emailRetryAtElapsed = SystemClock.elapsedRealtime()
                                    + error.retryAfterSeconds * SECOND_MS;
                        }
                        host.setControlsEnabled(true, emailRetryAtElapsed);
                        host.setStatus(error.getMessage(), Status.ERROR);
                        bindView();
                    }
                });
    }

    void resume() {
        resumed = true;
        updateEmailCountdown();
    }

    void pause() {
        resumed = false;
        handler.removeCallbacks(emailCountdownTask);
    }

    void bindView() {
        updateEmailButton(resumed);
    }

    void reset() {
        handler.removeCallbacks(emailCountdownTask);
        mode = Mode.LOGIN;
        rememberedUsername = "";
        rememberedEmail = "";
        approvalInFlight = false;
        emailInFlight = false;
        clearEmailChallenge();
    }

    void close() {
        closed = true;
        resumed = false;
        reset();
        handler.removeCallbacksAndMessages(null);
    }

    private void approve() {
        CheckinCenterClient.PairingStart start = activeStart();
        if (approvalInFlight || start == null) return;
        approvalInFlight = true;
        host.setControlsEnabled(false, emailRetryAtElapsed);
        host.setStatus("正在验证签到服务账号", Status.NORMAL);
        coordinator.approvePairing(start.userCode, host.username(), host.password(),
                new CheckinCenterClient.Callback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean value) {
                        if (!callbackActive(start)) return;
                        approvalInFlight = false;
                        host.clearPasswords();
                        host.setStatus("验证成功，正在完成设备连接", Status.ACCENT);
                        host.pairingApproved();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (!callbackActive(start)) return;
                        approvalInFlight = false;
                        host.clearPasswords();
                        host.setControlsEnabled(true, emailRetryAtElapsed);
                        host.setStatus(error.getMessage(), Status.ERROR);
                        host.focusPassword();
                    }
                });
    }

    private void register() {
        CheckinCenterClient.PairingStart start = activeStart();
        if (approvalInFlight || start == null) return;
        String username = clean(host.username());
        String password = host.password();
        if (!password.equals(host.passwordConfirmation())) {
            host.clearPasswords();
            host.setStatus("两次输入的密码不一致", Status.ERROR);
            return;
        }
        String email = clean(host.email());
        String emailCode = clean(host.emailCode());
        if (!validateEmail(start, email, emailCode)) return;
        rememberedUsername = username;
        rememberedEmail = email;
        approvalInFlight = true;
        host.setControlsEnabled(false, emailRetryAtElapsed);
        host.setStatus("正在创建签到服务账号", Status.NORMAL);
        coordinator.registerPairing(start.userCode, username, password, email,
                emailChallengeId, emailCode, start.registrationEmailRequired,
                new CheckinCenterClient.Callback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean value) {
                        if (!callbackActive(start)) return;
                        approvalInFlight = false;
                        host.clearPasswords();
                        host.setStatus("注册成功，正在完成设备连接", Status.ACCENT);
                        host.pairingApproved();
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (!callbackActive(start)) return;
                        approvalInFlight = false;
                        host.clearPasswords();
                        host.setControlsEnabled(true, emailRetryAtElapsed);
                        host.setStatus(error.getMessage(), Status.ERROR);
                    }
                });
    }

    private boolean validateEmail(CheckinCenterClient.PairingStart start,
                                  String email, String emailCode) {
        if (!start.registrationEmailRequired) return true;
        if (!CheckinCenterClient.validRegistrationEmail(email)) {
            host.setStatus("请输入正确的邮箱地址", Status.ERROR);
            return false;
        }
        if (emailChallengeId.isEmpty()
                || SystemClock.elapsedRealtime() >= emailExpiresAtElapsed) {
            host.setStatus("请先发送邮箱验证码", Status.ERROR);
            return false;
        }
        if (!email.equalsIgnoreCase(emailChallengeAddress)) {
            clearEmailChallenge();
            bindView();
            host.setStatus("邮箱已更改，请重新发送验证码", Status.ERROR);
            return false;
        }
        if (!CheckinCenterClient.validRegistrationEmailCode(emailCode)) {
            host.setStatus("请输入 6 位邮箱验证码", Status.ERROR);
            return false;
        }
        return true;
    }

    private CheckinCenterClient.PairingStart activeStart() {
        CheckinCenterClient.PairingStart start = pairing.startValue();
        if (start == null || !host.pairingVisible()) return null;
        if (pairing.expired()) {
            host.showError("配对已过期，请重新连接");
            return null;
        }
        return start;
    }

    private boolean callbackActive(CheckinCenterClient.PairingStart start) {
        return !closed && host.pairingVisible() && pairing.startValue() == start;
    }

    private void rememberInputs() {
        rememberedUsername = clean(host.username());
        rememberedEmail = clean(host.email());
    }

    private void clearEmailChallenge() {
        emailChallengeId = "";
        emailChallengeAddress = "";
        emailRetryAtElapsed = 0L;
        emailExpiresAtElapsed = 0L;
        host.clearEmailCode();
    }

    private void updateEmailCountdown() {
        handler.removeCallbacks(emailCountdownTask);
        if (!resumed || closed || !host.pairingVisible()) return;
        updateEmailButton(true);
    }

    private void updateEmailButton(boolean scheduleNext) {
        long now = SystemClock.elapsedRealtime();
        if (!emailChallengeId.isEmpty() && now >= emailExpiresAtElapsed) {
            clearEmailChallenge();
            host.setStatus("邮箱验证码已过期，请重新发送", Status.ERROR);
        }
        long retrySeconds = Math.max(0L,
                (emailRetryAtElapsed - now + 999L) / SECOND_MS);
        host.updateEmailButton(retrySeconds > 0L
                        ? retrySeconds + " 秒后可重发" : "发送邮箱验证码",
                !busy() && retrySeconds == 0L);
        if (scheduleNext && (retrySeconds > 0L || !emailChallengeId.isEmpty())) {
            handler.postDelayed(emailCountdownTask, SECOND_MS);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
