package com.openzen.heyboxcommunity;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ApiClient {
    enum RequestProfile {
        WEB,
        MOBILE,
        OFFICIAL_MOBILE,
        OFFICIAL_MOBILE_CLIENT,
        OFFICIAL_SPARSE,
        OFFICIAL_SPARSE_CLIENT
    }

    interface Callback {
        void onSuccess(JSONObject body);
        void onError(String message);
    }

    private final SessionStore session;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;

    ApiClient(SessionStore session) {
        this.session = session;
    }

    void get(String path, Map<String, String> extra, Callback callback) {
        if (closed) return;
        executor.execute(() -> request("GET", path, extra, null,
                HeyboxSigner.Algorithm.LEGACY, RequestProfile.WEB, callback));
    }

    void getSigned(String path, Map<String, String> extra,
                   HeyboxSigner.Algorithm algorithm, boolean mobile, Callback callback) {
        if (closed) return;
        executor.execute(() -> request("GET", path, extra, null, algorithm,
                mobile ? RequestProfile.MOBILE : RequestProfile.WEB, callback));
    }

    void getSigned(String path, Map<String, String> extra,
                   HeyboxSigner.Algorithm algorithm, RequestProfile profile,
                   Callback callback) {
        if (closed) return;
        executor.execute(() -> request("GET", path, extra, null, algorithm, profile, callback));
    }

    void postForm(String path, Map<String, String> body, Callback callback) {
        if (closed) return;
        executor.execute(() -> request("POST", path, new HashMap<>(), body,
                HeyboxSigner.Algorithm.LEGACY, RequestProfile.WEB, callback));
    }

    void postForm(String path, Map<String, String> queryExtra,
                  Map<String, String> body, Callback callback) {
        if (closed) return;
        executor.execute(() -> request("POST", path, queryExtra, body,
                HeyboxSigner.Algorithm.LEGACY, RequestProfile.WEB, callback));
    }

    void close() {
        closed = true;
        main.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    private void request(String method, String path, Map<String, String> extra,
                         Map<String, String> body, HeyboxSigner.Algorithm algorithm,
                         RequestProfile profile, Callback callback) {
        HttpURLConnection connection = null;
        try {
            if (closed || Thread.currentThread().isInterrupted()) return;
            Map<String, String> params = new HashMap<>(baseParams(profile));
            if (extra != null) params.putAll(extra);
            params.putAll(HeyboxSigner.sign(path, algorithm));
            URL url = new URL(endpoint() + path + "?" + encode(params));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(12000);
            applyHeaders(connection, profile);
            if ("POST".equals(method)) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type",
                        "application/x-www-form-urlencoded;charset=utf-8");
                byte[] bytes = encode(body == null ? new HashMap<>() : body).getBytes("UTF-8");
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
            session.mergeCookies(setCookies);
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String text = read(stream);
            if (status < 200 || status >= 300) {
                throw new IllegalStateException(httpErrorMessage(status, text));
            }
            JSONObject json = new JSONObject(text);
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
            postSuccess(callback, json);
        } catch (Exception error) {
            if (closed) return;
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            postError(callback, message);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void postSuccess(Callback callback, JSONObject json) {
        main.post(() -> {
            if (!closed) callback.onSuccess(json);
        });
    }

    private void postError(Callback callback, String message) {
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

    private Map<String, String> baseParams(RequestProfile profile) {
        if (profile == RequestProfile.MOBILE) return session.mobileCommonParams();
        if (profile == RequestProfile.OFFICIAL_MOBILE
                || profile == RequestProfile.OFFICIAL_MOBILE_CLIENT) {
            return session.officialMobileParams(true);
        }
        if (profile == RequestProfile.OFFICIAL_SPARSE
                || profile == RequestProfile.OFFICIAL_SPARSE_CLIENT) {
            return session.officialMobileParams(false);
        }
        return session.commonParams();
    }

    private void applyHeaders(HttpURLConnection connection, RequestProfile profile) {
        if (profile == RequestProfile.MOBILE) {
            HeaderProvider.applyMobile(connection, session);
            return;
        }
        if (profile == RequestProfile.OFFICIAL_MOBILE
                || profile == RequestProfile.OFFICIAL_SPARSE) {
            HeaderProvider.applyOfficialMobile(connection, session, false);
            return;
        }
        if (profile == RequestProfile.OFFICIAL_MOBILE_CLIENT
                || profile == RequestProfile.OFFICIAL_SPARSE_CLIENT) {
            HeaderProvider.applyOfficialMobile(connection, session, true);
            return;
        }
        HeaderProvider.apply(connection, session);
    }
}
