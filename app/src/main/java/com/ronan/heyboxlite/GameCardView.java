package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
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
    private final ImageView cover;
    private final TextView coverPlaceholder;
    private final TextView nameView;
    private final TextView stateView;
    private final TextView metadataView;
    private final LinearLayout priceRow;
    private final TextView currentPriceView;
    private final TextView originalPriceView;
    private final TextView discountView;

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

        LinearLayout content = row();
        int coverWidth = dp(roundLayout ? 58 : 72);
        int coverHeight = dp(roundLayout ? 54 : 64);
        FrameLayout coverFrame = new FrameLayout(activity);
        this.cover = new ImageView(activity);
        this.cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Compat.setBackground(this.cover, UiComponents.round(activity,
                placeholderColor(), 6, uiScale));
        Compat.clipToOutline(this.cover);
        coverFrame.addView(this.cover, new FrameLayout.LayoutParams(-1, -1));
        this.coverPlaceholder = text("游戏", 10.0f, tokens.muted);
        this.coverPlaceholder.setGravity(Gravity.CENTER);
        Compat.setBackground(this.coverPlaceholder, UiComponents.round(activity,
                placeholderColor(), 6, uiScale));
        coverFrame.addView(this.coverPlaceholder,
                new FrameLayout.LayoutParams(-1, -1));
        content.addView(coverFrame,
                new LinearLayout.LayoutParams(coverWidth, coverHeight));

        LinearLayout copy = column();
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, -2, 1.0f);
        copyParams.leftMargin = dp(8);
        content.addView(copy, copyParams);

        this.nameView = text("游戏", roundLayout ? 12.0f : 12.5f, tokens.text);
        this.nameView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        this.nameView.setMaxLines(2);
        this.nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(this.nameView);

        this.stateView = text("", 10.0f, tokens.muted);
        copy.addView(this.stateView, topParams(3));

        this.metadataView = text("", 9.5f, tokens.muted);
        this.metadataView.setSingleLine(true);
        this.metadataView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(this.metadataView, topParams(3));

        this.priceRow = new LinearLayout(activity);
        this.priceRow.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(this.priceRow, topParams(3));
        this.currentPriceView = text("", 11.5f, tokens.text);
        this.currentPriceView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        this.priceRow.addView(this.currentPriceView);
        this.originalPriceView = text("", 9.5f, tokens.muted);
        this.originalPriceView.setPaintFlags(this.originalPriceView.getPaintFlags()
                | Paint.STRIKE_THRU_TEXT_FLAG);
        LinearLayout.LayoutParams originalParams = topParams(0);
        originalParams.leftMargin = dp(5);
        this.priceRow.addView(this.originalPriceView, originalParams);
        this.discountView = text("", 9.0f, tokens.secondary);
        LinearLayout.LayoutParams discountParams = topParams(0);
        discountParams.leftMargin = dp(5);
        this.priceRow.addView(this.discountView, discountParams);

        addView(content, new FrameLayout.LayoutParams(-1, -2));
        showLoading("游戏");
    }

    static GameCardView loading(Activity activity, SessionStore session,
                                ThemeTokens tokens, boolean roundLayout,
                                String title) {
        GameCardView card = new GameCardView(activity, session, tokens, roundLayout);
        card.showLoading(title);
        return card;
    }

    void bind(GameCardData data) {
        if (data == null) {
            showUnavailable("");
            return;
        }

        this.nameView.setText(data.name.isEmpty() ? "游戏" : data.name);
        this.stateView.setVisibility(View.GONE);
        String info = join(data.platforms, data.score.isEmpty()
                ? "" : "评分 " + data.score, data.followers.isEmpty()
                ? "" : "关注 " + data.followers);
        setMetadata(info);
        setPrice(data);

        this.cover.setTag(null);
        this.cover.setImageDrawable(null);
        if (!data.coverUrl.isEmpty() && !session.noImage()) {
            this.cover.setVisibility(View.VISIBLE);
            this.coverPlaceholder.setVisibility(View.VISIBLE);
            ImageLoader.intoMeasuredStable(this.cover, data.coverUrl,
                    Math.max(180, dp(roundLayout ? 58 : 72) * 3),
                    (success, bitmap) -> {
                        if (success && bitmap != null) {
                            this.coverPlaceholder.setVisibility(View.GONE);
                        }
                    });
        } else {
            this.cover.setVisibility(View.GONE);
            this.coverPlaceholder.setVisibility(View.VISIBLE);
        }
    }

    private void setMetadata(String info) {
        this.metadataView.setText(info);
        this.metadataView.setVisibility(info.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setPrice(GameCardData data) {
        String current = data.free ? "免费" : price(data.currentPrice);
        this.currentPriceView.setText(current);
        String original = !data.originalPrice.isEmpty()
                && !data.originalPrice.equals(data.currentPrice)
                ? price(data.originalPrice) : "";
        this.originalPriceView.setText(original);
        String discount = !data.discount.isEmpty() && !"0".equals(data.discount)
                ? "-" + data.discount + "%" : "";
        this.discountView.setText(discount);
        boolean visible = !current.isEmpty() || !original.isEmpty() || !discount.isEmpty();
        this.priceRow.setVisibility(visible ? View.VISIBLE : View.GONE);
        this.originalPriceView.setVisibility(original.isEmpty() ? View.GONE : View.VISIBLE);
        this.discountView.setVisibility(discount.isEmpty() ? View.GONE : View.VISIBLE);
    }

    void showUnavailable(String appId) {
        this.nameView.setText("游戏信息暂不可用");
        this.stateView.setVisibility(View.GONE);
        setMetadata("");
        this.priceRow.setVisibility(View.GONE);
        this.cover.setTag(null);
        this.cover.setImageDrawable(null);
        this.cover.setVisibility(View.GONE);
        this.coverPlaceholder.setVisibility(View.VISIBLE);
        String id = appId == null ? "" : appId.trim();
        if (!id.isEmpty()) {
            setMetadata("ID " + id);
        }
    }

    private void showLoading(String title) {
        this.nameView.setText(title == null || title.trim().isEmpty()
                ? "游戏" : title.trim());
        this.stateView.setText("正在获取游戏信息");
        this.stateView.setVisibility(View.VISIBLE);
        setMetadata("");
        this.priceRow.setVisibility(View.GONE);
        this.cover.setTag(null);
        this.cover.setImageDrawable(null);
        this.cover.setVisibility(View.GONE);
        this.coverPlaceholder.setVisibility(View.VISIBLE);
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
