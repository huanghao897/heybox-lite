package com.ronan.heyboxlite;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Keeps request work and delivered callbacks within the session that queued them. */
final class ApiRequestDispatcher {
    interface Task {
        void run(long generation);
    }

    private final SessionStore session;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;

    ApiRequestDispatcher(SessionStore session) {
        this.session = session;
    }

    void enqueue(ApiClient.Callback callback, Task task) {
        if (closed) return;
        long generation = session.sessionGeneration();
        try {
            executor.execute(() -> {
                if (!closed && session.isCurrentSession(generation)) task.run(generation);
            });
        } catch (RejectedExecutionException error) {
            // close() may win the race after the initial closed check.
            if (!closed) postError(callback, "请求已关闭", generation);
        }
    }

    boolean isClosed() {
        return closed;
    }

    void close() {
        closed = true;
        main.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    void postSuccess(ApiClient.Callback callback, JSONObject json, long generation) {
        if (callback == null) return;
        main.post(() -> {
            if (!closed && session.isCurrentSession(generation)) callback.onSuccess(json);
        });
    }

    void postError(ApiClient.Callback callback, String message, long generation) {
        if (callback == null) return;
        main.post(() -> {
            if (!closed && session.isCurrentSession(generation)) callback.onError(message);
        });
    }
}
