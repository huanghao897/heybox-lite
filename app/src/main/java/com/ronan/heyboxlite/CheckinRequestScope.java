package com.ronan.heyboxlite;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import java.util.ArrayList;
import java.util.List;

/** Retains only retries already approved by the existing retry policy. */
final class CheckinRequestScope implements DefaultLifecycleObserver {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Retry> pending = new ArrayList<>();
    private Lifecycle lifecycle;
    private boolean resumed = true;
    private boolean closed;

    CheckinRequestScope() {}

    CheckinRequestScope(Context context) {
        if (context instanceof LifecycleOwner) {
            resumed = false;
            lifecycle = ((LifecycleOwner) context).getLifecycle();
            lifecycle.addObserver(this);
        }
    }

    boolean open() { return !closed; }

    void retry(Runnable action, long delayMillis) {
        if (closed) return;
        Retry retry = new Retry(action, SystemClock.elapsedRealtime() + delayMillis);
        pending.add(retry);
        if (resumed) post(retry);
    }

    void resume() {
        if (closed || resumed) return;
        resumed = true;
        for (Retry retry : pending) post(retry);
    }

    void pause() {
        resumed = false;
        handler.removeCallbacksAndMessages(null);
    }

    void close() {
        closed = true;
        pause();
        pending.clear();
        if (lifecycle != null) lifecycle.removeObserver(this);
        lifecycle = null;
    }

    @Override public void onResume(LifecycleOwner owner) { resume(); }
    @Override public void onPause(LifecycleOwner owner) { pause(); }
    @Override public void onStop(LifecycleOwner owner) { pause(); }
    @Override public void onDestroy(LifecycleOwner owner) { close(); }

    private void post(Retry retry) {
        handler.postDelayed(retry, Math.max(0L, retry.dueAt - SystemClock.elapsedRealtime()));
    }

    private final class Retry implements Runnable {
        final Runnable action;
        final long dueAt;

        Retry(Runnable action, long dueAt) {
            this.action = action;
            this.dueAt = dueAt;
        }

        @Override public void run() {
            if (!closed && resumed && pending.remove(this)) action.run();
        }
    }
}
