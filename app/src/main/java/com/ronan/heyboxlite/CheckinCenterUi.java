package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small, stateless view factory for the check-in pages. */
final class CheckinCenterUi {
    private final Activity activity;
    private final SessionStore session;
    private final ThemeTokens tokens;
    private final float scale;
    private final boolean roundLayout;

    CheckinCenterUi(Activity activity, SessionStore session, ThemeTokens tokens,
                    boolean roundLayout) {
        this.activity = activity;
        this.session = session;
        this.tokens = tokens;
        this.scale = session.uiScale() / 100.0f;
        this.roundLayout = roundLayout;
    }

    void addTop(ViewGroup parent, View child, int marginDp) {
        LinearLayout.LayoutParams params = child.getLayoutParams()
                instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) child.getLayoutParams()
                : new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(marginDp);
        parent.addView(child, params);
    }

    LinearLayout column(int color) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    TextView body(String value, int color) {
        return label(value, 12f, color);
    }

    TextView label(String value, float size, int color) {
        TextView view = UiComponents.label(activity, value, size, color,
                session.textScale() / 100.0f);
        view.setIncludeFontPadding(false);
        return view;
    }

    Button primaryButton(String value) {
        Button button = baseButton(value);
        button.setTextColor(tokens.onPrimary);
        Compat.setBackground(button, UiComponents.primaryButton(activity, tokens, scale));
        return button;
    }

    Button ghostButton(String value) {
        Button button = baseButton(value);
        button.setTextColor(tokens.text);
        Compat.setBackground(button, UiComponents.ghostButton(activity, tokens, scale));
        return button;
    }

    Button segmentButton(String value, boolean selected) {
        Button button = baseButton(value);
        button.setTextColor(selected ? tokens.text : tokens.muted);
        Compat.setBackground(button, UiComponents.round(activity,
                selected ? tokens.pressedSurface() : Color.TRANSPARENT, 8, scale));
        return button;
    }

    Button compactActionButton(String value) {
        Button button = baseButton(value);
        button.setTextSize(11f * session.textScale() / 100.0f);
        button.setTextColor(tokens.text);
        button.setPadding(dp(7), 0, dp(7), 0);
        Compat.setBackground(button, UiComponents.round(
                activity, tokens.pressedSurface(), 9, scale));
        return button;
    }

    Button quietButton(String value) {
        Button button = baseButton(value);
        button.setTextColor(tokens.muted);
        Compat.setBackground(button, UiComponents.round(
                activity, Color.TRANSPARENT, 10, scale));
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(36)));
        return button;
    }

    Button compactButton(String value) {
        Button button = baseButton(value);
        button.setTextColor(tokens.text);
        Compat.setBackground(button, UiComponents.round(
                activity, tokens.panelElevated, 9, scale));
        button.setTextSize(16f * session.textScale() / 100.0f);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    EditText input(String hint, int inputType) {
        EditText view = new EditText(activity);
        view.setSingleLine(true);
        view.setTextSize(13f * session.textScale() / 100.0f);
        view.setTextColor(tokens.text);
        view.setHintTextColor(tokens.muted);
        view.setHint(hint);
        view.setInputType(inputType);
        view.setSaveEnabled(false);
        view.setMinHeight(0);
        view.setMinimumHeight(0);
        view.setPadding(dp(11), 0, dp(11), 0);
        Compat.setBackground(view, UiComponents.round(
                activity, tokens.panelElevated, 9, scale));
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(roundLayout ? 40 : 42)));
        return view;
    }

    TextView errorBanner(String message) {
        TextView view = body(message, tokens.text);
        view.setPadding(dp(11), dp(9), dp(11), dp(9));
        view.setLineSpacing(0f, 1.12f);
        Compat.setBackground(view, UiComponents.round(activity,
                ThemeTokens.blend(tokens.panel, Color.rgb(190, 55, 55),
                        tokens.dark ? 0.22f : 0.10f), 10, scale));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(9);
        view.setLayoutParams(params);
        return view;
    }

    LinearLayout statusHeader(int iconRes, String title, String subtitle,
                              int titleColor, boolean showProgress) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View icon = showProgress && !Motions.off() ? progressTile() : iconTile(iconRes);
        int iconSize = dp(roundLayout ? 38 : 42);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.rightMargin = dp(10);
        row.addView(icon, iconParams);

        LinearLayout copy = column(Color.TRANSPARENT);
        TextView titleView = label(title, roundLayout ? 15f : 16f, titleColor);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setMaxLines(2);
        copy.addView(titleView);
        TextView subtitleView = body(subtitle, tokens.muted);
        subtitleView.setMaxLines(2);
        subtitleView.setPadding(0, dp(2), 0, 0);
        copy.addView(subtitleView);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    LinearLayout card() {
        LinearLayout card = column(Color.TRANSPARENT);
        int horizontal = roundLayout ? dp(12) : dp(14);
        int vertical = roundLayout ? dp(11) : dp(13);
        card.setPadding(horizontal, vertical, horizontal, vertical);
        Compat.setBackground(card, UiComponents.card(activity, tokens, scale));
        return card;
    }

    void applyPageInsets(LinearLayout page, boolean subpage) {
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int height = activity.getResources().getDisplayMetrics().heightPixels;
        int horizontal = roundLayout
                ? RoundLayoutMetrics.componentInset(width,
                RoundLayoutMetrics.PAGE_HORIZONTAL_RATIO, dp(10)) : dp(10);
        int top = roundLayout
                ? RoundLayoutMetrics.componentInset(Math.min(width, height),
                subpage ? RoundLayoutMetrics.SUBPAGE_TOP_RATIO
                        : RoundLayoutMetrics.PAGE_TOP_RATIO, dp(8)) : dp(8);
        page.setPadding(horizontal, top, horizontal,
                roundLayout ? Math.max(top, dp(18)) : dp(18));
    }

    private View progressTile() {
        FrameLayout tile = new FrameLayout(activity);
        Compat.setBackground(tile, UiComponents.monoChip(activity, tokens, scale));
        LoadingSpinnerView spinner = new LoadingSpinnerView(activity);
        spinner.setColor(tokens.text);
        int size = dp(roundLayout ? 19 : 21);
        tile.addView(spinner, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        return tile;
    }

    ImageView iconTile(int iconRes) {
        ImageView icon = new ImageView(activity);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        Drawable drawable = Compat.tintedDrawable(activity, iconRes, tokens.text);
        if (drawable != null) icon.setImageDrawable(drawable);
        Compat.setBackground(icon, UiComponents.monoChip(activity, tokens, scale));
        return icon;
    }

    private Button baseButton(String value) {
        Button button = UiComponents.button(activity);
        button.setText(value);
        button.setTextSize(12f * session.textScale() / 100.0f);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(roundLayout ? 38 : 42)));
        return button;
    }

    private int dp(int value) {
        return UiComponents.dp(activity, value, scale);
    }
}
