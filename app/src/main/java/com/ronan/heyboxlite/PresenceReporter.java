package com.ronan.heyboxlite;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PresenceReporter {
    interface Callback {
        void onResult(AccessStatus status);
    }

    interface Completion {
        void onComplete(boolean success, String warning);
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
                PresenceTransport.Result response = new PresenceTransport(session, authorization)
                        .send(payload);
                AccessStatus status = response.access;
                SYNC.record(SystemClock.elapsedRealtime(), identity, status != null,
                        response.errorMessage);
                if (status == null) {
                    new LocalDiagnosticsLog(session.appContext(),
                            new File(session.appContext().getFilesDir(), "offline-cache"))
                            .log("presence failed: " + response.diagnosticCode);
                }
                if (status != null && includeIdentity && identity.equals(session.userId())) {
                    session.markPresenceIdentityUploaded();
                }
                if (status != null && callback != null) {
                    MAIN.post(() -> callback.onResult(status));
                }
            }
            if (completion != null) {
                boolean hasToken = !authorization.token().isEmpty();
                boolean ready = SYNC.ready(identity, hasToken);
                String warning = SYNC.updateWarning(identity, hasToken);
                MAIN.post(() -> completion.onComplete(ready, warning));
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
}
