package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Builds the small account/device restriction page outside the activity coordinator. */
final class AccountBlockedView {
    static final class Result {
        final LinearLayout root;
        final TextView message;

        Result(LinearLayout root, TextView message) {
            this.root = root;
            this.message = message;
        }
    }

    private AccountBlockedView() {
    }

    static Result create(Activity activity, ThemeTokens tokens, int horizontalPadding,
                         int verticalPadding, Runnable retryAction) {
        int density = Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density));
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(tokens.background);
        root.setPadding(horizontalPadding, dp(verticalPadding, density),
                horizontalPadding, dp(verticalPadding, density));

        TextView title = text(activity, "无法使用", 20f, tokens.text);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView message = text(activity, "", 12.5f, tokens.muted);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(dp(2, density), 1.15f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, -2);
        messageParams.topMargin = dp(10, density);
        root.addView(message, messageParams);

        TextView retry = text(activity, "重新检查", 12.5f, Color.WHITE);
        retry.setGravity(Gravity.CENTER);
        retry.setPadding(dp(18, density), 0, dp(18, density), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(tokens.primary);
        background.setCornerRadius(dp(8, density));
        Compat.setBackground(retry, background);
        retry.setOnClickListener(view -> retryAction.run());
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(-2, dp(38, density));
        retryParams.topMargin = dp(18, density);
        root.addView(retry, retryParams);
        return new Result(root, message);
    }

    private static TextView text(Activity activity, String value, float size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private static int dp(int value, int density) {
        return Math.round(value * density);
    }
}
