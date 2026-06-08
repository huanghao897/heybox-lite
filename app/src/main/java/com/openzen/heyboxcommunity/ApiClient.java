package com.openzen.heyboxcommunity;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ApiClient {
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
        executor.execute(() -> request(path, extra, callback));
    }

    void close() {
        closed = true;
        main.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    private void request(String path, Map<String, String> extra, Callback callback) {
        HttpURLConnection connection = null;
        try {
            if (closed || Thread.currentThread().isInterrupted()) return;
            Map<String, String> params = new HashMap<>(session.commonParams());
            params.putAll(extra);
            params.putAll(HeyboxSigner.sign(path));
            URL url = new URL(endpoint() + path + "?" + encode(params));
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(12000);
            HeaderProvider.apply(connection, session);

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
                throw new IllegalStateException("HTTP " + status + ": " + trim(text));
            }
            JSONObject json = new JSONObject(text);
            Object apiStatus = json.opt("status");
            boolean failed = apiStatus instanceof Number && ((Number) apiStatus).intValue() < 0;
            failed |= apiStatus instanceof String && "failed".equalsIgnoreCase((String) apiStatus);
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

    private static String endpoint() {
        return EndpointProvider.baseUrl();
    }
}
