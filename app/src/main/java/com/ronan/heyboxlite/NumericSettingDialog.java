package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared numeric editor used by settings that also expose a range slider. */
final class NumericSettingDialog {
    interface IntListener {
        void onChanged(int value);
    }

    private final Activity activity;
    private final SessionStore session;
    private final ThemeTokens tokens;
    private final float uiScale;
    private final float textScale;
    private final Handler handler;

    NumericSettingDialog(Activity activity, SessionStore session, ThemeTokens tokens,
                         float uiScale, float textScale, Handler handler) {
        this.activity = activity;
        this.session = session;
        this.tokens = tokens;
        this.uiScale = uiScale;
        this.textScale = textScale;
        this.handler = handler;
    }

    void show(String title, String unit, int min, int sliderMax, int step, int current,
              boolean allowAboveSliderMax, IntListener listener, Runnable afterDismiss) {
        int lower = Math.min(min, sliderMax);
        int upper = Math.max(min, sliderMax);
        int selected = allowAboveSliderMax
                ? Math.max(lower, current)
                : Math.max(lower, Math.min(upper, current));
        int sliderValue = Math.max(lower, Math.min(upper, selected));

        LinearLayout box = dialogPanel(title);
        TextView hint = text(allowAboveSliderMax
                ? "滑杆显示常用范围，可直接输入更高数值"
                : "拖动调整，或点击数值直接输入", 11.0f, tokens.muted);
        box.addView(hint, params(-1, -2, 0));

        LinearLayout inputRow = new LinearLayout(activity);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(selected));
        input.setTextColor(tokens.text);
        input.setTextSize(sp(18.0f));
        input.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setGravity(Gravity.CENTER);
        input.setPadding(dp(10), 0, dp(10), 0);
        Compat.setBackground(input, UiComponents.round(
                activity, tokens.panelElevated, 10, uiScale));
        inputRow.addView(input, new LinearLayout.LayoutParams(0, dp(44), 1.0f));
        if (!android.text.TextUtils.isEmpty(unit)) {
            TextView unitView = text("%".equals(unit) ? "%" : " " + unit,
                    14.0f, tokens.muted);
            LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(-2, -2);
            unitParams.leftMargin = dp(8);
            inputRow.addView(unitView, unitParams);
        }
        box.addView(inputRow, params(-1, -2, 7));

        SettingRangeView range = new SettingRangeView(
                activity, tokens, lower, upper, step, sliderValue);
        box.addView(range, params(-1, dp(52), 5));

        LinearLayout limits = new LinearLayout(activity);
        limits.setGravity(Gravity.CENTER_VERTICAL);
        limits.addView(text(formatValue(lower, unit), 10.0f, tokens.muted),
                new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView upperView = text(formatValue(upper, unit)
                + (allowAboveSliderMax ? "+" : ""), 10.0f, tokens.muted);
        upperView.setGravity(Gravity.RIGHT);
        limits.addView(upperView, new LinearLayout.LayoutParams(0, -2, 1.0f));
        box.addView(limits, params(-1, -2, 0));

        boolean[] changed = {false};
        range.setListener(value -> {
            changed[0] = true;
            input.setText(String.valueOf(value));
            input.setSelection(input.length());
            listener.onChanged(value);
        });

        AlertDialog dialog = new AlertDialog.Builder(activity).setView(box).create();
        TextView done = dialogAction("确定", dialog);
        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setGravity(Gravity.END);
        actionRow.addView(done, new LinearLayout.LayoutParams(-2, dp(36)));
        box.addView(actionRow, params(-1, -2, 12));
        dialog.setCanceledOnTouchOutside(true);
        done.setOnClickListener(view -> {
            Integer value = parse(input, lower, upper, allowAboveSliderMax);
            if (value == null) return;
            changed[0] = true;
            listener.onChanged(value);
            dialog.dismiss();
        });
        dialog.setOnDismissListener(ignored -> {
            if (changed[0] && afterDismiss != null && !activity.isFinishing()) {
                handler.post(afterDismiss);
            }
        });
        present(dialog, box);
    }

    private Integer parse(EditText input, int lower, int upper,
                          boolean allowAboveSliderMax) {
        String raw = input.getText() == null ? "" : input.getText().toString().trim();
        long value;
        try {
            if (raw.length() == 0) throw new NumberFormatException();
            value = Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            input.setError("请输入整数");
            input.requestFocus();
            return null;
        }
        if (value < lower) {
            input.setError("不能小于 " + formatValue(lower, null));
            input.requestFocus();
            return null;
        }
        if (!allowAboveSliderMax && value > upper) {
            input.setError("不能大于 " + formatValue(upper, null));
            input.requestFocus();
            return null;
        }
        if (value > Integer.MAX_VALUE) {
            input.setError("数值过大");
            input.requestFocus();
            return null;
        }
        return (int) value;
    }

    private LinearLayout dialogPanel(String title) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        Compat.setBackground(box, UiComponents.round(activity, tokens.panel, 12, uiScale));
        TextView titleView = text(title, 17.0f, tokens.text);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    private TextView dialogAction(String label, AlertDialog dialog) {
        TextView action = text(label, 13.0f, ThemeTokens.contrast(tokens.text));
        action.setGravity(Gravity.CENTER);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        action.setMinWidth(dp(72));
        action.setPadding(dp(14), 0, dp(14), 0);
        Compat.setBackground(action, UiComponents.round(activity, tokens.text, 11, uiScale));
        action.setOnClickListener(view -> dialog.dismiss());
        return action;
    }

    private void present(AlertDialog dialog, View content) {
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(session.darkMode() ? 0.46f : 0.30f);
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(
                    Math.max(dp(220), Math.min(width - dp(24), dp(340))), -2);
        }
        Motions.dialogIn(content);
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(activity, value, size, color, textScale);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private LinearLayout.LayoutParams params(int width, int height, int topMargin) {
        LinearLayout.LayoutParams result = new LinearLayout.LayoutParams(width, height);
        result.topMargin = dp(topMargin);
        return result;
    }

    private String formatValue(int value, String unit) {
        if (android.text.TextUtils.isEmpty(unit)) return String.valueOf(value);
        return value + ("%".equals(unit) ? "%" : " " + unit);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density * uiScale);
    }

    private float sp(float value) {
        return value * textScale;
    }
}
