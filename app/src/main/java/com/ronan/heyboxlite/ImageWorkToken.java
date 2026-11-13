package com.ronan.heyboxlite;

/** Cooperative cancellation at disk, network and decoder boundaries (also on API 21). */
final class ImageWorkToken {
    private static final ThreadLocal<ImageWorkToken> CURRENT = new ThreadLocal<>();
    private volatile boolean cancelled;
    private Runnable abort;

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

    private synchronized void setAbort(Runnable action) {
        abort = action;
        if (cancelled && action != null) action.run();
    }

    synchronized void cancel() {
        cancelled = true;
        if (abort != null) abort.run();
        abort = null;
    }
}
