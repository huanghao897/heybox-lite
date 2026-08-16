package com.ronan.heyboxlite;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.IDN;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

final class CheckinCenterClient {
    static final String TRUSTED_ORIGIN = "https://heyboxlite.xyz";
    static final String API_BASE = TRUSTED_ORIGIN + "/checkin/api/lite";
    private static final String TRUSTED_HOST = "heyboxlite.xyz";
    private static final String PAIRING_PATH_PREFIX = "/checkin/";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int STANDARD_READ_TIMEOUT_MS = 25_000;
    private static final int SIGNING_READ_TIMEOUT_MS = 125_000;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final int MAX_QR_BYTES = 512 * 1024;

    enum Operation {
        PAIR_START,
        PAIR_APPROVE,
        PAIR_REGISTER,
        REGISTRATION_EMAIL,
        PAIR_POLL,
        SMS_SEND,
        SMS_SUBMIT,
        PASSWORD_LOGIN,
        STATUS,
        TASK_SETTINGS,
        RUN_NOW,
        REVOKE,
        BILLING_CREATE,
        BILLING_STATUS,
        BILLING_QR,
        BILLING_CLAIM
    }

    interface Callback<T> {
        void onSuccess(T value);
        void onError(ApiError error);
    }

    interface EventLogger {
        void log(String event);
    }

    enum ErrorKind {
        HTTP,
        TIMEOUT,
        TLS,
        NETWORK,
        PROTOCOL,
        CLIENT
    }

    static final class ApiError extends Exception {
        final Operation operation;
        final int statusCode;
        final ErrorKind kind;
        final String diagnosticCode;
        final String captchaUri;
        final int retryAfterSeconds;

        ApiError(Operation operation, int statusCode, String message) {
            this(operation, statusCode, message,
                    statusCode > 0 ? ErrorKind.HTTP : ErrorKind.CLIENT);
        }

        ApiError(Operation operation, int statusCode, String message, ErrorKind kind) {
            this(operation, statusCode, message, kind, "");
        }

        ApiError(Operation operation, int statusCode, String message, ErrorKind kind,
                 String diagnosticCode) {
            this(operation, statusCode, message, kind, diagnosticCode, "");
        }

        ApiError(Operation operation, int statusCode, String message, ErrorKind kind,
                 String diagnosticCode, String captchaUri) {
            this(operation, statusCode, message, kind, diagnosticCode, captchaUri, 0);
        }

        ApiError(Operation operation, int statusCode, String message, ErrorKind kind,
                 String diagnosticCode, String captchaUri, int retryAfterSeconds) {
            super(message);
            this.operation = operation;
            this.statusCode = statusCode;
            this.kind = kind;
            this.diagnosticCode = diagnosticCode;
            this.captchaUri = captchaUri;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        boolean authorizationInvalid() {
            return statusCode == 401;
        }

        boolean retryable() {
            return statusCode == 429 || statusCode == 502 || statusCode == 503;
        }

        boolean captchaRequired() {
            return "captcha_required".equals(diagnosticCode) && !captchaUri.isEmpty();
        }

    }

    static final class PairingStart {
        final String deviceCode;
        final String userCode;
        final int expiresInSeconds;
        final int intervalSeconds;
        final boolean registrationOpen;
        final boolean registrationEmailRequired;

        PairingStart(String deviceCode, String userCode,
                     int expiresInSeconds, int intervalSeconds,
                     boolean registrationOpen, boolean registrationEmailRequired) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.expiresInSeconds = expiresInSeconds;
            this.intervalSeconds = intervalSeconds;
            this.registrationOpen = registrationOpen;
            this.registrationEmailRequired = registrationEmailRequired;
        }
    }

    static final class RegistrationEmailSession {
        final String challengeId;
        final int retryAfterSeconds;
        final int expiresInSeconds;

        RegistrationEmailSession(String challengeId, int retryAfterSeconds,
                                 int expiresInSeconds) {
            this.challengeId = challengeId;
            this.retryAfterSeconds = retryAfterSeconds;
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    static final class PairingPoll {
        final String state;
        final String deviceToken;

        PairingPoll(String state, String deviceToken) {
            this.state = state;
            this.deviceToken = deviceToken;
        }

        boolean authorized() {
            return "authorized".equals(state) && !deviceToken.isEmpty();
        }
    }

    static final class ConnectedAccount {
        final String displayName;
        final String externalIdMasked;
        final boolean taskEnabled;

        ConnectedAccount(String displayName, String externalIdMasked, boolean taskEnabled) {
            this.displayName = displayName;
            this.externalIdMasked = externalIdMasked;
            this.taskEnabled = taskEnabled;
        }
    }

    static final class SmsSession {
        final String sessionId;
        final int retryAfterSeconds;
        final int expiresInSeconds;

        SmsSession(String sessionId, int retryAfterSeconds, int expiresInSeconds) {
            this.sessionId = sessionId;
            this.retryAfterSeconds = retryAfterSeconds;
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    static final class Status {
        final Account account;
        final Task task;
        final LastRun lastRun;
        final CheckinBilling.Membership membership;

        Status(Account account, Task task, LastRun lastRun,
               CheckinBilling.Membership membership) {
            this.account = account;
            this.task = task;
            this.lastRun = lastRun;
            this.membership = membership;
        }
    }

    static final class Account {
        final String state;
        final String displayName;
        final String externalIdMasked;

        Account(String state, String displayName, String externalIdMasked) {
            this.state = state;
            this.displayName = displayName;
            this.externalIdMasked = externalIdMasked;
        }
    }

    static final class Task {
        final boolean enabled;
        final boolean sign;
        final String scheduleTime;
        final int offsetMinutes;
        final String windowStart;
        final String windowEnd;
        final boolean platformBlocked;
        final boolean signBlocked;

        Task(boolean enabled, boolean sign, String scheduleTime, int offsetMinutes,
             String windowStart, String windowEnd, boolean platformBlocked,
             boolean signBlocked) {
            this.enabled = enabled;
            this.sign = sign;
            this.scheduleTime = scheduleTime;
            this.offsetMinutes = offsetMinutes;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.platformBlocked = platformBlocked;
            this.signBlocked = signBlocked;
        }

        boolean active() {
            return enabled && sign && !platformBlocked && !signBlocked;
        }
    }

    static final class LastRun {
        final long id;
        final String status;
        final String summary;
        final String startedAt;
        final String finishedAt;
        final CheckinResult checkIn;

        LastRun(long id, String status, String summary, String startedAt, String finishedAt,
                CheckinResult checkIn) {
            this.id = id;
            this.status = status;
            this.summary = summary;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.checkIn = checkIn;
        }
    }

    static final class CheckinResult {
        final boolean checkedIn;
        final boolean newlySigned;
        final int coinDelta;
        final int experienceDelta;
        final int streakDays;

        CheckinResult(boolean checkedIn, boolean newlySigned, int coinDelta,
                      int experienceDelta, int streakDays) {
            this.checkedIn = checkedIn;
            this.newlySigned = newlySigned;
            this.coinDelta = coinDelta;
            this.experienceDelta = experienceDelta;
            this.streakDays = streakDays;
        }
    }

    static final class RunResult {
        final String status;
        final String summary;
        final long runId;
        final CheckinResult checkIn;

        RunResult(String status, String summary, long runId, CheckinResult checkIn) {
            this.status = status;
            this.summary = summary;
            this.runId = runId;
            this.checkIn = checkIn;
        }
    }

    private interface Call<T> {
        T execute() throws ApiError;
    }

    private interface Parser<T> {
        T parse(JSONObject value) throws JSONException, ApiError;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "checkin-center-api");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final EventLogger eventLogger;
    private volatile boolean closed;

    CheckinCenterClient() {
        this(event -> {});
    }

    CheckinCenterClient(EventLogger eventLogger) {
        this.eventLogger = eventLogger;
    }

    void startPairing(String deviceName, String appVersion, int appVersionCode,
                      Callback<PairingStart> callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("device_name", clean(deviceName, "Android"));
            body.put("app_version", clean(appVersion, "unknown"));
            body.put("app_version_code", appVersionCode);
        } catch (JSONException impossible) {
            deliverError(callback, protocolError(Operation.PAIR_START));
            return;
        }
        submit(callback, () -> request(Operation.PAIR_START, "POST", "/pair/start", "",
                body, CheckinCenterClient::parsePairingStart));
    }

    void pollPairing(String deviceCode, Callback<PairingPoll> callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("device_code", requirePrefix(deviceCode, "ccpair1_",
                    Operation.PAIR_POLL));
        } catch (JSONException impossible) {
            deliverError(callback, protocolError(Operation.PAIR_POLL));
            return;
        } catch (ApiError error) {
            deliverError(callback, error);
            return;
        }
        submit(callback, () -> request(Operation.PAIR_POLL, "POST", "/pair/poll", "",
                body, CheckinCenterClient::parsePairingPoll));
    }

    void approvePairing(String userCode, String username, String password,
                        Callback<Boolean> callback) {
        String normalizedCode = userCode == null ? "" : userCode.trim();
        String normalizedUsername = username == null ? "" : username.trim();
        String rawPassword = password == null ? "" : password;
        if (!normalizedCode.matches("[A-Z0-9]{4}-[A-Z0-9]{4}")
                || normalizedUsername.length() < 3 || normalizedUsername.length() > 32
                || rawPassword.length() < 12 || rawPassword.length() > 128) {
            deliverError(callback, new ApiError(Operation.PAIR_APPROVE, 422,
                    "请输入正确的签到服务账号和密码"));
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("user_code", normalizedCode);
            body.put("username", normalizedUsername);
            body.put("password", rawPassword);
        } catch (JSONException impossible) {
            deliverError(callback, protocolError(Operation.PAIR_APPROVE));
            return;
        }
        submit(callback, () -> request(Operation.PAIR_APPROVE, "POST", "/pair/approve", "",
                body, value -> {
                    if (!"approved".equals(value.optString("state", ""))) {
                        throw protocolError(Operation.PAIR_APPROVE);
                    }
                    return Boolean.TRUE;
                }));
    }

    void sendRegistrationEmail(String userCode, String email,
                               Callback<RegistrationEmailSession> callback) {
        String normalizedCode = userCode == null ? "" : userCode.trim();
        String normalizedEmail = email == null ? "" : email.trim();
        if (!normalizedCode.matches("[A-Z0-9]{4}-[A-Z0-9]{4}")
                || !validRegistrationEmail(normalizedEmail)) {
            deliverError(callback, new ApiError(Operation.REGISTRATION_EMAIL, 422,
                    "请输入正确的邮箱地址"));
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("user_code", normalizedCode);
            body.put("email", normalizedEmail);
        } catch (JSONException impossible) {
            deliverError(callback, protocolError(Operation.REGISTRATION_EMAIL));
            return;
        }
        submit(callback, () -> request(Operation.REGISTRATION_EMAIL, "POST",
                "/pair/register/email/send", "", body,
                CheckinCenterClient::parseRegistrationEmailSession));
    }

    void registerPairing(String userCode, String username, String password,
                         String email, String emailChallengeId, String emailCode,
                         boolean emailRequired, Callback<Boolean> callback) {
        String normalizedCode = userCode == null ? "" : userCode.trim();
        String normalizedUsername = username == null ? "" : username.trim();
        String rawPassword = password == null ? "" : password;
        String normalizedEmail = email == null ? "" : email.trim();
        String challengeId = emailChallengeId == null ? "" : emailChallengeId.trim();
        String verificationCode = emailCode == null ? "" : emailCode.trim();
        if (!normalizedCode.matches("[A-Z0-9]{4}-[A-Z0-9]{4}")
                || !normalizedUsername.matches("[A-Za-z0-9_.-]{3,32}")
                || !validServicePassword(rawPassword)
                || (emailRequired && (!validRegistrationEmail(normalizedEmail)
                || !validRegistrationChallengeId(challengeId)
                || !validRegistrationEmailCode(verificationCode)))) {
            deliverError(callback, new ApiError(Operation.PAIR_REGISTER, 422,
                    "请检查注册信息"));
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("user_code", normalizedCode);
            body.put("username", normalizedUsername);
            body.put("password", rawPassword);
            if (emailRequired) {
                body.put("email", normalizedEmail);
                body.put("email_challenge_id", challengeId);
                body.put("email_code", verificationCode);
            }
        } catch (JSONException impossible) {
            deliverError(callback, protocolError(Operation.PAIR_REGISTER));
            return;
        }
        submit(callback, () -> request(Operation.PAIR_REGISTER, "POST", "/pair/register", "",
                body, value -> {
                    if (!"approved".equals(value.optString("state", ""))) {
                        throw protocolError(Operation.PAIR_REGISTER);
                    }
                    return Boolean.TRUE;
                }));
    }

    void sendSmsCode(String deviceToken, String phone, Callback<SmsSession> callback) {
        sendSmsCode(deviceToken, phone, "", "", callback);
    }

    void sendSmsCode(String deviceToken, String phone, String captchaTicket,
                     String captchaRandstr, Callback<SmsSession> callback) {
        final String token;
        try {
            token = requirePrefix(deviceToken, "ccdevice1_", Operation.SMS_SEND);
        } catch (ApiError error) {
            deliverError(callback, error);
            return;
        }
        String normalizedPhone = phone == null ? "" : phone.trim();
        if (!normalizedPhone.matches("[+0-9 -]{6,20}")) {
            deliverError(callback, new ApiError(Operation.SMS_SEND, 422,
                    "请输入正确的手机号"));
            return;
        }
        String ticket = captchaTicket == null ? "" : captchaTicket.trim();
        String randstr = captchaRandstr == null ? "" : captchaRandstr.trim();
        if (!captchaProofValid(ticket, randstr)) {
            deliverError(callback, new ApiError(Operation.SMS_SEND, 422,
                    "安全验证结果无效，请重新验证"));
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("phone", normalizedPhone);
            putCaptchaProof(body, ticket, randstr);
        } catch (JSONException impossible) {
            deliverError(callback, protocolError(Operation.SMS_SEND));
            return;
        }
        submit(callback, () -> request(Operation.SMS_SEND, "POST",
                "/heybox/login/sms/send", token, body,
                CheckinCenterClient::parseSmsSession));
    }

    void submitSmsCode(String deviceToken, String sessionId, String code,
                       Callback<ConnectedAccount> callback) {
        submitSmsCode(deviceToken, sessionId, code, "", "", callback);
    }

    void submitSmsCode(String deviceToken, String sessionId, String code,
                       String captchaTicket, String captchaRandstr,
                       Callback<ConnectedAccount> callback) {
        final String token;
        try {
            token = requirePrefix(deviceToken, "ccdevice1_", Operation.SMS_SUBMIT);
        } catch (ApiError error) {
            deliverError(callback, error);
            return;
        }
        String normalizedSession = sessionId == null ? "" : sessionId.trim();
        String normalizedCode = code == null ? "" : code.trim();
        if (normalizedSession.isEmpty() || normalizedSession.length() > 128
                || !normalizedCode.matches("[0-9]{4,8}")) {
            deliverError(callback, new ApiError(Operation.SMS_SUBMIT, 422,
                    "请输入正确的短信验证码"));
            return;
        }
        String ticket = captchaTicket == null ? "" : captchaTicket.trim();
        String randstr = captchaRandstr == null ? "" : captchaRandstr.trim();
        if (!captchaProofValid(ticket, randstr)) {
            deliverError(callback, new ApiError(Operation.SMS_SUBMIT, 422,
                    "安全验证结果无效，请重新验证"));
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("session_id", normalizedSession);
            body.put("code", normalizedCode);
            putCaptchaProof(body, ticket, randstr);
        } catch (JSONException impossible) {
            deliverError(callback, protocolError(Operation.SMS_SUBMIT));
            return;
        }
        submit(callback, () -> request(Operation.SMS_SUBMIT, "POST",
                "/heybox/login/sms/submit", token, body,
                value -> parseConnectedAccount(value, Operation.SMS_SUBMIT)));
    }

    void loginWithPassword(String deviceToken, String phone, String password,
                           String captchaTicket, String captchaRandstr,
                           Callback<ConnectedAccount> callback) {
        final String token;
        try {
            token = requirePrefix(deviceToken, "ccdevice1_", Operation.PASSWORD_LOGIN);
        } catch (ApiError error) {
            deliverError(callback, error);
            return;
        }
        String normalizedPhone = phone == null ? "" : phone.trim();
        String rawPassword = password == null ? "" : password;
        if (!normalizedPhone.matches("[+0-9 -]{6,20}")
                || rawPassword.length() < 6 || rawPassword.length() > 128) {
            deliverError(callback, new ApiError(Operation.PASSWORD_LOGIN, 422,
                    "请输入正确的手机号和密码"));
            return;
        }
        String ticket = captchaTicket == null ? "" : captchaTicket.trim();
        String randstr = captchaRandstr == null ? "" : captchaRandstr.trim();
        if (!captchaProofValid(ticket, randstr)) {
            deliverError(callback, new ApiError(Operation.PASSWORD_LOGIN, 422,
                    "安全验证结果无效，请重新验证"));
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("phone", normalizedPhone);
            body.put("password", rawPassword);
            putCaptchaProof(body, ticket, randstr);
        } catch (JSONException impossible) {
            deliverError(callback, protocolError(Operation.PASSWORD_LOGIN));
            return;
        }
        submit(callback, () -> request(Operation.PASSWORD_LOGIN, "POST",
                "/heybox/login/password", token, body,
                value -> parseConnectedAccount(value, Operation.PASSWORD_LOGIN)));
    }

    void getStatus(String deviceToken, Callback<Status> callback) {
        final String token;
        try {
            token = requirePrefix(deviceToken, "ccdevice1_", Operation.STATUS);
        } catch (ApiError error) {
            deliverError(callback, error);
            return;
        }
        submit(callback, () -> request(Operation.STATUS, "GET", "/status/heybox", token,
                null, CheckinCenterClient::parseStatus));
    }

    void updateTaskSettings(String deviceToken, boolean enabled, String scheduleTime,
                            int offsetMinutes, Callback<Task> callback) {
        final String token;
        try {
            token = requirePrefix(deviceToken, "ccdevice1_", Operation.TASK_SETTINGS);
        } catch (ApiError error) {
            deliverError(callback, error);
            return;
        }
        String time = scheduleTime == null ? "" : scheduleTime.trim();
        if (!time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
                || offsetMinutes < 0 || offsetMinutes > 720) {
            deliverError(callback, new ApiError(Operation.TASK_SETTINGS, 422,
                    "签到时间或随机偏移无效"));
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("enabled", enabled);
            body.put("schedule_time", time);
            body.put("offset_minutes", offsetMinutes);
        } catch (JSONException impossible) {
            deliverError(callback, protocolError(Operation.TASK_SETTINGS));
            return;
        }
        submit(callback, () -> request(Operation.TASK_SETTINGS, "PUT",
                "/tasks/heybox/settings", token, body, value -> {
                    JSONObject task = value.optJSONObject("task");
                    if (task == null) throw protocolError(Operation.TASK_SETTINGS);
                    return parseTask(task);
                }));
    }

    void runNow(String deviceToken, Callback<RunResult> callback) {
        final String token;
        try {
            token = requirePrefix(deviceToken, "ccdevice1_", Operation.RUN_NOW);
        } catch (ApiError error) {
            deliverError(callback, error);
            return;
        }
        submit(callback, () -> request(Operation.RUN_NOW, "POST", "/tasks/heybox/run",
                token, null, CheckinCenterClient::parseRunResult));
    }

    void revokeDevice(String deviceToken, Callback<Boolean> callback) {
        final String token;
        try {
            token = requirePrefix(deviceToken, "ccdevice1_", Operation.REVOKE);
        } catch (ApiError error) {
            deliverError(callback, error);
            return;
        }
        submit(callback, () -> request(Operation.REVOKE, "DELETE", "/device", token,
                null, value -> {
                    if (!"revoked".equals(value.optString("state"))) {
                        throw protocolError(Operation.REVOKE);
                    }
                    return Boolean.TRUE;
                 }));
    }

    void createBillingOrder(String deviceToken, int amountCents,
                            Callback<CheckinBilling.Order> callback) {
        if (!SponsorshipAmount.validCents(amountCents)) {
            deliverError(callback, new ApiError(Operation.BILLING_CREATE, 422,
                    "赞助金额无效"));
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("amount_cents", amountCents);
        } catch (JSONException impossible) {
            deliverError(callback, protocolError(Operation.BILLING_CREATE));
            return;
        }
        billingToken(deviceToken, Operation.BILLING_CREATE, callback,
                token -> submit(callback, () -> request(Operation.BILLING_CREATE, "POST",
                        "/billing/orders", token, body, CheckinBilling::parseOrder)));
    }

    void getBillingOrder(String deviceToken, String orderId,
                         Callback<CheckinBilling.Order> callback) {
        if (!CheckinBilling.validOrderId(orderId)) {
            deliverError(callback, protocolError(Operation.BILLING_STATUS));
            return;
        }
        billingToken(deviceToken, Operation.BILLING_STATUS, callback,
                token -> submit(callback, () -> request(Operation.BILLING_STATUS, "GET",
                        "/billing/orders/" + orderId, token, null, CheckinBilling::parseOrder)));
    }

    void loadBillingQr(String deviceToken, String orderId, Callback<byte[]> callback) {
        if (!CheckinBilling.validOrderId(orderId)) {
            deliverError(callback, protocolError(Operation.BILLING_QR));
            return;
        }
        billingToken(deviceToken, Operation.BILLING_QR, callback,
                token -> submit(callback, () -> requestBytes(Operation.BILLING_QR,
                        "/billing/orders/" + orderId + "/qr", token)));
    }

    void submitBillingClaim(String deviceToken, String orderId, String paymentReference,
                            Callback<CheckinBilling.Review> callback) {
        if (!CheckinBilling.validOrderId(orderId)
                || !CheckinBilling.validPaymentReference(paymentReference)) {
            deliverError(callback, new ApiError(Operation.BILLING_CLAIM, 422,
                    "支付订单号格式不正确"));
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("payment_reference", paymentReference.trim());
        } catch (JSONException impossible) {
            deliverError(callback, protocolError(Operation.BILLING_CLAIM));
            return;
        }
        billingToken(deviceToken, Operation.BILLING_CLAIM, callback,
                token -> submit(callback, () -> request(Operation.BILLING_CLAIM, "POST",
                        "/billing/orders/" + orderId + "/claim", token, body,
                        CheckinBilling::parseClaim)));
    }

    private <T> void billingToken(String deviceToken, Operation operation,
                                  Callback<T> callback, TokenCall<T> call) {
        final String token;
        try {
            token = requirePrefix(deviceToken, "ccdevice1_", operation);
        } catch (ApiError error) {
            deliverError(callback, error);
            return;
        }
        call.run(token);
    }

    private interface TokenCall<T> {
        void run(String token);
    }

    static URI requireTrustedPairingUri(String value) throws ApiError {
        return requireTrustedUri(value, false, Operation.PAIR_START);
    }

    static boolean isTrustedPairingUri(String value) {
        try {
            requireTrustedPairingUri(value);
            return true;
        } catch (ApiError ignored) {
            return false;
        }
    }

    private <T> T request(Operation operation, String method, String path, String token,
                          JSONObject body, Parser<T> parser) throws ApiError {
        HttpsURLConnection connection = null;
        long startedAt = SystemClock.elapsedRealtime();
        try {
            URI uri = requireTrustedUri(API_BASE + path, true, operation);
            URL url = uri.toURL();
            connection = (HttpsURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(readTimeoutMillis(operation));
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "heybox-Lite/" + BuildConfig.VERSION_NAME);
            if (!token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null) {
                byte[] bytes = body.toString().getBytes("UTF-8");
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                throw new ApiError(operation, status, "签到服务拒绝了跳转响应");
            }
            String response = readResponse(connection, status, operation);
            if (status < 200 || status >= 300) {
                throw statusError(operation, status, response);
            }
            JSONObject json = response.isEmpty() ? new JSONObject() : new JSONObject(response);
            return parser.parse(json);
        } catch (ApiError error) {
            logFailure(error, startedAt);
            throw error;
        } catch (IOException error) {
            ApiError classified = networkError(operation, error);
            logFailure(classified, startedAt);
            throw classified;
        } catch (JSONException error) {
            ApiError protocol = protocolError(operation);
            logFailure(protocol, startedAt);
            throw protocol;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private byte[] requestBytes(Operation operation, String path, String token)
            throws ApiError {
        HttpsURLConnection connection = null;
        long startedAt = SystemClock.elapsedRealtime();
        try {
            URI uri = requireTrustedUri(API_BASE + path, true, operation);
            URL url = uri.toURL();
            connection = (HttpsURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(STANDARD_READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "image/png");
            connection.setRequestProperty("User-Agent", "heybox-Lite/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                throw new ApiError(operation, status, "签到服务拒绝了跳转响应");
            }
            if (status < 200 || status >= 300) {
                String response = readResponse(connection, status, operation);
                throw statusError(operation, status, response);
            }
            String contentType = connection.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT)
                    .startsWith("image/png")) {
                throw protocolError(operation);
            }
            return readBytes(connection.getInputStream(), MAX_QR_BYTES, operation);
        } catch (ApiError error) {
            logFailure(error, startedAt);
            throw error;
        } catch (IOException error) {
            ApiError classified = networkError(operation, error);
            logFailure(classified, startedAt);
            throw classified;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readResponse(HttpsURLConnection connection, int status,
                                       Operation operation)
            throws IOException, ApiError {
        InputStream input = status >= 200 && status < 400
                ? connection.getInputStream() : connection.getErrorStream();
        if (input == null) return "";
        return new String(readBytes(input, MAX_RESPONSE_BYTES, operation), "UTF-8");
    }

    private static byte[] readBytes(InputStream input, int maxBytes, Operation operation)
            throws IOException, ApiError {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > maxBytes) {
                    throw new ApiError(operation, 0, "签到服务响应异常");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    static PairingStart parsePairingStart(JSONObject value)
            throws JSONException, ApiError {
        String deviceCode = required(value, "device_code", Operation.PAIR_START);
        String userCode = required(value, "user_code", Operation.PAIR_START);
        String verificationUri = required(value, "verification_uri", Operation.PAIR_START);
        requireTrustedPairingUri(verificationUri);
        int expiresIn = positive(value.optInt("expires_in"), Operation.PAIR_START);
        int interval = positive(value.optInt("interval"), Operation.PAIR_START);
        return new PairingStart(deviceCode, userCode, expiresIn, interval,
                value.optBoolean("registration_open", false),
                value.optBoolean("registration_email_required", false));
    }

    static RegistrationEmailSession parseRegistrationEmailSession(JSONObject value)
            throws ApiError {
        String challengeId = required(value, "challenge_id", Operation.REGISTRATION_EMAIL);
        if (!validRegistrationChallengeId(challengeId)) {
            throw protocolError(Operation.REGISTRATION_EMAIL);
        }
        int retryAfter = positive(value.optInt("retry_after"), Operation.REGISTRATION_EMAIL);
        int expiresIn = positive(value.optInt("expires_in"), Operation.REGISTRATION_EMAIL);
        return new RegistrationEmailSession(challengeId, retryAfter, expiresIn);
    }

    private static PairingPoll parsePairingPoll(JSONObject value) throws ApiError {
        String state = value.optString("state", "");
        if ("pending".equals(state)) return new PairingPoll(state, "");
        if (!"authorized".equals(state)) throw protocolError(Operation.PAIR_POLL);
        String token = value.optString("device_token", "");
        requirePrefix(token, "ccdevice1_", Operation.PAIR_POLL);
        return new PairingPoll(state, token);
    }

    private static ConnectedAccount parseConnectedAccount(JSONObject value, Operation operation)
            throws ApiError {
        if (!"connected".equals(value.optString("state", ""))) {
            throw protocolError(operation);
        }
        return new ConnectedAccount(value.optString("display_name", ""),
                value.optString("external_id_masked", ""),
                value.optBoolean("task_enabled", false));
    }

    private static SmsSession parseSmsSession(JSONObject value) throws ApiError {
        String sessionId = required(value, "session_id", Operation.SMS_SEND);
        if (sessionId.length() > 128) throw protocolError(Operation.SMS_SEND);
        int retryAfter = positive(value.optInt("retry_after"), Operation.SMS_SEND);
        int expiresIn = positive(value.optInt("expires_in"), Operation.SMS_SEND);
        return new SmsSession(sessionId, retryAfter, expiresIn);
    }

    private static Status parseStatus(JSONObject value) throws ApiError {
        JSONObject accountJson = value.optJSONObject("account");
        JSONObject taskJson = value.optJSONObject("task");
        if (accountJson == null || taskJson == null) throw protocolError(Operation.STATUS);
        Account account = new Account(accountJson.optString("state", ""),
                nullableString(accountJson, "display_name"),
                nullableString(accountJson, "external_id_masked"));
        Task task = parseTask(taskJson);
        JSONObject runJson = value.optJSONObject("last_run");
        LastRun run = runJson == null ? null : new LastRun(runJson.optLong("id", 0L),
                runJson.optString("status", ""), runJson.optString("summary", ""),
                runJson.optString("started_at", ""), runJson.optString("finished_at", ""),
                parseCheckinResult(runJson.optJSONObject("check_in")));
        return new Status(account, task, run, CheckinBilling.parseMembership(value));
    }

    private static Task parseTask(JSONObject taskJson) {
        return new Task(taskJson.optBoolean("enabled", false),
                taskJson.optBoolean("sign", false), taskJson.optString("schedule_time", ""),
                taskJson.optInt("offset_minutes", 0), taskJson.optString("window_start", ""),
                taskJson.optString("window_end", ""),
                taskJson.optBoolean("platform_blocked", false),
                taskJson.optBoolean("sign_blocked", false));
    }

    private static RunResult parseRunResult(JSONObject value) throws ApiError {
        String status = value.optString("status", "");
        if (status.isEmpty()) throw protocolError(Operation.RUN_NOW);
        return new RunResult(status, value.optString("summary", ""),
                value.optLong("run_id", 0L),
                parseCheckinResult(value.optJSONObject("check_in")));
    }

    private static CheckinResult parseCheckinResult(JSONObject value) {
        if (value == null) return new CheckinResult(false, false, -1, -1, -1);
        return new CheckinResult(value.optBoolean("checked_in", false),
                value.optBoolean("newly_signed", false), optionalInt(value, "coin_delta"),
                optionalInt(value, "experience_delta"), optionalInt(value, "streak_days"));
    }

    private static int optionalInt(JSONObject value, String key) {
        if (value.isNull(key) || !value.has(key)) return -1;
        int result = value.optInt(key, -1);
        return result >= 0 && result <= 1_000_000 ? result : -1;
    }

    private static URI requireTrustedUri(String value, boolean api, Operation operation)
            throws ApiError {
        try {
            URI uri = new URI(value);
            String path = uri.getRawPath();
            boolean pathAllowed = api ? path != null && path.startsWith("/checkin/api/lite/")
                    : path != null && (path.equals("/checkin")
                    || path.startsWith(PAIRING_PATH_PREFIX));
            if (!"https".equals(uri.getScheme()) || !TRUSTED_HOST.equals(uri.getHost())
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getUserInfo() != null || uri.getFragment() != null || !pathAllowed) {
                throw new ApiError(operation, 0, "签到服务地址不受信任");
            }
            return uri;
        } catch (URISyntaxException error) {
            throw new ApiError(operation, 0, "签到服务地址不受信任");
        }
    }

    private static String required(JSONObject value, String key, Operation operation)
            throws ApiError {
        String result = value.optString(key, "").trim();
        if (result.isEmpty()) throw protocolError(operation);
        return result;
    }

    private static int positive(int value, Operation operation) throws ApiError {
        if (value <= 0) throw protocolError(operation);
        return value;
    }

    private static String requirePrefix(String value, String prefix, Operation operation)
            throws ApiError {
        String result = value == null ? "" : value.trim();
        if (!result.startsWith(prefix) || result.length() < prefix.length() + 32
                || result.length() > prefix.length() + 96) {
            throw protocolError(operation);
        }
        for (int i = prefix.length(); i < result.length(); i++) {
            char character = result.charAt(i);
            if (!(character >= 'A' && character <= 'Z')
                    && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_' && character != '-') {
                throw protocolError(operation);
            }
        }
        return result;
    }

    private static String nullableString(JSONObject value, String key) {
        return value.isNull(key) ? "" : value.optString(key, "");
    }

    private static String clean(String value, String fallback) {
        String result = value == null ? "" : value.trim();
        return result.isEmpty() ? fallback : result;
    }

    private static ApiError protocolError(Operation operation) {
        return new ApiError(operation, 0, "签到服务响应异常", ErrorKind.PROTOCOL);
    }

    static int readTimeoutMillis(Operation operation) {
        return operation == Operation.RUN_NOW || operation == Operation.SMS_SEND
                || operation == Operation.SMS_SUBMIT
                || operation == Operation.PASSWORD_LOGIN
                ? SIGNING_READ_TIMEOUT_MS : STANDARD_READ_TIMEOUT_MS;
    }

    private static ApiError networkError(Operation operation, IOException error) {
        if (error instanceof SocketTimeoutException) {
            String message;
            if (operation == Operation.RUN_NOW) {
                message = "签到执行超时，请稍后刷新状态";
            } else if (operation == Operation.PASSWORD_LOGIN) {
                message = "小黑盒登录超时，请稍后重试";
            } else {
                message = "连接签到服务超时，请检查网络";
            }
            return new ApiError(operation, 0, message, ErrorKind.TIMEOUT);
        }
        if (error instanceof SSLException) {
            return new ApiError(operation, 0, "签到服务证书校验失败，请更新客户端",
                    ErrorKind.TLS);
        }
        if (error instanceof UnknownHostException || error instanceof ConnectException) {
            return new ApiError(operation, 0, "无法连接签到服务，请检查网络",
                    ErrorKind.NETWORK);
        }
        return new ApiError(operation, 0, "签到服务连接异常，请稍后重试",
                ErrorKind.NETWORK);
    }

    private void logFailure(ApiError error, long startedAt) {
        String detail = error.diagnosticCode.isEmpty()
                ? "" : " reason=" + error.diagnosticCode;
        eventLogger.log("checkin request failed operation=" + error.operation.name()
                + " status=" + error.statusCode
                + " category=" + error.kind.name().toLowerCase(Locale.ROOT)
                + detail
                + " elapsedMs=" + Math.max(0L, SystemClock.elapsedRealtime() - startedAt));
    }

    static ApiError statusError(Operation operation, int status, String response) {
        String diagnosticCode = serverErrorCode(response);
        String captchaUri = "";
        int retryAfterSeconds = serverRetryAfterSeconds(response);
        String message;
        switch (status) {
            case 401:
                message = operation == Operation.PAIR_APPROVE
                        ? "签到服务账号或密码错误"
                        : "签到服务连接已失效，请重新连接";
                break;
            case 402:
                message = "签到服务状态异常，请稍后重试";
                break;
            case 403:
                message = operation == Operation.PAIR_REGISTER
                        || operation == Operation.REGISTRATION_EMAIL
                        ? "签到服务当前未开放注册"
                        : "当前操作没有权限";
                break;
            case 404:
                if (operation == Operation.PAIR_POLL || operation == Operation.PAIR_APPROVE
                        || operation == Operation.PAIR_REGISTER
                        || operation == Operation.REGISTRATION_EMAIL) {
                    message = "配对请求不存在，请重新连接";
                } else if (operation == Operation.SMS_SEND
                        || operation == Operation.SMS_SUBMIT
                        || operation == Operation.PASSWORD_LOGIN) {
                    message = "服务器暂未支持手机号登录，请稍后重试";
                } else if (operation == Operation.BILLING_STATUS
                        || operation == Operation.BILLING_QR
                        || operation == Operation.BILLING_CLAIM) {
                    message = "赞助记录不存在";
                } else {
                    message = "签到任务尚未配置";
                }
                break;
            case 409:
                if ((operation == Operation.SMS_SEND || operation == Operation.SMS_SUBMIT
                        || operation == Operation.PASSWORD_LOGIN)
                        && "captcha_required".equals(diagnosticCode)) {
                    captchaUri = serverCaptchaUri(response);
                    message = captchaUri.isEmpty()
                            ? "小黑盒要求安全验证，但验证页面不可用"
                            : "请完成小黑盒安全验证";
                } else if (operation == Operation.PAIR_REGISTER) {
                    message = "registration_account_used".equals(diagnosticCode)
                            ? "签到服务账号或邮箱已被注册"
                            : "该签到服务账号已存在，或配对状态已变化";
                } else if (operation == Operation.BILLING_CREATE) {
                    message = "赞助码暂不可用，请稍后重试";
                } else {
                    message = "当前操作与服务器状态冲突，请稍后重试";
                }
                break;
            case 410:
                if (operation == Operation.SMS_SUBMIT) {
                    message = "短信验证码已过期，请重新发送";
                } else if (operation == Operation.BILLING_QR
                        || operation == Operation.BILLING_STATUS) {
                    message = "赞助码已过期，请重新生成";
                } else {
                    message = "配对已过期，请重新连接";
                }
                break;
            case 413:
                message = "签到资料异常，请更新客户端后重试";
                break;
            case 422:
                if (operation == Operation.PAIR_APPROVE) {
                    message = "签到服务账号信息无效";
                } else if (operation == Operation.PAIR_REGISTER) {
                    message = "registration_email_code_invalid".equals(diagnosticCode)
                            ? "邮箱验证码错误或已过期"
                            : "注册信息无效，请检查账号、密码和验证码";
                } else if (operation == Operation.REGISTRATION_EMAIL) {
                    message = "邮箱地址或配对状态无效";
                } else if (operation == Operation.SMS_SEND) {
                    message = "手机号无效或发送过于频繁，请稍后重试";
                } else if (operation == Operation.SMS_SUBMIT) {
                    message = "验证码无效、已过期或登录失败";
                } else if (operation == Operation.PASSWORD_LOGIN) {
                    message = "手机号或密码错误，登录失败";
                } else if (operation == Operation.TASK_SETTINGS) {
                    message = "签到时间或随机偏移无效";
                } else if (operation == Operation.BILLING_CLAIM) {
                    message = "支付订单号格式不正确";
                } else if (operation == Operation.BILLING_CREATE) {
                    message = "赞助金额无效";
                } else {
                    message = "签到服务请求无效";
                }
                break;
            case 429:
                message = operation == Operation.REGISTRATION_EMAIL
                        ? "邮箱验证码发送过于频繁，请稍后重试"
                        : "操作过于频繁，请稍后重试";
                break;
            case 502:
            case 503:
                message = operation == Operation.REGISTRATION_EMAIL
                        ? "验证邮件暂时无法发送，请稍后重试"
                        : "签到服务暂时不可用，请稍后重试";
                break;
            default:
                message = "签到服务请求失败";
                break;
        }
        return new ApiError(operation, status, message, ErrorKind.HTTP,
                diagnosticCode, captchaUri, retryAfterSeconds);
    }

    static String serverCaptchaUri(String response) {
        try {
            String uri = new JSONObject(response).optString("verification_uri", "").trim();
            return CheckinCaptchaContract.isTrustedPageUri(uri) ? uri : "";
        } catch (JSONException ignored) {
            return "";
        }
    }

    static boolean captchaProofValid(String ticket, String randstr) {
        String cleanTicket = ticket == null ? "" : ticket.trim();
        String cleanRandstr = randstr == null ? "" : randstr.trim();
        if (cleanTicket.isEmpty() && cleanRandstr.isEmpty()) return true;
        return !cleanTicket.isEmpty() && !cleanRandstr.isEmpty()
                && cleanTicket.length() <= 4096 && cleanRandstr.length() <= 512
                && !hasControl(cleanTicket) && !hasControl(cleanRandstr);
    }

    private static void putCaptchaProof(JSONObject body, String ticket, String randstr)
            throws JSONException {
        if (ticket.isEmpty()) return;
        body.put("captcha_ticket", ticket);
        body.put("captcha_randstr", randstr);
    }

    private static boolean hasControl(String value) {
        for (int index = 0; index < value.length(); index++) {
            char item = value.charAt(index);
            if (item < 0x20 || item == 0x7f) return true;
        }
        return false;
    }

    static String serverErrorCode(String response) {
        try {
            String error = new JSONObject(response).optString("error", "");
            if ("captcha_required".equals(error)) return "captcha_required";
            if ("registration email code is invalid".equals(error)) {
                return "registration_email_code_invalid";
            }
            if ("registration account is already used".equals(error)) {
                return "registration_account_used";
            }
            if ("registration email limit reached".equals(error)) {
                return "registration_email_rate_limited";
            }
        } catch (JSONException ignored) {
        }
        return "";
    }

    static int serverRetryAfterSeconds(String response) {
        try {
            int value = new JSONObject(response).optInt("retry_after", 0);
            return value > 0 && value <= 3_600 ? value : 0;
        } catch (JSONException ignored) {
            return 0;
        }
    }

    static boolean validRegistrationEmail(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 254
                || normalized.indexOf('@') != normalized.lastIndexOf('@')) return false;
        int separator = normalized.lastIndexOf('@');
        if (separator <= 0 || separator == normalized.length() - 1) return false;
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isWhitespace(normalized.charAt(index))) return false;
        }
        String local = normalized.substring(0, separator);
        if (local.length() > 64 || local.startsWith(".") || local.endsWith(".")
                || local.contains("..")
                || !local.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+")) return false;
        final String asciiDomain;
        try {
            asciiDomain = IDN.toASCII(normalized.substring(separator + 1));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        if (asciiDomain.length() > 253) return false;
        String[] labels = asciiDomain.split("\\.", -1);
        if (labels.length < 2) return false;
        for (String label : labels) {
            if (!label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")) {
                return false;
            }
        }
        return true;
    }

    static boolean validRegistrationChallengeId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{20,80}");
    }

    static boolean validRegistrationEmailCode(String value) {
        return value != null && value.matches("[0-9]{6}");
    }

    static boolean validServicePassword(String value) {
        if (value == null || value.length() < 12 || value.length() > 128
                || !value.trim().equals(value)) return false;
        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        boolean symbol = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= 'a' && character <= 'z') lower = true;
            else if (character >= 'A' && character <= 'Z') upper = true;
            else if (character >= '0' && character <= '9') digit = true;
            else if (!Character.isWhitespace(character)) symbol = true;
        }
        return (lower ? 1 : 0) + (upper ? 1 : 0) + (digit ? 1 : 0)
                + (symbol ? 1 : 0) >= 3;
    }

    private <T> void submit(Callback<T> callback, Call<T> call) {
        if (closed) {
            deliverError(callback, new ApiError(Operation.STATUS, 0, "签到服务已关闭"));
            return;
        }
        executor.execute(() -> {
            try {
                T value = call.execute();
                mainHandler.post(() -> {
                    if (!closed && callback != null) callback.onSuccess(value);
                });
            } catch (ApiError error) {
                deliverError(callback, error);
            }
        });
    }

    private <T> void deliverError(Callback<T> callback, ApiError error) {
        mainHandler.post(() -> {
            if (!closed && callback != null) callback.onError(error);
        });
    }

    public void close() {
        closed = true;
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }
}
