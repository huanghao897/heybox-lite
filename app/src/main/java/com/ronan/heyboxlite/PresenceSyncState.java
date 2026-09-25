package com.ronan.heyboxlite;

/** Used only on the presence executor; queued checks share one registration attempt. */
final class PresenceSyncState {
    static final long INTERVAL_MS = 10L * 60L * 1000L;
    static final long RETRY_MS = 30_000L;

    private long lastAttemptAt = -1L;
    private String lastIdentity = "";
    private boolean succeeded;

    boolean shouldSend(long now, String identity, boolean hasToken, boolean force) {
        if (force || lastAttemptAt < 0L || !lastIdentity.equals(identity)) return true;
        long elapsed = now - lastAttemptAt;
        if (!succeeded) return elapsed >= RETRY_MS;
        return !hasToken || elapsed >= INTERVAL_MS;
    }

    void record(long now, String identity, boolean success) {
        lastAttemptAt = now;
        lastIdentity = identity;
        succeeded = success;
    }

    boolean ready(String identity, boolean hasToken) {
        return succeeded && hasToken && lastIdentity.equals(identity);
    }
}
