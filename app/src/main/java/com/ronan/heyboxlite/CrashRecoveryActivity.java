package com.ronan.heyboxlite;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CrashRecoveryActivity extends Activity {
    private static final int WRITE_LOG_PERMISSION = 9141;
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private CrashRecoveryView view;
    private String report;
    private boolean saving;

    @Override protected void onCreate(Bundle state) {
        CrashReporter.setProcess(getPackageName() + ":crash");
        super.onCreate(state);
        SessionStore session = new SessionStore(this);
        report = CrashReporter.latestCrashReport(this);
        view = new CrashRecoveryView(this, session, report,
                this::reopenApp, this::closeApp, this::saveReport);
        setContentView(view);
        Compat.colorSystemBars(getWindow(), ThemeTokens.of(session.darkMode(), 0, 0).background);
        getWindow().getDecorView().setSystemUiVisibility(Compat.fullscreenFlags());
        CrashUploads.schedule(this);
    }

    private void saveReport() {
        if (saving) return;
        if (DiagnosticsExporter.needsLegacyWritePermission(this)) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    WRITE_LOG_PERMISSION);
            return;
        }
        saving = true;
        view.status("正在保存");
        String name = "heybox-lite-crash-" + new SimpleDateFormat(
                "yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt";
        files.execute(() -> {
            String path = DiagnosticsExporter.save(this, name, report);
            runOnUiThread(() -> {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                saving = false;
                view.status(path == null ? "保存失败，日志仍保留在本机" : "已保存至 Download/heyboxlite");
            });
        });
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions,
                                                     int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == WRITE_LOG_PERMISSION && grants.length > 0
                && grants[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) saveReport();
    }

    private void reopenApp() {
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
        super.onDestroy();
    }
}
