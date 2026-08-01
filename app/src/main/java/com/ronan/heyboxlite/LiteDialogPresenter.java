package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class LiteDialogPresenter {
    private final Activity activity;
    private final SessionStore session;
    private ThemeTokens tokens;

    LiteDialogPresenter(Activity activity, SessionStore session, ThemeTokens tokens) {
        this.activity = activity;
        this.session = session;
        this.tokens = tokens;
    }

    void updateTheme(ThemeTokens tokens) {
        this.tokens = tokens;
    }

    void show(String title, String message, String positiveText, Runnable positiveAction,
              String negativeText, Runnable negativeAction,
              String neutralText, Runnable neutralAction) {
        if (this.activity.isFinishing()) return;

        LinearLayout content = new LinearLayout(this.activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable background = UiComponents.round(
                this.activity, this.tokens.panel, 12, uiScale());
        background.setStroke(Math.max(1, dp(1)), this.tokens.hairline);
        Compat.setBackground(content, background);

        TextView titleView = text(title, 17.0f, this.tokens.text);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        titleView.setLineSpacing(0.0f, 1.08f);
        content.addView(titleView, new LinearLayout.LayoutParams(-1, -2));

        MaxHeightScrollView scroll = new MaxHeightScrollView(this.activity);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        TextView body = text(message, 13.0f, this.tokens.text);
        body.setLineSpacing(dp(2), 1.18f);
        body.setTextIsSelectable(true);
        body.setPadding(0, dp(10), 0, dp(2));
        int availableHeight = this.activity.getResources().getDisplayMetrics().heightPixels - dp(230);
        scroll.setMaxHeight(Math.max(dp(96), Math.min(availableHeight, dp(330))));
        scroll.addView(body, new android.widget.FrameLayout.LayoutParams(-1, -2));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog[] holder = new AlertDialog[1];
        List<TextView> actions = new ArrayList<>();
        addAction(actions, neutralText, false, holder, neutralAction);
        addAction(actions, negativeText, false, holder, negativeAction);
        addAction(actions, positiveText, true, holder, positiveAction);
        addActions(content, actions);

        AlertDialog dialog = new AlertDialog.Builder(this.activity).setView(content).create();
        holder[0] = dialog;
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            dialog.getWindow().setDimAmount(this.session.darkMode() ? 0.46f : 0.32f);
            dialog.getWindow().setLayout(dialogWidth(), -2);
        }
        Motions.dialogIn(content);
    }

    private void addAction(List<TextView> actions, String label, boolean primary,
                           AlertDialog[] holder, Runnable action) {
        if (!TextUtils.isEmpty(label)) {
            actions.add(action(label, primary, holder, action));
        }
    }

    private void addActions(LinearLayout content, List<TextView> actionViews) {
        if (actionViews.isEmpty()) return;
        boolean compact = actionsFitInOneRow(actionViews);
        LinearLayout actions = new LinearLayout(this.activity);
        actions.setOrientation(compact ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        actions.setGravity(compact ? Gravity.END : Gravity.CENTER_HORIZONTAL);
        actions.setPadding(0, dp(8), 0, 0);
        for (int i = 0; i < actionViews.size(); i++) {
            LinearLayout.LayoutParams params = compact
                    ? new LinearLayout.LayoutParams(-2, dp(36))
                    : new LinearLayout.LayoutParams(-1, dp(36));
            if (i > 0) {
                if (compact) params.leftMargin = dp(7);
                else params.topMargin = dp(7);
            }
            actions.addView(actionViews.get(i), params);
        }
        content.addView(actions);
    }

    private TextView action(String label, boolean primary, AlertDialog[] holder,
                            Runnable command) {
        int fill = primary ? this.tokens.text : this.tokens.panelElevated;
        int color = primary ? ThemeTokens.contrast(this.tokens.text) : this.tokens.text;
        TextView view = text(label, 13.0f, color);
        view.setTypeface(android.graphics.Typeface.DEFAULT,
                primary ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(dp(72));
        view.setMinHeight(dp(36));
        view.setPadding(dp(12), 0, dp(12), 0);
        Compat.setBackground(view, UiComponents.round(
                this.activity, fill, 11, uiScale()));
        view.setOnClickListener(ignored -> {
            UiComponents.press(view);
            AlertDialog dialog = holder[0];
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
            if (command != null && !this.activity.isFinishing()) command.run();
        });
        return view;
    }

    private boolean actionsFitInOneRow(List<TextView> actions) {
        if (actions.size() > 2) return false;
        int requiredWidth = dp(7) * Math.max(0, actions.size() - 1);
        for (TextView action : actions) {
            int textWidth = (int) Math.ceil(
                    action.getPaint().measureText(action.getText().toString()));
            requiredWidth += Math.max(dp(72), textWidth
                    + action.getPaddingLeft() + action.getPaddingRight());
        }
        return requiredWidth <= dialogWidth() - dp(32);
    }

    private int dialogWidth() {
        int screenWidth = this.activity.getResources().getDisplayMetrics().widthPixels;
        int preferred = Math.max(dp(220), Math.min(screenWidth - dp(28), dp(360)));
        return Math.max(1, Math.min(screenWidth - dp(8), preferred));
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(
                this.activity, value, size, color, this.session.textScale() / 100.0f);
        view.setTypeface(android.graphics.Typeface.DEFAULT);
        return view;
    }

    private int dp(int value) {
        return UiComponents.dp(this.activity, value, uiScale());
    }

    private float uiScale() {
        return this.session.uiScale() / 100.0f;
    }
}
