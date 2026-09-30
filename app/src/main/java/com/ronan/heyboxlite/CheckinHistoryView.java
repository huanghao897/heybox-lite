package com.ronan.heyboxlite;

import android.widget.LinearLayout;

/** Renders the compact, server-backed check-in history list. */
final class CheckinHistoryView {
    private final SettingsUi settingsUi;

    CheckinHistoryView(SettingsUi settingsUi) {
        this.settingsUi = settingsUi;
    }

    void addTo(LinearLayout page, CheckinHistory history, boolean loading,
               String error, Runnable retry) {
        this.settingsUi.addSection(page, "最近签到");
        LinearLayout records = this.settingsUi.list();
        if (loading) {
            this.settingsUi.addInfoEntry(records, "正在同步记录",
                    "读取签到服务器的最近执行结果", null, R.drawable.il_refresh);
        } else if (error != null && !error.isEmpty()) {
            this.settingsUi.addEntry(records, "暂时无法读取记录", error,
                    "重新读取", R.drawable.il_refresh, retry);
        } else if (history == null || history.entries.isEmpty()) {
            this.settingsUi.addInfoEntry(records, "暂无记录",
                    "签到任务执行后会显示在这里", null, R.drawable.il_history);
        } else {
            for (CheckinHistory.Entry entry : history.entries) {
                this.settingsUi.addInfoEntry(records, entry.state,
                        entry.summaryPreview(), entry.displayTime(),
                        iconFor(entry));
            }
        }
        page.addView(records);
    }

    private int iconFor(CheckinHistory.Entry entry) {
        if ("失败".equals(entry.state)) return R.drawable.il_info;
        if ("执行中".equals(entry.state)) return R.drawable.il_refresh;
        return R.drawable.il_history;
    }
}
