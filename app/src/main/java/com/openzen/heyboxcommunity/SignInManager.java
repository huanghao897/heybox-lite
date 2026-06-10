package com.openzen.heyboxcommunity;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

final class SignInManager {
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
                    "登录后可以签到", "扫码登录后可使用每日签到");
        }

        static Result pending() {
            return new Result(true, false, false, false,
                    "今日还未签到", "打开软件后会自动尝试一次，也可以手动签到");
        }

        static Result running() {
            return new Result(true, false, false, true,
                    "正在签到", "正在向小黑盒接口提交签到请求");
        }

        static Result cachedSuccess(String summary) {
            return new Result(true, true, true, false,
                    "今日已签到", first(summary, "今日已完成签到"));
        }

        static Result cachedFailure(String summary) {
            return new Result(true, false, false, false,
                    "今日签到失败", first(summary, "稍后可以手动再试"));
        }

        static Result success(boolean alreadySigned, String message, String summary) {
            return new Result(true, true, alreadySigned, false,
                    first(message, alreadySigned ? "今日已签到" : "签到成功"),
                    first(summary, alreadySigned ? "今日已完成签到" : "签到完成"));
        }

        static Result failure(String message) {
            return new Result(true, false, false, false,
                    first(message, "签到失败"), first(message, "稍后可以手动再试"));
        }
    }

    private final SessionStore session;
    private final ApiClient api;
    private final Logger logger;
    private boolean inFlight;

    SignInManager(SessionStore session, ApiClient api, Logger logger) {
        this.session = session;
        this.api = api;
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
        log("sign-in request start auto=" + automatic + " date=" + today);
        api.get(EndpointProvider.taskSign(), Collections.emptyMap(), new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                inFlight = false;
                Result result = parse(body);
                if (result.success) {
                    session.setLastSignSuccessDate(today);
                }
                session.setSignSummary(result.summary);
                log("sign-in result success=" + result.success
                        + " already=" + result.alreadySigned
                        + " message=" + safe(result.message));
                dispatch(callback, result);
            }

            @Override public void onError(String message) {
                inFlight = false;
                boolean already = looksAlreadySigned(message);
                Result result = already
                        ? Result.success(true, "今日已签到", "接口返回今日已签到")
                        : Result.failure("签到失败：" + first(message, "网络或接口异常"));
                if (result.success) {
                    session.setLastSignSuccessDate(today);
                }
                session.setSignSummary(result.summary);
                log("sign-in error treatedSuccess=" + result.success
                        + " message=" + safe(message));
                dispatch(callback, result);
            }
        });
    }

    private Result parse(JSONObject body) {
        String message = first(findText(body, "msg", "message", "toast", "desc"), "签到成功");
        boolean already = looksAlreadySigned(message);
        String streak = findText(body, "sign_in_streak", "signin_streak",
                "continue_days", "continuous_days", "streak", "days");
        String coin = findText(body, "sign_in_coin", "coin", "coins", "heybox_coin");
        String exp = findText(body, "sign_in_exp", "exp", "experience");
        String summary = buildSummary(streak, coin, exp, already);
        return Result.success(already, already ? "今日已签到" : message, summary);
    }

    private static String buildSummary(String streak, String coin, String exp,
                                       boolean already) {
        StringBuilder out = new StringBuilder();
        if (!streak.isEmpty()) out.append("连续 ").append(streak).append(" 天");
        if (!coin.isEmpty()) appendPart(out, "盒币 +" + coin);
        if (!exp.isEmpty()) appendPart(out, "经验 +" + exp);
        if (out.length() == 0) out.append(already ? "今日已完成签到" : "签到完成");
        return out.toString();
    }

    private static void appendPart(StringBuilder out, String value) {
        if (out.length() > 0) out.append(" · ");
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

    private static String optText(JSONObject object, String key) {
        if (object == null || key == null || !object.has(key)) return "";
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) return "";
        return String.valueOf(value).trim();
    }

    private static boolean looksAlreadySigned(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        return (value.contains("已") && value.contains("签"))
                || (value.contains("今日") && value.contains("签"))
                || (lower.contains("already") && lower.contains("sign"));
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
