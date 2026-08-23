package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

final class UpdateInstaller {
    interface Host {
        void showToast(String message);
    }

    private final Activity activity;
    private final SessionStore session;
    private final LocalCache localCache;
    private final Handler mainHandler;
    private final LiteDialogPresenter dialogs;
    private final ThemeTokens tokens;
    private final Host host;

    UpdateInstaller(Activity activity, SessionStore session, LocalCache localCache,
                    Handler mainHandler, LiteDialogPresenter dialogs,
                    ThemeTokens tokens, Host host) {
        this.activity = activity;
        this.session = session;
        this.localCache = localCache;
        this.mainHandler = mainHandler;
        this.dialogs = dialogs;
        this.tokens = tokens;
        this.host = host;
    }

    void openExternal(String url) {
        try {
            this.activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException | SecurityException error) {
            this.host.showToast("无法打开链接");
        }
    }

    void openUpdate(String url) {
        String trusted = UpdateChecker.trustedUrlOrEmpty(url);
        if (TextUtils.isEmpty(trusted)) {
            this.host.showToast("没有可用下载链接");
            return;
        }
        startDownload(trusted);
    }

    private void startDownload(String url) {
        if (Build.VERSION.SDK_INT >= 26
                && !this.activity.getPackageManager().canRequestPackageInstalls()) {
            this.dialogs.show("需要安装权限",
                    "为了在 App 内完成更新，需要先允许 heybox Lite 安装未知来源应用。"
                            + "授权后请回到 App 再点一次下载。",
                    "去授权", this::openUnknownSourcesSettings,
                    "取消", null, null, null);
            return;
        }

        LinearLayout box = new LinearLayout(this.activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable background = UiComponents.round(
                this.activity, this.tokens.panel, 14, uiScale());
        background.setStroke(Math.max(1, dp(1)), this.tokens.hairline);
        Compat.setBackground(box, background);
        TextView title = boldText("正在下载更新", 16.0f, this.tokens.text);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView state = text("准备下载...", 12.0f, this.tokens.muted);
        addTop(box, state, 8);
        ProgressBar progress = new ProgressBar(
                this.activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        if (Build.VERSION.SDK_INT >= 21) {
            progress.setProgressTintList(ColorStateList.valueOf(this.tokens.primary));
            progress.setProgressBackgroundTintList(ColorStateList.valueOf(
                    this.session.darkMode()
                            ? Color.rgb(65, 65, 65) : Color.rgb(224, 224, 224)));
        }
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(8));
        barParams.topMargin = dp(12);
        box.addView(progress, barParams);
        TextView hint = text("下载完成后会打开系统安装器", 11.0f, this.tokens.muted);
        hint.setGravity(Gravity.CENTER);
        addTop(box, hint, 10);

        AlertDialog dialog = new AlertDialog.Builder(this.activity).setView(box).create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            dialog.getWindow().setDimAmount(this.session.darkMode() ? 0.46f : 0.32f);
            int width = this.activity.getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(
                    Math.max(dp(220), Math.min(width - dp(28), dp(360))), -2);
        }
        new Thread(() -> download(url, progress, state, dialog),
                "heybox-update-download").start();
    }

    private void download(String url, ProgressBar progress,
                          TextView state, AlertDialog dialog) {
        try {
            File ready = UpdateDownloadClient.download(this.activity, url,
                    BuildConfig.VERSION_NAME, (percent, indeterminate) ->
                            this.mainHandler.post(() -> {
                                if (this.activity.isFinishing()) return;
                                progress.setIndeterminate(indeterminate);
                                state.setText(indeterminate
                                        ? "正在下载..." : "已下载" + percent + "%");
                                if (!indeterminate) progress.setProgress(percent);
                            }));
            this.mainHandler.post(() -> {
                if (this.activity.isFinishing()) return;
                progress.setIndeterminate(false);
                progress.setProgress(100);
                state.setText("下载完成，正在打开安装器...");
                if (dialog.isShowing()) dialog.dismiss();
                install(ready);
            });
        } catch (IOException | RuntimeException error) {
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            this.localCache.log("update download failed: " + message);
            this.mainHandler.post(() -> {
                if (dialog.isShowing()) dialog.dismiss();
                if (!this.activity.isFinishing()) {
                    this.dialogs.show("下载失败", message, "打开浏览器下载",
                            () -> openExternal(url), "知道了", null, null, null);
                }
            });
        }
    }

    private void install(File apk) {
        if (apk == null || !apk.isFile()) {
            this.host.showToast("安装包不存在");
            return;
        }
        if (!UpdateApkVerifier.isTrusted(this.activity, apk)) {
            if (!apk.delete()) apk.deleteOnExit();
            this.localCache.log("update rejected reason=signature_or_package_mismatch");
            this.dialogs.show("安装包校验失败",
                    "下载的安装包不是 heybox Lite 官方签名，已停止安装。",
                    "知道了", null, null, null, null, null);
            return;
        }
        try {
            Uri uri = UpdateApkProvider.uriFor(apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                intent.setClipData(ClipData.newUri(
                        this.activity.getContentResolver(), apk.getName(), uri));
            }
            this.activity.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException
                 | IllegalArgumentException error) {
            this.localCache.log("update install failed: "
                    + error.getClass().getSimpleName() + " " + error.getMessage());
            this.dialogs.show("无法打开安装器",
                    "安装包已经下载完成，但系统安装器没有响应。可以改用浏览器下载后手动安装",
                    "知道了", null, null, null, null, null);
        }
    }

    private void openUnknownSourcesSettings() {
        Intent appSettings = new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES",
                Uri.parse("package:" + this.activity.getPackageName()));
        if (tryOpenSettings(appSettings)
                || tryOpenSettings(new Intent("android.settings.SECURITY_SETTINGS"))) return;
        this.host.showToast("无法打开系统设置，请手动允许安装未知应用");
    }

    private boolean tryOpenSettings(Intent intent) {
        try {
            this.activity.startActivity(intent);
            return true;
        } catch (RuntimeException error) {
            this.localCache.log("system settings unavailable action=" + intent.getAction()
                    + " error=" + error.getClass().getSimpleName());
            return false;
        }
    }

    private TextView boldText(String value, float size, int color) {
        TextView view = text(value, size, color);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(
                this.activity, value, size, color, this.session.textScale() / 100.0f);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private void addTop(LinearLayout parent, TextView view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(view, params);
    }

    private int dp(int value) {
        return UiComponents.dp(this.activity, value, uiScale());
    }

    private float uiScale() {
        return this.session.uiScale() / 100.0f;
    }
}
