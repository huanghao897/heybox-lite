package com.ronan.heyboxlite;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

final class ApiClient {
    private static final boolean ENABLE_NATIVE_SIGNER = true;
    private static final String OFFICIAL_TASK_CHANNEL = "heybox_oppo";
    private static final String OFFICIAL_TASK_APP = "heybox";

    enum RequestProfile {
        WEB,
        MOBILE,
        OFFICIAL_MOBILE,
        OFFICIAL_MOBILE_CLIENT,
        OFFICIAL_MOBILE_CLIENT_MIN_COOKIE,
        OFFICIAL_MOBILE_CLIENT_MIN_COOKIE_ENCODED,
        OFFICIAL_MOBILE_CLIENT_MERGED,
        OFFICIAL_MOBILE_CLIENT_KEYS,
        OFFICIAL_MOBILE_CLIENT_FALLBACK,
        OFFICIAL_MOBILE_CLIENT_FALLBACK_KEYS,
        OFFICIAL_MOBILE_CLIENT_RAW_COOKIE,
        OFFICIAL_MOBILE_LOGIN,
        OFFICIAL_SPARSE,
        OFFICIAL_SPARSE_CLIENT
    }

    interface Callback {
        default void onResponseCookies(List<String> cookies) {}
        void onSuccess(JSONObject body);
        void onError(String message);
    }

    interface Logger {
        void log(String message);
    }

    private final SessionStore session;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Logger logger;
    private volatile boolean closed;

    ApiClient(SessionStore session) {
        this(session, null);
    }

    ApiClient(SessionStore session, Logger logger) {
        this.session = session;
        this.logger = logger;
    }

    void get(String path, Map<String, String> extra, Callback callback) {
        if (closed) return;
        executor.execute(() -> request("GET", path, extra, null,
                HeyboxSigner.Algorithm.LEGACY, RequestProfile.WEB, callback));
    }

    void getSigned(String path, Map<String, String> extra,
                   HeyboxSigner.Algorithm algorithm, RequestProfile profile,
                   Callback callback) {
        if (closed) return;
        executor.execute(() -> request("GET", path, extra, null, algorithm, profile, callback));
    }

    void getSignedIsolated(String path, Map<String, String> extra,
                           HeyboxSigner.Algorithm algorithm, RequestProfile profile,
                           Callback callback) {
        if (closed) return;
        executor.execute(() -> request("GET", path, extra, null, algorithm, profile,
                callback, false, true));
    }

    void getRawIsolated(String label, String rawUrl, Map<String, String> headers,
                        Callback callback) {
        if (closed) return;
        executor.execute(() -> rawRequest(label, rawUrl, headers, callback));
    }

    void postForm(String path, Map<String, String> body, Callback callback) {
        if (closed) return;
        executor.execute(() -> request("POST", path, new LinkedHashMap<>(), body,
                HeyboxSigner.Algorithm.LEGACY, RequestProfile.WEB, callback));
    }

    void postForm(String path, Map<String, String> queryExtra,
                  Map<String, String> body, Callback callback) {
        if (closed) return;
        executor.execute(() -> request("POST", path, queryExtra, body,
                HeyboxSigner.Algorithm.LEGACY, RequestProfile.WEB, callback));
    }

    void postSigned(String path, Map<String, String> queryExtra,
                    Map<String, String> body, HeyboxSigner.Algorithm algorithm,
                    RequestProfile profile, Callback callback) {
        if (closed) return;
        executor.execute(() -> request("POST", path, queryExtra, body,
                algorithm, profile, callback));
    }

    void postSignedIsolated(String path, Map<String, String> queryExtra,
                            Map<String, String> body, HeyboxSigner.Algorithm algorithm,
                            RequestProfile profile, Callback callback) {
        if (closed) return;
        executor.execute(() -> request("POST", path, queryExtra, body,
                algorithm, profile, callback, false, false));
    }

    void postSignedQueryOnly(String path, Map<String, String> queryExtra,
                             Map<String, String> body, HeyboxSigner.Algorithm algorithm,
                             RequestProfile profile, Callback callback) {
        if (closed) return;
        Map<String, String> query = new LinkedHashMap<>();
        if (queryExtra != null) query.putAll(queryExtra);
        if (body != null) query.putAll(body);
        executor.execute(() -> request("POST", path, query, new LinkedHashMap<>(),
                algorithm, profile, callback));
    }

    void close() {
        closed = true;
        main.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    private void request(String method, String path, Map<String, String> extra,
                         Map<String, String> body, HeyboxSigner.Algorithm algorithm,
                         RequestProfile profile, Callback callback) {
        request(method, path, extra, body, algorithm, profile, callback, true);
    }

    private void request(String method, String path, Map<String, String> extra,
                         Map<String, String> body, HeyboxSigner.Algorithm algorithm,
                         RequestProfile profile, Callback callback,
                         boolean mergeResponseCookies) {
        request(method, path, extra, body, algorithm, profile, callback,
                mergeResponseCookies, false);
    }

    private void request(String method, String path, Map<String, String> extra,
                         Map<String, String> body, HeyboxSigner.Algorithm algorithm,
                         RequestProfile profile, Callback callback,
                         boolean mergeResponseCookies, boolean useSignInCredentials) {
        HttpURLConnection connection = null;
        boolean taskRequest = isTaskPath(path);
        boolean debugRequest = taskRequest || isWritePath(path);
        try {
            if (closed || Thread.currentThread().isInterrupted()) return;
            Map<String, String> params = new LinkedHashMap<>(
                    baseParams(profile, useSignInCredentials));
            if (extra != null) params.putAll(extra);
            params.putAll(HeyboxSigner.sign(path, algorithm));
            Map<String, String> signParams = nativeSignParams(method, profile, params, body);
            String nativeUrlOverride = addNativeSecurityParamsIfNeeded(
                    path, profile, params, signParams);
            if (debugRequest) {
                logTask("api " + debugKind(path) + " request method=" + method
                        + " path=" + path
                        + " profile=" + profile
                        + " algorithm=" + algorithm
                        + " queryOrder=" + orderedKeys(params)
                        + " queryKeys=" + sortedKeys(params)
                        + " bodyKeys=" + sortedKeys(body)
                        + " security=" + securityKeys(params));
            }
            URL url = new URL(nativeUrlOverride == null || nativeUrlOverride.isEmpty()
                    ? endpoint() + path + "?" + encode(params)
                    : nativeUrlOverride);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(12000);
            applyHeaders(connection, profile, useSignInCredentials);
            if (debugRequest) {
                logTask("api " + debugKind(path) + " headers path=" + path
                        + " profile=" + profile
                        + " headers=" + requestHeaderSummary(connection));
                logTask("api " + debugKind(path) + " final url path=" + path
                        + " host=" + url.getHost()
                        + " query=" + querySummary(url));
            }
            if ("POST".equals(method)) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type",
                        "application/x-www-form-urlencoded;charset=utf-8");
                byte[] bytes = encode(body == null ? new LinkedHashMap<>() : body)
                        .getBytes("UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }

            int status = connection.getResponseCode();
            List<String> setCookies = connection.getHeaderFields()
                    .get(SecureStrings.setCookieHeader());
            if (setCookies == null) {
                setCookies = connection.getHeaderFields().get(SecureStrings.setCookieHeaderLower());
            }
            if (mergeResponseCookies) {
                session.mergeCookies(setCookies);
            } else if (debugRequest && setCookies != null && !setCookies.isEmpty()) {
                logTask("api " + debugKind(path) + " isolated response cookies ignored path="
                        + path + " count=" + setCookies.size());
            }
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String contentEncoding = connection.getContentEncoding();
            if (stream != null && contentEncoding != null
                    && "gzip".equalsIgnoreCase(contentEncoding.trim())) {
                stream = new GZIPInputStream(stream);
            }
            String text = read(stream);
            if (debugRequest) {
                logTask("api " + debugKind(path) + " response path=" + path
                        + " http=" + status
                        + " bytes=" + (text == null ? 0 : text.length()));
            }
            if (status < 200 || status >= 300) {
                throw new IllegalStateException(httpErrorMessage(status, text));
            }
            JSONObject json = new JSONObject(text);
            if (debugRequest) {
                logTask("api " + debugKind(path) + " json path=" + path
                        + " status=" + String.valueOf(json.opt("status"))
                        + " keys=" + jsonKeys(json)
                        + " msg=" + trim(first(json.optString("msg"),
                                json.optString("message"), "")));
            }
            Object apiStatus = json.opt("status");
            boolean failed = apiStatus instanceof Number && ((Number) apiStatus).intValue() < 0;
            if (apiStatus instanceof String) {
                String statusText = ((String) apiStatus).trim();
                failed |= "failed".equalsIgnoreCase(statusText)
                        || "fail".equalsIgnoreCase(statusText)
                        || "error".equalsIgnoreCase(statusText)
                        || "login".equalsIgnoreCase(statusText)
                        || "relogin".equalsIgnoreCase(statusText)
                        || "lack_token".equalsIgnoreCase(statusText)
                        || "show_captcha".equalsIgnoreCase(statusText)
                        || "name_verify".equalsIgnoreCase(statusText)
                        || "need_alipay_verify".equalsIgnoreCase(statusText)
                        || "need_bind_phone".equalsIgnoreCase(statusText)
                        || "need_phone_code".equalsIgnoreCase(statusText);
                if ("POST".equals(method) && !statusText.isEmpty()
                        && !"ok".equalsIgnoreCase(statusText)
                        && !"success".equalsIgnoreCase(statusText)) {
                    failed = true;
                }
            }
            if (failed && json.optString("msg").isEmpty()
                    && json.optString("message").isEmpty()) {
                throw new IllegalStateException(statusMessage(apiStatus));
            }
            if (failed) throw new IllegalStateException(
                    first(json.optString("msg"), json.optString("message"), "接口返回失败"));
            postSuccess(callback, json, setCookies);
        } catch (Exception error) {
            if (closed) return;
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            if (debugRequest) {
                logTask("api " + debugKind(path) + " error path=" + path
                        + " profile=" + profile
                        + " algorithm=" + algorithm
                        + " message=" + trim(message));
            }
            postError(callback, message);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void rawRequest(String label, String rawUrl, Map<String, String> headers,
                            Callback callback) {
        HttpURLConnection connection = null;
        String safeLabel = label == null || label.isEmpty() ? "raw" : label;
        try {
            if (closed || Thread.currentThread().isInterrupted()) return;
            if (rawUrl == null || !rawUrl.startsWith("https://api.xiaoheihe.cn/")) {
                throw new IllegalArgumentException("bad replay url");
            }
            URL url = new URL(rawUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(12000);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
                        connection.setRequestProperty(key, value);
                    }
                }
            }
            logTask("api task replay request label=" + safeLabel
                    + " headers=" + requestHeaderSummary(connection)
                    + " query=" + querySummary(url));
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String contentEncoding = connection.getContentEncoding();
            if (stream != null && contentEncoding != null
                    && "gzip".equalsIgnoreCase(contentEncoding.trim())) {
                stream = new GZIPInputStream(stream);
            }
            String text = read(stream);
            logTask("api task replay response label=" + safeLabel
                    + " http=" + status
                    + " bytes=" + (text == null ? 0 : text.length()));
            if (status < 200 || status >= 300) {
                throw new IllegalStateException(httpErrorMessage(status, text));
            }
            JSONObject json = new JSONObject(text);
            logTask("api task replay json label=" + safeLabel
                    + " status=" + String.valueOf(json.opt("status"))
                    + " keys=" + jsonKeys(json)
                    + " msg=" + trim(first(json.optString("msg"),
                            json.optString("message"), "")));
            Object apiStatus = json.opt("status");
            boolean failed = apiStatus instanceof Number && ((Number) apiStatus).intValue() < 0;
            if (apiStatus instanceof String) {
                String statusText = ((String) apiStatus).trim();
                failed = "failed".equalsIgnoreCase(statusText)
                        || "fail".equalsIgnoreCase(statusText)
                        || "error".equalsIgnoreCase(statusText)
                        || "login".equalsIgnoreCase(statusText)
                        || "relogin".equalsIgnoreCase(statusText);
            }
            if (failed) {
                throw new IllegalStateException(trim(first(json.optString("msg"),
                        json.optString("message"), "请求失败")));
            }
            postSuccess(callback, json);
        } catch (Exception error) {
            postError(callback, error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String addNativeSecurityParamsIfNeeded(String path, RequestProfile profile,
                                                   Map<String, String> params,
                                                   Map<String, String> signParams) {
        if (!isOfficialNativeClient(profile)
                || !isNativeSecurityPath(path)) {
            return "";
        }
        if (!ENABLE_NATIVE_SIGNER) {
            logTask("native signer disabled path=" + path);
            return "";
        }
        Map<String, String> nativeParams = NativeSignBridge.sign(
                session.appContext(), session, path, signParams, forceFallbackSigner(profile),
                profile == RequestProfile.OFFICIAL_MOBILE_LOGIN,
                this::logTask);
        if (nativeParams.isEmpty()) {
            logTask("native signer skipped path=" + path
                    + " reason=" + NativeSecuritySigner.unavailableReason());
            return "";
        }
        String nativeUrl = nativeParams.remove(NativeSignService.EXTRA_NATIVE_URL);
        normalizeOfficialTaskParams(path, nativeParams);
        mergeNativeSecurityParams(path, params, nativeParams, profile);
        logTask("native signer applied path=" + path
                + " security=" + securityKeys(params)
                + " urlOverride=" + (nativeUrl != null && !nativeUrl.isEmpty())
                + " useNativeUrl=" + useNativeUrlOverride(profile));
        if (isTaskPath(path)) return "";
        return useNativeUrlOverride(profile) && nativeUrl != null ? nativeUrl : "";
    }

    private void normalizeOfficialTaskParams(String path, Map<String, String> params) {
        if (!isTaskPath(path) || params == null || params.isEmpty()) return;
        params.put("channel", OFFICIAL_TASK_CHANNEL);
        params.put("x_app", OFFICIAL_TASK_APP);
        if (OfficialContext.officialBuildCode() != null
                && !OfficialContext.officialBuildCode().isEmpty()) {
            params.put("build", OfficialContext.officialBuildCode());
        }
        if (OfficialContext.officialVersionName() != null
                && !OfficialContext.officialVersionName().isEmpty()) {
            params.put("version", OfficialContext.officialVersionName());
        }
    }

    private void mergeNativeSecurityParams(String path, Map<String, String> params,
                                           Map<String, String> nativeParams,
                                           RequestProfile profile) {
        if (replaceWithOfficialNativeParams(path, profile, nativeParams)) {
            params.clear();
            for (Map.Entry<String, String> entry : nativeParams.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || key.isEmpty() || value == null) continue;
                params.put(key, value);
            }
            logTask("native signer replaced query params with official set keys="
                    + sortedKeys(params));
            return;
        }
        boolean nativeHasHkey = nativeParams.containsKey(SecureStrings.hkey());
        boolean nativeHasKey = nativeParams.containsKey(SecureStrings.keyParam());
        if (!nativeHasHkey && nativeHasKey) {
            params.put(SecureStrings.time(), nativeParams.get(SecureStrings.time()));
            params.put(SecureStrings.nonce(), nativeParams.get(SecureStrings.nonce()));
            params.put(SecureStrings.hkey(), nativeParams.get(SecureStrings.keyParam()));
            params.put(SecureStrings.keyParam(), nativeParams.get(SecureStrings.keyParam()));
            if (nativeParams.containsKey(SecureStrings.rndParam())) {
                params.put(SecureStrings.rndParam(), nativeParams.get(SecureStrings.rndParam()));
            }
            logTask("native signer promoted key to hkey");
            return;
        }
        for (Map.Entry<String, String> entry : nativeParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isEmpty() || value == null) continue;
            if (!nativeHasHkey
                    && (SecureStrings.time().equals(key) || SecureStrings.nonce().equals(key))) {
                continue;
            }
            if (isNativeSecurityParam(key) || !params.containsKey(key)) {
                params.put(key, value);
            }
        }
        logTask("native signer merged security params with base keys=" + sortedKeys(params));
    }

    private boolean replaceWithOfficialNativeParams(String path, RequestProfile profile,
                                                    Map<String, String> nativeParams) {
        if (!isOfficialNativeClient(profile) || !isNativeSecurityPath(path)
                || nativeParams == null || nativeParams.isEmpty()) {
            return false;
        }
        return hasOfficialNativeSecurity(nativeParams);
    }

    private static boolean hasOfficialNativeSecurity(Map<String, String> nativeParams) {
        return nativeParams != null
                && nativeParams.containsKey(SecureStrings.hkey())
                && nativeParams.containsKey(SecureStrings.nonce())
                && nativeParams.containsKey(SecureStrings.time())
                && nativeParams.containsKey(SecureStrings.heyboxId());
    }

    private void postSuccess(Callback callback, JSONObject json) {
        postSuccess(callback, json, Collections.emptyList());
    }

    private void postSuccess(Callback callback, JSONObject json, List<String> responseCookies) {
        if (callback == null) return;
        List<String> cookies = responseCookies == null
                ? Collections.emptyList() : new ArrayList<>(responseCookies);
        main.post(() -> {
            if (closed) return;
            callback.onResponseCookies(Collections.unmodifiableList(cookies));
            callback.onSuccess(json);
        });
    }

    private void postError(Callback callback, String message) {
        if (callback == null) return;
        main.post(() -> {
            if (!closed) callback.onError(message);
        });
    }

    private static String encode(Map<String, String> params) throws Exception {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null) continue;
            if (query.length() > 0) query.append('&');
            query.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            query.append('=');
            query.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return query.toString();
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, "UTF-8"))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0) result.append(buffer, 0, count);
        }
        return result.toString();
    }

    private static String first(String a, String b, String fallback) {
        if (a != null && !a.isEmpty()) return a;
        if (b != null && !b.isEmpty()) return b;
        return fallback;
    }

    private static String trim(String value) {
        return value.length() > 240 ? value.substring(0, 240) : value;
    }

    private static boolean isTaskPath(String path) {
        return path != null && path.contains("/task/");
    }

    private static boolean isWritePath(String path) {
        String clean = normalizePath(path);
        if (clean.isEmpty()) return false;
        return normalizePath(EndpointProvider.linkLikeCombo()).equals(clean)
                || normalizePath(EndpointProvider.awardLink()).equals(clean)
                || normalizePath(EndpointProvider.favourLink()).equals(clean)
                || normalizePath(EndpointProvider.followUser()).equals(clean)
                || normalizePath(EndpointProvider.unfollowUser()).equals(clean)
                || normalizePath(EndpointProvider.supportComment()).equals(clean)
                || normalizePath(EndpointProvider.createComment()).equals(clean);
    }

    private static String debugKind(String path) {
        return isTaskPath(path) ? "task" : "write";
    }

    private static Map<String, String> nativeSignParams(String method, RequestProfile profile,
                                                        Map<String, String> query,
                                                        Map<String, String> body) {
        Map<String, String> out = new LinkedHashMap<>();
        if (query != null) out.putAll(query);
        if ("POST".equals(method) && isOfficialNativeClient(profile)
                && profile != RequestProfile.OFFICIAL_MOBILE_LOGIN
                && body != null && !body.isEmpty()) {
            for (Map.Entry<String, String> entry : body.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || key.isEmpty() || value == null) continue;
                out.put(key, value);
            }
        }
        return out;
    }

    private void logTask(String message) {
        if (logger != null) logger.log(message);
    }

    private static List<String> sortedKeys(Map<String, String> values) {
        List<String> keys = new ArrayList<>();
        if (values != null) {
            for (String key : values.keySet()) {
                if (key != null) keys.add(key);
            }
        }
        Collections.sort(keys);
        return keys;
    }

    private static List<String> orderedKeys(Map<String, String> values) {
        List<String> keys = new ArrayList<>();
        if (values != null) {
            for (String key : values.keySet()) {
                if (key != null) keys.add(key);
            }
        }
        return keys;
    }

    private static String securityKeys(Map<String, String> params) {
        if (params == null) return "none";
        return "hkey=" + params.containsKey(SecureStrings.hkey())
                + ",nonce=" + params.containsKey(SecureStrings.nonce())
                + ",_time=" + params.containsKey(SecureStrings.time())
                + ",key=" + params.containsKey(SecureStrings.keyParam())
                + ",_rnd=" + params.containsKey(SecureStrings.rndParam());
    }

    private static String jsonKeys(JSONObject object) {
        List<String> keys = new ArrayList<>();
        if (object != null) {
            java.util.Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
        }
        Collections.sort(keys);
        return keys.toString();
    }

    private static String querySummary(URL url) {
        if (url == null || url.getQuery() == null || url.getQuery().isEmpty()) return "empty";
        StringBuilder out = new StringBuilder();
        String[] parts = url.getQuery().split("&");
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            int equals = part.indexOf('=');
            String key = equals >= 0 ? part.substring(0, equals) : part;
            String value = equals >= 0 ? part.substring(equals + 1) : "";
            if (out.length() > 0) out.append(',');
            out.append(key).append("=len").append(value.length());
        }
        return out.length() == 0 ? "empty" : out.toString();
    }

    private static String httpErrorMessage(int status, String text) {
        if (status == 403) return "HTTP 403：请求过于频繁或被接口限制，请稍后重试";
        if (status == 404) return "HTTP 404：接口不存在或暂不可用";
        String compact = text == null ? "" : text
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (compact.isEmpty()) compact = "请求失败";
        return "HTTP " + status + "：" + trim(compact);
    }

    private static String statusMessage(Object status) {
        if (status == null) return "\u63a5\u53e3\u8fd4\u56de\u5931\u8d25";
        String value = String.valueOf(status);
        if ("lack_token".equalsIgnoreCase(value)) {
            return "\u7f3a\u5c11\u5b89\u5168\u4ee4\u724c\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55\u6216\u7a0d\u540e\u518d\u8bd5";
        }
        if ("login".equalsIgnoreCase(value) || "relogin".equalsIgnoreCase(value)) {
            return "\u767b\u5f55\u5df2\u8fc7\u671f\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55";
        }
        if ("show_captcha".equalsIgnoreCase(value)) {
            return "\u9700\u8981\u5b8c\u6210\u9a8c\u8bc1\u540e\u624d\u80fd\u7ee7\u7eed";
        }
        return "\u63a5\u53e3\u8fd4\u56de\u5931\u8d25: " + value;
    }

    private static String endpoint() {
        return EndpointProvider.baseUrl();
    }

    private Map<String, String> baseParams(RequestProfile profile,
                                           boolean useSignInCredentials) {
        if (profile == RequestProfile.MOBILE) return session.mobileCommonParams();
        if (profile == RequestProfile.OFFICIAL_MOBILE_LOGIN) {
            Map<String, String> params = new LinkedHashMap<>(session.officialMobileParams(true));
            params.put(SecureStrings.heyboxId(), "-1");
            params.remove(SecureStrings.userid());
            params.remove(SecureStrings.userId());
            return params;
        }
        if (profile == RequestProfile.OFFICIAL_MOBILE
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE_ENCODED
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_MERGED
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_KEYS
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK_KEYS
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_RAW_COOKIE) {
            if (useSignInCredentials) return session.signInOfficialMobileParams(true);
            return session.officialMobileParams(true);
        }
        if (profile == RequestProfile.OFFICIAL_SPARSE
                || profile == RequestProfile.OFFICIAL_SPARSE_CLIENT) {
            if (useSignInCredentials) return session.signInOfficialMobileParams(false);
            return session.officialMobileParams(false);
        }
        return session.commonParams();
    }

    private void applyHeaders(HttpURLConnection connection, RequestProfile profile,
                              boolean useSignInCredentials) {
        if (profile == RequestProfile.OFFICIAL_MOBILE_LOGIN) {
            HeaderProvider.applyOfficialAnonymous(connection, session);
            return;
        }
        if (profile == RequestProfile.MOBILE) {
            HeaderProvider.applyMobile(connection, session);
            return;
        }
        if (profile == RequestProfile.OFFICIAL_MOBILE
                || profile == RequestProfile.OFFICIAL_SPARSE) {
            if (useSignInCredentials) {
                HeaderProvider.applySignInOfficialMobile(connection, session, false);
                return;
            }
            HeaderProvider.applyOfficialMobile(connection, session, false);
            return;
        }
        if (profile == RequestProfile.OFFICIAL_MOBILE_CLIENT) {
            if (useSignInCredentials) {
                HeaderProvider.applySignInOfficialRequest(connection, session, false);
                return;
            }
            HeaderProvider.applyOfficialRequest(connection, session, false);
            return;
        }
        if (profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE_ENCODED) {
            if (useSignInCredentials) {
                HeaderProvider.applySignInOfficialMinimalRequest(connection, session, false);
                return;
            }
            HeaderProvider.applyOfficialMinimalRequest(connection, session, false);
            return;
        }
        if (profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_MERGED) {
            if (useSignInCredentials) {
                HeaderProvider.applySignInOfficialRequest(connection, session, false);
                return;
            }
            HeaderProvider.applyOfficialRequest(connection, session, false);
            return;
        }
        if (profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_KEYS
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK_KEYS
                || profile == RequestProfile.OFFICIAL_SPARSE_CLIENT) {
            if (useSignInCredentials) {
                HeaderProvider.applySignInOfficialRequest(connection, session, true);
                return;
            }
            HeaderProvider.applyOfficialRequest(connection, session, true);
            return;
        }
        if (profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK) {
            if (useSignInCredentials) {
                HeaderProvider.applySignInOfficialRequest(connection, session, false);
                return;
            }
            HeaderProvider.applyOfficialRequest(connection, session, false);
            return;
        }
        if (profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_RAW_COOKIE) {
            if (useSignInCredentials) {
                HeaderProvider.applySignInOfficialMobileRawCookie(connection, session);
                return;
            }
            HeaderProvider.applyOfficialMobileRawCookie(connection, session);
            return;
        }
        HeaderProvider.apply(connection, session);
    }

    private static boolean isOfficialNativeClient(RequestProfile profile) {
        return profile == RequestProfile.OFFICIAL_MOBILE_CLIENT
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE_ENCODED
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_MERGED
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_KEYS
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK_KEYS
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_RAW_COOKIE
                || profile == RequestProfile.OFFICIAL_MOBILE_LOGIN;
    }

    private static boolean forceFallbackSigner(RequestProfile profile) {
        return profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK_KEYS;
    }

    private static boolean useNativeUrlOverride(RequestProfile profile) {
        return profile == RequestProfile.OFFICIAL_MOBILE_CLIENT
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_MIN_COOKIE_ENCODED
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_KEYS
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_FALLBACK_KEYS
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT_RAW_COOKIE
                || profile == RequestProfile.OFFICIAL_MOBILE_LOGIN;
    }

    private static boolean isNativeSecurityParam(String key) {
        return SecureStrings.hkey().equals(key)
                || SecureStrings.keyParam().equals(key)
                || SecureStrings.nonce().equals(key)
                || SecureStrings.time().equals(key)
                || SecureStrings.rndParam().equals(key);
    }

    private static String requestHeaderSummary(HttpURLConnection connection) {
        try {
            Map<String, List<String>> props = connection.getRequestProperties();
            if (props == null || props.isEmpty()) return "empty";
            List<String> keys = new ArrayList<>();
            for (String key : props.keySet()) {
                if (key != null) keys.add(key);
            }
            Collections.sort(keys);
            StringBuilder out = new StringBuilder();
            for (String key : keys) {
                if (out.length() > 0) out.append(',');
                List<String> values = props.get(key);
                if (SecureStrings.cookieHeader().equalsIgnoreCase(key)) {
                    out.append(key)
                            .append("{keys=")
                            .append(cookieKeySummary(values))
                            .append(",len=")
                            .append(totalValueLength(values))
                            .append('}');
                } else {
                    out.append(key).append("=len").append(totalValueLength(values));
                }
            }
            return out.length() == 0 ? "empty" : out.toString();
        } catch (Throwable error) {
            return "unavailable:" + error.getClass().getSimpleName();
        }
    }

    private static int totalValueLength(List<String> values) {
        if (values == null || values.isEmpty()) return 0;
        int length = 0;
        for (String value : values) {
            if (value != null) length += value.length();
        }
        return length;
    }

    private static String cookieKeySummary(List<String> values) {
        if (values == null || values.isEmpty()) return "none";
        List<String> keys = new ArrayList<>();
        for (String value : values) {
            if (value == null) continue;
            String[] parts = value.split(";");
            for (String part : parts) {
                if (part == null) continue;
                int equals = part.indexOf('=');
                String key = equals >= 0 ? part.substring(0, equals) : part;
                key = key.trim();
                if (!key.isEmpty() && !keys.contains(key)) keys.add(key);
            }
        }
        return keys.isEmpty() ? "none" : keys.toString();
    }

    private static boolean isNativeSecurityPath(String path) {
        return isTaskPath(path) || isMobileLoginPath(path);
    }

    private static boolean isMobileLoginPath(String path) {
        String clean = normalizePath(path);
        return normalizePath(EndpointProvider.mobileLoginCode()).equals(clean)
                || normalizePath(EndpointProvider.mobileLogin()).equals(clean);
    }

    private static String normalizePath(String path) {
        if (path == null) return "";
        String clean = path.trim();
        while (clean.endsWith("/") && clean.length() > 1) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }
}
