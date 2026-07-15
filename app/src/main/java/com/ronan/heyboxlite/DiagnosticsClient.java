package com.ronan.heyboxlite;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

    static void selfTest(Context context, SessionStore session, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            StringBuilder report = new StringBuilder();
            report.append("heybox Lite self-test\n");
            report.append("version: ").append(BuildConfig.VERSION_NAME).append(" (")
                    .append(BuildConfig.VERSION_CODE).append(")\n");
            report.append("device: ").append(Build.MODEL).append(" / Android ")
                    .append(Build.VERSION.RELEASE).append('\n');
            report.append("login: ").append(session != null && session.isLoggedIn()
                    ? "ok" : "guest").append('\n');
            report.append("network: ").append(networkAvailable(app) ? "ok" : "offline").append('\n');
            report.append("cache: ").append(cacheWritable(app) ? "ok" : "failed").append('\n');
            boolean server = endpointAvailable();
            report.append("server: ").append(server ? "ok" : "failed").append('\n');
            String value = report.toString();
            MAIN.post(() -> callback.onResult(server, value));
        });
    }

    static void upload(SessionStore session, String report, Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject body = new JSONObject();
                body.put("userId", session == null ? "" : session.userId());
                body.put("version", BuildConfig.VERSION_NAME);
                body.put("versionCode", BuildConfig.VERSION_CODE);
                body.put("model", Build.MODEL == null ? "" : Build.MODEL);
                body.put("report", report == null ? "" : report);
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

    private static boolean networkAvailable(Context context) {
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    private static boolean cacheWritable(Context context) {
        File test = new File(context.getCacheDir(), "self-test.tmp");
        byte[] expected = "ok".getBytes(UTF_8);
        try (FileOutputStream output = new FileOutputStream(test)) {
            output.write(expected);
        } catch (Exception error) {
            return false;
        }
        try (FileInputStream input = new FileInputStream(test)) {
            return input.read() == 'o' && input.read() == 'k';
        } catch (Exception error) {
            return false;
        } finally {
            test.delete();
        }
    }

    private static boolean endpointAvailable() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(UpdateChecker.requireTrustedUrl(
                    BuildConfig.CONFIG_API_URL)).openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            return connection.getResponseCode() / 100 == 2;
        } catch (Exception error) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
