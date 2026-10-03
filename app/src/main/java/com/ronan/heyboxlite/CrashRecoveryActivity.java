package com.ronan.heyboxlite;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.ComponentActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CrashRecoveryActivity extends ComponentActivity {
    private static final int WRITE_LOG_PERMISSION = 9141;
    private static final String SAVED_STATUS = "crash.save-status";
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private ComposeCrashRecoveryHost view;
    private String report;
    private boolean saving;
    private String status = "日志已保存，将自动上报";

    @Override protected void onCreate(Bundle state) {
        CrashReporter.setProcess(getPackageName() + ":crash");
        super.onCreate(state);
        SessionStore session = new SessionStore(this);
        report = CrashReporter.latestCrashReport(this);
        view = new ComposeCrashRecoveryHost(this, session, report,
                this::reopenApp, this::closeApp, this::saveReport);
        if (state != null) updateStatus(state.getString(SAVED_STATUS, status), false);
        Compat.colorSystemBars(getWindow(), ThemeTokens.of(session.darkMode(), 0, 0).background);
        getWindow().getDecorView().setSystemUiVisibility(Compat.fullscreenFlags());
        CrashUploads.schedule(this);
    }

    private void saveReport() {
        if (saving || isFinishing() || isDestroyed()) return;
        saving = true;
        if (DiagnosticsExporter.needsLegacyWritePermission(this)) {
            updateStatus("等待存储权限", true);
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    WRITE_LOG_PERMISSION);
            return;
        }
        updateStatus("正在保存", true);
        String name = "heybox-lite-crash-" + new SimpleDateFormat(
                "yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt";
        files.execute(() -> {
            String path = DiagnosticsExporter.save(this, name, report);
            runOnUiThread(() -> {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                updateStatus(path == null ? "保存失败，日志仍保留在本机"
                        : "已保存至 Download/heyboxlite", false);
            });
        });
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions,
                                                     int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request != WRITE_LOG_PERMISSION || view == null || isFinishing() || isDestroyed()) return;
        saving = false;
        if (grants.length > 0
                && grants[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) saveReport();
        else updateStatus("未获存储权限，日志仍保留在本机", false);
    }

    private void updateStatus(String value, boolean busy) {
        if (view == null || isFinishing() || isDestroyed()) return;
        status = value;
        saving = busy;
        view.status(value, busy);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        // An interrupted export must not restore a permanently disabled save button.
        if (!saving) state.putString(SAVED_STATUS, status);
        super.onSaveInstanceState(state);
    }

    private void reopenApp() {
        if (isFinishing() || isDestroyed()) return;
        Intent intent = new Intent(this, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void closeApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) finishAndRemoveTask();
        else finish();
    }

    @Override protected void onDestroy() {
        files.shutdown();
        if (view != null) {
            view.close();
            view = null;
        }
        super.onDestroy();
    }
}
