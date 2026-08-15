package com.ronan.heyboxlite;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.Locale;

final class CheckinCenterCoordinator {
    interface AuthorizationListener {
        void onAuthorizationChanged(boolean paired);
    }

    private final CheckinCenterStore store;
    private final CheckinCenterClient client;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AuthorizationListener authorizationListener;

    CheckinCenterCoordinator(Context context, LocalCache diagnostics) {
        store = new CheckinCenterStore(context);
        client = new CheckinCenterClient(diagnostics::log);
    }

    void setAuthorizationListener(AuthorizationListener listener) {
        authorizationListener = listener;
    }

    boolean supported() {
        return store.supported();
    }

    boolean paired() {
        return store.hasDeviceToken();
    }

    void startPairing(CheckinCenterClient.Callback<CheckinCenterClient.PairingStart> callback) {
        if (!supported()) {
            fail(callback, CheckinCenterClient.Operation.PAIR_START,
                    "小黑盒自动签到需要 Android 7.0 或更高版本");
            return;
        }
        client.startPairing(deviceName(), BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
                callback);
    }

    void pollPairing(String deviceCode,
                     CheckinCenterClient.Callback<CheckinCenterClient.PairingPoll> callback) {
        client.pollPairing(deviceCode, callback);
    }

    void approvePairing(String userCode, String username, String password,
                        CheckinCenterClient.Callback<Boolean> callback) {
        client.approvePairing(userCode, username, password, callback);
    }

    void sendRegistrationEmail(String userCode, String email,
                               CheckinCenterClient.Callback<
                                       CheckinCenterClient.RegistrationEmailSession> callback) {
        client.sendRegistrationEmail(userCode, email, callback);
    }

    void registerPairing(String userCode, String username, String password,
                         String email, String emailChallengeId, String emailCode,
                         boolean emailRequired, CheckinCenterClient.Callback<Boolean> callback) {
        client.registerPairing(userCode, username, password, email, emailChallengeId,
                emailCode, emailRequired, callback);
    }

    boolean authorize(String deviceToken) {
        try {
            store.saveDeviceToken(deviceToken);
            notifyAuthorizationChanged(true);
            return true;
        } catch (Exception error) {
            store.clearAuthorization();
            notifyAuthorizationChanged(false);
            return false;
        }
    }

    void getStatus(CheckinCenterClient.Callback<CheckinCenterClient.Status> callback) {
        String token = store.deviceToken();
        if (token.isEmpty()) {
            fail(callback, CheckinCenterClient.Operation.STATUS, "尚未连接签到服务");
            return;
        }
        statusAttempt(token, callback, 0);
    }

    void sendSmsCode(String phone, String captchaTicket, String captchaRandstr,
                     CheckinCenterClient.Callback<CheckinCenterClient.SmsSession> callback) {
        String token = store.deviceToken();
        if (token.isEmpty()) {
            fail(callback, CheckinCenterClient.Operation.SMS_SEND, "尚未连接签到服务");
            return;
        }
        client.sendSmsCode(token, phone, captchaTicket, captchaRandstr,
                authorizationAware(callback));
    }

    void submitSmsCode(String sessionId, String code, String captchaTicket,
                       String captchaRandstr,
                       CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>
                               callback) {
        String token = store.deviceToken();
        if (token.isEmpty()) {
            fail(callback, CheckinCenterClient.Operation.SMS_SUBMIT, "尚未连接签到服务");
            return;
        }
        client.submitSmsCode(token, sessionId, code, captchaTicket, captchaRandstr,
                authorizationAware(callback));
    }

    void loginWithPassword(String phone, String password, String captchaTicket,
                           String captchaRandstr,
                           CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>
                                   callback) {
        String token = store.deviceToken();
        if (token.isEmpty()) {
            fail(callback, CheckinCenterClient.Operation.PASSWORD_LOGIN,
                    "尚未连接签到服务");
            return;
        }
        client.loginWithPassword(token, phone, password, captchaTicket, captchaRandstr,
                authorizationAware(callback));
    }

    void updateTaskSettings(boolean enabled, String scheduleTime, int offsetMinutes,
                            CheckinCenterClient.Callback<CheckinCenterClient.Task> callback) {
        String token = store.deviceToken();
        if (token.isEmpty()) {
            fail(callback, CheckinCenterClient.Operation.TASK_SETTINGS,
                    "尚未连接签到服务");
            return;
        }
        client.updateTaskSettings(token, enabled, scheduleTime, offsetMinutes,
                authorizationAware(callback));
    }

    void runNow(CheckinCenterClient.Callback<CheckinCenterClient.RunResult> callback) {
        String token = store.deviceToken();
        if (token.isEmpty()) {
            fail(callback, CheckinCenterClient.Operation.RUN_NOW, "尚未连接签到服务");
            return;
        }
        runAttempt(token, callback, 0);
    }

    void revokeDevice(CheckinCenterClient.Callback<Boolean> callback) {
        String token = store.deviceToken();
        if (token.isEmpty()) {
            clearAuthorization();
            if (callback != null) callback.onSuccess(Boolean.TRUE);
            return;
        }
        revokeAttempt(token, callback, 0);
    }

    void createBillingOrder(CheckinCenterClient.Callback<CheckinBilling.Order> callback) {
        String token = store.deviceToken();
        if (token.isEmpty()) {
            fail(callback, CheckinCenterClient.Operation.BILLING_CREATE, "尚未连接签到服务");
            return;
        }
        client.createBillingOrder(token, authorizationAware(callback));
    }

    void getBillingOrder(String orderId,
                         CheckinCenterClient.Callback<CheckinBilling.Order> callback) {
        String token = store.deviceToken();
        if (token.isEmpty()) {
            fail(callback, CheckinCenterClient.Operation.BILLING_STATUS, "尚未连接签到服务");
            return;
        }
        client.getBillingOrder(token, orderId, authorizationAware(callback));
    }

    void loadBillingQr(String orderId, CheckinCenterClient.Callback<byte[]> callback) {
        String token = store.deviceToken();
        if (token.isEmpty()) {
            fail(callback, CheckinCenterClient.Operation.BILLING_QR, "尚未连接签到服务");
            return;
        }
        client.loadBillingQr(token, orderId, authorizationAware(callback));
    }

    void submitBillingClaim(String orderId, String paymentReference,
                            CheckinCenterClient.Callback<CheckinBilling.Review> callback) {
        String token = store.deviceToken();
        if (token.isEmpty()) {
            fail(callback, CheckinCenterClient.Operation.BILLING_CLAIM, "尚未连接签到服务");
            return;
        }
        client.submitBillingClaim(token, orderId, paymentReference,
                authorizationAware(callback));
    }

    private void statusAttempt(String token,
                               CheckinCenterClient.Callback<CheckinCenterClient.Status> callback,
                               int retries) {
        client.getStatus(token, new CheckinCenterClient.Callback<CheckinCenterClient.Status>() {
            @Override
            public void onSuccess(CheckinCenterClient.Status value) {
                if (callback != null) callback.onSuccess(value);
            }

            @Override
            public void onError(CheckinCenterClient.ApiError error) {
                if (handleAuthorizationError(error)) {
                    if (callback != null) callback.onError(error);
                    return;
                }
                if (CheckinRetryPolicy.shouldRetry(error, retries)) {
                    handler.postDelayed(() -> statusAttempt(token, callback, retries + 1),
                            CheckinRetryPolicy.delayMillis(error, retries, 0L));
                    return;
                }
                if (callback != null) callback.onError(error);
            }
        });
    }

    private void runAttempt(String token,
                            CheckinCenterClient.Callback<CheckinCenterClient.RunResult> callback,
                            int retries) {
        client.runNow(token, new CheckinCenterClient.Callback<CheckinCenterClient.RunResult>() {
            @Override
            public void onSuccess(CheckinCenterClient.RunResult value) {
                if (callback != null) callback.onSuccess(value);
            }

            @Override
            public void onError(CheckinCenterClient.ApiError error) {
                if (handleAuthorizationError(error)) {
                    if (callback != null) callback.onError(error);
                    return;
                }
                if (CheckinRetryPolicy.shouldRetry(error, retries)) {
                    handler.postDelayed(() -> runAttempt(token, callback, retries + 1),
                            CheckinRetryPolicy.delayMillis(error, retries, 4_000L));
                    return;
                }
                if (callback != null) callback.onError(error);
            }
        });
    }

    private void revokeAttempt(String token, CheckinCenterClient.Callback<Boolean> callback,
                               int retries) {
        client.revokeDevice(token, new CheckinCenterClient.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean value) {
                clearAuthorization();
                if (callback != null) callback.onSuccess(Boolean.TRUE);
            }

            @Override
            public void onError(CheckinCenterClient.ApiError error) {
                if (error.authorizationInvalid()) {
                    clearAuthorization();
                    if (callback != null) callback.onSuccess(Boolean.TRUE);
                    return;
                }
                if (CheckinRetryPolicy.shouldRetry(error, retries)) {
                    handler.postDelayed(() -> revokeAttempt(token, callback, retries + 1),
                            CheckinRetryPolicy.delayMillis(error, retries, 0L));
                    return;
                }
                if (callback != null) callback.onError(error);
            }
        });
    }

    private boolean handleAuthorizationError(CheckinCenterClient.ApiError error) {
        if (error == null || !error.authorizationInvalid()) return false;
        clearAuthorization();
        return true;
    }

    private <T> CheckinCenterClient.Callback<T> authorizationAware(
            CheckinCenterClient.Callback<T> callback) {
        return new CheckinCenterClient.Callback<T>() {
            @Override
            public void onSuccess(T value) {
                if (callback != null) callback.onSuccess(value);
            }

            @Override
            public void onError(CheckinCenterClient.ApiError error) {
                handleAuthorizationError(error);
                if (callback != null) callback.onError(error);
            }
        };
    }

    private void clearAuthorization() {
        store.clearAuthorization();
        notifyAuthorizationChanged(false);
    }

    private void notifyAuthorizationChanged(boolean paired) {
        if (authorizationListener != null) authorizationListener.onAuthorizationChanged(paired);
    }

    private static String deviceName() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        if (model.isEmpty()) return "Android";
        if (manufacturer.isEmpty()
                || model.toLowerCase(Locale.ROOT)
                .startsWith(manufacturer.toLowerCase(Locale.ROOT))) {
            return model;
        }
        return manufacturer + " " + model;
    }

    private static <T> void fail(CheckinCenterClient.Callback<T> callback,
                                 CheckinCenterClient.Operation operation, String message) {
        if (callback != null) callback.onError(new CheckinCenterClient.ApiError(
                operation, 0, message));
    }

    public void close() {
        handler.removeCallbacksAndMessages(null);
        client.close();
    }
}
