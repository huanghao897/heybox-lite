package com.ronan.heyboxlite;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PhoneLoginManager {
    interface CodeCallback {
        void onCodeSent(int retryAfterSeconds);
        void onError(String message);
    }

    interface LoginCallback {
        void onSuccess(PhoneLoginResponse response);
        void onError(String message);
    }

    interface Logger {
        void log(String message);
    }

    private interface EncryptedPhoneCallback {
        void onReady(String encryptedPhone);
        void onError(String message);
    }

    private final Context app;
    private final SessionStore session;
    private final ApiClient api;
    private final Logger logger;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService cryptoExecutor = Executors.newSingleThreadExecutor();
    private boolean codeRequestInFlight;
    private boolean loginInFlight;
    private boolean closed;

    PhoneLoginManager(Context context, SessionStore session, ApiClient api, Logger logger) {
        Context application = context.getApplicationContext();
        this.app = application == null ? context : application;
        this.session = session;
        this.api = api;
        this.logger = logger;
    }

    void requestCode(String phone, CodeCallback callback) {
        String normalizedPhone = PhoneNumber.normalizeChineseMobile(phone);
        if (normalizedPhone.isEmpty()) {
            callback.onError("\u8bf7\u8f93\u5165\u6b63\u786e\u7684\u624b\u673a\u53f7");
            return;
        }
        if (codeRequestInFlight) return;
        codeRequestInFlight = true;
        session.clearPendingMobileLoginCookies();
        encryptPhone(normalizedPhone, new EncryptedPhoneCallback() {
            @Override public void onReady(String encryptedPhone) {
                Map<String, String> body = new LinkedHashMap<>();
                body.put("phone_num", encryptedPhone);
                api.postSignedIsolated(EndpointProvider.mobileLoginCode(),
                        Collections.emptyMap(), body, HeyboxSigner.Algorithm.ANDROID,
                        ApiClient.RequestProfile.OFFICIAL_MOBILE_LOGIN,
                        new ApiClient.Callback() {
                            @Override public void onResponseCookies(List<String> cookies) {
                                session.mergePendingMobileLoginCookies(cookies);
                            }

                            @Override public void onSuccess(JSONObject response) {
                                codeRequestInFlight = false;
                                log("mobile login verification code requested");
                                callback.onCodeSent(PhoneLoginResponse.retryAfterSeconds(response));
                            }

                            @Override public void onError(String message) {
                                codeRequestInFlight = false;
                                log("mobile login code request failed: " + safe(message));
                                callback.onError(message);
                            }
                        });
            }

            @Override public void onError(String message) {
                codeRequestInFlight = false;
                callback.onError(message);
            }
        });
    }

    void login(String phone, String code, LoginCallback callback) {
        String normalizedPhone = PhoneNumber.normalizeChineseMobile(phone);
        if (normalizedPhone.isEmpty()) {
            callback.onError("\u8bf7\u8f93\u5165\u6b63\u786e\u7684\u624b\u673a\u53f7");
            return;
        }
        if (!PhoneNumber.isVerificationCode(code)) {
            callback.onError("\u8bf7\u8f93\u5165\u6b63\u786e\u7684\u9a8c\u8bc1\u7801");
            return;
        }
        if (loginInFlight) return;
        loginInFlight = true;
        encryptPhone(normalizedPhone, new EncryptedPhoneCallback() {
            @Override public void onReady(String encryptedPhone) {
                Map<String, String> query = new LinkedHashMap<>();
                query.put("code", code.trim());
                query.put("is_new_device", session.hasSignInCredentials() ? "0" : "1");
                Map<String, String> body = new LinkedHashMap<>();
                body.put("phone_num", encryptedPhone);
                api.postSignedIsolated(EndpointProvider.mobileLogin(), query, body,
                        HeyboxSigner.Algorithm.ANDROID,
                        ApiClient.RequestProfile.OFFICIAL_MOBILE_LOGIN,
                        new ApiClient.Callback() {
                            @Override public void onResponseCookies(List<String> cookies) {
                                session.mergePendingMobileLoginCookies(cookies);
                            }

                            @Override public void onSuccess(JSONObject response) {
                                loginInFlight = false;
                                try {
                                    PhoneLoginResponse parsed = PhoneLoginResponse.parse(response);
                                    session.saveMobileLogin(parsed);
                                    log("mobile login succeeded userIdLen="
                                            + parsed.userId.length());
                                    callback.onSuccess(parsed);
                                } catch (RuntimeException error) {
                                    if (error instanceof IllegalStateException) {
                                        session.clearSignInCredentials();
                                    }
                                    session.clearPendingMobileLoginCookies();
                                    callback.onError(error.getMessage());
                                }
                            }

                            @Override public void onError(String message) {
                                loginInFlight = false;
                                log("mobile login failed: " + safe(message));
                                callback.onError(message);
                            }
                        });
            }

            @Override public void onError(String message) {
                loginInFlight = false;
                callback.onError(message);
            }
        });
    }

    void close() {
        closed = true;
        main.removeCallbacksAndMessages(null);
        cryptoExecutor.shutdownNow();
    }

    private void encryptPhone(String normalizedPhone, EncryptedPhoneCallback callback) {
        cryptoExecutor.execute(() -> {
            MobileLoginCryptoBridge.Result result = MobileLoginCryptoBridge.encrypt(
                    app, normalizedPhone, this::log);
            main.post(() -> {
                if (closed) return;
                if (result.isSuccess()) {
                    log("mobile login phone encrypted outputLen="
                            + result.encryptedPhone.length());
                    callback.onReady(result.encryptedPhone);
                } else {
                    callback.onError("\u624b\u673a\u53f7\u52a0\u5bc6\u5931\u8d25: "
                            + safe(result.error));
                }
            });
        });
    }

    private void log(String message) {
        if (logger != null) logger.log(message);
    }

    private static String safe(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 160 ? clean.substring(0, 160) : clean;
    }
}
