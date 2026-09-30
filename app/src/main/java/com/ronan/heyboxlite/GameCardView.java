package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class GameCardView extends FrameLayout {
    private final Activity activity;
    private final SessionStore session;
    private final ThemeTokens tokens;
    private final boolean roundLayout;
    private final float uiScale;

    private GameCardView(Activity activity, SessionStore session,
                         ThemeTokens tokens, boolean roundLayout) {
        super(activity);
        this.activity = activity;
        this.session = session;
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.uiScale = session.uiScale() / 100.0f;
        setMinimumHeight(dp(roundLayout ? 68 : 78));
        setPadding(dp(7), dp(7), dp(8), dp(7));
        Compat.setBackground(this, UiComponents.card(activity, tokens, uiScale));
    }

    static GameCardView loading(Activity activity, SessionStore session,
                                ThemeTokens tokens, boolean roundLayout,
                                String title) {
        GameCardView card = new GameCardView(activity, session, tokens, roundLayout);
        LinearLayout row = card.row();
        TextView label = card.text(title.isEmpty() ? "游戏" : title, 12.5f, tokens.text);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView state = card.text("正在获取游戏信息", 10.0f, tokens.muted);
        LinearLayout copy = card.column();
        copy.addView(label);
        copy.addView(state, card.topParams(3));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1.0f));
        card.addView(row, new FrameLayout.LayoutParams(-1, -2));
        return card;
    }

    void bind(GameCardData data) {
        if (data == null) {
            showUnavailable("");
            return;
        }
        removeAllViews();
        LinearLayout row = row();
        if (!data.coverUrl.isEmpty() && !session.noImage()) {
            ImageView cover = new ImageView(activity);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Compat.setBackground(cover, UiComponents.round(activity,
                    placeholderColor(), 6, uiScale));
            Compat.clipToOutline(cover);
            int width = dp(roundLayout ? 58 : 72);
            int height = dp(roundLayout ? 54 : 64);
            row.addView(cover, new LinearLayout.LayoutParams(width, height));
            ImageLoader.intoMeasuredRevealStable(cover, data.coverUrl,
                    Math.max(180, width * 3), null);
        } else {
            TextView placeholder = text("游戏", 10.0f, tokens.muted);
            placeholder.setGravity(Gravity.CENTER);
            Compat.setBackground(placeholder, UiComponents.round(activity,
                    placeholderColor(), 6, uiScale));
            int width = dp(roundLayout ? 58 : 72);
            row.addView(placeholder, new LinearLayout.LayoutParams(width,
                    dp(roundLayout ? 54 : 64)));
        }

        LinearLayout copy = column();
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, -2, 1.0f);
        copyParams.leftMargin = dp(8);
        row.addView(copy, copyParams);

        TextView name = text(data.name.isEmpty() ? "游戏" : data.name,
                roundLayout ? 12.0f : 12.5f, tokens.text);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(name);

        String info = join(data.platforms, data.score.isEmpty()
                ? "" : "评分 " + data.score, data.followers.isEmpty()
                ? "" : "关注 " + data.followers);
        if (!info.isEmpty()) {
            TextView metadata = text(info, 9.5f, tokens.muted);
            metadata.setSingleLine(true);
            metadata.setEllipsize(android.text.TextUtils.TruncateAt.END);
            copy.addView(metadata, topParams(3));
        }

        String current = data.free ? "免费" : price(data.currentPrice);
        if (!current.isEmpty() || !data.discount.isEmpty()) {
            LinearLayout priceRow = new LinearLayout(activity);
            priceRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView currentView = text(current, 11.5f, tokens.text);
            currentView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            priceRow.addView(currentView);
            if (!data.originalPrice.isEmpty()
                    && !data.originalPrice.equals(data.currentPrice)) {
                TextView original = text(price(data.originalPrice), 9.5f, tokens.muted);
                original.setPaintFlags(original.getPaintFlags()
                        | Paint.STRIKE_THRU_TEXT_FLAG);
                LinearLayout.LayoutParams originalParams = topParams(0);
                originalParams.leftMargin = dp(5);
                priceRow.addView(original, originalParams);
            }
            if (!data.discount.isEmpty() && !"0".equals(data.discount)) {
                TextView discount = text("-" + data.discount + "%", 9.0f,
                        tokens.secondary);
                LinearLayout.LayoutParams discountParams = topParams(0);
                discountParams.leftMargin = dp(5);
                priceRow.addView(discount, discountParams);
            }
            copy.addView(priceRow, topParams(3));
        }
        addView(row, new FrameLayout.LayoutParams(-1, -2));
    }

    void showUnavailable(String appId) {
        removeAllViews();
        LinearLayout row = row();
        TextView placeholder = text("游戏", 10.0f, tokens.muted);
        placeholder.setGravity(Gravity.CENTER);
        Compat.setBackground(placeholder, UiComponents.round(activity,
                placeholderColor(), 6, uiScale));
        row.addView(placeholder, new LinearLayout.LayoutParams(
                dp(roundLayout ? 58 : 72), dp(roundLayout ? 52 : 62)));

        LinearLayout copy = column();
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, -2, 1.0f);
        copyParams.leftMargin = dp(8);
        row.addView(copy, copyParams);
        TextView title = text("游戏信息暂不可用", roundLayout ? 12.0f : 12.5f,
                tokens.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(title);
        String id = appId == null ? "" : appId.trim();
        if (!id.isEmpty()) {
            copy.addView(text("ID " + id, 9.5f, tokens.muted), topParams(3));
        }
        addView(row, new FrameLayout.LayoutParams(-1, -2));
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private TextView text(String value, float size, int color) {
        return UiComponents.label(activity, value, size, color,
                session.textScale() / 100.0f);
    }

    private LinearLayout.LayoutParams topParams(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.topMargin = dp(margin);
        return params;
    }

    private String price(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        return value.startsWith("¥") || value.startsWith("￥")
                ? value : "¥" + value;
    }

    private String join(String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (result.length() > 0) result.append(" · ");
            result.append(value.trim());
        }
        return result.toString();
    }

    private int placeholderColor() {
        return session.darkMode() ? Color.rgb(39, 40, 43)
                : Color.rgb(231, 233, 236);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources()
                .getDisplayMetrics().density * uiScale);
    }
}
