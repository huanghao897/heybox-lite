package com.ronan.heyboxlite;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

final class SignInManager {
    private static final boolean ENABLED = false;

    interface Callback {
        void onResult(Result result);
    }

    interface Logger {
        void log(String message);
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

        static Result disabled() {
            return new Result(true, false, false, false,
                    "\u7b7e\u5230\u5df2\u6682\u505c",
                    "\u7b7e\u5230\u529f\u80fd\u5df2\u6683\u65f6\u5173\u95ed");
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

    private final SessionStore session;
    private final ApiClient api;
    private final Logger logger;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean inFlight;

    SignInManager(SessionStore session, ApiClient api, WriteTokenProvider ignored,
                  Logger logger) {
        this.session = session;
        this.api = api;
        this.logger = logger;
    }

    Result currentState() {
        if (!ENABLED) return Result.disabled();
        if (!session.isLoggedIn()) return Result.loggedOut();
        if (inFlight) return Result.running();
        String today = today();
        String summary = session.signSummary();
        if (today.equals(session.lastSignSuccessDate())) return Result.cachedSuccess(summary);
        return Result.pending();
    }

    void autoSignInIfNeeded(Callback callback) {
        if (!ENABLED) {
            dispatch(callback, Result.disabled());
            return;
        }
        if (!session.isLoggedIn()) {
            dispatch(callback, Result.loggedOut());
            return;
        }
        String today = today();
        if (today.equals(session.lastSignSuccessDate())
                || today.equals(session.lastSignAttemptDate())) {
            dispatch(callback, currentState());
            return;
        }
        request(callback);
    }

    void signIn(Callback callback) {
        if (!ENABLED) {
            dispatch(callback, Result.disabled());
            return;
        }
        if (!session.isLoggedIn()) {
            dispatch(callback, Result.loggedOut());
            return;
        }
        request(callback);
    }

    private void request(Callback callback) {
        if (inFlight) {
            dispatch(callback, Result.running());
            return;
        }
        inFlight = true;
        String today = today();
        session.setLastSignAttemptDate(today);
        log("sign-in start");
        checkState(today, callback);
    }

    private void checkState(String today, Callback callback) {
        api.getSignedIsolated(EndpointProvider.taskSignV3State(), Collections.emptyMap(),
                HeyboxSigner.Algorithm.ANDROID,
                ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE,
                new ApiClient.Callback() {
                    @Override public void onSuccess(JSONObject body) {
                        String state = findText(body, "state", "sign_state", "signin_state");
                        boolean already = truthy(body, "signed", "is_signed", "has_signed",
                                "already_signed", "today_signed")
                                || looksSignedState(state);
                        log("sign-in state=" + state + " already=" + already);
                        if (already) {
                            finish(today, Result.success(true, "\u4eca\u65e5\u5df2\u7b7e\u5230",
                                    parseSummary(body)), callback);
                            return;
                        }
                        submit(today, callback);
                    }

                    @Override public void onError(String message) {
                        log("sign-in state check failed: " + safe(message));
                        submit(today, callback);
                    }
                });
    }

    private void submit(String today, Callback callback) {
        api.getSignedIsolated(EndpointProvider.taskSignV3(), Collections.emptyMap(),
                HeyboxSigner.Algorithm.ANDROID,
                ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE,
                new ApiClient.Callback() {
                    @Override public void onSuccess(JSONObject body) {
                        String state = findText(body, "state", "sign_state", "signin_state");
                        String msg = findText(body, "msg", "message", "toast", "desc");
                        boolean already = looksAlreadySigned(msg) || looksSignedState(state);
                        String summary = parseSummary(body);
                        log("sign-in submit ok state=" + state + " already=" + already);
                        finish(today, Result.success(already, msg, summary), callback);
                    }

                    @Override public void onError(String message) {
                        log("sign-in submit failed: " + safe(message));
                        if (looksAlreadySigned(message)) {
                            finish(today, Result.success(true, "\u4eca\u65e5\u5df2\u7b7e\u5230",
                                    "\u4eca\u65e5\u5df2\u5b8c\u6210\u7b7e\u5230"), callback);
                            return;
                        }
                        finish(today, Result.failure(message), callback);
                    }
                });
    }

    private void finish(String today, Result result, Callback callback) {
        inFlight = false;
        if (result.success) {
            session.setLastSignSuccessDate(today);
            session.setLastSignAttemptDate(today);
        }
        session.setSignSummary(result.summary);
        log("sign-in finish success=" + result.success + " already=" + result.alreadySigned);
        dispatch(callback, result);
    }

    private String parseSummary(JSONObject body) {
        String streak = findText(body, "sign_in_streak", "signin_streak",
                "continue_days", "continuous_days", "streak", "days");
        String coinAward = findText(body, "award_coin", "reward_coin",
                "sign_in_coin", "sign_in_member_coin");
        String coinTotal = findText(body, "coin", "coins", "heybox_coin");
        String expAward = findText(body, "award_exp", "reward_exp", "sign_in_exp");
        String expTotal = findText(body, "exp", "experience");
        StringBuilder out = new StringBuilder();
        if (!streak.isEmpty()) out.append("\u8fde\u7eed ").append(streak).append(" \u5929");
        if (!coinAward.isEmpty()) {
            if (out.length() > 0) out.append(" / ");
            out.append("\u672c\u6b21\u76d2\u5e01 +").append(coinAward);
        } else if (!coinTotal.isEmpty()) {
            if (out.length() > 0) out.append(" / ");
            out.append("\u7d2f\u8ba1\u76d2\u5e01 ").append(coinTotal);
        }
        if (!expAward.isEmpty()) {
            if (out.length() > 0) out.append(" / ");
            out.append("\u672c\u6b21\u7ecf\u9a8c +").append(expAward);
        } else if (!expTotal.isEmpty()) {
            if (out.length() > 0) out.append(" / ");
            out.append("\u7d2f\u8ba1\u7ecf\u9a8c ").append(expTotal);
        }
        return out.length() == 0 ? "\u7b7e\u5230\u5b8c\u6210" : out.toString();
    }

    private static String findText(JSONObject object, String... keys) {
        if (object == null || keys == null) return "";
        for (String key : keys) {
            String value = optText(object, key);
            if (!value.isEmpty()) return value;
        }
        java.util.Iterator<String> names = object.keys();
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
            Object value = object.opt(key);
            if (value instanceof Boolean && (Boolean) value) return true;
            if (value instanceof Number && ((Number) value).intValue() == 1) return true;
            String text = value == null ? "" : String.valueOf(value).trim();
            if ("1".equals(text) || "true".equalsIgnoreCase(text)) return true;
        }
        return false;
    }

    private static String optText(JSONObject object, String key) {
        if (object == null || key == null || !object.has(key)) return "";
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) return "";
        return String.valueOf(value).trim();
    }

    private static boolean looksAlreadySigned(String value) {
        if (value == null) return false;
        return (value.contains("\u5df2") && value.contains("\u7b7e"))
                || (value.contains("\u4eca\u65e5") && value.contains("\u7b7e"));
    }

    private static boolean looksSignedState(String value) {
        if (value == null) return false;
        String lower = value.trim().toLowerCase(Locale.US);
        if (lower.isEmpty() || lower.contains("un") || lower.contains("not")) return false;
        return "1".equals(lower) || "ok".equals(lower) || "signed".equals(lower)
                || "done".equals(lower) || "finished".equals(lower)
                || lower.contains("\u5df2\u7b7e");
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
