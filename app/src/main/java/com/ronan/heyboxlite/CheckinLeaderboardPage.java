package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Displays the public rankings provided by CheckinCenter. */
final class CheckinLeaderboardPage {
    interface Host {
        void closePage();

        boolean isActive();

        int pageHorizontalPadding();

        int subpageTopPadding();

        int roundHeaderInset();
    }

    private enum Board {
        CHECKIN,
        SPONSORSHIP
    }

    private final Activity activity;
    private final SessionStore session;
    private final CheckinCenterCoordinator coordinator;
    private final ThemeTokens tokens;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CheckinCenterUi ui;
    private final SettingsUi settingsUi;
    private final CheckinLeaderboardLayout dimensions;
    private final FrameLayout root;
    private final boolean roundLayout;

    private Board board = Board.CHECKIN;
    private CheckinLeaderboard.Data data;
    private String errorMessage = "";
    private boolean loading;
    private boolean closed;

    CheckinLeaderboardPage(Activity activity, SessionStore session,
                           CheckinCenterCoordinator coordinator, ThemeTokens tokens,
                           boolean roundLayout, Host host) {
        this.activity = activity;
        this.session = session;
        this.coordinator = coordinator;
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.host = host;
        this.ui = new CheckinCenterUi(activity, session, tokens, roundLayout);
        this.dimensions = CheckinLeaderboardLayout.create(activity, session, roundLayout);
        this.settingsUi = new SettingsUi(activity, session, tokens, roundLayout,
                host.roundHeaderInset(), handler, host::closePage);
        this.root = new FrameLayout(activity);
        this.root.setBackgroundColor(tokens.background);
    }

    View view() {
        return root;
    }

    boolean usesRoundLayout() {
        return roundLayout;
    }

    void onResume() {
        if (!closed && data == null && !loading) refresh();
    }

    void onPause() {
    }

    void refresh() {
        if (closed || loading) return;
        loading = true;
        errorMessage = "";
        render();
        coordinator.getLeaderboard(new CheckinCenterClient.Callback<CheckinLeaderboard.Data>() {
            @Override
            public void onSuccess(CheckinLeaderboard.Data value) {
                data = value;
                loading = false;
                errorMessage = "";
                if (!closed && host.isActive()) render();
            }

            @Override
            public void onError(CheckinCenterClient.ApiError error) {
                loading = false;
                errorMessage = error == null || TextUtils.isEmpty(error.getMessage())
                        ? "排行榜加载失败" : error.getMessage();
                if (!closed && host.isActive()) render();
            }
        });
    }

    void close() {
        closed = true;
        handler.removeCallbacksAndMessages(null);
        ImageLoader.cancelTree(root);
        root.removeAllViews();
    }

    private void render() {
        if (closed) return;
        ImageLoader.cancelTree(root);
        root.removeAllViews();

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout page = ui.column(tokens.background);
        int horizontalPadding = dimensions.pageHorizontalPadding(host.pageHorizontalPadding());
        page.setPadding(horizontalPadding, host.subpageTopPadding(),
                horizontalPadding, dimensions.pageBottomPadding());
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        page.addView(settingsUi.topCard(activity.getString(R.string.title_leaderboard)));

        if (loading || data == null && errorMessage.isEmpty()) {
            ui.addTop(page, loadingCard(), dimensions.sectionGap());
        } else if (!errorMessage.isEmpty()) {
            ui.addTop(page, errorCard(), dimensions.sectionGap());
        } else {
            ui.addTop(page, boardSelector(), dimensions.sectionGap());
            ui.addTop(page, boardCard(), dimensions.compactGap());
        }
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private View loadingCard() {
        LinearLayout card = card();
        card.addView(ui.statusHeader(R.drawable.il_leaderboard, "正在加载",
                "正在读取排行榜", tokens.text, true));
        return card;
    }

    private View errorCard() {
        LinearLayout card = card();
        card.addView(ui.statusHeader(R.drawable.il_leaderboard, "暂时无法加载",
                errorMessage, tokens.text, false));
        Button retry = ui.ghostButton("重新加载");
        retry.setOnClickListener(view -> {
            UiComponents.press(view);
            refresh();
        });
        ui.addTop(card, retry, roundLayout ? 10 : 12);
        return card;
    }

    private View boardSelector() {
        LinearLayout selector = new LinearLayout(activity);
        selector.setGravity(Gravity.CENTER_VERTICAL);
        int selectorPadding = dimensions.selectorPadding();
        selector.setPadding(selectorPadding, selectorPadding,
                selectorPadding, selectorPadding);
        Compat.setBackground(selector, UiComponents.round(
                activity, tokens.panelElevated, dimensions.isCompact() ? 9 : 10, scale()));

        Button checkin = ui.segmentButton("连续签到", board == Board.CHECKIN);
        Button sponsorship = ui.segmentButton("赞助排行", board == Board.SPONSORSHIP);
        checkin.setOnClickListener(view -> select(Board.CHECKIN));
        sponsorship.setOnClickListener(view -> select(Board.SPONSORSHIP));

        int height = dimensions.selectorItemHeight();
        selector.addView(checkin, new LinearLayout.LayoutParams(0, height, 1.0f));
        selector.addView(sponsorship, new LinearLayout.LayoutParams(0, height, 1.0f));
        selector.setMinimumHeight(dimensions.selectorHeight());
        return selector;
    }

    private void select(Board selected) {
        if (closed || board == selected || data == null) return;
        board = selected;
        render();
    }

    private View boardCard() {
        LinearLayout card = card();
        String title = board == Board.CHECKIN ? "连续签到" : "赞助排行";
        String subtitle = board == Board.CHECKIN
                ? "按连续签到天数排序" : "按累计赞助金额排序";
        List<CheckinLeaderboard.Entry> entries = board == Board.CHECKIN
                ? data.checkin : data.sponsorship;

        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = ui.label(title, dimensions.cardTitleSp(), tokens.text);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        heading.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView count = ui.label(entries.size() + " 人", dimensions.bodySp(), tokens.muted);
        count.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        heading.addView(count, new LinearLayout.LayoutParams(-2, -2));
        card.addView(heading);

        TextView hint = ui.body(subtitle, tokens.muted);
        hint.setPadding(0, uiDp(2), 0, 0);
        card.addView(hint);

        if (entries.isEmpty()) {
            TextView empty = ui.body("暂无数据", tokens.muted);
            empty.setGravity(Gravity.CENTER);
            int emptyPadding = dimensions.emptyVerticalPadding();
            empty.setPadding(0, emptyPadding, 0, emptyPadding);
            card.addView(empty);
            return card;
        }

        for (int index = 0; index < entries.size(); index++) {
            CheckinLeaderboard.Entry entry = entries.get(index);
            if (index > 0) addDivider(card, entry.rank);
            card.addView(entryRow(entry));
        }
        return card;
    }

    private LinearLayout card() {
        LinearLayout card = ui.card();
        int horizontal = dimensions.cardHorizontalPadding();
        int vertical = dimensions.cardVerticalPadding();
        card.setPadding(horizontal, vertical, horizontal, vertical);
        return card;
    }

    private View entryRow(CheckinLeaderboard.Entry entry) {
        LinearLayout row = new LinearLayout(activity);
        row.setBaselineAligned(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int vertical = dimensions.rowVerticalPadding();
        row.setPadding(0, vertical, 0, vertical);
        row.setMinimumHeight(dimensions.rowMinimumHeight());

        int rankWidth = dimensions.rankWidth(entry.rank);
        TextView rank = ui.label(String.valueOf(entry.rank), dimensions.bodySp(),
                entry.rank <= 3 ? tokens.accent : tokens.muted);
        rank.setGravity(Gravity.CENTER);
        rank.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        row.addView(rank, new LinearLayout.LayoutParams(rankWidth, uiDp(30)));

        int avatarSize = dimensions.avatarSize();
        FrameLayout avatar = avatarView(entry, avatarSize);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                avatarSize, avatarSize);
        avatarParams.leftMargin = dimensions.avatarLeftMargin();
        avatarParams.rightMargin = dimensions.avatarRightMargin();
        row.addView(avatar, avatarParams);

        TextView name = ui.body(entry.displayName, tokens.text);
        name.setMinWidth(0);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1.0f));

        TextView value = ui.body(entry.valueLabel, tokens.text);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setMinWidth(0);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        int pagePadding = dimensions.pageHorizontalPadding(host.pageHorizontalPadding());
        int valueWidth = dimensions.valueColumnWidth(pagePadding, entry.rank);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(valueWidth, -2);
        valueParams.leftMargin = dimensions.valueLeftMargin();
        row.addView(value, valueParams);
        return row;
    }

    private FrameLayout avatarView(CheckinLeaderboard.Entry entry, int size) {
        FrameLayout container = new FrameLayout(activity);

        TextView fallback = ui.label(entry.initial, dimensions.bodySp(), tokens.text);
        fallback.setGravity(Gravity.CENTER);
        fallback.setSingleLine(true);
        fallback.setEllipsize(TextUtils.TruncateAt.END);
        fallback.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        Compat.setBackground(fallback, UiComponents.round(
                activity, tokens.pressedSurface(), dimensions.avatarRadius(), scale()));
        container.addView(fallback, new FrameLayout.LayoutParams(-1, -1));

        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Compat.setBackground(image, UiComponents.round(
                activity, tokens.pressedSurface(), dimensions.avatarRadius(), scale()));
        Compat.clipToOutline(image);
        image.setVisibility(View.INVISIBLE);
        container.addView(image, new FrameLayout.LayoutParams(-1, -1));

        if (!session.noImage() && !entry.avatarUrl.isEmpty()) {
            ImageLoader.intoMeasuredStable(image, entry.avatarUrl, size,
                    (success, bitmap) -> {
                        if (success && bitmap != null) {
                            image.setVisibility(View.VISIBLE);
                            fallback.setVisibility(View.GONE);
                        } else {
                            image.setVisibility(View.INVISIBLE);
                            fallback.setVisibility(View.VISIBLE);
                        }
                    });
        }
        return container;
    }

    private void addDivider(LinearLayout parent, int rank) {
        View divider = new View(activity);
        divider.setBackgroundColor(tokens.hairline);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,
                Math.max(1, uiDp(1) / 2));
        params.leftMargin = dimensions.dividerInset(rank);
        parent.addView(divider, params);
    }

    private int uiDp(int value) {
        return UiComponents.dp(activity, value, scale());
    }

    private float scale() {
        return session.uiScale() / 100.0f;
    }
}
