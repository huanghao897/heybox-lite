package com.openzen.heyboxcommunity;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PresenceReporter {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static long lastPingAt;

    private PresenceReporter() {}

    static void ping() {
        long now = System.currentTimeMillis();
        if (now - lastPingAt < 60_000L) return;
        lastPingAt = now;
        EXECUTOR.execute(PresenceReporter::request);
    }

    private static void request() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(presenceUrl());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setDoOutput(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent",
                    "heybox-Lite/" + BuildConfig.VERSION_NAME);
            connection.getResponseCode();
        } catch (Exception ignored) {
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
