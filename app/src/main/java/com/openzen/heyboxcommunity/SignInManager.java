package com.openzen.heyboxcommunity;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

final class SignInManager {
    interface Callback {
        void onResult(Result result);
    }

    interface Logger {
        void log(String message);
    }

    private interface RequestStarter {
        void start(ApiClient.Callback callback);
    }

    static final class Result {
        final boolean loggedIn;
        final boolean success;
        final boolean alreadySigned;
        final boolean inFlight;
        final String message;
        final String summary;

        private Result(boolean loggedIn, boolean success, boolean alreadySigned,
                       boolean inFlight, String message, String summary) {
            this.loggedIn = loggedIn;
            this.success = success;
            this.alreadySigned = alreadySigned;
            this.inFlight = inFlight;
            this.message = message == null ? "" : message;
            this.summary = summary == null ? "" : summary;
        }

        static Result loggedOut() {
            return new Result(false, false, false, false,
                    "\u767b\u5f55\u540e\u53ef\u4ee5\u7b7e\u5230",
                    "\u626b\u7801\u767b\u5f55\u540e\u53ef\u4f7f\u7528\u6bcf\u65e5\u7b7e\u5230");
        }

        static Result pending() {
            return new Result(true, false, false, false,
                    "\u4eca\u65e5\u8fd8\u672a\u7b7e\u5230",
                    "\u6253\u5f00\u8f6f\u4ef6\u540e\u4f1a\u81ea\u52a8\u5c1d\u8bd5\u4e00\u6b21\uff0c\u4e5f\u53ef\u4ee5\u624b\u52a8\u7b7e\u5230");
        }

        static Result running() {
            return new Result(true, false, false, true,
                    "\u6b63\u5728\u7b7e\u5230",
                    "\u6b63\u5728\u5411\u5c0f\u9ed1\u76d2\u63a5\u53e3\u63d0\u4ea4\u7b7e\u5230\u8bf7\u6c42");
        }

        static Result cachedSuccess(String summary) {
            return new Result(true, true, true, false,
                    "\u4eca\u65e5\u5df2\u7b7e\u5230",
                    first(summary, "\u4eca\u65e5\u5df2\u5b8c\u6210\u7b7e\u5230"));
        }

        static Result cachedFailure(String summary) {
            return new Result(true, false, false, false,
                    "\u4eca\u65e5\u7b7e\u5230\u5931\u8d25",
                    first(summary, "\u7a0d\u540e\u53ef\u4ee5\u624b\u52a8\u518d\u8bd5"));
        }

        static Result success(boolean alreadySigned, String message, String summary) {
            return new Result(true, true, alreadySigned, false,
                    first(message, alreadySigned ? "\u4eca\u65e5\u5df2\u7b7e\u5230" : "\u7b7e\u5230\u6210\u529f"),
                    first(summary, alreadySigned ? "\u4eca\u65e5\u5df2\u5b8c\u6210\u7b7e\u5230" : "\u7b7e\u5230\u5b8c\u6210"));
        }

        static Result failure(String message) {
            return new Result(true, false, false, false,
                    first(message, "\u7b7e\u5230\u5931\u8d25"),
                    first(message, "\u7a0d\u540e\u53ef\u4ee5\u624b\u52a8\u518d\u8bd5"));
        }
    }

    private static final class Attempt {
        final String label;
        final RequestStarter starter;

        Attempt(String label, RequestStarter starter) {
            this.label = label;
            this.starter = starter;
        }
    }

    private final SessionStore session;
    private final ApiClient api;
    private final WriteTokenProvider tokenProvider;
    private final Logger logger;
    private boolean inFlight;

    SignInManager(SessionStore session, ApiClient api, WriteTokenProvider tokenProvider,
                  Logger logger) {
        this.session = session;
        this.api = api;
        this.tokenProvider = tokenProvider;
        this.logger = logger;
    }

    Result currentState() {
        if (!session.isLoggedIn()) return Result.loggedOut();
        if (inFlight) return Result.running();
        String today = today();
        String summary = session.signSummary();
        if (today.equals(session.lastSignSuccessDate())) return Result.cachedSuccess(summary);
        if (today.equals(session.lastSignAttemptDate()) && !summary.isEmpty()) {
            return Result.cachedFailure(summary);
        }
        return Result.pending();
    }

    void autoSignInIfNeeded(Callback callback) {
        if (!session.isLoggedIn()) {
            dispatch(callback, Result.loggedOut());
            return;
        }
        String today = today();
        if (today.equals(session.lastSignAttemptDate())
                || today.equals(session.lastSignSuccessDate())) {
            dispatch(callback, currentState());
            return;
        }
        request(true, callback);
    }

    void signIn(Callback callback) {
        if (!session.isLoggedIn()) {
            dispatch(callback, Result.loggedOut());
            return;
        }
        request(false, callback);
    }

    private void request(boolean automatic, Callback callback) {
        if (inFlight) {
            dispatch(callback, Result.running());
            return;
        }
        inFlight = true;
        String today = today();
        session.setLastSignAttemptDate(today);
        log("sign-in request start auto=" + automatic
                + " date=" + today
                + " cookieKeys=" + session.authCookieKeysForLog());
        runAttempts(buildAttempts(), 0, "", "", false, today, callback);
    }

    private Attempt[] buildAttempts() {
        return new Attempt[] {
                new Attempt("v3-sign-web", cb -> api.get(
                        EndpointProvider.taskSignV3(), Collections.emptyMap(), cb)),
                new Attempt("v3-sign-mobile", cb -> api.get(
                        EndpointProvider.taskSignV3(), mobileParams(), cb)),
                new Attempt("legacy-sign-web", cb -> api.get(
                        EndpointProvider.taskSign(), Collections.emptyMap(), cb)),
                new Attempt("legacy-sign-mobile", cb -> api.get(
                        EndpointProvider.taskSign(), mobileParams(), cb)),
                new Attempt("v2-sign-mobile", cb -> api.get(
                        EndpointProvider.taskSignV2(), mobileParams(), cb))
        };
    }

    private void runAttempts(Attempt[] attempts, int index, String lastError,
                             String importantError, boolean tokenRetried, String today,
                             Callback callback) {
        if (index >= attempts.length) {
            if (!tokenRetried && shouldRefreshToken(lastError) && tokenProvider != null) {
                refreshTokenAndRetry(attempts, today, callback);
                return;
            }
            String message = first(importantError, lastError);
            finish(today, Result.failure("\u7b7e\u5230\u5931\u8d25\uff1a"
                    + first(message, "\u63a5\u53e3\u672a\u8fd4\u56de\u53ef\u7528\u7ed3\u679c")),
                    callback);
            return;
        }

        Attempt attempt = attempts[index];
        log("sign-in attempt " + index + " " + attempt.label);
        attempt.starter.start(new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                Result result = parse(body);
                log("sign-in attempt " + attempt.label
                        + " success already=" + result.alreadySigned
                        + " message=" + safe(result.message));
                finish(today, result, callback);
            }

            @Override public void onError(String message) {
                log("sign-in attempt " + attempt.label + " failed: " + safe(message));
                if (looksAlreadySigned(message)) {
                    finish(today, Result.success(true,
                            "\u4eca\u65e5\u5df2\u7b7e\u5230",
                            "\u63a5\u53e3\u8fd4\u56de\u4eca\u65e5\u5df2\u7b7e\u5230"), callback);
                    return;
                }
                if (!tokenRetried && shouldRefreshToken(message) && tokenProvider != null) {
                    refreshTokenAndRetry(attempts, today, callback);
                    return;
                }
                String nextImportant = importantError;
                if (nextImportant == null || nextImportant.isEmpty()) {
                    nextImportant = isLoginError(message) ? message : "";
                }
                runAttempts(attempts, index + 1, first(message, lastError),
                        nextImportant, tokenRetried, today, callback);
            }
        });
    }

    private void refreshTokenAndRetry(Attempt[] attempts, String today, Callback callback) {
        log("sign-in refreshing write token after parameter/token error");
        tokenProvider.refresh(new WriteTokenProvider.Callback() {
            @Override public void onReady() {
                log("sign-in token refresh ok cookieKeys=" + session.authCookieKeysForLog());
                runAttempts(attempts, 0, "", "", true, today, callback);
            }

            @Override public void onError(String message) {
                log("sign-in token refresh failed: " + safe(message));
                finish(today, Result.failure("\u7b7e\u5230\u5931\u8d25\uff1a"
                        + first(message, "\u9a8c\u8bc1 token \u83b7\u53d6\u5931\u8d25")), callback);
            }
        });
    }

    private void finish(String today, Result result, Callback callback) {
        inFlight = false;
        if (result.success) {
            session.setLastSignSuccessDate(today);
        }
        session.setSignSummary(result.summary);
        log("sign-in finish success=" + result.success
                + " already=" + result.alreadySigned
                + " message=" + safe(result.message));
        dispatch(callback, result);
    }

    private Map<String, String> mobileParams() {
        Map<String, String> params = new HashMap<>();
        String id = session.userId();
        params.put("os_type", "Android");
        params.put("client_type", "android");
        params.put("x_client_type", "android");
        params.put("x_os_type", "Android");
        params.put("x_app", "heybox");
        params.put("version", "1.3.379");
        params.put("device_info", "Android");
        params.put(SecureStrings.heyboxId(), id);
        if (!id.isEmpty()) params.put(SecureStrings.userid(), id);
        return params;
    }

    private Result parse(JSONObject body) {
        String message = first(findText(body, "msg", "message", "toast", "desc"),
                "\u7b7e\u5230\u6210\u529f");
        boolean already = looksAlreadySigned(message)
                || truthy(body, "signed", "is_signed", "has_signed", "already_signed");
        String streak = findText(body, "sign_in_streak", "signin_streak",
                "continue_days", "continuous_days", "streak", "days");
        String coin = findText(body, "sign_in_coin", "sign_in_member_coin",
                "coin", "coins", "heybox_coin");
        String exp = findText(body, "sign_in_exp", "exp", "experience");
        String summary = buildSummary(streak, coin, exp, already);
        return Result.success(already,
                already ? "\u4eca\u65e5\u5df2\u7b7e\u5230" : message, summary);
    }

    private static String buildSummary(String streak, String coin, String exp,
                                       boolean already) {
        StringBuilder out = new StringBuilder();
        if (!streak.isEmpty()) out.append("\u8fde\u7eed ").append(streak).append(" \u5929");
        if (!coin.isEmpty()) appendPart(out, "\u76d2\u5e01 +" + coin);
        if (!exp.isEmpty()) appendPart(out, "\u7ecf\u9a8c +" + exp);
        if (out.length() == 0) {
            out.append(already ? "\u4eca\u65e5\u5df2\u5b8c\u6210\u7b7e\u5230" : "\u7b7e\u5230\u5b8c\u6210");
        }
        return out.toString();
    }

    private static void appendPart(StringBuilder out, String value) {
        if (out.length() > 0) out.append(" / ");
        out.append(value);
    }

    private static String findText(JSONObject object, String... keys) {
        if (object == null || keys == null) return "";
        for (String key : keys) {
            String value = optText(object, key);
            if (!value.isEmpty()) return value;
        }
        Iterator<String> names = object.keys();
        while (names.hasNext()) {
            Object value = object.opt(names.next());
            if (value instanceof JSONObject) {
                String nested = findText((JSONObject) value, keys);
                if (!nested.isEmpty()) return nested;
            }
        }
        return "";
    }

    private static boolean truthy(JSONObject object, String... keys) {
        if (object == null || keys == null) return false;
        for (String key : keys) {
            if (truthyValue(object.opt(key))) return true;
        }
        Iterator<String> names = object.keys();
        while (names.hasNext()) {
            Object value = object.opt(names.next());
            if (value instanceof JSONObject && truthy((JSONObject) value, keys)) return true;
        }
        return false;
    }

    private static boolean truthyValue(Object value) {
        if (value == null || value == JSONObject.NULL) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() == 1;
        String text = String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text)
                || "yes".equalsIgnoreCase(text) || "\u5df2\u7b7e\u5230".equals(text);
    }

    private static String optText(JSONObject object, String key) {
        if (object == null || key == null || !object.has(key)) return "";
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) return "";
        return String.valueOf(value).trim();
    }

    private static boolean looksAlreadySigned(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        return (value.contains("\u5df2") && value.contains("\u7b7e"))
                || (value.contains("\u4eca\u65e5") && value.contains("\u7b7e"))
                || (lower.contains("already") && lower.contains("sign"));
    }

    private static boolean isLoginError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.US);
        return lower.contains("login") || lower.contains("relogin")
                || message.contains("\u767b\u5f55")
                || message.contains("\u91cd\u65b0\u767b\u5f55");
    }

    private static boolean shouldRefreshToken(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.US);
        return lower.contains("lack_token")
                || lower.contains("x_xhh_tokenid")
                || lower.contains("tokenid")
                || lower.contains("token")
                || lower.contains("param")
                || lower.contains("sign")
                || message.contains("\u4ee4\u724c")
                || message.contains("\u53c2\u6570")
                || message.contains("\u9a8c\u8bc1")
                || message.contains("\u975e\u6cd5");
    }

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private void dispatch(Callback callback, Result result) {
        if (callback != null) callback.onResult(result);
    }

    private void log(String message) {
        if (logger != null) logger.log(message);
    }

    private static String safe(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 180 ? clean.substring(0, 180) : clean;
    }

    private static String first(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
