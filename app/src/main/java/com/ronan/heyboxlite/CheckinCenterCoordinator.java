package com.ronan.heyboxlite;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

final class CheckinCenterCoordinator {
    interface AuthorizationListener {
        void onAuthorizationChanged(boolean paired);
    }

    private final SessionStore session;
    private final CheckinCenterStore store;
    private final CheckinCenterClient client;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences sessionPreferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener cookieListener;
    private final List<CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>>
            syncCallbacks = new ArrayList<>();
    private final Runnable changedCookieSync = () -> syncCredentials(false, null);
    private AuthorizationListener authorizationListener;
    private boolean syncing;
    private boolean resyncRequested;
    private String activeSyncFingerprint = "";
    private boolean closed;

    CheckinCenterCoordinator(Context context, SessionStore session) {
        this.session = session;
        this.store = new CheckinCenterStore(context);
        this.client = new CheckinCenterClient();
        this.sessionPreferences = context.getApplicationContext().getSharedPreferences(
                SecureStrings.preferencesName(), Context.MODE_PRIVATE);
        this.cookieListener = (preferences, key) -> {
            if (!SecureStrings.encryptedCookieKey().equals(key)) return;
            handler.removeCallbacks(changedCookieSync);
            handler.postDelayed(changedCookieSync, 1_200L);
        };
        this.sessionPreferences.registerOnSharedPreferenceChangeListener(cookieListener);
    }

    void setAuthorizationListener(AuthorizationListener listener) {
        this.authorizationListener = listener;
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

    void syncIfNeeded() {
        syncCredentials(false, null);
    }

    void syncCredentials(boolean force,
                         CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount> callback) {
        if (closed) return;
        if (!paired()) {
            fail(callback, CheckinCenterClient.Operation.CREDENTIAL_SYNC,
                    "尚未连接签到服务");
            return;
        }
        if (!session.isLoggedIn()) {
            completeWithoutSync(callback);
            return;
        }
        final CheckinCredentialPayload payload;
        try {
            payload = CheckinCredentialPayload.fromSession(session);
        } catch (CheckinCredentialPayload.InvalidCredentials error) {
            if (callback != null) {
                callback.onError(new CheckinCenterClient.ApiError(
                        CheckinCenterClient.Operation.CREDENTIAL_SYNC, 422, error.getMessage()));
            }
            return;
        }
        if (!force && payload.fingerprint.equals(store.credentialFingerprint())) {
            completeWithoutSync(callback);
            return;
        }
        if (callback != null) syncCallbacks.add(callback);
        if (syncing) {
            if (!payload.fingerprint.equals(activeSyncFingerprint)) {
                resyncRequested = true;
            }
            return;
        }
        syncing = true;
        activeSyncFingerprint = payload.fingerprint;
        syncAttempt(payload, 0);
    }

    void getStatus(CheckinCenterClient.Callback<CheckinCenterClient.Status> callback) {
        String token = store.deviceToken();
        if (token.isEmpty()) {
            fail(callback, CheckinCenterClient.Operation.STATUS, "尚未连接签到服务");
            return;
        }
        statusAttempt(token, callback, 0);
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

    private void syncAttempt(CheckinCredentialPayload payload, int retries) {
        String token = store.deviceToken();
        if (token.isEmpty()) {
            finishSync(null, new CheckinCenterClient.ApiError(
                    CheckinCenterClient.Operation.CREDENTIAL_SYNC, 401,
                    "签到服务连接已失效，请重新连接"));
            return;
        }
        client.syncCredentials(token, payload.json,
                new CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>() {
                    @Override
                    public void onSuccess(CheckinCenterClient.ConnectedAccount value) {
                        store.saveCredentialFingerprint(payload.fingerprint);
                        finishSync(value, null);
                    }

                    @Override
                    public void onError(CheckinCenterClient.ApiError error) {
                        if (handleAuthorizationError(error)) {
                            finishSync(null, error);
                            return;
                        }
                        if (CheckinRetryPolicy.shouldRetry(error, retries)) {
                            handler.postDelayed(() -> syncAttempt(payload, retries + 1),
                                    CheckinRetryPolicy.delayMillis(error, retries, 0L));
                            return;
                        }
                        finishSync(null, error);
                    }
                });
    }

    private void finishSync(CheckinCenterClient.ConnectedAccount value,
                            CheckinCenterClient.ApiError error) {
        syncing = false;
        activeSyncFingerprint = "";
        if (error == null && resyncRequested && paired() && session.isLoggedIn()) {
            resyncRequested = false;
            try {
                CheckinCredentialPayload next = CheckinCredentialPayload.fromSession(session);
                if (!next.fingerprint.equals(store.credentialFingerprint())) {
                    syncing = true;
                    activeSyncFingerprint = next.fingerprint;
                    syncAttempt(next, 0);
                    return;
                }
            } catch (CheckinCredentialPayload.InvalidCredentials invalid) {
                error = new CheckinCenterClient.ApiError(
                        CheckinCenterClient.Operation.CREDENTIAL_SYNC, 422,
                        invalid.getMessage());
            }
        }
        resyncRequested = false;
        List<CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount>> callbacks =
                new ArrayList<>(syncCallbacks);
        syncCallbacks.clear();
        for (CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount> callback
                : callbacks) {
            if (error == null) callback.onSuccess(value);
            else callback.onError(error);
        }
    }

    private void completeWithoutSync(
            CheckinCenterClient.Callback<CheckinCenterClient.ConnectedAccount> callback) {
        if (callback == null) return;
        handler.post(() -> {
            if (!closed) callback.onSuccess(null);
        });
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

    private boolean handleAuthorizationError(CheckinCenterClient.ApiError error) {
        if (error == null || !error.authorizationInvalid()) return false;
        clearAuthorization();
        return true;
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
        if (manufacturer.isEmpty() || model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
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
        closed = true;
        sessionPreferences.unregisterOnSharedPreferenceChangeListener(cookieListener);
        handler.removeCallbacksAndMessages(null);
        syncCallbacks.clear();
        client.close();
    }
}
