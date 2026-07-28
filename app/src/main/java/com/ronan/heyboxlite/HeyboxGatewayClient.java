package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HeyboxGatewayClient {
    static final class Result {
        final JSONObject body;
        final List<String> setCookies;

        Result(JSONObject body, List<String> setCookies) {
            this.body = body;
            this.setCookies = setCookies;
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
            throws Exception {
        String operation = operationFor(path);
        if (operation.isEmpty()) throw new IllegalArgumentException("unsupported gateway operation");

        JSONObject payload = new JSONObject();
        payload.put("operation", operation);
        payload.put("params", new JSONObject(params == null
                ? Collections.emptyMap() : params));
        payload.put("cookie", session == null ? "" : session.getCookie());
        payload.put("clientVersion", BuildConfig.VERSION_NAME);
        payload.put("clientVersionCode", BuildConfig.VERSION_CODE);
        byte[] bytes = payload.toString().getBytes(UTF_8);

        HttpURLConnection connection = null;
        try {
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
                throw new IllegalStateException(message);
            }
            JSONObject body = response.optJSONObject("body");
            if (body == null) throw new IllegalStateException("中转服务返回格式异常");
            JSONArray cookies = response.optJSONArray("setCookies");
            List<String> setCookies = new ArrayList<>();
            if (cookies != null) {
                for (int index = 0; index < cookies.length(); index++) {
                    String value = cookies.optString(index, "");
                    if (!value.isEmpty()) setCookies.add(value);
                }
            }
            return new Result(body, setCookies);
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
