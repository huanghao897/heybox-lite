package com.ronan.heyboxlite;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

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
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private DiagnosticsClient() {}

    static void upload(SessionStore session, String report, Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String safeReport = DiagnosticSanitizer.forUpload(report);
                JSONObject body = new JSONObject();
                body.put("userId", session == null ? "" : session.userId());
                body.put("version", BuildConfig.VERSION_NAME);
                body.put("versionCode", BuildConfig.VERSION_CODE);
                body.put("model", Build.MODEL == null ? "" : Build.MODEL);
                body.put("report", safeReport);
                byte[] payload = body.toString().getBytes(UTF_8);
                connection = (HttpURLConnection) new URL(UpdateChecker.requireTrustedUrl(
                        BuildConfig.DIAGNOSTICS_API_URL)).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(5000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(payload);
                }
                boolean success = connection.getResponseCode() / 100 == 2;
                MAIN.post(() -> callback.onResult(success,
                        success ? "诊断已提交" : "提交失败"));
            } catch (Exception error) {
                MAIN.post(() -> callback.onResult(false, "提交失败：" +
                        (error.getMessage() == null ? error.getClass().getSimpleName()
                                : error.getMessage())));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    static String crashReport(Context context, SessionStore session, String crashLog) {
        StringBuilder report = new StringBuilder("heybox Lite crash report\n");
        report.append("version: ").append(BuildConfig.VERSION_NAME).append(" (")
                .append(BuildConfig.VERSION_CODE).append(")\n");
        report.append("device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append(" / Android ")
                .append(Build.VERSION.RELEASE).append(" api ")
                .append(Build.VERSION.SDK_INT).append('\n');
        report.append("loggedIn: ").append(session != null && session.isLoggedIn()).append('\n');
        report.append("package: ").append(context.getPackageName()).append("\n\n");
        report.append(DiagnosticSanitizer.redact(crashLog));
        return report.toString();
    }

}
