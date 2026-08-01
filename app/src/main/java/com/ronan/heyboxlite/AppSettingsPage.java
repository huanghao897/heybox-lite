package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class AppSettingsPage {
    interface Host {
        LinearLayout openPage(String key, String title);

        void showDialog(String title, String message, String positiveText,
                        Runnable positiveAction, String negativeText,
                        Runnable negativeAction, String neutralText,
                        Runnable neutralAction);

        void reloadFeed(boolean resetPaging);

        void exportDiagnostics();

        void uploadDiagnostics();

        void showLogin();

        void showToast(String message);

        FrameLayout content();
    }

    private final Activity activity;
    private final SessionStore session;
    private final LocalCache localCache;
    private final SettingsUi settingsUi;
    private final ThemeTokens tokens;
    private final CrownScrollController crownScrollController;
    private final CacheMaintenance cacheMaintenance;
    private final Host host;

    AppSettingsPage(Activity activity, SessionStore session, LocalCache localCache,
                    SettingsUi settingsUi, ThemeTokens tokens,
                    CrownScrollController crownScrollController,
                    CacheMaintenance cacheMaintenance, Host host) {
        this.activity = activity;
        this.session = session;
        this.localCache = localCache;
        this.settingsUi = settingsUi;
        this.tokens = tokens;
        this.crownScrollController = crownScrollController;
        this.cacheMaintenance = cacheMaintenance;
        this.host = host;
    }

    void showContentAndCache() {
        LinearLayout page = this.host.openPage("app_settings", "内容与缓存");
        this.settingsUi.addSection(page, "浏览与交互");
        LinearLayout panel = this.settingsUi.list();
        addTop(panel, toggle("无图模式", this.session.noImage(), value -> {
            this.session.setNoImage(value);
            this.host.reloadFeed(false);
        }), 0);
        SettingsUi.Entry[] networkEntry = new SettingsUi.Entry[1];
        networkEntry[0] = this.settingsUi.addEntry(panel, "网络模式", null,
                networkModeLabel(), R.drawable.il_globe,
                () -> showNetworkModePicker(networkEntry[0]));
        addTop(panel, toggle("表冠滚动", this.session.crownScrollEnabled(), value -> {
            this.session.setCrownScrollEnabled(value);
            if (!value) this.crownScrollController.reset();
        }), 0);
        this.settingsUi.addRangeEntry(panel, "滚动速度", "%", R.drawable.il_scroll,
                CrownScrollController.MIN_SPEED_PERCENT,
                CrownScrollController.MAX_SPEED_PERCENT, 5,
                this.session.crownScrollSpeed(), this.session::setCrownScrollSpeed, null);
        addTop(panel, toggle("右滑返回上一级", this.session.shellBackSwipe(),
                this.session::setShellBackSwipe), 0);
        addTop(panel, toggle("退出确认", this.session.confirmExitOnBack(),
                this.session::setConfirmExitOnBack), 0);
        addTop(panel, toggle("记住帖子阅读位置", this.session.rememberDetailScroll(),
                this.session::setRememberDetailScroll), 0);
        this.settingsUi.addChoiceEntry(panel, "自动清理", R.drawable.il_cleanup,
                new String[]{"关闭", "30 天"}, this.session.autoOfflineCleanup() ? 1 : 0,
                value -> {
                    boolean enabled = value == 1;
                    this.session.setAutoOfflineCleanup(enabled);
                    if (enabled) this.cacheMaintenance.pruneOffline(null);
                }, null);
        addTop(panel, toggle("双击评论回复", this.session.doubleTapCommentReply(),
                this.session::setDoubleTapCommentReply), 0);
        page.addView(panel);

        this.settingsUi.addSection(page, "内容过滤");
        LinearLayout filter = this.settingsUi.list();
        filter.setPadding(dp(12), dp(12), dp(12), dp(12));
        EditText keywords = new EditText(this.activity);
        keywords.setText(this.session.blockKeywords());
        keywords.setHint("屏蔽关键词，用逗号分隔");
        keywords.setTextColor(this.tokens.text);
        keywords.setHintTextColor(this.tokens.subtle);
        keywords.setTextSize(sp(13.0f));
        keywords.setSingleLine(false);
        keywords.setMinLines(3);
        keywords.setMaxLines(3);
        keywords.setMinHeight(dp(78));
        keywords.setGravity(Gravity.TOP);
        keywords.setPadding(dp(12), dp(10), dp(12), dp(10));
        int fieldColor = this.session.darkMode()
                ? Color.rgb(16, 16, 17) : this.tokens.panelElevated;
        Compat.setBackground(keywords, UiComponents.round(
                this.activity, fieldColor, 9, uiScale()));
        filter.addView(keywords, new LinearLayout.LayoutParams(-1, -2));
        Button save = commandButton("保存内容过滤");
        save.setOnClickListener(view -> {
            this.session.setBlockKeywords(keywords.getText().toString());
            this.host.reloadFeed(true);
            this.host.showToast("内容过滤已保存");
        });
        addTop(filter, save, 10);
        page.addView(filter);

        this.settingsUi.addSection(page, "维护");
        LinearLayout maintain = this.settingsUi.list();
        SettingsUi.Entry[] pruneEntry = new SettingsUi.Entry[1];
        pruneEntry[0] = this.settingsUi.addEntry(maintain, "清理过期离线内容", null,
                this.localCache.detailCount() + " 篇", R.drawable.il_cleanup, () ->
                        this.cacheMaintenance.pruneOffline(() -> {
                            if (pruneEntry[0].value != null) {
                                pruneEntry[0].value.setText(
                                        this.localCache.detailCount() + " 篇");
                            }
                            this.host.showToast("过期离线内容已清理");
                        }));
        addEntry(maintain, "导出日志", null, R.drawable.il_scroll,
                this.host::exportDiagnostics);
        addEntry(maintain, "上传日志", null, R.drawable.il_info,
                this.host::uploadDiagnostics);
        SettingsUi.Entry[] cacheEntry = new SettingsUi.Entry[1];
        cacheEntry[0] = this.settingsUi.addEntry(maintain, "清除缓存", null,
                Format.cacheMb(this.cacheMaintenance.cacheBytes()),
                R.drawable.il_cleanup, () -> {
                    long cleared = this.cacheMaintenance.clearTemporaryCache();
                    if (cacheEntry[0].value != null) {
                        cacheEntry[0].value.setText(
                                Format.cacheMb(this.cacheMaintenance.cacheBytes()));
                    }
                    this.host.showToast("已清除缓存 " + Format.cacheMb(cleared));
                });
        addEntry(maintain, this.session.isLoggedIn() ? "退出登录" : "二维码登录",
                this.session.isLoggedIn()
                        ? "当前账号 ID " + this.session.userId() : "扫码登录小黑盒账号",
                this.session.isLoggedIn() ? R.drawable.ic_logout : R.drawable.il_qr,
                () -> {
                    if (this.session.isLoggedIn()) {
                        this.session.clearSession();
                        this.host.reloadFeed(false);
                        this.host.showToast("已退出登录");
                    }
                    this.host.showLogin();
                });
        page.addView(maintain);
    }

    void showStartup() {
        LinearLayout page = this.host.openPage("startup_settings", "启动与更新");
        this.settingsUi.addSection(page, "启动与更新");
        LinearLayout panel = this.settingsUi.list();
        addTop(panel, toggle("进入软件时检查更新", this.session.autoUpdateCheck(),
                this.session::setAutoUpdateCheck), 0);
        addTop(panel, toggle("显示开屏动画", this.session.splashEnabled(),
                this.session::setSplashEnabled), 0);
        this.settingsUi.addTextEntry(panel, "开屏文字", this.session.splashText(),
                R.drawable.il_info, this.session::setSplashText);
        this.settingsUi.addRangeEntry(panel, "开屏时长", "ms", R.drawable.il_history,
                500, 2600, 50, this.session.splashDuration(),
                this.session::setSplashDuration, null);
        addEntry(panel, "预览开屏动画", null, R.drawable.il_eye,
                () -> showSplashPreview(this.session.splashText(),
                        this.session.splashDuration()));
        page.addView(panel);
    }

    private void showSplashPreview(String value, int duration) {
        String previewText = value == null || value.isEmpty()
                ? "方寸之间，看见热爱" : value;
        FrameLayout overlay = new FrameLayout(this.activity);
        overlay.setTag("splash_preview");
        boolean dark = this.session.darkMode();
        overlay.setBackgroundColor(dark
                ? Color.rgb(14, 15, 16) : Color.rgb(246, 247, 249));
        if (!dark) {
            ImageView mark = new ImageView(this.activity);
            mark.setImageResource(R.drawable.splash_logo);
            mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    dp(96), dp(96), Gravity.CENTER);
            params.bottomMargin = dp(54);
            overlay.addView(mark, params);
        }
        TextView message = text("", 14.0f, this.tokens.text);
        message.setTypeface(Typeface.MONOSPACE);
        message.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams messageParams = new FrameLayout.LayoutParams(
                -1, dp(52), Gravity.CENTER);
        messageParams.topMargin = dp(dark ? 0 : 58);
        messageParams.leftMargin = dp(18);
        messageParams.rightMargin = dp(18);
        overlay.addView(message, messageParams);
        TextView close = text("点击任意位置退出预览", 10.0f, this.tokens.muted);
        close.setGravity(Gravity.CENTER);
        overlay.addView(close, new FrameLayout.LayoutParams(-1, dp(34), Gravity.BOTTOM));
        overlay.setOnClickListener(view -> this.host.content().removeView(overlay));
        this.host.content().addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        int[] frame = {0};
        Runnable animation = new Runnable() {
            @Override
            public void run() {
                if (overlay.getParent() == null) return;
                int count = Math.min(frame[0], previewText.length());
                message.setText(previewText.substring(0, count)
                        + (count < previewText.length() ? "_" : ""));
                frame[0]++;
                if (count < previewText.length()) {
                    message.postDelayed(this,
                            Math.max(28, duration / Math.max(1, previewText.length() + 4)));
                }
            }
        };
        animation.run();
    }

    private void showNetworkModePicker(SettingsUi.Entry entry) {
        this.host.showDialog("网络模式",
                "省流量：使用缩略图，不自动播放动图\n标准：使用缩略图并播放动图\n原图：优先加载高清图片",
                "标准", () -> setNetworkMode(1, entry),
                "省流量", () -> setNetworkMode(0, entry),
                "原图", () -> setNetworkMode(2, entry));
    }

    private void setNetworkMode(int mode, SettingsUi.Entry entry) {
        boolean changed = this.session.networkMode() != mode;
        this.session.setNetworkMode(mode);
        if (entry != null && entry.value != null) {
            entry.value.setText(networkModeLabel());
            Motions.selected(entry.value);
        }
        if (changed) this.host.reloadFeed(false);
        this.host.showToast("已切换为" + networkModeLabel());
    }

    private String networkModeLabel() {
        int mode = this.session.networkMode();
        return mode == 0 ? "省流量" : mode == 2 ? "原图" : "标准";
    }

    private LinearLayout toggle(String label, boolean initial,
                                SettingsUi.ToggleListener listener) {
        return this.settingsUi.toggle(label, "", null, initial, listener);
    }

    private void addEntry(LinearLayout parent, String name, String description,
                          int icon, Runnable action) {
        this.settingsUi.addEntry(parent, name, description, null, icon, action);
    }

    private Button commandButton(String value) {
        Button button = new Button(this.activity);
        button.setText(value);
        button.setTextSize(sp(12.0f));
        button.setTextColor(this.tokens.text);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(40));
        if (Build.VERSION.SDK_INT >= 21) button.setStateListAnimator(null);
        Compat.setBackground(button, UiComponents.round(
                this.activity, this.tokens.panelElevated, 11, uiScale()));
        button.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) UiComponents.press(view);
            return false;
        });
        return button;
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(
                this.activity, value, size, color, this.session.textScale() / 100.0f);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private void addTop(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(view, params);
    }

    private int dp(int value) {
        return UiComponents.dp(this.activity, value, uiScale());
    }

    private float sp(float value) {
        return value * this.session.textScale() / 100.0f;
    }

    private float uiScale() {
        return this.session.uiScale() / 100.0f;
    }
}
