package com.ronan.heyboxlite;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

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

    interface Completion {
        void onComplete(boolean success);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final PresenceSyncState SYNC = new PresenceSyncState();

    private PresenceReporter() {}

    static void ping(SessionStore session, ReadingTimeTracker readingTime) {
        ping(session, readingTime, false, null, null);
    }

    static void ping(SessionStore session, ReadingTimeTracker readingTime, Callback callback) {
        ping(session, readingTime, false, callback, null);
    }

    static void prepareUpdate(SessionStore session, Completion completion) {
        // This uses the heartbeat queue, so neither manual nor startup checks
        // can overtake registration or an account change.
        ping(session, null, false, null, completion);
    }

    static void pingNow(SessionStore session, ReadingTimeTracker readingTime, Callback callback) {
        ping(session, readingTime, true, callback, null);
    }

    private static void ping(SessionStore session, ReadingTimeTracker readingTime,
                             boolean force, Callback callback, Completion completion) {
        String identity = session.isLoggedIn() ? session.userId() : "";
        boolean includeIdentity = session.isLoggedIn()
                && !session.presenceIdentityUploaded();
        final String payload = buildPayload(session, readingTime, includeIdentity);
        EXECUTOR.execute(() -> {
            DeviceAuthorizationStore authorization = new DeviceAuthorizationStore(
                    session.appContext());
            if (SYNC.shouldSend(SystemClock.elapsedRealtime(), identity,
                    !authorization.token().isEmpty(), force)) {
                AccessStatus status = request(session, payload, true);
                SYNC.record(SystemClock.elapsedRealtime(), identity, status != null);
                if (status != null && includeIdentity && identity.equals(session.userId())) {
                    session.markPresenceIdentityUploaded();
                }
                if (status != null && callback != null) {
                    MAIN.post(() -> callback.onResult(status));
                }
            }
            if (completion != null) {
                boolean ready = SYNC.ready(identity, !authorization.token().isEmpty());
                MAIN.post(() -> completion.onComplete(ready));
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

    private static AccessStatus request(SessionStore session, String payload,
                                        boolean retryWithoutToken) {
        HttpURLConnection connection = null;
        try {
            DeviceAuthorizationStore authorization = new DeviceAuthorizationStore(
                    session.appContext());
            URL url = new URL(presenceUrl());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent",
                    "heybox-Lite/" + BuildConfig.VERSION_NAME);
            authorization.apply(connection, session);
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
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED && retryWithoutToken
                    && !authorization.token().isEmpty()) {
                authorization.clear();
                return request(session, payload, false);
            }
            if (status < 200 || status >= 300) return null;
            JSONObject body = response.isEmpty() ? new JSONObject() : new JSONObject(response);
            String deviceToken = body.optString("deviceToken", "");
            if (!deviceToken.isEmpty()) authorization.save(deviceToken);
            return AccessStatus.from(body);
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
