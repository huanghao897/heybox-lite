package com.ronan.heyboxlite;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CrashRecoveryActivity extends Activity {
    private SessionStore session;
    private String report;
    private TextView status;
    private Button upload;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        session = new SessionStore(this);
        String crash = CrashReporter.pendingCrashReport(this);
        if (crash.isEmpty()) crash = CrashReporter.latestCrashReport(this);
        report = DiagnosticsClient.crashReport(this, session, crash);
        render(crash);
        CrashReporter.markHandled(this, crash);
    }

    private void render(String crash) {
        boolean dark = session.darkMode();
        int background = dark ? Color.rgb(14, 15, 16) : Color.rgb(246, 247, 249);
        int panel = dark ? Color.rgb(31, 32, 34) : Color.WHITE;
        int text = dark ? Color.rgb(243, 243, 243) : Color.rgb(25, 27, 29);
        int muted = dark ? Color.rgb(166, 168, 172) : Color.rgb(96, 99, 104);
        int accent = dark ? Color.WHITE : Color.rgb(25, 27, 29);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(18), dp(14), dp(18));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(16));
        Compat.setBackground(card, round(panel, 14));
        page.addView(card, new LinearLayout.LayoutParams(-1, -2));

        TextView title = label("应用出现异常", 19, text);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(title);
        TextView description = label(
                "故障信息已经脱敏保存。你可以重新打开应用，也可以保存或主动上传日志。",
                13, muted);
        description.setLineSpacing(0f, 1.15f);
        addTop(card, description, 8);

        String details = DiagnosticSanitizer.redact(crash);
        if (details.length() > 3_000) details = details.substring(0, 3_000) + "\n...";
        TextView stack = label(details.isEmpty() ? "未能读取故障详情" : details, 11, muted);
        stack.setTypeface(Typeface.MONOSPACE);
        stack.setTextIsSelectable(true);
        stack.setLineSpacing(0f, 1.08f);
        stack.setPadding(dp(10), dp(9), dp(10), dp(9));
        Compat.setBackground(stack, round(background, 8));
        addTop(card, stack, 12);

        status = label("日志不会自动上传", 12, muted);
        addTop(card, status, 10);

        Button reopen = button("重新打开", accent, dark ? Color.BLACK : Color.WHITE);
        reopen.setOnClickListener(view -> reopenApp());
        addTop(card, reopen, 12);
        Button save = button("保存日志", panel, text);
        save.setOnClickListener(view -> saveReport());
        addTop(card, save, 7);
        upload = button("上传日志", panel, text);
        upload.setOnClickListener(view -> uploadReport());
        addTop(card, upload, 7);
        Button exit = button("退出", panel, text);
        exit.setOnClickListener(view -> closeApp());
        addTop(card, exit, 7);

        setContentView(scroll);
        Compat.colorSystemBars(getWindow(), background);
    }

    private void saveReport() {
        String name = "heybox-lite-crash-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt";
        String path = DiagnosticsExporter.save(this, name, report);
        status.setText(path == null ? "保存失败" : "已保存到 " + path);
    }

    private void uploadReport() {
        upload.setEnabled(false);
        status.setText("正在上传日志");
        DiagnosticsClient.upload(session, report, (success, message) -> {
            if (isFinishing()) return;
            upload.setEnabled(true);
            status.setText(message);
        });
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

    private Button button(String value, int color, int textColor) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(textColor);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable background = round(color, 10);
        background.setStroke(Math.max(1, dp(1)), Color.rgb(92, 94, 98));
        Compat.setBackground(button, background);
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(40)));
        return button;
    }

    private TextView label(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        return view;
    }

    private void addTop(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = child.getLayoutParams() instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) child.getLayoutParams()
                : new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(child, params);
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
