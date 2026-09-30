package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;

final class VideoSettingsDialog {
    private VideoSettingsDialog() {}

    static void show(Activity activity, SessionStore session, ThemeTokens tokens) {
        if (activity.isFinishing()) return;
        float scale = session.uiScale() / 100f;
        boolean round = session.usesRoundLayout();
        ScrollView scroll = new ScrollView(activity);
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        int padding = UiComponents.dp(activity, round ? 16 : 12, scale);
        page.setPadding(padding, padding, padding, padding);
        page.setBackgroundColor(tokens.background);
        scroll.addView(page);
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(scroll).create();
        Handler handler = new Handler(Looper.getMainLooper());
        SettingsUi ui = new SettingsUi(activity, session, tokens, round, 0,
                handler, dialog::dismiss);
        new VideoSettingsPage(session, ui, (key, title) -> {
            page.addView(ui.topCard(title));
            return page;
        }).show();
        dialog.setOnDismissListener(ignored -> {
            handler.removeCallbacksAndMessages(null);
            Motions.resetTree(page);
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(tokens.background));
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            int height = activity.getResources().getDisplayMetrics().heightPixels;
            dialog.getWindow().setLayout(round ? width : Math.min(width, padding * 28),
                    round ? height : Math.round(height * 0.85f));
        }
    }
}
