package com.openzen.heyboxcommunity;

import android.os.Build;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PresenceReporter {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static long lastPingAt;

    private PresenceReporter() {}

    static void ping(SessionStore session) {
        long now = System.currentTimeMillis();
        if (now - lastPingAt < 60_000L) return;
        lastPingAt = now;
        final String payload = buildPayload(session);
        EXECUTOR.execute(() -> request(payload));
    }

    /** 仅上报公开身份（ID/昵称/头像）与版本机型，不携带任何 Cookie/凭据。 */
    private static String buildPayload(SessionStore session) {
        try {
            JSONObject body = new JSONObject();
            if (session != null) {
                body.put("deviceId", session.deviceIdentifier());
                if (session.isLoggedIn()) {
                    body.put("userId", session.userId());
                    body.put("username", session.userName());
                    body.put("avatar", session.avatar());
                }
            }
            body.put("version", BuildConfig.VERSION_NAME);
            body.put("versionCode", BuildConfig.VERSION_CODE);
            body.put("model", Build.MODEL == null ? "" : Build.MODEL);
            body.put("os", Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE);
            return body.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void request(String payload) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(presenceUrl());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent",
                    "heybox-Lite/" + BuildConfig.VERSION_NAME);
            if (payload == null || payload.isEmpty()) {
                connection.setDoOutput(false);
            } else {
                byte[] bytes = payload.getBytes("UTF-8");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type",
                        "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                OutputStream output = connection.getOutputStream();
                output.write(bytes);
                output.close();
            }
            connection.getResponseCode();
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String presenceUrl() throws Exception {
        URI source = new URI(UpdateChecker.requireTrustedUrl(BuildConfig.UPDATE_API_URL));
        String authority = source.getRawAuthority();
        if (authority == null || authority.isEmpty()) {
            throw new IllegalArgumentException("missing update host");
        }
        return source.getScheme() + "://" + authority + "/api/presence";
    }
}
