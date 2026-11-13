package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** View-only header for the retained native feed. */
final class NativeFeedHeader {
    private final Activity activity;
    private final SessionStore session;
    private final ThemeTokens tokens;
    private final boolean round;
    private final FeedPage.Host host;

    NativeFeedHeader(Activity activity, SessionStore session, ThemeTokens tokens,
                     boolean round, FeedPage.Host host) {
        this.activity = activity;
        this.session = session;
        this.tokens = tokens;
        this.round = round;
        this.host = host;
    }

    View create() {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(tokens.background);
        int horizontal = round ? 0 : dp(10);
        container.setPadding(horizontal, round ? host.roundHeaderTopPadding() : dp(6),
                horizontal, dp(4));
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        int inset = round ? host.roundHeaderInset() : dp(2);
        heading.setPadding(inset, 0, inset, 0);
        TextView title = label("社区", round ? 21f : 23f, tokens.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(-1, dp(round ? 34 : 38)));
        container.addView(heading);
        LinearLayout searchRow = new LinearLayout(activity);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(11), 0, dp(8), 0);
        Compat.setBackground(searchRow, UiComponents.round(activity, tokens.panel, 12,
                session.uiScale() / 100f));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(round ? 35 : 40));
        if (round) {
            params.leftMargin = host.roundSearchInset();
            params.rightMargin = params.leftMargin;
        }
        container.addView(searchRow, params);
        TextView search = label("搜索帖子、作者或关键词", 12.5f, tokens.muted);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setSingleLine(true);
        android.graphics.drawable.Drawable icon = Compat.tintedDrawable(activity,
                R.drawable.il_search, tokens.muted);
        if (icon != null) {
            icon.setBounds(0, 0, dp(16), dp(16));
            search.setCompoundDrawables(icon, null, null, null);
            search.setCompoundDrawablePadding(dp(7));
        }
        search.setOnClickListener(view -> host.showSearch());
        searchRow.addView(search, new LinearLayout.LayoutParams(0, -1, 1f));
        return container;
    }

    private TextView label(String value, float size, int color) {
        TextView view = UiComponents.label(activity, value, size, color, session.textScale() / 100f);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private int dp(int value) {
        return UiComponents.dp(activity, value, session.uiScale() / 100f);
    }
}
