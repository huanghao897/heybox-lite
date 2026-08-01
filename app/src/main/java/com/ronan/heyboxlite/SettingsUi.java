package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class SettingsUi {
    interface IntListener {
        void onChanged(int value);
    }

    interface ToggleListener {
        void onChanged(boolean value);
    }

    interface TextListener {
        void onChanged(String value);
    }

    static final class Entry {
        final LinearLayout root;
        final TextView description;
        final TextView value;

        Entry(LinearLayout root, TextView description, TextView value) {
            this.root = root;
            this.description = description;
            this.value = value;
        }
    }

    private final Activity activity;
    private final SessionStore session;
    private final ThemeTokens tokens;
    private final boolean roundLayout;
    private final int roundHeaderInset;
    private final float uiScale;
    private final float textScale;
    private final Handler handler;
    private final Runnable backAction;

    SettingsUi(Activity activity, SessionStore session, ThemeTokens tokens,
               boolean roundLayout, int roundHeaderInset, Handler handler,
               Runnable backAction) {
        this.activity = activity;
        this.session = session;
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.roundHeaderInset = roundHeaderInset;
        this.uiScale = session.uiScale() / 100.0f;
        this.textScale = session.textScale() / 100.0f;
        this.handler = handler;
        this.backAction = backAction;
    }

    LinearLayout list() {
        LinearLayout list = vertical(tokens.panel);
        Compat.setBackground(list, UiComponents.round(activity, tokens.panel,
                roundLayout ? 9 : 11, uiScale));
        return list;
    }

    void addSection(LinearLayout page, String label) {
        TextView view = text(label, 10.0f, tokens.subtle);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(6), 0, 0, dp(4));
        addTop(page, view, roundLayout ? 10 : 12);
    }

    View topCard(String title) {
        LinearLayout box = new LinearLayout(activity);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(roundLayout ? roundHeaderInset : dp(1), 0,
                roundLayout ? roundHeaderInset : dp(2), 0);
        box.setBackgroundColor(tokens.background);
        ImageView back = new ImageView(activity);
        back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Drawable icon = Compat.tintedDrawable(activity, R.drawable.ic_arrow_back, tokens.text);
        if (icon != null) back.setImageDrawable(icon);
        back.setPadding(dp(6), dp(6), dp(6), dp(6));
        back.setContentDescription("返回");
        back.setOnClickListener(view -> runCommand(back, backAction));
        int backSize = dp(roundLayout ? 30 : 32);
        box.addView(back, new LinearLayout.LayoutParams(backSize, backSize));
        TextView name = text(title, roundLayout ? 17.0f : 19.0f, tokens.text);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, dp(roundLayout ? 38 : 42), 1.0f);
        nameParams.leftMargin = dp(roundLayout ? 8 : 10);
        box.addView(name, nameParams);
        return box;
    }

    Entry addEntry(LinearLayout parent, String name, String description,
                   String value, int icon, Runnable action) {
        addDivider(parent);
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int horizontal = dp(roundLayout ? 10 : 12);
        int vertical = dp(roundLayout ? 7 : 9);
        row.setPadding(horizontal, vertical, horizontal, vertical);
        row.setMinimumHeight(dp(60));

        ImageView marker = new ImageView(activity);
        marker.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Drawable markerIcon = Compat.tintedDrawable(activity, icon, tokens.text);
        if (markerIcon != null) marker.setImageDrawable(markerIcon);
        marker.setPadding(dp(8), dp(8), dp(8), dp(8));
        Compat.setBackground(marker, UiComponents.monoChip(activity, tokens, uiScale));
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        markerParams.rightMargin = dp(10);
        row.addView(marker, markerParams);

        LinearLayout copy = vertical(0);
        int titleColor = name.startsWith("退出登录")
                ? Color.rgb(228, 88, 88) : tokens.text;
        TextView title = text(name, 15.0f, titleColor);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(title);
        TextView descriptionView = null;
        if (!TextUtils.isEmpty(description)) {
            descriptionView = text("", 11.0f, tokens.muted);
            descriptionView.setPadding(0, dp(1), 0, 0);
            EmojiRenderer.set(descriptionView, description, session.darkMode());
            copy.addView(descriptionView);
        }
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        copyParams.rightMargin = dp(6);
        row.addView(copy, copyParams);

        TextView valueView = null;
        if (!TextUtils.isEmpty(value)) {
            valueView = text(value, 12.0f, tokens.muted);
            valueView.setSingleLine(true);
            valueView.setEllipsize(TextUtils.TruncateAt.END);
            valueView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(-2, -2);
            valueParams.leftMargin = dp(4);
            valueParams.rightMargin = dp(2);
            row.addView(valueView, valueParams);
        }
        ImageView arrow = new ImageView(activity);
        arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Drawable chevron = Compat.tintedDrawable(activity, R.drawable.il_chevron, tokens.muted);
        if (chevron != null) arrow.setImageDrawable(chevron);
        arrow.setAlpha(0.55f);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(18), dp(18)));
        row.setOnClickListener(view -> runCommand(row, action));
        parent.addView(row);
        return new Entry(row, descriptionView, valueView);
    }

    LinearLayout toggle(String label, String description, String trailingValue,
                        boolean initial, ToggleListener listener) {
        LinearLayout row = vertical(0);
        LinearLayout content = new LinearLayout(activity);
        content.setGravity(Gravity.CENTER_VERTICAL);
        int horizontal = dp(roundLayout ? 10 : 12);
        int vertical = dp(roundLayout ? 7 : 9);
        content.setPadding(horizontal, vertical, horizontal, vertical);

        ImageView icon = new ImageView(activity);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        Drawable drawable = Compat.tintedDrawable(activity, toggleIcon(label), tokens.text);
        if (drawable != null) icon.setImageDrawable(drawable);
        Compat.setBackground(icon, UiComponents.monoChip(activity, tokens, uiScale));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.rightMargin = dp(10);
        content.addView(icon, iconParams);

        LinearLayout copy = vertical(0);
        TextView title = text(label, 15.0f, tokens.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(title);
        if (!TextUtils.isEmpty(description)) {
            TextView detail = text(description, 11.0f, tokens.muted);
            detail.setPadding(0, dp(1), 0, 0);
            copy.addView(detail);
        }
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        copyParams.rightMargin = dp(6);
        content.addView(copy, copyParams);
        if (!TextUtils.isEmpty(trailingValue)) {
            TextView trailing = text(trailingValue, 12.0f, tokens.muted);
            trailing.setSingleLine(true);
            LinearLayout.LayoutParams trailingParams = new LinearLayout.LayoutParams(-2, -2);
            trailingParams.rightMargin = dp(8);
            content.addView(trailing, trailingParams);
        }

        boolean[] enabled = {initial};
        FrameLayout toggle = new FrameLayout(activity);
        View thumb = new View(activity);
        Compat.setBackground(thumb, UiComponents.round(activity, Color.WHITE, 9, uiScale));
        FrameLayout.LayoutParams thumbParams = new FrameLayout.LayoutParams(
                dp(18), dp(18), Gravity.CENTER_VERTICAL);
        thumbParams.leftMargin = dp(3);
        toggle.addView(thumb, thumbParams);
        int travel = dp(18);
        Runnable paintTrack = () -> Compat.setBackground(toggle,
                UiComponents.round(activity, toggleTrackColor(enabled[0]), 12, uiScale));
        paintTrack.run();
        thumb.setTranslationX(enabled[0] ? travel : 0.0f);
        content.addView(toggle, new LinearLayout.LayoutParams(dp(42), dp(24)));
        content.setMinimumHeight(dp(60));
        row.addView(content, new LinearLayout.LayoutParams(-1, -2));
        addRowDivider(row);
        content.setOnClickListener(view -> {
            enabled[0] = !enabled[0];
            paintTrack.run();
            thumb.animate().cancel();
            thumb.animate().translationX(enabled[0] ? travel : 0.0f)
                    .setDuration(MotionSpec.ENTER_MS)
                    .setInterpolator(MotionSpec.EASE_OUT)
                    .start();
            listener.onChanged(enabled[0]);
        });
        row.setTag("settings_row_with_divider");
        return row;
    }

    Entry addRangeEntry(LinearLayout parent, String label, String unit, int icon,
                        int min, int max, int step, int current,
                        IntListener listener, Runnable afterDismiss) {
        Entry[] entry = new Entry[1];
        int[] selected = {current};
        entry[0] = addEntry(parent, label, null, formatValue(current, unit), icon, () ->
                showRangeDialog(label, unit, min, max, step, selected[0], value -> {
                    selected[0] = value;
                    listener.onChanged(value);
                    entry[0].value.setText(formatValue(value, unit));
                }, afterDismiss));
        return entry[0];
    }

    Entry addChoiceEntry(LinearLayout parent, String label, int icon,
                         String[] options, int selected, IntListener listener,
                         Runnable afterDismiss) {
        Entry[] entry = new Entry[1];
        int[] chosen = {Math.max(0, Math.min(options.length - 1, selected))};
        entry[0] = addEntry(parent, label, null, options[chosen[0]], icon, () ->
                showChoiceDialog(label, options, chosen[0], value -> {
                    chosen[0] = value;
                    listener.onChanged(value);
                    entry[0].value.setText(options[value]);
                }, afterDismiss));
        return entry[0];
    }

    Entry addTextEntry(LinearLayout parent, String label, String value,
                       int icon, TextListener listener) {
        Entry[] entry = new Entry[1];
        String[] current = {value == null ? "" : value};
        entry[0] = addEntry(parent, label, null, current[0], icon, () ->
                showTextDialog(label, current[0], updated -> {
                    current[0] = updated;
                    listener.onChanged(updated);
                    if (entry[0].value != null) entry[0].value.setText(updated);
                }));
        return entry[0];
    }

    LinearLayout dialogPanel(String title) {
        LinearLayout box = vertical(tokens.panel);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        Compat.setBackground(box, UiComponents.round(activity, tokens.panel, 12, uiScale));
        TextView titleView = text(title, 17.0f, tokens.text);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    void present(AlertDialog dialog, View content) {
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

    private void showRangeDialog(String title, String unit, int min, int max,
                                 int step, int current, IntListener listener,
                                 Runnable afterDismiss) {
        LinearLayout box = dialogPanel(title);
        LinearLayout valueRow = new LinearLayout(activity);
        valueRow.setGravity(Gravity.CENTER_VERTICAL);
        valueRow.addView(text("拖动调整", 11.0f, tokens.muted),
                new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView valueView = text(formatValue(current, unit), 19.0f, tokens.text);
        valueView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        valueRow.addView(valueView, new LinearLayout.LayoutParams(-2, -2));
        addTop(box, valueRow, 6);

        SettingRangeView range = new SettingRangeView(activity, tokens,
                min, max, step, current);
        LinearLayout.LayoutParams rangeParams = new LinearLayout.LayoutParams(-1, dp(52));
        rangeParams.topMargin = dp(6);
        box.addView(range, rangeParams);
        LinearLayout limits = new LinearLayout(activity);
        limits.setGravity(Gravity.CENTER_VERTICAL);
        limits.addView(text(formatValue(min, unit), 10.0f, tokens.muted),
                new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView upper = text(formatValue(max, unit), 10.0f, tokens.muted);
        upper.setGravity(Gravity.RIGHT);
        limits.addView(upper, new LinearLayout.LayoutParams(0, -2, 1.0f));
        addTop(box, limits, 0);

        boolean[] changed = {false};
        range.setListener(value -> {
            changed[0] = true;
            valueView.setText(formatValue(value, unit));
            listener.onChanged(value);
        });
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(box).create();
        TextView done = dialogAction("确定", dialog);
        addDialogAction(box, done);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(value -> {
            if (changed[0] && afterDismiss != null && !activity.isFinishing()) {
                handler.post(afterDismiss);
            }
        });
        present(dialog, box);
    }

    private void showChoiceDialog(String title, String[] options, int selected,
                                  IntListener listener, Runnable afterDismiss) {
        LinearLayout box = dialogPanel(title);
        LinearLayout list = vertical(0);
        addTop(box, list, 8);
        int[] choice = {-1};
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(box).create();
        for (int i = 0; i < options.length; i++) {
            int index = i;
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), 0, dp(8), 0);
            TextView label = text(options[i], 14.0f,
                    i == selected ? tokens.text : tokens.muted);
            label.setTypeface(Typeface.DEFAULT,
                    i == selected ? Typeface.BOLD : Typeface.NORMAL);
            row.addView(label, new LinearLayout.LayoutParams(0, dp(46), 1.0f));
            TextView check = text(i == selected ? "✓" : "", 16.0f, tokens.text);
            check.setGravity(Gravity.CENTER);
            row.addView(check, new LinearLayout.LayoutParams(dp(28), dp(46)));
            row.setOnClickListener(view -> {
                choice[0] = index;
                runCommand(row, dialog::dismiss);
            });
            list.addView(row);
            if (i < options.length - 1) addChoiceDivider(list);
        }
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(value -> {
            if (choice[0] < 0) return;
            listener.onChanged(choice[0]);
            if (afterDismiss != null && !activity.isFinishing()) handler.post(afterDismiss);
        });
        present(dialog, box);
    }

    private void showTextDialog(String title, String current, TextListener listener) {
        LinearLayout box = dialogPanel(title);
        EditText input = new EditText(activity);
        input.setText(current);
        input.setTextColor(tokens.text);
        input.setTextSize(sp(14.0f));
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(12), 0, dp(12), 0);
        Compat.setBackground(input, UiComponents.round(
                activity, tokens.panelElevated, 10, uiScale));
        box.addView(input, new LinearLayout.LayoutParams(-1, dp(44)));
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(box).create();
        TextView done = dialogAction("确定", dialog);
        addDialogAction(box, done);
        dialog.setCanceledOnTouchOutside(true);
        done.setOnClickListener(view -> {
            listener.onChanged(input.getText().toString().trim());
            dialog.dismiss();
        });
        present(dialog, box);
    }

    private TextView dialogAction(String label, AlertDialog dialog) {
        TextView action = text(label, 13.0f, ThemeTokens.contrast(tokens.text));
        action.setGravity(Gravity.CENTER);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        action.setMinWidth(dp(72));
        action.setPadding(dp(14), 0, dp(14), 0);
        Compat.setBackground(action, UiComponents.round(
                activity, tokens.text, 11, uiScale));
        action.setOnClickListener(view -> dialog.dismiss());
        return action;
    }

    private void addDialogAction(LinearLayout parent, TextView action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.END);
        row.addView(action, new LinearLayout.LayoutParams(-2, dp(36)));
        addTop(parent, row, 12);
    }

    void addDivider(LinearLayout parent) {
        if (parent.getChildCount() == 0) return;
        View last = parent.getChildAt(parent.getChildCount() - 1);
        if ("settings_divider".equals(last.getTag())
                || "settings_row_with_divider".equals(last.getTag())) return;
        View divider = new View(activity);
        divider.setTag("settings_divider");
        divider.setBackgroundColor(dividerColor());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                -1, Math.max(1, dp(1) / 2));
        params.leftMargin = dp(60);
        parent.addView(divider, params);
    }

    private void addRowDivider(LinearLayout row) {
        View divider = new View(activity);
        divider.setTag("settings_divider");
        divider.setBackgroundColor(dividerColor());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                -1, Math.max(1, dp(1) / 2));
        params.leftMargin = dp(60);
        row.addView(divider, params);
    }

    private void addChoiceDivider(LinearLayout list) {
        View divider = new View(activity);
        divider.setBackgroundColor(tokens.hairline);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                -1, Math.max(1, dp(1) / 2));
        params.leftMargin = dp(10);
        list.addView(divider, params);
    }

    private int dividerColor() {
        return session.darkMode() ? Color.argb(16, 255, 255, 255)
                : Color.argb(14, 0, 0, 0);
    }

    private int toggleTrackColor(boolean enabled) {
        if (session.darkMode()) return enabled
                ? Color.rgb(119, 119, 125) : Color.rgb(58, 58, 62);
        return enabled ? Color.rgb(166, 166, 171) : Color.rgb(209, 209, 214);
    }

    private int toggleIcon(String label) {
        if (label.contains("夜间")) return R.drawable.il_sun;
        if (label.contains("圆屏")) return R.drawable.il_round_screen;
        if (label.contains("加粗")) return R.drawable.il_bold;
        if (label.contains("无图")) return R.drawable.il_image;
        if (label.contains("动图")) return R.drawable.il_gif;
        if (label.contains("原图")) return R.drawable.il_zoom;
        if (label.contains("退出确认")) return R.drawable.il_gesture_block;
        if (label.contains("右滑")) return R.drawable.il_swipe;
        if (label.contains("阅读位置") || label.contains("表冠")) return R.drawable.il_scroll;
        if (label.contains("清理")) return R.drawable.il_cleanup;
        if (label.contains("评论回复")) return R.drawable.il_reply;
        if (label.contains("更新")) return R.drawable.il_update;
        if (label.contains("开屏")) return R.drawable.il_splash;
        return R.drawable.il_settings;
    }

    private String formatValue(int value, String unit) {
        if (TextUtils.isEmpty(unit)) return String.valueOf(value);
        return value + ("%".equals(unit) ? "%" : " " + unit);
    }

    private void runCommand(View view, Runnable action) {
        UiComponents.press(view);
        action.run();
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(activity, value, size, color, textScale);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private LinearLayout vertical(int color) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    private void addTop(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(view, params);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density * uiScale);
    }

    private float sp(float value) {
        return value * textScale;
    }
}
