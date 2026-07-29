package com.ronan.heyboxlite;

import android.os.SystemClock;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class HeyboxGatewayClient {
    static final class GatewayException extends Exception {
        final int status;
        final String code;

        GatewayException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code == null ? "" : code;
        }

        GatewayException(String message, Throwable cause) {
            super(message, cause);
            this.status = 0;
            this.code = "GATEWAY_UNAVAILABLE";
        }
    }

    static final class Result {
        final JSONObject body;
        final long roundTripMs;
        final long gatewayMs;
        final long upstreamMs;

        Result(JSONObject body, long roundTripMs, long gatewayMs, long upstreamMs) {
            this.body = body;
            this.roundTripMs = roundTripMs;
            this.gatewayMs = gatewayMs;
            this.upstreamMs = upstreamMs;
        }
    }

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Map<String, String> OPERATIONS = operations();

    private HeyboxGatewayClient() {}

    static String operationFor(String path) {
        String value = OPERATIONS.get(path == null ? "" : path);
        return value == null ? "" : value;
    }

    static Result get(SessionStore session, String path, Map<String, String> params)
            throws GatewayException {
        String operation = operationFor(path);
        if (operation.isEmpty()) {
            throw new GatewayException(400, "UNSUPPORTED_OPERATION", "不支持该中转操作");
        }

        HttpURLConnection connection = null;
        long startedAt = SystemClock.elapsedRealtime();
        try {
            JSONObject payload = new JSONObject();
            payload.put("operation", operation);
            payload.put("params", new JSONObject(params == null
                    ? Collections.emptyMap() : params));
            payload.put("cookie", session == null ? "" : session.getCookie());
            payload.put("clientVersion", BuildConfig.VERSION_NAME);
            payload.put("clientVersionCode", BuildConfig.VERSION_CODE);
            byte[] bytes = payload.toString().getBytes(UTF_8);

            URL url = new URL(UpdateChecker.requireTrustedUrl(
                    BuildConfig.HEYBOX_GATEWAY_API_URL));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("User-Agent",
                    "heybox-Lite/" + BuildConfig.VERSION_NAME);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            JSONObject response = new JSONObject(read(stream));
            if (status < 200 || status >= 300) {
                String message = response.optString("error", "中转服务请求失败");
                throw new GatewayException(status,
                        response.optString("code", "GATEWAY_ERROR"), message);
            }
            JSONObject body = response.optJSONObject("body");
            if (body == null) {
                throw new GatewayException(status, "INVALID_RESPONSE",
                        "中转服务返回格式异常");
            }
            JSONObject timing = response.optJSONObject("timing");
            long gatewayMs = timing == null ? -1L
                    : Math.max(-1L, timing.optLong("gatewayMs", -1L));
            long upstreamMs = timing == null ? -1L
                    : Math.max(-1L, timing.optLong("upstreamMs", -1L));
            return new Result(body,
                    Math.max(0L, SystemClock.elapsedRealtime() - startedAt),
                    gatewayMs, upstreamMs);
        } catch (GatewayException error) {
            throw error;
        } catch (Exception error) {
            throw new GatewayException("暂时无法连接中转服务", error);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static Map<String, String> operations() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(EndpointProvider.feeds(), "feed.list");
        values.put(EndpointProvider.linkTreeV2(), "post.detail");
        values.put(EndpointProvider.subComments(), "comment.list");
        values.put(EndpointProvider.search(), "search.links");
        return Collections.unmodifiableMap(values);
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "{}";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) >= 0) result.append(buffer, 0, count);
        }
        return result.toString();
    }
}
