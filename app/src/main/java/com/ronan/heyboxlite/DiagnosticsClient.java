package com.ronan.heyboxlite;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class DiagnosticsClient {
    interface Callback {
        void onResult(boolean success, String text);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int MAX_REPLY_BYTES = 4096;

    private DiagnosticsClient() {}

    static void upload(SessionStore session, String report, Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        EXECUTOR.execute(() -> {
            try {
                boolean success = send(session == null ? "" : session.userId(),
                        report, DiagnosticSource.MANUAL);
                main.post(() -> callback.onResult(success,
                        success ? "诊断已提交" : "提交失败"));
            } catch (Exception error) {
                main.post(() -> callback.onResult(false, "提交失败，请稍后重试"));
            }
        });
    }

    static boolean send(String userId, String report, DiagnosticSource source) throws IOException {
        HttpURLConnection connection = null;
        try {
            JSONObject body = new JSONObject();
            body.put("userId", userId);
            body.put("version", BuildConfig.VERSION_NAME);
            body.put("versionCode", BuildConfig.VERSION_CODE);
            body.put("model", Build.MODEL == null ? "" : Build.MODEL);
            body.put("source", source.value);
            body.put("report", DiagnosticSanitizer.forUpload(report));
            byte[] payload = body.toString().getBytes(UTF_8);
            connection = (HttpURLConnection) new URL(UpdateChecker.requireTrustedUrl(
                    BuildConfig.DIAGNOSTICS_API_URL)).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(5000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            if (connection.getResponseCode() / 100 != 2) return false;
            try (InputStream input = connection.getInputStream()) {
                return acceptedResponse(input);
            }
        } catch (org.json.JSONException error) {
            throw new IOException("Cannot encode diagnostic report", error);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static boolean acceptedResponse(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > MAX_REPLY_BYTES) return false;
            bytes.write(buffer, 0, count);
        }
        try {
            JSONObject reply = new JSONObject(new String(bytes.toByteArray(), UTF_8));
            return Boolean.TRUE.equals(reply.opt("ok"));
        } catch (org.json.JSONException error) {
            return false;
        }
    }
}
