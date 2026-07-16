package com.ronan.heyboxlite;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PresenceReporter {
    interface Callback {
        void onResult(AccessStatus status);
    }

    private static final long PING_INTERVAL_MS = 10L * 60L * 1000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static long lastPingAt;

    private PresenceReporter() {}

    static void ping(SessionStore session, ReadingTimeTracker readingTime) {
        ping(session, readingTime, false, null);
    }

    static void ping(SessionStore session, ReadingTimeTracker readingTime, Callback callback) {
        ping(session, readingTime, false, callback);
    }

    static void pingNow(SessionStore session, ReadingTimeTracker readingTime, Callback callback) {
        ping(session, readingTime, true, callback);
    }

    private static synchronized void ping(SessionStore session, ReadingTimeTracker readingTime,
                                          boolean force, Callback callback) {
        long now = System.currentTimeMillis();
        boolean includeIdentity = session != null && session.isLoggedIn()
                && !session.presenceIdentityUploaded();
        if (!force && !includeIdentity && now - lastPingAt < PING_INTERVAL_MS) return;
        lastPingAt = now;
        final String payload = buildPayload(session, readingTime, includeIdentity);
        EXECUTOR.execute(() -> {
            AccessStatus status = request(payload);
            if (status != null && includeIdentity) session.markPresenceIdentityUploaded();
            if (status != null && callback != null) {
                MAIN.post(() -> callback.onResult(status));
            }
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
                body.put("deviceId", session.presenceDeviceIdentifier());
                if (session.isLoggedIn()) {
                    body.put("userId", session.userId());
                }
                if (includeIdentity) {
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

    private static AccessStatus request(String payload) {
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
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = read(stream);
            if (status < 200 || status >= 300) return null;
            return AccessStatus.from(response.isEmpty() ? null : new JSONObject(response));
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            char[] buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) >= 0) value.append(buffer, 0, count);
        }
        return value.toString();
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
