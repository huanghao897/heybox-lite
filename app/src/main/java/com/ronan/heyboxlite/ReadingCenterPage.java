package com.ronan.heyboxlite;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class ReadingCenterPage {
    interface Host {
        boolean isActive();
        void prepare();
        void showContent(View view);
        void retain(View view);
        void showDetail(FeedItem item);
        void showReadingStats();
        void showWatchLater();
        void showCloudHistory();
        void addBottomSpace(LinearLayout page);
        void showToast(String message);
        String readingSummary();
        int horizontalPadding();
        int topPadding();
    }

    private final Activity activity;
    private final SessionStore session;
    private final LocalCache cache;
    private final SettingsUi settingsUi;
    private final ThemeTokens tokens;
    private final Host host;
    private final ReadingCenterLoader loader;

    ReadingCenterPage(Activity activity, SessionStore session, LocalCache cache,
                      SettingsUi settingsUi, ThemeTokens tokens, Host host) {
        this.activity = activity;
        this.session = session;
        this.cache = cache;
        this.settingsUi = settingsUi;
        this.tokens = tokens;
        this.host = host;
        this.loader = new ReadingCenterLoader(activity, cache);
    }

    void show() {
        if (this.host.isActive()) return;
        this.host.prepare();
        ScrollView scroll = new ScrollView(this.activity);
        LinearLayout page = new LinearLayout(this.activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(this.tokens.background);
        int horizontal = this.host.horizontalPadding();
        page.setPadding(horizontal, this.host.topPadding(), horizontal, dp(18));
        scroll.addView(page);
        LoadingSpinnerView loading = new LoadingSpinnerView(this.activity);
        loading.setColor(this.tokens.primary);
        page.addView(loading, new LinearLayout.LayoutParams(-1, dp(54)));
        this.host.retain(scroll);
        this.host.showContent(scroll);
        this.loader.load(new ReadingCenterLoader.Callback() {
            @Override public void onLoaded(ReadingCenterLoader.Snapshot snapshot) {
                if (host.isActive()) render(page, snapshot);
            }

            @Override public void onError() {
                if (!host.isActive()) return;
                page.removeAllViews();
                TextView error = text("阅读数据读取失败", 12.5f, tokens.muted);
                error.setGravity(Gravity.CENTER);
                page.addView(error, new LinearLayout.LayoutParams(-1, dp(54)));
                host.showToast("阅读中心加载失败，异常信息已保存");
            }
        });
    }

    void close() {
        this.loader.close();
    }

    private void render(LinearLayout page, ReadingCenterLoader.Snapshot snapshot) {
        page.removeAllViews();
        if (snapshot.recent != null) {
            FeedItem item = snapshot.recent;
            LinearLayout panel = this.settingsUi.list();
            this.settingsUi.addEntry(panel, "继续阅读", item.title, null,
                    R.drawable.il_reading, () -> this.host.showDetail(item));
            if (this.cache.scroll(item.id) > 0) {
                addTop(panel, text("已记录上次阅读位置", 10.5f, this.tokens.muted), 0);
            }
            page.addView(panel);
        }
        LinearLayout library = this.settingsUi.list();
        this.settingsUi.addEntry(library, "阅读时长", this.host.readingSummary(), null,
                R.drawable.il_reading, this.host::showReadingStats);
        this.settingsUi.addEntry(library, "稍后看", snapshot.watchLaterCount + " 篇 · "
                        + Format.cacheMb(snapshot.offlineBytes), null,
                R.drawable.il_history, this.host::showWatchLater);
        this.settingsUi.addEntry(library, "历史记录", this.session.isLoggedIn()
                        ? "与小黑盒账号同步" : "登录后查看小黑盒记录", null,
                R.drawable.il_history, this.host::showCloudHistory);
        addTop(page, library, page.getChildCount() == 0 ? 0 : 8);
        this.host.addBottomSpace(page);
    }

    private TextView text(String value, float size, int color) {
        return UiComponents.label(this.activity, value, size, color,
                this.session.textScale() / 100.0f);
    }

    private void addTop(LinearLayout parent, View child, int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(marginDp);
        parent.addView(child, params);
    }

    private int dp(int value) {
        return Math.round(value * this.activity.getResources().getDisplayMetrics().density
                * this.session.uiScale() / 100.0f);
    }
}
