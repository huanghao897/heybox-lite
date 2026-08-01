package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

final class ProfilePage {
    interface Host {
        void prepareProfileChrome();

        boolean isProfileActive();

        void showPage(View page);

        void showLoading();

        void hideLoading();

        void showLogin();

        void showReadingCenter();

        void showFavorites();

        void showCheckinCenter();

        void showSettings();

        void showUserSpace(String userId, String name, String avatar);

        void showToast(String message);

        void addBottomSafeSpace(LinearLayout page);

        String readingSummary();

        int pageHorizontalPadding();

        int pageTopPadding();

        int roundHeaderInset();
    }

    private final Activity activity;
    private final SessionStore session;
    private final ApiClient api;
    private final LocalCache localCache;
    private final SettingsUi settingsUi;
    private final ThemeTokens tokens;
    private final CheckinCenterCoordinator checkinCoordinator;
    private final boolean roundLayout;
    private final Host host;

    private View cachedPage;
    private boolean cachedLoggedIn;
    private String cachedUserId = "";
    private TextView readingSummaryView;

    ProfilePage(Activity activity, SessionStore session, ApiClient api,
                LocalCache localCache, SettingsUi settingsUi, ThemeTokens tokens,
                CheckinCenterCoordinator checkinCoordinator,
                boolean roundLayout, Host host) {
        this.activity = activity;
        this.session = session;
        this.api = api;
        this.localCache = localCache;
        this.settingsUi = settingsUi;
        this.tokens = tokens;
        this.checkinCoordinator = checkinCoordinator;
        this.roundLayout = roundLayout;
        this.host = host;
    }

    void show() {
        this.host.prepareProfileChrome();
        updateReadingSummary();
        if (!this.session.isLoggedIn()) {
            showGuest();
            return;
        }
        if (this.cachedPage != null && this.cachedLoggedIn
                && this.session.userId().equals(this.cachedUserId)
                && this.cachedPage.getParent() == null) {
            this.host.showPage(this.cachedPage);
            return;
        }
        this.host.showLoading();
        this.api.get(EndpointProvider.profileUserLinks(),
                OfficialRequestParams.profileLinks(this.session.userId(), 0, 1),
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        if (!host.isProfileActive() || activity.isFinishing()) return;
                        host.hideLoading();
                        render(body);
                    }

                    @Override
                    public void onError(String message) {
                        if (!host.isProfileActive() || activity.isFinishing()) return;
                        host.hideLoading();
                        host.showToast("个人资料加载失败" + message);
                        render(new JSONObject());
                    }
                });
    }

    void invalidate() {
        this.cachedPage = null;
        this.cachedLoggedIn = false;
        this.cachedUserId = "";
        this.readingSummaryView = null;
    }

    View cachedPage() {
        return this.cachedPage;
    }

    void updateReadingSummary() {
        if (this.readingSummaryView != null) {
            this.readingSummaryView.setText(this.host.readingSummary());
        }
    }

    private void showGuest() {
        if (this.cachedPage != null && !this.cachedLoggedIn
                && this.cachedPage.getParent() == null) {
            this.host.showPage(this.cachedPage);
            return;
        }
        ScrollView scroll = new ScrollView(this.activity);
        LinearLayout page = page();
        scroll.addView(page);
        page.addView(topLevelTitle("我的"));
        LinearLayout profile = card();
        LinearLayout header = new LinearLayout(this.activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView avatar = new ImageView(this.activity);
        avatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int avatarBackground = this.session.darkMode()
                ? Color.rgb(42, 43, 45) : Color.rgb(228, 230, 233);
        Compat.setBackground(avatar, UiComponents.round(
                this.activity, avatarBackground, 24, uiScale()));
        Drawable person = Compat.tintedDrawable(
                this.activity, R.drawable.il_person, this.tokens.muted);
        if (person != null) avatar.setImageDrawable(person);
        avatar.setPadding(dp(11), dp(11), dp(11), dp(11));
        int avatarSize = dp(this.roundLayout ? 54 : 48);
        header.addView(avatar, new LinearLayout.LayoutParams(avatarSize, avatarSize));

        LinearLayout copy = vertical(0);
        copy.addView(boldText("未登录", 18.0f, this.tokens.text));
        copy.addView(text("登录后可点赞、收藏和评论", 12.0f, this.tokens.muted));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        copyParams.leftMargin = dp(12);
        header.addView(copy, copyParams);
        profile.addView(header);
        Button login = commandButton("扫码登录", R.drawable.il_qr);
        login.setOnClickListener(view -> this.host.showLogin());
        addTop(profile, login, 13);
        page.addView(profile);
        addMenu(page, false);
        this.host.addBottomSafeSpace(page);
        cache(scroll, false, "");
        this.host.showPage(scroll);
    }

    private void render(JSONObject body) {
        JSONObject account = ProfileData.user(body);
        ScrollView scroll = new ScrollView(this.activity);
        LinearLayout page = page();
        scroll.addView(page);
        page.addView(topLevelTitle("我的"));
        LinearLayout profile = card();
        LinearLayout header = new LinearLayout(this.activity);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView avatar = new ImageView(this.activity);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int avatarBackground = this.session.darkMode()
                ? Color.rgb(42, 43, 45) : Color.rgb(228, 230, 233);
        Compat.setBackground(avatar, UiComponents.round(
                this.activity, avatarBackground, 28, uiScale()));
        Compat.clipToOutline(avatar);
        String avatarUrl = account == null ? this.session.avatar()
                : account.optString("avatar", this.session.avatar());
        if (!this.session.noImage() && !avatarUrl.isEmpty()) {
            ImageLoader.intoPlain(avatar, avatarUrl, 160);
        } else {
            Drawable placeholder = Compat.tintedDrawable(
                    this.activity, R.drawable.il_person, this.tokens.muted);
            if (placeholder != null) avatar.setImageDrawable(placeholder);
            avatar.setPadding(dp(13), dp(13), dp(13), dp(13));
        }
        int avatarSize = dp(this.roundLayout ? 54 : 56);
        header.addView(avatar, new LinearLayout.LayoutParams(avatarSize, avatarSize));

        LinearLayout copy = vertical(0);
        String name = account == null ? this.session.userName()
                : account.optString("username", this.session.userName());
        copy.addView(boldText(name.isEmpty() ? "小黑盒用户" : name,
                this.roundLayout ? 17.0f : 18.0f, this.tokens.text));
        copy.addView(text("ID " + this.session.userId(), 11.0f, this.tokens.muted));
        if (account != null) {
            TextView stats = text("关注 " + ProfileData.followCount(account)
                    + "  粉丝 " + ProfileData.fanCount(account)
                    + "  获赞 " + ProfileData.likeCount(account),
                    12.0f, this.tokens.accent);
            LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(-2, -2);
            statsParams.topMargin = dp(4);
            copy.addView(stats, statsParams);
        }
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        copyParams.leftMargin = dp(12);
        header.addView(copy, copyParams);
        ImageView arrow = new ImageView(this.activity);
        arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Drawable chevron = Compat.tintedDrawable(
                this.activity, R.drawable.il_chevron, this.tokens.muted);
        if (chevron != null) arrow.setImageDrawable(chevron);
        arrow.setAlpha(0.55f);
        header.addView(arrow, new LinearLayout.LayoutParams(dp(18), dp(18)));
        profile.addView(header);
        String signature = account == null ? "" : account.optString("signature");
        if (!signature.isEmpty()) {
            addTop(profile, text(signature, 13.0f, this.tokens.text), 11);
        }
        String displayName = name.isEmpty() ? "小黑盒用户" : name;
        profile.setOnClickListener(view -> {
            UiComponents.press(profile);
            this.host.showUserSpace(this.session.userId(), displayName, avatarUrl);
        });
        page.addView(profile);
        addMenu(page, true);
        this.host.addBottomSafeSpace(page);
        cache(scroll, true, this.session.userId());
        this.host.showPage(scroll);
    }

    private void addMenu(LinearLayout page, boolean loggedIn) {
        this.settingsUi.addSection(page, "阅读");
        LinearLayout panel = this.settingsUi.list();
        this.settingsUi.addEntry(panel, "阅读中心", null,
                String.valueOf(this.localCache.recentItems().size()),
                R.drawable.il_reading, this.host::showReadingCenter);
        this.settingsUi.addEntry(panel, "收藏", null, null,
                R.drawable.il_bookmark, () -> {
                    if (loggedIn) this.host.showFavorites();
                    else this.host.showLogin();
                });
        this.settingsUi.addEntry(panel, "小黑盒签到", null,
                this.checkinCoordinator != null && this.checkinCoordinator.paired()
                        ? "已连接" : null,
                R.drawable.il_calendar, this.host::showCheckinCenter);
        page.addView(panel);
        this.settingsUi.addSection(page, "其他");
        LinearLayout other = this.settingsUi.list();
        this.settingsUi.addEntry(other, "设置", null, null,
                R.drawable.il_settings, this.host::showSettings);
        page.addView(other);
    }

    private LinearLayout page() {
        LinearLayout page = vertical(this.tokens.background);
        page.setPadding(this.host.pageHorizontalPadding(), this.host.pageTopPadding(),
                this.host.pageHorizontalPadding(), dp(12));
        return page;
    }

    private TextView topLevelTitle(String title) {
        TextView view = boldText(title, this.roundLayout ? 21.0f : 23.0f, this.tokens.text);
        view.setGravity(Gravity.CENTER_VERTICAL);
        int horizontal = this.roundLayout ? this.host.roundHeaderInset() : dp(2);
        view.setPadding(horizontal, 0, horizontal, 0);
        view.setBackgroundColor(this.tokens.background);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                -1, dp(this.roundLayout ? 36 : 42)));
        return view;
    }

    private LinearLayout card() {
        LinearLayout card = vertical(this.tokens.panel);
        int horizontal = dp(this.roundLayout ? 11 : 12);
        card.setPadding(horizontal, dp(11), horizontal, dp(11));
        Compat.setBackground(card, UiComponents.round(
                this.activity, this.tokens.panel,
                this.roundLayout ? 9 : 10, uiScale()));
        return card;
    }

    private Button commandButton(String value, int iconResource) {
        Button button = new Button(this.activity);
        button.setText(value);
        button.setTextSize(sp(12.0f));
        button.setTextColor(this.tokens.accent);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(40));
        Compat.setBackground(button, null);
        Drawable icon = Compat.tintedDrawable(
                this.activity, iconResource, this.tokens.accent);
        if (icon != null) {
            icon.setBounds(0, 0, dp(15), dp(15));
            button.setCompoundDrawables(icon, null, null, null);
            button.setCompoundDrawablePadding(dp(4));
        }
        return button;
    }

    private void cache(View page, boolean loggedIn, String userId) {
        this.cachedPage = page;
        this.cachedLoggedIn = loggedIn;
        this.cachedUserId = userId;
    }

    private TextView boldText(String value, float size, int color) {
        TextView view = text(value, size, color);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(
                this.activity, value, size, color, this.session.textScale() / 100.0f);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private LinearLayout vertical(int color) {
        LinearLayout layout = new LinearLayout(this.activity);
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
        return UiComponents.dp(this.activity, value, uiScale());
    }

    private float sp(float value) {
        return value * this.session.textScale() / 100.0f;
    }

    private float uiScale() {
        return this.session.uiScale() / 100.0f;
    }
}
