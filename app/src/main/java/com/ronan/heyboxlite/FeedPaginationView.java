package com.ronan.heyboxlite;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

final class FeedPaginationView extends FrameLayout {
    private final TextView footer;
    private final TransientStatusBanner banner;
    private final int mutedColor;
    private final int retryColor;

    FeedPaginationView(Context context, ListView list, ThemeTokens tokens,
                       float uiScale, float textScale, int bannerTopMargin,
                       Runnable retry) {
        super(context);
        mutedColor = tokens.muted;
        retryColor = tokens.secondary;
        setBackgroundColor(tokens.background);

        footer = UiComponents.label(context, "", 12.0f, mutedColor, textScale);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(context, 8, uiScale), dp(context, 10, uiScale),
                dp(context, 8, uiScale), dp(context, 16, uiScale));
        footer.setMinHeight(dp(context, 46, uiScale));
        footer.setOnClickListener(view -> retry.run());
        list.addFooterView(footer, null, false);
        addView(list, new LayoutParams(-1, -1));

        banner = new TransientStatusBanner(context, tokens, uiScale, textScale,
                "正在寻找更多帖子");
        LayoutParams bannerParams = new LayoutParams(-2, -2,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        bannerParams.topMargin = bannerTopMargin;
        addView(banner, bannerParams);
    }

    void render(FeedPagingState state, boolean hasItems, boolean active) {
        if (state.loadingMore() && active) {
            banner.showBanner();
        } else {
            banner.hideBanner();
        }

        boolean visible = hasItems || state.loadingMore()
                || state.loadMoreFailed() || state.noMore();
        footer.setVisibility(visible ? VISIBLE : GONE);
        footer.setEnabled(state.loadMoreFailed());
        if (state.loadingMore()) {
            setFooter("", mutedColor);
        } else if (state.loadMoreFailed()) {
            setFooter("加载失败，点按重试", retryColor);
        } else if (state.noMore()) {
            setFooter("没有更多了", mutedColor);
        } else {
            setFooter("上滑加载更多", mutedColor);
        }
    }

    void finishMotion() {
        banner.dismissImmediately();
    }

    void close() {
        banner.dismissImmediately();
        footer.setOnClickListener(null);
    }

    private void setFooter(String value, int color) {
        footer.setText(value);
        footer.setTextColor(color);
    }

    private static int dp(Context context, int value, float scale) {
        return Math.round(value * context.getResources().getDisplayMetrics().density * scale);
    }
}
