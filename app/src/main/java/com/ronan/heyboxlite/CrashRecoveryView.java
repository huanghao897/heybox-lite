package com.ronan.heyboxlite;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Actions never share a ScrollView with a potentially long crash stack. */
@SuppressLint("ViewConstructor")
final class CrashRecoveryView extends FrameLayout {
    private final LinearLayout content;
    private final TextView title;
    private final TextView summary;
    private final TextView status;
    private final Button restart;
    private final LinearLayout secondary;
    private final boolean round;
    private final float scale;

    CrashRecoveryView(Context context, SessionStore session, String report,
                      Runnable reopen, Runnable exit, Runnable save) {
        super(context);
        round = session.usesRoundLayout();
        scale = Math.max(0.6f, Math.min(1f, session.uiScale() / 100f));
        ThemeTokens tokens = ThemeTokens.of(session.darkMode(), 0, 0);
        setBackgroundColor(tokens.background);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        addView(content, new LayoutParams(-1, -1, Gravity.CENTER));

        title = label("应用出现异常", 17, tokens.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));

        summary = label(CrashText.summary(report), 11, tokens.muted);
        summary.setMaxLines(3);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(dp(6), dp(4), dp(6), dp(4));
        content.addView(summary, new LinearLayout.LayoutParams(-1, 0, 1f));

        status = label("日志已保存，将自动上报", 10, tokens.muted);
        status.setGravity(Gravity.CENTER);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));

        restart = button("重启应用", tokens.onPrimary);
        restart.setTag("crash-restart");
        Compat.setBackground(restart, UiComponents.primaryButton(context, tokens, scale));
        restart.setOnClickListener(view -> reopen.run());
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(-1, dp(38));
        mainParams.topMargin = dp(6);
        content.addView(restart, mainParams);

        secondary = new LinearLayout(context);
        secondary.setGravity(Gravity.CENTER_VERTICAL);
        Button close = button("退出", tokens.text);
        close.setTag("crash-exit");
        Compat.setBackground(close, UiComponents.round(context, tokens.panel, 10, scale));
        close.setOnClickListener(view -> exit.run());
        secondary.addView(close, new LinearLayout.LayoutParams(0, -1, 1f));
        ImageButton export = new ImageButton(context);
        export.setTag("crash-save");
        export.setContentDescription("保存完整日志");
        export.setImageDrawable(Compat.tintedDrawable(context, R.drawable.il_scroll, tokens.text));
        export.setPadding(dp(8), dp(8), dp(8), dp(8));
        Compat.setBackground(export, UiComponents.round(context, tokens.panel, 10, scale));
        export.setOnClickListener(view -> save.run());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(dp(38), -1);
        saveParams.leftMargin = dp(6);
        secondary.addView(export, saveParams);
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(-1, dp(34));
        secondaryParams.topMargin = dp(5);
        content.addView(secondary, secondaryParams);
    }

    void status(String value) { status.setText(value); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        int diameter = Math.min(width, height);
        int innerWidth = round ? Math.round(diameter * 0.74f) : width - dp(24);
        int innerHeight = round ? Math.round(diameter * 0.65f) : height - dp(20);
        LayoutParams params = (LayoutParams) content.getLayoutParams();
        params.width = Math.max(1, Math.min(dp(340), innerWidth));
        params.height = Math.max(1, Math.min(dp(270), innerHeight));
        title.getLayoutParams().height = Math.max(1, Math.min(dp(28), innerHeight / 6));
        status.getLayoutParams().height = Math.max(1, Math.min(dp(20), innerHeight / 8));
        restart.getLayoutParams().height = Math.max(1, Math.min(dp(38), innerHeight / 5));
        secondary.getLayoutParams().height = Math.max(1, Math.min(dp(34), innerHeight / 5));
        // Only the summary shrinks on very short displays; actions retain their own layout rows.
        super.onMeasure(widthSpec, heightSpec);
    }

    private Button button(String text, int color) {
        Button button = UiComponents.button(getContext());
        button.setText(text);
        button.setTextColor(color);
        button.setTextSize(13f * scale);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        return button;
    }

    private TextView label(String text, float size, int color) {
        TextView view = UiComponents.label(getContext(), text, size, color, scale);
        view.setIncludeFontPadding(false);
        return view;
    }

    private int dp(int value) { return UiComponents.dp(getContext(), value, scale); }
}
