package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Builds the stable content states of the check-in page without owning its lifecycle. */
final class CheckinCenterPageRenderer {
    interface Actions {
        void beginPairing();
        void openMobileLogin();
        void runNow();
        void openTaskSettings();
        void openSponsorship(CheckinBilling.Membership membership);
        void requestRevoke();
        void refresh();
        void loadHistory();
    }

    private final Activity activity;
    private final ThemeTokens tokens;
    private final CheckinCenterUi ui;
    private final SettingsUi settingsUi;
    private final CheckinHistoryView historyView;
    private final CheckinTaskSettingsView taskSettingsView;
    private final boolean roundLayout;
    private final float scale;
    private final Actions actions;

    CheckinCenterPageRenderer(Activity activity, SessionStore session, ThemeTokens tokens,
                              CheckinCenterUi ui, SettingsUi settingsUi,
                              CheckinHistoryView historyView,
                              CheckinTaskSettingsView taskSettingsView,
                              boolean roundLayout, Actions actions) {
        this.activity = activity;
        this.tokens = tokens;
        this.ui = ui;
        this.settingsUi = settingsUi;
        this.historyView = historyView;
        this.taskSettingsView = taskSettingsView;
        this.roundLayout = roundLayout;
        this.scale = session.uiScale() / 100.0f;
        this.actions = actions;
    }

    void render(LinearLayout page, CheckinCenterPage.State state,
                CheckinCenterClient.Status status, CheckinHistory history,
                boolean historyLoading, String historyError, String errorMessage,
                boolean paired, boolean supported) {
        if (errorMessage != null && !errorMessage.isEmpty()) {
            addTop(page, ui.errorBanner(errorMessage), 6);
        }
        if (!paired) {
            renderUnpaired(page, supported);
        } else if (state == CheckinCenterPage.State.TASK_SETTINGS && status != null) {
            taskSettingsView.addTo(page, status.task);
        } else if (state == CheckinCenterPage.State.SYNCING
                || state == CheckinCenterPage.State.ERROR || status == null) {
            renderLoading(page, state);
        } else {
            renderConnected(page, state, status, history, historyLoading, historyError);
        }
    }

    private void renderUnpaired(LinearLayout page, boolean supported) {
        LinearLayout card = card();
        String subtitle = supported ? "连接后由服务器按计划执行" : "当前设备不支持安全连接";
        card.addView(statusHeader(R.drawable.il_calendar, "未连接", subtitle, tokens.text));
        addTop(card, body("登录签到服务后，再连接需要签到的小黑盒账号。", tokens.muted), 12);
        Button connect = primaryButton("连接签到服务");
        connect.setEnabled(supported);
        connect.setOnClickListener(view -> {
            UiComponents.press(view);
            actions.beginPairing();
        });
        addTop(card, connect, 13);
        page.addView(card);
    }

    private void renderLoading(LinearLayout page, CheckinCenterPage.State state) {
        LinearLayout card = card();
        String title = state == CheckinCenterPage.State.SYNCING ? "正在连接" : "暂时无法连接";
        String message = state == CheckinCenterPage.State.SYNCING
                ? "正在读取账号与签到计划"
                : state == CheckinCenterPage.State.ERROR
                ? "可重新加载状态，或撤销此设备"
                : "正在处理签到任务";
        card.addView(statusHeader(R.drawable.il_refresh, title, message,
                state == CheckinCenterPage.State.ERROR ? tokens.muted : tokens.text,
                state == CheckinCenterPage.State.SYNCING));
        page.addView(card);
        if (state == CheckinCenterPage.State.ERROR) addRecoveryActions(page);
    }

    private void renderConnected(LinearLayout page, CheckinCenterPage.State state,
                                 CheckinCenterClient.Status status, CheckinHistory history,
                                 boolean historyLoading, String historyError) {
        boolean accountConnected = "connected".equalsIgnoreCase(status.account.state);
        LinearLayout statusCard = card();
        String stateLabel = !accountConnected ? "等待手机号登录"
                : state == CheckinCenterPage.State.RUNNING ? "执行中" : taskStateLabel(status.task);
        int stateColor = state == CheckinCenterPage.State.RUNNING
                || accountConnected && status.task.active() ? tokens.text : tokens.muted;
        statusCard.addView(statusHeader(R.drawable.il_calendar, stateLabel,
                accountConnected ? accountLabel(status.account) : "尚未连接小黑盒账号",
                stateColor, state == CheckinCenterPage.State.RUNNING));
        if (!accountConnected) {
            addTop(statusCard, body("自动签到需要单独使用手机号登录小黑盒账号。", tokens.muted), 11);
            Button login = primaryButton("手机号登录");
            login.setOnClickListener(view -> {
                UiComponents.press(view);
                actions.openMobileLogin();
            });
            addTop(statusCard, login, 12);
            page.addView(statusCard);

            settingsUi.addSection(page, "服务");
            LinearLayout service = settingsUi.list();
            addSponsorshipEntry(service, status.membership);
            settingsUi.addEntry(service, "撤销此设备", null, null,
                    R.drawable.ic_logout, actions::requestRevoke);
            page.addView(service);
            return;
        }
        addTop(statusCard, scheduleMetric(status.task), 13);
        Button run = primaryButton(state == CheckinCenterPage.State.RUNNING ? "正在签到" : "立即签到");
        run.setEnabled(state != CheckinCenterPage.State.RUNNING && status.task.active());
        run.setOnClickListener(view -> {
            UiComponents.press(view);
            actions.runNow();
        });
        addTop(statusCard, run, 12);
        page.addView(statusCard);

        settingsUi.addSection(page, "管理");
        LinearLayout management = settingsUi.list();
        settingsUi.addEntry(management, "签到设置", null,
                CheckinTaskSettingsView.scheduleLabel(status.task) + " · "
                        + CheckinTaskSettingsView.offsetLabel(status.task.offsetMinutes),
                R.drawable.il_settings, actions::openTaskSettings);
        addSponsorshipEntry(management, status.membership);
        settingsUi.addEntry(management, "更换账号", null, "手机号登录",
                R.drawable.il_person, actions::openMobileLogin);
        page.addView(management);

        historyView.addTo(page, history, historyLoading, historyError, actions::loadHistory);

        Button revoke = quietButton("撤销此设备");
        revoke.setEnabled(state != CheckinCenterPage.State.RUNNING);
        revoke.setOnClickListener(view -> actions.requestRevoke());
        addTop(page, revoke, 8);
    }

    private void addSponsorshipEntry(LinearLayout list,
                                     CheckinBilling.Membership membership) {
        if (membership == null || !membership.voluntarySponsorship) return;
        if (membership.checkoutAvailable) {
            settingsUi.addEntry(list, "赞助", null, "自愿支持", R.drawable.il_qr,
                    () -> actions.openSponsorship(membership));
        } else {
            settingsUi.addInfoEntry(list, "赞助", null, "暂不可用", R.drawable.il_qr);
        }
    }

    private void addRecoveryActions(LinearLayout page) {
        settingsUi.addSection(page, "操作");
        LinearLayout actionsList = settingsUi.list();
        settingsUi.addEntry(actionsList, "登录小黑盒", null, "手机号",
                R.drawable.il_person, actions::openMobileLogin);
        settingsUi.addEntry(actionsList, "重新加载", null, null,
                R.drawable.il_refresh, actions::refresh);
        settingsUi.addEntry(actionsList, "撤销此设备", null, null,
                R.drawable.ic_logout, actions::requestRevoke);
        page.addView(actionsList);
    }

    private LinearLayout statusHeader(int iconRes, String title, String subtitle,
                                      int titleColor) {
        return ui.statusHeader(iconRes, title, subtitle, titleColor, false);
    }

    private LinearLayout statusHeader(int iconRes, String title, String subtitle,
                                      int titleColor, boolean showProgress) {
        return ui.statusHeader(iconRes, title, subtitle, titleColor, showProgress);
    }

    private LinearLayout scheduleMetric(CheckinCenterClient.Task task) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = column(Color.TRANSPARENT);
        copy.addView(label("下次签到", 10.5f, tokens.muted));
        String window = windowLabel(task);
        TextView detail = body(window.isEmpty() ? CheckinTaskSettingsView.enabledLabel(task) : window,
                tokens.muted);
        detail.setPadding(0, dp(2), 0, 0);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView time = label(CheckinTaskSettingsView.scheduleLabel(task),
                roundLayout ? 20f : 22f, tokens.text);
        time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        time.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        time.setSingleLine(true);
        row.addView(time, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    private LinearLayout card() {
        return ui.card();
    }

    private LinearLayout column(int color) {
        return ui.column(color);
    }

    private TextView body(String value, int color) {
        return ui.body(value, color);
    }

    private TextView label(String value, float size, int color) {
        return ui.label(value, size, color);
    }

    private Button primaryButton(String value) {
        return ui.primaryButton(value);
    }

    private Button quietButton(String value) {
        return ui.quietButton(value);
    }

    private void addTop(ViewGroup parent, View child, int marginDp) {
        ui.addTop(parent, child, marginDp);
    }

    private String accountLabel(CheckinCenterClient.Account account) {
        String name = account.displayName.isEmpty() ? "小黑盒账号" : account.displayName;
        return account.externalIdMasked.isEmpty() ? name : name + "  " + account.externalIdMasked;
    }

    private String taskStateLabel(CheckinCenterClient.Task task) {
        if (task.platformBlocked || task.signBlocked) return "自动签到已暂停";
        return task.active() ? "自动签到已启用" : "自动签到未启用";
    }

    private String windowLabel(CheckinCenterClient.Task task) {
        if (task.windowStart.isEmpty()) return task.windowEnd;
        if (task.windowEnd.isEmpty()) return task.windowStart;
        return task.windowStart + " - " + task.windowEnd;
    }

    private int dp(int value) {
        return UiComponents.dp(activity, value, scale);
    }
}
