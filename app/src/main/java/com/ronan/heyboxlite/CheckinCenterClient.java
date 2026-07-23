package com.ronan.heyboxlite;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

final class CheckinCenterClient {
    static final String TRUSTED_ORIGIN = "https://8.138.134.236";
    static final String API_BASE = TRUSTED_ORIGIN + "/checkin/api/lite";
    private static final String TRUSTED_HOST = "8.138.134.236";
    private static final String WEB_PATH_PREFIX = "/checkin/";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 25_000;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    enum Operation {
        PAIR_START,
        PAIR_POLL,
        CREDENTIAL_SYNC,
        STATUS,
        RUN_NOW,
        REVOKE
    }

    interface Callback<T> {
        void onSuccess(T value);
        void onError(ApiError error);
    }

    static final class ApiError extends Exception {
        final Operation operation;
        final int statusCode;

        ApiError(Operation operation, int statusCode, String message) {
            super(message);
            this.operation = operation;
            this.statusCode = statusCode;
        }

        boolean authorizationInvalid() {
            return statusCode == 401;
        }

        boolean retryable() {
            return statusCode == 429 || statusCode == 502 || statusCode == 503;
        }
    }

    static final class PairingStart {
        final String deviceCode;
        final String userCode;
        final String verificationUri;
        final int expiresInSeconds;
        final int intervalSeconds;

        PairingStart(String deviceCode, String userCode, String verificationUri,
                     int expiresInSeconds, int intervalSeconds) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.expiresInSeconds = expiresInSeconds;
            this.intervalSeconds = intervalSeconds;
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

    static final class Status {
        final Account account;
        final Task task;
        final LastRun lastRun;

        Status(Account account, Task task, LastRun lastRun) {
            this.account = account;
            this.task = task;
            this.lastRun = lastRun;
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

        LastRun(long id, String status, String summary, String startedAt, String finishedAt) {
            this.id = id;
            this.status = status;
            this.summary = summary;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
        }
    }

    static final class RunResult {
        final String status;
        final String summary;
        final long runId;

        RunResult(String status, String summary, long runId) {
            this.status = status;
            this.summary = summary;
            this.runId = runId;
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
    private volatile boolean closed;

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

    void syncCredentials(String deviceToken, JSONObject payload,
                         Callback<ConnectedAccount> callback) {
        final String token;
        try {
            token = requirePrefix(deviceToken, "ccdevice1_", Operation.CREDENTIAL_SYNC);
        } catch (ApiError error) {
            deliverError(callback, error);
            return;
        }
        submit(callback, () -> request(Operation.CREDENTIAL_SYNC, "POST",
                "/credentials/heybox", token, payload,
                CheckinCenterClient::parseConnectedAccount));
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

    static URI requireTrustedWebUri(String value) throws ApiError {
        return requireTrustedUri(value, false, Operation.PAIR_START);
    }

    static boolean isTrustedWebUri(String value) {
        try {
            requireTrustedWebUri(value);
            return true;
        } catch (ApiError ignored) {
            return false;
        }
    }

    private <T> T request(Operation operation, String method, String path, String token,
                          JSONObject body, Parser<T> parser) throws ApiError {
        HttpsURLConnection connection = null;
        try {
            URI uri = requireTrustedUri(API_BASE + path, true, operation);
            URL url = uri.toURL();
            connection = (HttpsURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
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
                throw statusError(operation, status);
            }
            JSONObject json = response.isEmpty() ? new JSONObject() : new JSONObject(response);
            return parser.parse(json);
        } catch (ApiError error) {
            throw error;
        } catch (IOException error) {
            throw new ApiError(operation, 0, "无法安全连接签到服务");
        } catch (JSONException error) {
            throw protocolError(operation);
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
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new ApiError(operation, 0, "签到服务响应异常");
                }
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        }
    }

    private static PairingStart parsePairingStart(JSONObject value)
            throws JSONException, ApiError {
        String deviceCode = required(value, "device_code", Operation.PAIR_START);
        String userCode = required(value, "user_code", Operation.PAIR_START);
        String verificationUri = required(value, "verification_uri", Operation.PAIR_START);
        requireTrustedWebUri(verificationUri);
        int expiresIn = positive(value.optInt("expires_in"), Operation.PAIR_START);
        int interval = positive(value.optInt("interval"), Operation.PAIR_START);
        return new PairingStart(deviceCode, userCode, verificationUri, expiresIn, interval);
    }

    private static PairingPoll parsePairingPoll(JSONObject value) throws ApiError {
        String state = value.optString("state", "");
        if ("pending".equals(state)) return new PairingPoll(state, "");
        if (!"authorized".equals(state)) throw protocolError(Operation.PAIR_POLL);
        String token = value.optString("device_token", "");
        requirePrefix(token, "ccdevice1_", Operation.PAIR_POLL);
        return new PairingPoll(state, token);
    }

    private static ConnectedAccount parseConnectedAccount(JSONObject value) throws ApiError {
        if (!"connected".equals(value.optString("state", ""))) {
            throw protocolError(Operation.CREDENTIAL_SYNC);
        }
        return new ConnectedAccount(value.optString("display_name", ""),
                value.optString("external_id_masked", ""),
                value.optBoolean("task_enabled", false));
    }

    private static Status parseStatus(JSONObject value) throws ApiError {
        JSONObject accountJson = value.optJSONObject("account");
        JSONObject taskJson = value.optJSONObject("task");
        if (accountJson == null || taskJson == null) throw protocolError(Operation.STATUS);
        Account account = new Account(accountJson.optString("state", ""),
                nullableString(accountJson, "display_name"),
                nullableString(accountJson, "external_id_masked"));
        Task task = new Task(taskJson.optBoolean("enabled", false),
                taskJson.optBoolean("sign", false), taskJson.optString("schedule_time", ""),
                taskJson.optInt("offset_minutes", 0), taskJson.optString("window_start", ""),
                taskJson.optString("window_end", ""),
                taskJson.optBoolean("platform_blocked", false),
                taskJson.optBoolean("sign_blocked", false));
        JSONObject runJson = value.optJSONObject("last_run");
        LastRun run = runJson == null ? null : new LastRun(runJson.optLong("id", 0L),
                runJson.optString("status", ""), runJson.optString("summary", ""),
                runJson.optString("started_at", ""), runJson.optString("finished_at", ""));
        return new Status(account, task, run);
    }

    private static RunResult parseRunResult(JSONObject value) throws ApiError {
        String status = value.optString("status", "");
        if (status.isEmpty()) throw protocolError(Operation.RUN_NOW);
        return new RunResult(status, value.optString("summary", ""),
                value.optLong("run_id", 0L));
    }

    private static URI requireTrustedUri(String value, boolean api, Operation operation)
            throws ApiError {
        try {
            URI uri = new URI(value);
            String path = uri.getRawPath();
            boolean pathAllowed = api ? path != null && path.startsWith("/checkin/api/lite/")
                    : path != null && (path.equals("/checkin")
                    || path.startsWith(WEB_PATH_PREFIX));
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
        return new ApiError(operation, 0, "签到服务响应异常");
    }

    private static ApiError statusError(Operation operation, int status) {
        switch (status) {
            case 401:
                return new ApiError(operation, status, "签到服务连接已失效，请重新连接");
            case 404:
                return new ApiError(operation, status, operation == Operation.PAIR_POLL
                        ? "配对请求不存在，请重新连接" : "签到任务尚未配置");
            case 409:
                return new ApiError(operation, status, operation == Operation.CREDENTIAL_SYNC
                        ? "签到服务已绑定其他小黑盒账号，请先在网页解除旧绑定"
                        : "当前操作与服务器状态冲突，请稍后重试");
            case 410:
                return new ApiError(operation, status, "配对已过期，请重新连接");
            case 413:
                return new ApiError(operation, status, "签到资料异常，请更新客户端后重试");
            case 422:
                return new ApiError(operation, status, operation == Operation.CREDENTIAL_SYNC
                        ? "Cookie、账号或设备资料不一致，请重新登录小黑盒"
                        : "签到服务请求无效");
            case 429:
                return new ApiError(operation, status, "操作过于频繁，请稍后重试");
            case 502:
            case 503:
                return new ApiError(operation, status, "签到服务暂时不可用，请稍后重试");
            default:
                return new ApiError(operation, status, "签到服务请求失败");
        }
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
