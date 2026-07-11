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
    private static final long PING_INTERVAL_MS = 10L * 60L * 1000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static long lastPingAt;

    private PresenceReporter() {}

    static void ping(SessionStore session, ReadingTimeTracker readingTime) {
        long now = System.currentTimeMillis();
        boolean includeIdentity = session != null && session.isLoggedIn()
                && !session.presenceIdentityUploaded();
        if (!includeIdentity && now - lastPingAt < PING_INTERVAL_MS) return;
        lastPingAt = now;
        final String payload = buildPayload(session, readingTime, includeIdentity);
        EXECUTOR.execute(() -> {
            if (request(payload) && includeIdentity) session.markPresenceIdentityUploaded();
        });
    }

    private static String buildPayload(SessionStore session, ReadingTimeTracker readingTime,
                                       boolean includeIdentity) {
        try {
            JSONObject body = new JSONObject();
            if (readingTime != null) {
                body.put("readingTimeSeconds", readingTime.stats().totalMs() / 1000L);
            }
            if (session != null) {
                if (session.isLoggedIn()) {
                    body.put("userId", session.userId());
                }
                if (includeIdentity) {
                    body.put("deviceId", session.deviceIdentifier());
                    body.put("username", session.userName());
                    body.put("avatar", session.avatar());
                }
            }
            body.put("version", BuildConfig.VERSION_NAME);
            body.put("versionCode", BuildConfig.VERSION_CODE);
            if (includeIdentity) {
                body.put("model", Build.MODEL == null ? "" : Build.MODEL);
                body.put("os", Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE);
            }
            return body.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean request(String payload) {
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
            int status = connection.getResponseCode();
            return status >= 200 && status < 300;
        } catch (Exception ignored) {
            return false;
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
