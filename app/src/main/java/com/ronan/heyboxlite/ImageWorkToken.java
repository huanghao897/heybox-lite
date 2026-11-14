package com.ronan.heyboxlite;

import java.util.concurrent.Executor;

/** Cooperative cancellation at disk, network and decoder boundaries (also on API 21). */
final class ImageWorkToken {
    private static final ThreadLocal<ImageWorkToken> CURRENT = new ThreadLocal<>();
    private volatile boolean cancelled;
    private Runnable abort;
    private final Executor abortExecutor;

    ImageWorkToken() { this(ImageNetworkAbort.EXECUTOR); }
    ImageWorkToken(Executor abortExecutor) { this.abortExecutor = abortExecutor; }

    static void enter(ImageWorkToken token) { CURRENT.set(token); }
    static void leave() { CURRENT.remove(); }
    static boolean stopped() {
        ImageWorkToken token = CURRENT.get();
        return token != null && token.cancelled;
    }

    static void abortWith(Runnable action) {
        ImageWorkToken token = CURRENT.get();
        if (token != null) token.setAbort(action);
    }

    private void setAbort(Runnable action) {
        synchronized (this) {
            if (!cancelled) { abort = action; return; }
        }
        if (action != null) abortExecutor.execute(action);
    }

    void cancel() {
        Runnable action;
        synchronized (this) {
            if (cancelled) return;
            cancelled = true;
            action = abort;
            abort = null;
        }
        if (action != null) abortExecutor.execute(action);
    }
}
