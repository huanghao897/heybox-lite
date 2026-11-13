package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Native editor view only; draft ownership and submission belong to the controller. */
final class NativeCommentEditor {
    interface Events {
        void changed(String value);
        void send(String value, AlertDialog dialog, View button);
    }

    private final Activity activity;
    private final SessionStore session;
    private final ThemeTokens tokens;

    NativeCommentEditor(Activity activity, SessionStore session, ThemeTokens tokens) {
        this.activity = activity;
        this.session = session;
        this.tokens = tokens;
    }

    AlertDialog show(boolean reply, boolean round, String draft, Events events) {
        int background = session.darkMode() ? Color.rgb(30, 31, 33) : Color.rgb(250, 250, 251);
        int inputBackground = session.darkMode() ? Color.rgb(12, 13, 14) : Color.rgb(241, 242, 244);
        int border = session.darkMode() ? Color.rgb(66, 68, 72) : Color.rgb(218, 221, 225);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        Compat.setBackground(panel, stroke(background, 16, border));
        TextView title = text(reply ? "回复评论" : "发表评论", 16, tokens.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = text(reply ? "回复这条评论" : "写下你的想法", 11, tokens.muted);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(4);
        panel.addView(hint, hintParams);

        EditText input = new EditText(activity);
        input.setTextColor(tokens.text);
        input.setHintTextColor(tokens.muted);
        input.setTextSize(14f * session.textScale() / 100f);
        input.setMinLines(3);
        input.setMaxLines(5);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setSingleLine(false);
        input.setHint(reply ? "输入回复内容" : "友好交流，理性讨论");
        input.setText(draft);
        input.setSelection(draft.length());
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                events.changed(value == null ? "" : value.toString());
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        Compat.setBackground(input, stroke(inputBackground, 14, border));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(118));
        inputParams.topMargin = dp(12);
        panel.addView(input, inputParams);

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(panel).create();
        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, dp(12), 0, 0);
        TextView cancel = action("取消", false);
        TextView send = action("发送", true);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(0, dp(38), 1f);
        sendParams.leftMargin = dp(9);
        actions.addView(cancel, cancelParams);
        actions.addView(send, sendParams);
        panel.addView(actions);
        cancel.setOnClickListener(view -> {
            UiComponents.press(cancel);
            dialog.dismiss();
        });
        send.setOnClickListener(view -> {
            UiComponents.press(send);
            events.send(input.getText().toString().trim(), dialog, send);
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            dialog.getWindow().setDimAmount(session.darkMode() ? 0.56f : 0.36f);
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(Math.max(1,
                    Math.min(width - dp(round ? 24 : 14) * 2, dp(380))), -2);
        }
        return dialog;
    }

    private TextView action(String label, boolean primary) {
        int fill = primary ? tokens.primary : ThemeTokens.blend(tokens.panel, tokens.muted,
                session.darkMode() ? 0.22f : 0.09f);
        int border = primary ? tokens.primary : ThemeTokens.blend(tokens.panel, tokens.muted, 0.28f);
        TextView view = text(label, 13, primary ? ThemeTokens.contrast(fill) : tokens.text);
        view.setTypeface(Typeface.DEFAULT, primary ? Typeface.BOLD : Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), 0, dp(10), 0);
        Compat.setBackground(view, stroke(fill, 19, border));
        return view;
    }

    private TextView text(String value, float size, int color) {
        return UiComponents.label(activity, value, size, color, session.textScale() / 100f);
    }

    private GradientDrawable stroke(int color, int radius, int border) {
        GradientDrawable drawable = UiComponents.round(activity, color, radius, session.uiScale() / 100f);
        drawable.setStroke(Math.max(1, dp(1)), border);
        return drawable;
    }

    private int dp(int value) {
        return UiComponents.dp(activity, value, session.uiScale() / 100f);
    }
}
