package com.ronan.heyboxlite;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

final class CheckinCenterClient {
    static final String TRUSTED_ORIGIN = "https://heyboxlite.xyz";
    static final String API_BASE = TRUSTED_ORIGIN + "/checkin/api/lite";
    static final String TRUSTED_HOST = "heyboxlite.xyz";
    static final String PAIRING_PATH_PREFIX = "/checkin/";
    static final int CONNECT_TIMEOUT_MS = 10_000;
    static final int STANDARD_READ_TIMEOUT_MS = 25_000;
    static final int SIGNING_READ_TIMEOUT_MS = 125_000;
    static final int MAX_RESPONSE_BYTES = 64 * 1024;
    static final int MAX_QR_BYTES = 512 * 1024;

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
        LEADERBOARD,
        TASK_SETTINGS,
        HISTORY,
        RECOVERY_EMAIL,
        RECOVERY_COMPLETE,
        RUN_NOW,
        REVOKE,
        BILLING_CATALOG,
        BILLING_HISTORY,
        BILLING_REDEEM,
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
        final CheckinSharing sharing;

        Task(boolean enabled, boolean sign, String scheduleTime, int offsetMinutes,
             String windowStart, String windowEnd, boolean platformBlocked,
             boolean signBlocked, CheckinSharing sharing) {
            this.enabled = enabled;
            this.sign = sign;
            this.scheduleTime = scheduleTime;
            this.offsetMinutes = offsetMinutes;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.platformBlocked = platformBlocked;
            this.signBlocked = signBlocked;
            this.sharing = sharing;
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

    interface Parser<T> {
        T parse(JSONObject value) throws JSONException, ApiError;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "checkin-center-api");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final EventLogger eventLogger;
    private final CheckinCenterTransport transport;
    private volatile boolean closed;

    CheckinCenterClient() {
        this(event -> {});
    }

    CheckinCenterClient(EventLogger eventLogger) {
        this.eventLogger = eventLogger;
        this.transport = new CheckinCenterTransport(eventLogger);
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

    void getLeaderboard(Callback<CheckinLeaderboard.Data> callback) {
        submit(callback, () -> request(Operation.LEADERBOARD, "GET", "/leaderboard", "",
                null, CheckinLeaderboard::parse));
    }

    void updateTaskSettings(String deviceToken, boolean enabled, String scheduleTime,
                            int offsetMinutes, String shareAction, Boolean shareEnabled,
                            Callback<Task> callback) {
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
            CheckinSharing.putChange(body, shareAction, shareEnabled);
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
        new CheckinMembershipApi(this).createBillingOrder(
                deviceToken, amountCents, legacyBillingCallback(callback));
    }

    void getBillingOrder(String deviceToken, String orderId,
                         Callback<CheckinBilling.Order> callback) {
        new CheckinMembershipApi(this).getBillingOrder(
                deviceToken, orderId, legacyBillingCallback(callback));
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
        new CheckinMembershipApi(this).submitBillingClaim(
                deviceToken, orderId, paymentReference, legacyBillingCallback(callback));
    }

    private <T> Callback<T> legacyBillingCallback(Callback<T> callback) {
        return new Callback<T>() {
            @Override
            public void onSuccess(T value) {
                if (callback != null) callback.onSuccess(value);
            }

            @Override
            public void onError(ApiError error) {
                deliverError(callback, error);
            }
        };
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
        return CheckinCenterTransport.requireTrustedPairingUri(value);
    }

    static boolean isTrustedPairingUri(String value) {
        return CheckinCenterTransport.isTrustedPairingUri(value);
    }

    private <T> T request(Operation operation, String method, String path, String token,
                           JSONObject body, Parser<T> parser) throws ApiError {
        return transport.request(operation, method, path, token, body, parser);
    }

    private byte[] requestBytes(Operation operation, String path, String token)
            throws ApiError {
        return transport.requestBytes(operation, path, token);
    }

    static PairingStart parsePairingStart(JSONObject value)
            throws JSONException, ApiError {
        return CheckinResponseParser.parsePairingStart(value);
    }

    static RegistrationEmailSession parseRegistrationEmailSession(JSONObject value)
            throws ApiError {
        return CheckinResponseParser.parseRegistrationEmailSession(value);
    }

    private static PairingPoll parsePairingPoll(JSONObject value) throws ApiError {
        return CheckinResponseParser.parsePairingPoll(value);
    }

    private static ConnectedAccount parseConnectedAccount(JSONObject value, Operation operation)
            throws ApiError {
        return CheckinResponseParser.parseConnectedAccount(value, operation);
    }

    private static SmsSession parseSmsSession(JSONObject value) throws ApiError {
        return CheckinResponseParser.parseSmsSession(value);
    }

    private static Status parseStatus(JSONObject value) throws ApiError {
        return CheckinResponseParser.parseStatus(value);
    }

    private static Task parseTask(JSONObject taskJson) {
        return CheckinResponseParser.parseTask(taskJson);
    }

    private static RunResult parseRunResult(JSONObject value) throws ApiError {
        return CheckinResponseParser.parseRunResult(value);
    }

    private static CheckinResult parseCheckinResult(JSONObject value) {
        return CheckinResponseParser.parseCheckinResult(value);
    }

    static URI requireTrustedUri(String value, boolean api, Operation operation)
            throws ApiError {
        return CheckinCenterTransport.requireTrustedUri(value, api, operation);
    }

    static String requirePrefix(String value, String prefix, Operation operation)
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

    private static String clean(String value, String fallback) {
        String result = value == null ? "" : value.trim();
        return result.isEmpty() ? fallback : result;
    }

    static ApiError protocolError(Operation operation) {
        return new ApiError(operation, 0, "签到服务响应异常", ErrorKind.PROTOCOL);
    }

    static int readTimeoutMillis(Operation operation) {
        return CheckinCenterTransport.readTimeoutMillis(operation);
    }

    static ApiError statusError(Operation operation, int status, String response) {
        return CheckinCenterTransport.statusError(operation, status, response);
    }

    static String serverCaptchaUri(String response) {
        return CheckinCenterTransport.serverCaptchaUri(response);
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
        return CheckinCenterTransport.serverErrorCode(response);
    }

    static int serverRetryAfterSeconds(String response) {
        return CheckinCenterTransport.serverRetryAfterSeconds(response);
    }

    static boolean validRegistrationEmail(String value) {
        return CheckinServiceValidation.email(value);
    }

    static boolean validRegistrationChallengeId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{20,80}");
    }

    static boolean validRegistrationEmailCode(String value) {
        return value != null && value.matches("[0-9]{6}");
    }

    static boolean validServicePassword(String value) {
        return CheckinServiceValidation.password(value);
    }

    <T> void accountRequest(Operation operation, String method, String path, String token,
                            JSONObject body, Parser<T> parser, Callback<T> callback) {
        submit(callback, () -> request(operation, method, path, token, body, parser));
    }

    private <T> void submit(Callback<T> callback, Call<T> call) {
        if (closed) {
            deliverError(callback, new ApiError(Operation.STATUS, 0, "签到服务已关闭"));
            return;
        }
        Runnable task = () -> {
            try {
                T value = call.execute();
                mainHandler.post(() -> {
                    if (!closed && callback != null) callback.onSuccess(value);
                });
            } catch (ApiError error) {
                deliverError(callback, error);
            } catch (RuntimeException error) {
                // Parsers and platform network implementations can still throw
                // unchecked failures. Surface them through the normal callback
                // instead of leaving a page permanently in its loading state.
                eventLogger.log("checkin response handling failed");
                deliverError(callback, new ApiError(Operation.STATUS, 0,
                        "签到服务响应异常", ErrorKind.PROTOCOL));
            }
        };
        try {
            executor.execute(task);
        } catch (RejectedExecutionException error) {
            if (!closed) deliverError(callback,
                    new ApiError(Operation.STATUS, 0, "签到服务已关闭"));
        }
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
