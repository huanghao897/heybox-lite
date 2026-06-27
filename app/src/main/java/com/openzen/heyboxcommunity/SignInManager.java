package com.openzen.heyboxcommunity;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;
import org.json.JSONArray;

import java.net.URI;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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

        static Result unknown(String summary) {
            return new Result(true, false, false, false,
                    "\u7b7e\u5230\u72b6\u6001\u672a\u786e\u8ba4",
                    first(summary, "\u5c0f\u9ed1\u76d2\u672a\u8fd4\u56de\u53ef\u786e\u8ba4\u7684\u7b7e\u5230\u7ed3\u679c"));
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
        final String path;
        final Map<String, String> extra;
        final HeyboxSigner.Algorithm algorithm;
        final ApiClient.RequestProfile profile;
        final boolean stateOnly;
        final boolean taskListState;
        final RequestStarter starter;

        Attempt(String label, String path, HeyboxSigner.Algorithm algorithm,
                ApiClient.RequestProfile profile, boolean stateOnly, boolean taskListState,
                RequestStarter starter) {
            this.label = label;
            this.path = path;
            this.extra = Collections.emptyMap();
            this.algorithm = algorithm;
            this.profile = profile;
            this.stateOnly = stateOnly;
            this.taskListState = taskListState;
            this.starter = starter;
        }

        Attempt(String label, String path, Map<String, String> extra,
                HeyboxSigner.Algorithm algorithm, ApiClient.RequestProfile profile,
                RequestStarter starter) {
            this.label = label;
            this.path = path;
            this.extra = extra == null ? Collections.emptyMap() : extra;
            this.algorithm = algorithm;
            this.profile = profile;
            this.stateOnly = false;
            this.taskListState = false;
            this.starter = starter;
        }

        Attempt(String label, String path, Map<String, String> extra,
                HeyboxSigner.Algorithm algorithm, ApiClient.RequestProfile profile,
                boolean stateOnly, boolean taskListState, RequestStarter starter) {
            this.label = label;
            this.path = path;
            this.extra = extra == null ? Collections.emptyMap() : extra;
            this.algorithm = algorithm;
            this.profile = profile;
            this.stateOnly = stateOnly;
            this.taskListState = taskListState;
            this.starter = starter;
        }

        Attempt(String label, RequestStarter starter) {
            this.label = label;
            this.path = "";
            this.extra = Collections.emptyMap();
            this.algorithm = HeyboxSigner.Algorithm.LEGACY;
            this.profile = ApiClient.RequestProfile.WEB;
            this.stateOnly = false;
            this.taskListState = false;
            this.starter = starter;
        }
    }

    private static final class TaskListState {
        final Result result;
        final String actionPath;
        final Map<String, String> actionParams;

        TaskListState(Result result, String actionPath, Map<String, String> actionParams) {
            this.result = result;
            this.actionPath = actionPath == null ? "" : actionPath;
            this.actionParams = actionParams == null
                    ? Collections.emptyMap() : actionParams;
        }
    }

    private static final class TaskAction {
        final String path;
        final Map<String, String> params;

        TaskAction(String path, Map<String, String> params) {
            this.path = path == null ? "" : path;
            this.params = params == null ? Collections.emptyMap() : params;
        }
    }

    private final SessionStore session;
    private final ApiClient api;
    private final WriteTokenProvider tokenProvider;
    private final Logger logger;
    private final Handler main = new Handler(Looper.getMainLooper());
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
            if (isCachedUnconfirmedSummary(summary)) return Result.unknown(summary);
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
        String credentialState = session.importCurrentSessionForSignInLog();
        log("sign-in credentials " + credentialState
                + " isolated=" + session.signInCredentialSummaryForLog());
        if (!session.hasSignInCredentials()) {
            log("sign-in pkey missing; continuing in isolated probe mode");
        }
        if (!session.hasCookieValue(SecureStrings.xXhhTokenId())
                && tokenProvider != null) {
            log("sign-in write token missing");
        }
        primeNativeRndThenRun(false, today, automatic, callback);
    }

    private void primeNativeRndThenRun(boolean tokenRetried, String today,
                                       boolean automatic, Callback callback) {
        log("sign-in native rnd warmup start");
        Attempt[] warmups = new Attempt[] {
                signedAttempt("rnd-web-legacy", EndpointProvider.getuiFix(),
                        HeyboxSigner.Algorithm.LEGACY,
                        ApiClient.RequestProfile.WEB),
                signedAttempt("rnd-mobile-android", EndpointProvider.getuiFix(),
                        HeyboxSigner.Algorithm.ANDROID,
                        ApiClient.RequestProfile.MOBILE),
                signedAttempt("rnd-official-client", EndpointProvider.getuiFix(),
                        HeyboxSigner.Algorithm.ANDROID,
                        ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT)
        };
        runRndWarmups(warmups, 0, tokenRetried, today, automatic, callback);
    }

    private void runRndWarmups(Attempt[] warmups, int index, boolean tokenRetried,
                               String today, boolean automatic, Callback callback) {
        if (index >= warmups.length) {
            log("sign-in native rnd warmup unavailable; continue without rnd");
            runAttempts(buildAttempts(!automatic), 0, "", "", tokenRetried, today,
                    !automatic, null, callback);
            return;
        }
        Attempt attempt = warmups[index];
        log("sign-in native rnd warmup attempt " + attempt.label);
        attempt.starter.start(new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                if (session.saveNativeRndConfig(body)) {
                    log("sign-in native rnd warmup cached via " + attempt.label);
                }
                log("sign-in native rnd warmup ok via " + attempt.label);
                runAttempts(buildAttempts(!automatic), 0, "", "", tokenRetried, today,
                        !automatic, null, callback);
            }

            @Override public void onError(String message) {
                log("sign-in native rnd warmup attempt " + attempt.label
                        + " failed: " + safe(message));
                runRndWarmups(warmups, index + 1, tokenRetried, today, automatic, callback);
            }
        });
    }

    private Attempt[] buildAttempts(boolean includeReplay) {
        List<Attempt> attempts = new ArrayList<>();
        if (includeReplay && session.hasSignInReplayRequest()) {
            attempts.add(new Attempt("replay-captured-sign", cb -> api.getRawIsolated(
                    "replay-captured-sign",
                    session.signInReplayUrl(),
                    session.signInReplayHeaders(),
                    cb)));
        }
        attempts.add(taskListAttempt("task-list-v2-web-legacy",
                EndpointProvider.taskListV2(),
                HeyboxSigner.Algorithm.LEGACY,
                ApiClient.RequestProfile.WEB));
        attempts.add(taskListAttempt("task-list-v2-mobile-android",
                EndpointProvider.taskListV2(),
                HeyboxSigner.Algorithm.ANDROID,
                ApiClient.RequestProfile.MOBILE));
        attempts.add(taskListAttempt("task-list-v2-official-min-cookie",
                EndpointProvider.taskListV2NoSlash(),
                HeyboxSigner.Algorithm.ANDROID,
                ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE));
        attempts.add(stateAttempt("v3-state-official-min-cookie",
                EndpointProvider.taskSignV3State(),
                HeyboxSigner.Algorithm.ANDROID,
                ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE));

        if (!session.hasSignInCredentials()) {
            log("sign-in active submit skipped: no pkey in isolated session");
            return attempts.toArray(new Attempt[0]);
        }

        attempts.add(signedAttempt("v3-sign-official-min-cookie",
                EndpointProvider.taskSignV3(),
                HeyboxSigner.Algorithm.ANDROID,
                ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE));
        attempts.add(signedAttempt("v3-sign-official-client",
                EndpointProvider.taskSignV3(),
                HeyboxSigner.Algorithm.ANDROID,
                ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT));
        attempts.add(signedAttempt("v3-sign-native-fallback",
                EndpointProvider.taskSignV3(),
                HeyboxSigner.Algorithm.ANDROID,
                ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK));
        attempts.add(signedAttempt("v3-sign-official-raw-cookie",
                EndpointProvider.taskSignV3(),
                HeyboxSigner.Algorithm.ANDROID,
                ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_RAW_COOKIE));
        return attempts.toArray(new Attempt[0]);
    }

    private Attempt taskListAttempt(String label, String path, HeyboxSigner.Algorithm algorithm,
                                    ApiClient.RequestProfile profile) {
        return new Attempt(label, path, algorithm, profile, true, true, cb -> api.getSignedIsolated(
                path, Collections.emptyMap(), algorithm, profile, cb));
    }

    private Attempt stateAttempt(String label, String path, HeyboxSigner.Algorithm algorithm,
                                 ApiClient.RequestProfile profile) {
        return new Attempt(label, path, algorithm, profile, true, false, cb -> api.getSignedIsolated(
                path, Collections.emptyMap(), algorithm, profile, cb));
    }

    private Attempt signedAttempt(String label, String path, HeyboxSigner.Algorithm algorithm,
                                  ApiClient.RequestProfile profile) {
        return new Attempt(label, path, algorithm, profile, false, false, cb -> api.getSignedIsolated(
                path, Collections.emptyMap(), algorithm, profile, cb));
    }

    private Attempt signedAttempt(String label, String path, Map<String, String> extra,
                                  HeyboxSigner.Algorithm algorithm,
                                  ApiClient.RequestProfile profile) {
        Map<String, String> params = extra == null ? Collections.emptyMap() : extra;
        return new Attempt(label, path, params, algorithm, profile,
                cb -> api.getSignedIsolated(path, params, algorithm, profile, cb));
    }

    private Attempt actionAttempt(String label, TaskListState state,
                                  HeyboxSigner.Algorithm algorithm,
                                  ApiClient.RequestProfile profile) {
        Map<String, String> extra = new LinkedHashMap<>(state.actionParams);
        return new Attempt(label, state.actionPath, extra, algorithm, profile,
                cb -> api.getSignedIsolated(state.actionPath, extra, algorithm, profile, cb));
    }

    private Attempt[] insertActionAttempts(Attempt[] attempts, int insertAt, TaskListState state) {
        List<Attempt> merged = new ArrayList<>();
        for (int i = 0; i < attempts.length; i++) {
            if (i == insertAt) addTaskActionAttempts(merged, state);
            Attempt attempt = attempts[i];
            if (!sameActionPath(attempt.path, state.actionPath)) merged.add(attempt);
        }
        if (insertAt >= attempts.length) addTaskActionAttempts(merged, state);
        return merged.toArray(new Attempt[0]);
    }

    private void addTaskActionAttempts(List<Attempt> out, TaskListState state) {
        out.add(actionAttempt("task-action-web-legacy", state,
                HeyboxSigner.Algorithm.LEGACY, ApiClient.RequestProfile.WEB));
        out.add(actionAttempt("task-action-mobile-android", state,
                HeyboxSigner.Algorithm.ANDROID, ApiClient.RequestProfile.MOBILE));
        out.add(actionAttempt("task-action-official-client-android", state,
                HeyboxSigner.Algorithm.ANDROID, ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT));
        out.add(actionAttempt("task-action-official-min-cookie", state,
                HeyboxSigner.Algorithm.ANDROID,
                ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE));
        out.add(actionAttempt("task-action-official-raw-cookie", state,
                HeyboxSigner.Algorithm.ANDROID,
                ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_RAW_COOKIE));
        out.add(actionAttempt("task-action-official-client-web", state,
                HeyboxSigner.Algorithm.WEB, ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT));
    }

    private static boolean sameActionPath(String left, String right) {
        return normalizeActionPath(left).equals(normalizeActionPath(right));
    }

    private void runAttempts(Attempt[] attempts, int index, String lastError,
                             String importantError, boolean tokenRetried, String today,
                             boolean probeAlreadySigned, Result knownSignedResult,
                             Callback callback) {
        if (index >= attempts.length) {
            if (knownSignedResult != null && knownSignedResult.success
                    && knownSignedResult.alreadySigned) {
                log("sign-in v3 probe finished; keeping task-list signed state lastError="
                        + safe(first(importantError, lastError)));
                finish(today, knownSignedResult, callback);
                return;
            }
            if (!tokenRetried && shouldRefreshToken(lastError) && tokenProvider != null) {
                log("sign-in token refresh requested but skipped: isolated mode");
            }
            String message = first(importantError, lastError);
            if (shouldKeepStateUnconfirmed(message)) {
                finish(today, Result.unknown(readableFailure(message)), callback);
            } else {
                finish(today, Result.failure("\u7b7e\u5230\u5931\u8d25\uff1a"
                        + readableFailure(message)),
                        callback);
            }
            return;
        }

        Attempt attempt = attempts[index];
        log("sign-in attempt " + index + " " + attempt.label
                + " path=" + attempt.path
                + " profile=" + attempt.profile
                + " algorithm=" + attempt.algorithm
                + " params=" + paramKeys(attempt.profile, attempt.algorithm)
                + " extraKeys=" + attempt.extra.keySet()
                + " cookieKeys=" + cookieKeys(attempt.profile));
        try {
            attempt.starter.start(new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                if (attempt.stateOnly) {
                    TaskListState taskState = attempt.taskListState
                            ? parseTaskListState(body) : null;
                    Result state = taskState == null ? parseState(body) : taskState.result;
                    log("sign-in state " + attempt.label
                            + " signed=" + state.alreadySigned
                            + " message=" + safe(state.message));
                    if (state.success && state.alreadySigned) {
                        finish(today, state, callback);
                        return;
                    }
                    Attempt[] nextAttempts = attempts;
                    if (taskState != null) {
                        log("sign-in task action path=" + taskState.actionPath
                                + " queryKeys=" + taskState.actionParams.keySet());
                        if (!taskState.actionPath.isEmpty()) {
                            nextAttempts = insertActionAttempts(attempts, index + 1, taskState);
                        }
                    }
                    runAttempts(nextAttempts, index + 1, lastError,
                            importantError, tokenRetried, today, probeAlreadySigned,
                            knownSignedResult, callback);
                    return;
                }
                Result result = parse(body);
                if (isV3SignAttempt(attempt.path)) {
                    String stateText = findText(body, "state", "sign_state", "signin_state");
                    if (!"ignore".equalsIgnoreCase(stateText)) {
                        log("sign-in v3 submit returned state=" + safe(stateText)
                                + "; polling final state");
                        pollSignState(0, today, callback);
                        return;
                    }
                }
                log("sign-in attempt " + attempt.label
                        + " success already=" + result.alreadySigned
                        + " state=" + safe(findText(body, "state", "sign_state", "signin_state"))
                        + " keys=" + jsonKeys(body)
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
                    log("sign-in token refresh requested by attempt but skipped: isolated mode");
                }
                String nextImportant = chooseImportantError(importantError, message);
                runAttempts(attempts, index + 1, first(message, lastError),
                        nextImportant, tokenRetried, today, probeAlreadySigned,
                        knownSignedResult, callback);
            }
            });
        } catch (Throwable error) {
            String message = error.getClass().getSimpleName() + ": " + safe(error.getMessage());
            log("sign-in attempt " + attempt.label + " crashed before request: " + message);
            runAttempts(attempts, index + 1, first(message, lastError),
                    importantError, tokenRetried, today, probeAlreadySigned,
                    knownSignedResult, callback);
        }
    }

    private void pollSignState(int round, String today, Callback callback) {
        if (round >= 10) {
            log("sign-in state polling timed out");
            finish(today, Result.failure("\u7b7e\u5230\u72b6\u6001\u8fd8\u5728\u5904\u7406\uff0c\u7a0d\u540e\u518d\u6253\u5f00\u770b\u770b"), callback);
            return;
        }
        long delay = round == 0 ? 0L : pollDelay(round);
        main.postDelayed(() -> {
            log("sign-in state poll round=" + round);
            api.getSignedIsolated(EndpointProvider.taskSignV3State(), Collections.emptyMap(),
                    HeyboxSigner.Algorithm.ANDROID,
                    ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE,
                    new ApiClient.Callback() {
                        @Override public void onSuccess(JSONObject body) {
                            Result state = parseState(body);
                            String stateText = findText(body, "state", "sign_state", "signin_state");
                            log("sign-in state poll result round=" + round
                                    + " state=" + safe(stateText)
                                    + " signed=" + state.alreadySigned
                                    + " message=" + safe(state.message));
                            if (state.success && state.alreadySigned) {
                                finish(today, state, callback);
                            } else if (isWaitingState(stateText)) {
                                pollSignState(round + 1, today, callback);
                            } else {
                                finish(today, state, callback);
                            }
                        }

                        @Override public void onError(String message) {
                            log("sign-in state poll failed round=" + round + ": " + safe(message));
                            if (round < 3) {
                                pollSignState(round + 1, today, callback);
                            } else {
                                finish(today, Result.failure("\u7b7e\u5230\u72b6\u6001\u83b7\u53d6\u5931\u8d25\uff1a"
                                        + readableFailure(message)), callback);
                            }
                        }
                    });
        }, delay);
    }

    private static long pollDelay(int round) {
        if (round < 2) return 2000L;
        if (round < 4) return 3000L;
        return round < 6 ? 5000L : 10000L;
    }

    private void refreshTokenAndRetry(Attempt[] attempts, String today,
                                      boolean probeAlreadySigned,
                                      Result knownSignedResult, Callback callback) {
        log("sign-in token refresh disabled: isolated mode protects main session");
        finish(today, Result.failure("\u7b7e\u5230\u5931\u8d25\uff1a\u63a5\u53e3\u6821\u9a8c\u672a\u901a\u8fc7"),
                callback);
    }

    private void finish(String today, Result result, Callback callback) {
        inFlight = false;
        if (result != null && !result.success
                && today.equals(session.lastSignSuccessDate())) {
            Result cached = Result.cachedSuccess(session.signSummary());
            log("sign-in finish ignored failure because today is already signed: "
                    + safe(result.message));
            dispatch(callback, cached);
            return;
        }
        if (result.success) {
            session.setLastSignSuccessDate(today);
            session.setLastSignAttemptDate(today);
        } else if (result.message.contains("\u72b6\u6001\u672a\u786e\u8ba4")) {
            session.setLastSignAttemptDate("");
        }
        session.setSignSummary(result.summary);
        log("sign-in finish success=" + result.success
                + " already=" + result.alreadySigned
                + " message=" + safe(result.message));
        dispatch(callback, result);
    }

    private Result parse(JSONObject body) {
        String message = first(findText(body, "msg", "message", "toast", "desc"),
                "\u7b7e\u5230\u6210\u529f");
        String state = findText(body, "state", "sign_state", "signin_state");
        boolean already = looksAlreadySigned(message)
                || looksSignedState(state)
                || truthy(body, "signed", "is_signed", "has_signed", "already_signed");
        String streak = findText(body, "sign_in_streak", "signin_streak",
                "continue_days", "continuous_days", "streak", "days");
        String coinAward = findText(body, "award_coin", "reward_coin",
                "sign_in_coin", "sign_in_member_coin");
        String coinTotal = findText(body, "coin", "coins", "heybox_coin");
        String expAward = findText(body, "award_exp", "reward_exp", "sign_in_exp");
        String expTotal = findText(body, "exp", "experience");
        String summary = buildSummary(streak, coinAward, coinTotal, expAward, expTotal, already);
        return Result.success(already,
                already ? successMessageForState(state) : message, summary);
    }

    private Result parseState(JSONObject body) {
        String message = first(findText(body, "msg", "message", "toast", "desc",
                        "description", "notify_description"),
                "\u7b7e\u5230\u72b6\u6001\u5df2\u83b7\u53d6");
        String state = findText(body, "state", "sign_state", "signin_state");
        boolean already = looksAlreadySigned(message)
                || looksSignedState(state)
                || truthy(body, "signed", "is_signed", "has_signed", "already_signed");
        String streak = findText(body, "sign_in_streak", "signin_streak",
                "continue_days", "continuous_days", "streak", "days");
        String coinAward = findText(body, "award_coin", "reward_coin",
                "sign_in_coin", "sign_in_member_coin");
        String coinTotal = findText(body, "coin", "coins", "heybox_coin");
        String expAward = findText(body, "award_exp", "reward_exp", "sign_in_exp");
        String expTotal = findText(body, "exp", "experience");
        String summary = buildSummary(streak, coinAward, coinTotal, expAward, expTotal, already);
        if (already) return Result.success(true, successMessageForState(state), summary);
        return Result.failure(message);
    }

    private TaskListState parseTaskListState(JSONObject body) {
        JSONObject result = body == null ? null : body.optJSONObject("result");
        JSONObject source = result == null ? body : result;
        JSONObject signInfo = source == null ? null : source.optJSONObject("sign_v2_info");
        boolean already = false;
        String desc = "";
        if (signInfo != null) {
            already = truthyValue(signInfo.opt("today_signed"))
                    || looksSignedState(optText(signInfo, "today_signed"));
            desc = first(optText(signInfo, "sign_desc"), optText(signInfo, "description"));
        }
        JSONObject task = findSignTask(source, 0);
        if (task != null) {
            already = already || looksSignedState(optText(task, "state"))
                    || looksAlreadySigned(optText(task, "state_desc"));
            desc = first(desc, optText(task, "state_desc"));
        }
        String streak = first(findText(source, "sign_in_streak", "signin_streak",
                        "continue_days", "continuous_days", "streak", "days"),
                task == null ? "" : optText(task, "sign_in_streak"));
        String coinAward = first(task == null ? "" : optText(task, "award_coin"),
                findText(source, "award_coin", "reward_coin",
                        "sign_in_coin", "sign_in_member_coin"));
        String coinTotal = findText(source, "coin", "coins", "heybox_coin");
        String expAward = first(task == null ? "" : optText(task, "award_exp"),
                findText(source, "award_exp", "reward_exp", "sign_in_exp"));
        String expTotal = findText(source, "exp", "experience");
        String summary = buildSummary(streak, coinAward, coinTotal, expAward, expTotal, already);
        String actionUrl = task == null ? "" : findActionUrl(task);
        TaskAction action = parseTaskAction(actionUrl);
        if (task != null) {
            log("sign-in task-list task title=" + safe(optText(task, "title"))
                    + " type=" + safe(optText(task, "type"))
                    + " state=" + safe(optText(task, "state"))
                    + " desc=" + safe(first(optText(task, "state_desc"), desc))
                    + " action=" + safe(actionUrl));
        }
        Result parsed = already
                ? Result.success(true, "\u4eca\u65e5\u5df2\u7b7e\u5230", summary)
                : Result.failure(first(desc, "\u4eca\u65e5\u8fd8\u672a\u7b7e\u5230"));
        return new TaskListState(parsed, action.path, action.params);
    }

    private static String findActionUrl(JSONObject task) {
        String direct = first(optText(task, "url"), optText(task, "action_url"));
        direct = first(direct, optText(task, "jump_url"));
        direct = first(direct, optText(task, "link_url"));
        direct = first(direct, optText(task, "target_url"));
        direct = first(direct, optText(task, "deeplink"));
        direct = first(direct, optText(task, "deep_link"));
        direct = first(direct, optText(task, "scheme"));
        if (!direct.isEmpty()) return direct;
        return findUrlLikeText(task, 0);
    }

    private static String findUrlLikeText(Object node, int depth) {
        if (node == null || depth > 6) return "";
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            Iterator<String> names = object.keys();
            while (names.hasNext()) {
                String name = names.next();
                Object value = object.opt(name);
                String text = value == null || value == JSONObject.NULL ? "" : String.valueOf(value);
                String lowerName = name.toLowerCase(Locale.US);
                if ((lowerName.contains("url") || lowerName.contains("link")
                        || lowerName.contains("scheme") || lowerName.contains("action"))
                        && looksLikeActionText(text)) {
                    return text.trim();
                }
                String nested = findUrlLikeText(value, depth + 1);
                if (!nested.isEmpty()) return nested;
            }
            return "";
        }
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                String nested = findUrlLikeText(array.opt(i), depth + 1);
                if (!nested.isEmpty()) return nested;
            }
            return "";
        }
        String text = String.valueOf(node);
        return looksLikeActionText(text) ? text.trim() : "";
    }

    private static boolean looksLikeActionText(String value) {
        if (value == null) return false;
        String text = value.toLowerCase(Locale.US);
        return text.contains("/task/")
                || text.contains("task%2f")
                || text.contains("sign_v")
                || text.contains("sign%5f")
                || text.contains("\u7b7e\u5230");
    }

    private static TaskAction parseTaskAction(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return new TaskAction("", Collections.emptyMap());

        TaskAction nested = parseNestedUrl(text);
        if (!nested.path.isEmpty()) return nested;

        String decoded = decode(text);
        if (!decoded.equals(text)) {
            nested = parseNestedUrl(decoded);
            if (!nested.path.isEmpty()) return nested;
        }

        String source = decoded;
        String path = "";
        String query = "";
        try {
            URI uri = new URI(source);
            if (uri.getRawQuery() != null) query = uri.getRawQuery();
            if (uri.getScheme() != null && uri.getHost() != null
                    && "task".equalsIgnoreCase(uri.getHost())) {
                path = "/task" + first(uri.getRawPath(), "");
            } else if (uri.getRawPath() != null && uri.getRawPath().contains("/task/")) {
                path = uri.getRawPath();
            }
        } catch (Exception ignored) {
        }
        if (path.isEmpty()) {
            int taskIndex = source.indexOf("/task/");
            if (taskIndex < 0) taskIndex = source.indexOf("task/");
            if (taskIndex >= 0) {
                String tail = source.substring(taskIndex);
                if (!tail.startsWith("/")) tail = "/" + tail;
                int question = tail.indexOf('?');
                int end = tail.length();
                for (char marker : new char[] {' ', '"', '\'', '&', '#', '<', '>'}) {
                    int markerIndex = tail.indexOf(marker);
                    if (markerIndex >= 0 && markerIndex < end
                            && (question < 0 || markerIndex < question)) {
                        end = markerIndex;
                    }
                }
                String chunk = tail.substring(0, end);
                int queryStart = chunk.indexOf('?');
                if (queryStart >= 0) {
                    path = chunk.substring(0, queryStart);
                    query = chunk.substring(queryStart + 1);
                } else {
                    path = chunk;
                    if (question >= 0 && question + 1 < tail.length()) {
                        query = tail.substring(question + 1);
                    }
                }
            }
        }
        path = normalizeActionPath(path);
        if (path.isEmpty()) return new TaskAction("", Collections.emptyMap());
        return new TaskAction(path, parseQuery(query));
    }

    private static TaskAction parseNestedUrl(String value) {
        Map<String, String> params = parseQuery(queryOf(value));
        for (String key : new String[] {"url", "action_url", "jump_url", "target_url",
                "link_url", "redirect", "redirect_url", "scheme", "deeplink", "deep_link"}) {
            String nested = params.get(key);
            if (nested == null || nested.trim().isEmpty()) continue;
            TaskAction action = parseTaskAction(nested);
            if (!action.path.isEmpty()) return action;
        }
        return new TaskAction("", Collections.emptyMap());
    }

    private static String queryOf(String value) {
        if (value == null) return "";
        int question = value.indexOf('?');
        if (question >= 0 && question + 1 < value.length()) {
            return value.substring(question + 1);
        }
        return value.contains("=") ? value : "";
    }

    private static Map<String, String> parseQuery(String query) {
        if (query == null || query.trim().isEmpty()) return Collections.emptyMap();
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            if (pair == null || pair.isEmpty()) continue;
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";
            key = decode(key).trim();
            if (key.isEmpty()) continue;
            params.put(key, decode(value));
        }
        return params;
    }

    private static String normalizeActionPath(String path) {
        String value = path == null ? "" : decode(path).trim();
        if (value.isEmpty()) return "";
        int question = value.indexOf('?');
        if (question >= 0) value = value.substring(0, question);
        if (!value.startsWith("/")) value = "/" + value;
        while (value.contains("//")) value = value.replace("//", "/");
        return value;
    }

    private static String decode(String value) {
        if (value == null) return "";
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    private static JSONObject findSignTask(Object node, int depth) {
        if (node == null || depth > 8) return null;
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                JSONObject found = findSignTask(array.opt(i), depth + 1);
                if (found != null) return found;
            }
            return null;
        }
        if (!(node instanceof JSONObject)) return null;
        JSONObject object = (JSONObject) node;
        String title = optText(object, "title") + " " + optText(object, "desc")
                + " " + optText(object, "type") + " " + optText(object, "url");
        String lower = title.toLowerCase(Locale.US);
        if (title.contains("\u7b7e\u5230") || lower.contains("sign")) return object;
        Iterator<String> names = object.keys();
        while (names.hasNext()) {
            JSONObject found = findSignTask(object.opt(names.next()), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private static String buildSummary(String streak, String coinAward, String coinTotal,
                                       String expAward, String expTotal, boolean already) {
        StringBuilder out = new StringBuilder();
        if (!streak.isEmpty()) out.append("\u8fde\u7eed ").append(streak).append(" \u5929");
        if (!coinAward.isEmpty()) appendPart(out, "\u672c\u6b21\u76d2\u5e01 +" + coinAward);
        else if (!coinTotal.isEmpty()) appendPart(out, "\u7d2f\u8ba1\u76d2\u5e01 " + coinTotal);
        if (!expAward.isEmpty()) appendPart(out, "\u672c\u6b21\u7ecf\u9a8c +" + expAward);
        else if (!expTotal.isEmpty()) appendPart(out, "\u7d2f\u8ba1\u7ecf\u9a8c " + expTotal);
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

    private static String jsonKeys(JSONObject object) {
        if (object == null) return "[]";
        List<String> keys = new ArrayList<>();
        Iterator<String> iterator = object.keys();
        while (iterator.hasNext() && keys.size() < 12) keys.add(iterator.next());
        return keys.toString();
    }

    private static boolean looksAlreadySigned(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        return (value.contains("\u5df2") && value.contains("\u7b7e"))
                || (value.contains("\u4eca\u65e5") && value.contains("\u7b7e"))
                || (lower.contains("already") && lower.contains("sign"));
    }

    private static boolean looksSignedState(String value) {
        if (value == null) return false;
        String lower = value.trim().toLowerCase(Locale.US);
        if (lower.isEmpty() || lower.contains("un") || lower.contains("not")) return false;
        return "1".equals(lower)
                || "ok".equals(lower)
                || "ignore".equals(lower)
                || "signed".equals(lower)
                || "done".equals(lower)
                || "finish".equals(lower)
                || "finished".equals(lower)
                || lower.contains("\u5df2\u7b7e");
    }

    private static boolean isWaitingState(String value) {
        if (value == null) return false;
        String lower = value.trim().toLowerCase(Locale.US);
        return "waiting".equals(lower)
                || "pending".equals(lower)
                || "processing".equals(lower)
                || lower.contains("wait");
    }

    private static boolean isV3SignAttempt(String path) {
        if (path == null) return false;
        String clean = normalizeActionPath(path);
        while (clean.endsWith("/") && clean.length() > 1) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return EndpointProvider.taskSignV3().equals(clean);
    }

    private static String successMessageForState(String state) {
        String value = state == null ? "" : state.trim().toLowerCase(Locale.US);
        if ("ignore".equals(value)) return "\u4eca\u65e5\u7b7e\u5230\u5df2\u5904\u7406";
        if ("ok".equals(value)) return "\u7b7e\u5230\u6210\u529f";
        return "\u4eca\u65e5\u5df2\u7b7e\u5230";
    }

    private static boolean isLoginError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.US);
        return lower.contains("login") || lower.contains("relogin")
                || message.contains("\u767b\u5f55")
                || message.contains("\u91cd\u65b0\u767b\u5f55");
    }

    private static String chooseImportantError(String current, String next) {
        if (current != null && !current.isEmpty()) {
            if (isValidationError(current)) return current;
            if (!isValidationError(next)) return current;
        }
        if (isValidationError(next) || isLoginError(next)) return next;
        return first(current, "");
    }

    private static boolean shouldRefreshToken(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.US);
        return lower.contains("lack_token")
                || lower.contains("x_xhh_tokenid")
                || lower.contains("tokenid")
                || lower.contains("token")
                || message.contains("\u4ee4\u724c");
    }

    private static boolean isValidationError(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.US);
        return lower.contains("illegal")
                || lower.contains("invalid")
                || message.contains("\u975e\u6cd5")
                || message.contains("\u6821\u9a8c")
                || message.contains("\u9a8c\u8bc1\u53c2\u6570");
    }

    private static boolean shouldKeepStateUnconfirmed(String message) {
        return isValidationError(message);
    }

    private static boolean isCachedUnconfirmedSummary(String value) {
        return isValidationError(value)
                || (value != null && value.contains("\u72b6\u6001\u672a\u786e\u8ba4"));
    }

    private static String readableFailure(String message) {
        String value = first(message, "\u63a5\u53e3\u672a\u8fd4\u56de\u53ef\u7528\u7ed3\u679c");
        if (value.contains("\u975e\u6cd5") || value.toLowerCase(Locale.US).contains("illegal")) {
            return "\u63a5\u53e3\u6821\u9a8c\u672a\u901a\u8fc7\uff0c\u5df2\u4fdd\u7559\u5f53\u524d\u7b7e\u5230\u72b6\u6001";
        }
        return value;
    }

    private String cookieKeys(ApiClient.RequestProfile profile) {
        if (profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT
                || profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE
                || profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE_ENCODED
                || profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MERGED
                || profile == ApiClient.RequestProfile.OFFICIAL_SPARSE_CLIENT) {
            if (profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE
                    || profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE_ENCODED) {
                return session.officialMinimalCookieKeysForLog(false);
            }
            return session.officialBridgeCookieKeysForLog(false);
        }
        if (profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_KEYS) {
            return session.officialBridgeCookieKeysForLog(true);
        }
        if (profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK) {
            return session.officialBridgeCookieKeysForLog(false);
        }
        if (profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK_KEYS) {
            return session.officialBridgeCookieKeysForLog(true);
        }
        if (profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_RAW_COOKIE) {
            return session.authCookieKeysForLog();
        }
        if (profile == ApiClient.RequestProfile.OFFICIAL_MOBILE
                || profile == ApiClient.RequestProfile.OFFICIAL_SPARSE) {
            return session.officialMobileCookieKeysForLog(false);
        }
        return session.authCookieKeysForLog();
    }

    private static String paramKeys(ApiClient.RequestProfile profile,
                                    HeyboxSigner.Algorithm algorithm) {
        StringBuilder out = new StringBuilder();
        if (profile == ApiClient.RequestProfile.WEB) {
            out.append("web-common");
        } else if (profile == ApiClient.RequestProfile.MOBILE) {
            out.append("mobile-common");
        } else if (profile == ApiClient.RequestProfile.OFFICIAL_MOBILE
                || profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT
                || profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE
                || profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE_ENCODED
                || profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_MERGED
                || profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_KEYS
                || profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK
                || profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK_KEYS
                || profile == ApiClient.RequestProfile.OFFICIAL_MOBILE_CLIENT_RAW_COOKIE) {
            out.append("official-mobile-common");
        } else {
            out.append("official-sparse-common");
        }
        String sign = signKeys(algorithm);
        if (!sign.isEmpty()) out.append('+').append(sign);
        return out.toString();
    }

    private static String signKeys(HeyboxSigner.Algorithm algorithm) {
        if (algorithm == HeyboxSigner.Algorithm.NONE) return "";
        if (algorithm == HeyboxSigner.Algorithm.PLAIN
                || algorithm == HeyboxSigner.Algorithm.OLD_MD5) return "hkey,_time";
        if (algorithm == HeyboxSigner.Algorithm.PLAIN_KEY) return "key,_time";
        if (algorithm == HeyboxSigner.Algorithm.WEB_KEY
                || algorithm == HeyboxSigner.Algorithm.ANDROID_KEY
                || algorithm == HeyboxSigner.Algorithm.LEGACY_KEY) {
            return "key,_time,nonce";
        }
        return "hkey,_time,nonce";
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
