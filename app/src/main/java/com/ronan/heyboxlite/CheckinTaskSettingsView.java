package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

final class CheckinTaskSettingsView {
    private final Activity activity;
    private final SessionStore session;
    private final ThemeTokens tokens;
    private final SettingsUi settingsUi;
    private final CheckinCenterUi ui;
    private final CheckinTaskSettingsFlow flow;
    private final boolean roundLayout;
    private final float scale;
    private View enabledSwitch;
    private View timeButton;
    private Button offsetMinus;
    private Button offsetPlus;

    CheckinTaskSettingsView(Activity activity, SessionStore session, ThemeTokens tokens,
                            SettingsUi settingsUi, CheckinCenterUi ui,
                            CheckinTaskSettingsFlow flow, boolean roundLayout) {
        this.activity = activity;
        this.session = session;
        this.tokens = tokens;
        this.settingsUi = settingsUi;
        this.ui = ui;
        this.flow = flow;
        this.roundLayout = roundLayout;
        this.scale = session.uiScale() / 100f;
    }

    void addTo(LinearLayout page, CheckinCenterClient.Task task) {
        settingsUi.addSection(page, "计划");
        LinearLayout settings = settingsUi.list();
        enabledSwitch = settingsUi.toggle("自动签到", null,
                enabledLabel(task), task.enabled,
                checked -> flow.save(checked, normalizedTime(task), task.offsetMinutes));
        enabledSwitch.setEnabled(!flow.requestInFlight());
        settings.addView(enabledSwitch);

        timeButton = settingsUi.addEntry(settings, "执行时间", null,
                scheduleLabel(task), R.drawable.il_calendar,
                () -> showTimePicker(task)).root;
        timeButton.setEnabled(!flow.requestInFlight());

        settingsUi.addDivider(settings);
        settings.addView(offsetRow(task));
        if (task.platformBlocked || task.signBlocked) {
            TextView warning = ui.body("服务已暂停此任务，当前设置会保留。", tokens.muted);
            warning.setPadding(dp(12), dp(8), dp(12), dp(10));
            settings.addView(warning);
        }
        page.addView(settings);
    }

    void setControlsEnabled(boolean enabled) {
        setEnabledTree(enabledSwitch, enabled);
        if (timeButton != null) timeButton.setEnabled(enabled);
        if (offsetMinus != null) offsetMinus.setEnabled(enabled);
        if (offsetPlus != null) offsetPlus.setEnabled(enabled);
    }

    void clear() {
        enabledSwitch = null;
        timeButton = null;
        offsetMinus = null;
        offsetPlus = null;
    }

    private void showTimePicker(CheckinCenterClient.Task task) {
        if (flow.requestInFlight()) return;
        String[] parts = normalizedTime(task).split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        TimePickerDialog dialog = new TimePickerDialog(activity,
                (view, selectedHour, selectedMinute) -> flow.save(task.enabled,
                        String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute),
                        task.offsetMinutes), hour, minute, true);
        dialog.setTitle("执行时间");
        dialog.show();
    }

    private LinearLayout offsetRow(CheckinCenterClient.Task task) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(roundLayout ? 10 : 12), dp(7),
                dp(roundLayout ? 10 : 12), dp(7));
        row.setMinimumHeight(dp(60));

        ImageView icon = ui.iconTile(R.drawable.il_scroll);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.rightMargin = dp(10);
        row.addView(icon, iconParams);

        TextView title = ui.label("随机偏移", 15f, tokens.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        offsetMinus = ui.compactButton("-");
        offsetMinus.setContentDescription("减少随机偏移");
        offsetMinus.setEnabled(!flow.requestInFlight() && task.offsetMinutes > 0);
        offsetMinus.setOnClickListener(view -> flow.save(task.enabled,
                normalizedTime(task), Math.max(0, task.offsetMinutes - 30)));
        row.addView(offsetMinus, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView value = ui.body(offsetLabel(task.offsetMinutes), tokens.text);
        value.setGravity(Gravity.CENTER);
        value.setSingleLine(true);
        row.addView(value, new LinearLayout.LayoutParams(dp(roundLayout ? 58 : 66), dp(32)));

        offsetPlus = ui.compactButton("+");
        offsetPlus.setContentDescription("增加随机偏移");
        offsetPlus.setEnabled(!flow.requestInFlight() && task.offsetMinutes < 720);
        offsetPlus.setOnClickListener(view -> flow.save(task.enabled,
                normalizedTime(task), Math.min(720, task.offsetMinutes + 30)));
        row.addView(offsetPlus, new LinearLayout.LayoutParams(dp(32), dp(32)));
        return row;
    }

    private static void setEnabledTree(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            setEnabledTree(group.getChildAt(index), enabled);
        }
    }

    static String normalizedTime(CheckinCenterClient.Task task) {
        return task.scheduleTime.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")
                ? task.scheduleTime : "08:30";
    }

    static String scheduleLabel(CheckinCenterClient.Task task) {
        return task.scheduleTime.isEmpty() ? "未设置" : task.scheduleTime;
    }

    static String enabledLabel(CheckinCenterClient.Task task) {
        if (task.platformBlocked || task.signBlocked) return "已暂停";
        return task.enabled && task.sign ? "已启用" : "未启用";
    }

    static String offsetLabel(int minutes) {
        int value = Math.max(0, minutes);
        return value == 0 ? "无" : value + " 分钟";
    }

    private int dp(int value) {
        return UiComponents.dp(activity, value, scale);
    }
}
