package com.ronan.heyboxlite;

final class CheckinRetryPolicy {
    private static final int MAX_RETRIES = 2;

    private CheckinRetryPolicy() {}

    static boolean shouldRetry(CheckinCenterClient.ApiError error, int completedRetries) {
        return error != null && error.retryable() && completedRetries < MAX_RETRIES;
    }

    static long delayMillis(CheckinCenterClient.ApiError error, int completedRetries,
                            long minimumMillis) {
        long base = error != null && error.statusCode == 429 ? 4_000L : 2_000L;
        int shift = Math.max(0, Math.min(3, completedRetries));
        long serverDelay = error == null ? 0L : error.retryAfterSeconds * 1_000L;
        return Math.max(serverDelay, Math.max(minimumMillis, Math.min(15_000L, base << shift)));
    }
}
