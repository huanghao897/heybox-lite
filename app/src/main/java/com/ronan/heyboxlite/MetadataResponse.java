package com.ronan.heyboxlite;

import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.util.zip.GZIPInputStream;

/** Bounds small update, configuration and announcement responses before JSON parsing. */
final class MetadataResponse {
    static final int MAX_BYTES = 256 * 1024;
    static final long MAX_DURATION_MS = 15_000L;

    private MetadataResponse() {}

    static String read(HttpURLConnection connection, int status, long startedAt) throws IOException {
        checkDeadline(startedAt);
        String length = connection.getHeaderField("Content-Length");
        if (length != null) {
            try {
                if (Long.parseLong(length.trim()) > MAX_BYTES) throw tooLarge();
            } catch (NumberFormatException ignored) {
                // Missing or invalid lengths still go through the actual-byte limit.
            }
        }
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) return "";
        try (InputStream raw = stream;
             InputStream input = "gzip".equalsIgnoreCase(connection.getContentEncoding())
                     ? new GZIPInputStream(raw) : raw;
             ByteArrayOutputStream output = new ByteArrayOutputStream(4096)) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = input.read(buffer, 0, Math.min(buffer.length, MAX_BYTES - total + 1))) != -1) {
                checkDeadline(startedAt);
                total += count;
                if (total > MAX_BYTES) throw tooLarge();
                output.write(buffer, 0, count);
            }
            checkDeadline(startedAt);
            return output.toString("UTF-8");
        }
    }

    private static IOException tooLarge() {
        return new IOException("服务器元数据超过大小限制，请稍后重试");
    }

    private static void checkDeadline(long startedAt) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()
                || SystemClock.elapsedRealtime() - startedAt >= MAX_DURATION_MS) {
            throw new InterruptedIOException("服务器元数据读取超时，请稍后重试");
        }
    }
}
