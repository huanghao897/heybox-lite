package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class NoticeCenter {
    interface Host {
        LinearLayout openSettingsPage(String key, String title);

        void showAnnouncementPage(View page);

        boolean isAnnouncementPageActive();

        void openUpdateUrl(String url);

        void openUrl(String url);

        void showToast(String message);

        int pageHorizontalPadding();

        int subpageTopPadding();
    }

    private static final String WELCOME_ID = "welcome-heybox-lite-1.77";

    private final Activity activity;
    private final SessionStore session;
    private final SettingsUi settingsUi;
    private final LiteDialogPresenter dialogs;
    private final ThemeTokens tokens;
    private final boolean roundLayout;
    private final Host host;

    NoticeCenter(Activity activity, SessionStore session, SettingsUi settingsUi,
                 LiteDialogPresenter dialogs, ThemeTokens tokens,
                 boolean roundLayout, Host host) {
        this.activity = activity;
        this.session = session;
        this.settingsUi = settingsUi;
        this.dialogs = dialogs;
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.host = host;
    }

    void checkUpdateOnLaunch() {
        UpdateChecker.check(appVersion(), this.session.userId(),
                this.session.testReleaseId(), new UpdateChecker.Callback() {
                    @Override
                    public void onResult(UpdateChecker.Result result) {
                        if (!result.updateAvailable || activity.isFinishing()) return;
                        if (result.title != null || result.notes != null) {
                            showUpdate(result);
                            return;
                        }
                        String target = result.downloadUrl.isEmpty()
                                ? result.releaseUrl : result.downloadUrl;
                        dialogs.show("发现新版本 " + result.version,
                                "heybox Lite 有新版本可用，是否前往下载", "下载", () -> {
                                    rememberTestRelease(result);
                                    host.openUpdateUrl(target);
                                }, "稍后", null, null, null);
                    }

                    @Override
                    public void onError(String message) {
                    }
                });
    }

    void checkAnnouncementOnLaunch() {
        AnnouncementChecker.Item welcome = welcomeAnnouncement();
        if (shouldShowWelcome(welcome)) {
            markSeen(welcome);
            showAnnouncement(welcome);
            return;
        }
        AnnouncementChecker.load(new AnnouncementChecker.Callback() {
            @Override
            public void onResult(List<AnnouncementChecker.Item> items) {
                AnnouncementChecker.Item item = firstUnseen(items);
                if (!activity.isFinishing() && item != null) showAnnouncement(item);
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    void showUpdate(UpdateChecker.Result result) {
        String releaseTitle = TextUtils.isEmpty(result.title) ? "" : result.title.trim();
        String notes = TextUtils.isEmpty(result.notes)
                ? "暂无更新内容说明" : limitUpdateNotes(result.notes.trim());
        StringBuilder message = new StringBuilder()
                .append("当前版本：").append(appVersion()).append('\n')
                .append("最新版本：").append(result.version);
        if (!releaseTitle.isEmpty()) message.append('\n').append("发布标题：").append(releaseTitle);
        message.append("\n\n更新内容：\n").append(notes);
        String target = TextUtils.isEmpty(result.downloadUrl)
                ? result.releaseUrl : result.downloadUrl;
        this.dialogs.show("发现新版本 " + result.version, message.toString(),
                "下载", () -> {
                    rememberTestRelease(result);
                    this.host.openUpdateUrl(target);
                }, "稍后", null, null, null);
    }

    void showAnnouncementBoard() {
        LinearLayout page = vertical(this.tokens.background);
        int horizontal = this.host.pageHorizontalPadding();
        page.setPadding(horizontal, this.host.subpageTopPadding(), horizontal, dp(8));
        page.addView(this.settingsUi.topCard("公告"));
        PullRefreshListView list = new PullRefreshListView(this.activity,
                this.tokens.background, this.tokens.muted, this.tokens.accent,
                uiScale(), textScale());
        list.setBackgroundColor(this.tokens.background);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(0));
        list.setCacheColorHint(0);
        list.setClipToPadding(false);
        list.setPadding(0, dp(6), 0, dp(10));
        AnnouncementListAdapter adapter = new AnnouncementListAdapter(
                this.activity, this.tokens, uiScale(), textScale(), this::showAnnouncement);
        list.setAdapter((ListAdapter) adapter);
        list.setPullRefreshAction(() -> loadAnnouncements(list, adapter));
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.host.showAnnouncementPage(page);
        list.post(() -> {
            if (!this.activity.isFinishing() && this.host.isAnnouncementPageActive()) {
                list.setRefreshing(true);
                loadAnnouncements(list, adapter);
            }
        });
    }

    void showAbout() {
        LinearLayout page = this.host.openSettingsPage("about", "关于");
        int markSize = dp(this.roundLayout ? 64 : 72);
        int iconSize = dp(this.roundLayout ? 52 : 58);
        FrameLayout appMark = new FrameLayout(this.activity);
        Compat.setBackground(appMark, UiComponents.round(
                this.activity, this.tokens.panelElevated, 18, uiScale()));
        ImageView appIcon = new ImageView(this.activity);
        appIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Drawable icon = Compat.tintedDrawable(
                this.activity, R.mipmap.about_app_mark, this.tokens.text);
        if (icon != null) appIcon.setImageDrawable(icon);
        appMark.addView(appIcon, new FrameLayout.LayoutParams(
                iconSize, iconSize, Gravity.CENTER));
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(markSize, markSize);
        markParams.gravity = Gravity.CENTER_HORIZONTAL;
        markParams.topMargin = dp(14);
        page.addView(appMark, markParams);

        TextView appName = boldText("heybox Lite", 18.0f, this.tokens.text);
        appName.setGravity(Gravity.CENTER);
        addTop(page, appName, 8);
        TextView version = text(appVersion() + " · " + BuildConfig.VERSION_CODE,
                11.0f, this.tokens.muted);
        version.setGravity(Gravity.CENTER);
        addTop(page, version, 4);
        TextView developer = boldText("开发者：Ronan", 13.0f, this.tokens.text);
        developer.setGravity(Gravity.CENTER);
        addTop(page, developer, 7);

        this.settingsUi.addSection(page, "信息");
        LinearLayout actions = this.settingsUi.list();
        addEntry(actions, "公告列表", R.drawable.il_info, this::showAnnouncementBoard);
        addEntry(actions, "交流群", R.drawable.il_qr, this::showFeedbackGroupQr);
        boolean[] checking = {false};
        addEntry(actions, "检查更新", R.drawable.il_update, () -> {
            if (checking[0]) return;
            checking[0] = true;
            this.host.showToast("正在检查更新");
            UpdateChecker.check(appVersion(), this.session.userId(),
                    this.session.testReleaseId(), new UpdateChecker.Callback() {
                        @Override
                        public void onResult(UpdateChecker.Result result) {
                            if (activity.isFinishing()) return;
                            checking[0] = false;
                            if (result.updateAvailable) showUpdate(result);
                            else host.showToast("当前已是最新版");
                        }

                        @Override
                        public void onError(String message) {
                            if (activity.isFinishing()) return;
                            checking[0] = false;
                            host.showToast("检查更新失败：" + message);
                        }
                    });
        });
        addEntry(actions, "GitHub 项目", R.drawable.il_globe,
                () -> this.host.openUrl("https://github.com/huanghao897/heybox-lite"));
        page.addView(actions);

        this.settingsUi.addSection(page, "说明");
        LinearLayout notes = this.settingsUi.list();
        notes.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView support = text("支持 Android 7.0 及以上系统", 12.0f, this.tokens.text);
        support.setLineSpacing(0.0f, 1.18f);
        notes.addView(support);
        TextView source = text("基于 HeyWear 进行二次开发与方屏适配，非官方应用。",
                12.0f, this.tokens.muted);
        source.setLineSpacing(0.0f, 1.18f);
        addTop(notes, source, 7);
        page.addView(notes);
    }

    void showFeedbackGroupQr() {
        LinearLayout box = vertical(Color.WHITE);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        Compat.setBackground(box, UiComponents.round(
                this.activity, Color.WHITE, 12, uiScale()));
        TextView title = boldText("交流群", 16.0f, Color.rgb(28, 28, 28));
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView group = text("QQ群：781941517", 12.0f, Color.rgb(84, 84, 84));
        group.setGravity(Gravity.CENTER);
        addTop(box, group, 5);
        ImageView qr = new ImageView(this.activity);
        qr.setImageResource(R.drawable.qq_feedback_group_qr);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setAdjustViewBounds(true);
        qr.setPadding(dp(4), dp(4), dp(4), dp(4));
        int width = this.activity.getResources().getDisplayMetrics().widthPixels;
        int height = this.activity.getResources().getDisplayMetrics().heightPixels;
        int size = Math.max(dp(150), Math.min(Math.min(width - dp(56),
                height - dp(170)), dp(300)));
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(size, size);
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrParams.topMargin = dp(10);
        box.addView(qr, qrParams);
        TextView hint = text("点击空白处关闭", 11.0f, Color.rgb(120, 120, 120));
        hint.setGravity(Gravity.CENTER);
        addTop(box, hint, 8);
        AlertDialog dialog = new AlertDialog.Builder(this.activity).setView(box).create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        }
    }

    private void loadAnnouncements(PullRefreshListView list,
                                   AnnouncementListAdapter adapter) {
        AnnouncementChecker.load(new AnnouncementChecker.Callback() {
            @Override
            public void onResult(List<AnnouncementChecker.Item> items) {
                if (!host.isAnnouncementPageActive() || activity.isFinishing()) {
                    list.setRefreshing(false);
                    return;
                }
                adapter.setItems(withWelcomeAnnouncement(items));
                list.setRefreshing(false);
            }

            @Override
            public void onError(String message) {
                if (!host.isAnnouncementPageActive() || activity.isFinishing()) {
                    list.setRefreshing(false);
                    return;
                }
                host.showToast("公告加载失败，已显示本地欢迎公告");
                adapter.setItems(Collections.singletonList(welcomeAnnouncement()));
                list.setRefreshing(false);
            }
        });
    }

    private boolean shouldShowWelcome(AnnouncementChecker.Item item) {
        if (item == null || TextUtils.isEmpty(item.id)
                || this.session.isAnnouncementSeen(item.id)) return false;
        String previous = this.session.lastAnnouncementId();
        if (!TextUtils.isEmpty(previous) && !item.id.equals(previous)) {
            this.session.markAnnouncementSeen(item.id);
            return false;
        }
        return true;
    }

    private List<AnnouncementChecker.Item> withWelcomeAnnouncement(
            List<AnnouncementChecker.Item> items) {
        List<AnnouncementChecker.Item> result = new ArrayList<>();
        result.add(welcomeAnnouncement());
        if (items != null) {
            for (AnnouncementChecker.Item item : items) {
                if (item != null && !WELCOME_ID.equals(item.id)) result.add(item);
            }
        }
        return result;
    }

    private AnnouncementChecker.Item welcomeAnnouncement() {
        return new AnnouncementChecker.Item(WELCOME_ID, "欢迎使用 heybox Lite",
                "欢迎使用 heybox Lite。这里会放版本公告和重要提醒。\n\n"
                        + "遇到 bug 或有建议，可以加入交流群：781941517。\n\n"
                        + "本项目仅用于学习、研究与个人使用，请在遵守平台规则的前提下使用",
                "normal", appVersion(), true, true);
    }

    private AnnouncementChecker.Item firstUnseen(List<AnnouncementChecker.Item> items) {
        if (items == null) return null;
        for (AnnouncementChecker.Item item : items) {
            if (item == null || !item.enabled
                    || item.title.isEmpty() && item.content.isEmpty()) continue;
            if (!item.onceOnly || item.id.isEmpty()
                    || !this.session.isAnnouncementSeen(item.id)) return item;
        }
        return null;
    }

    private void showAnnouncement(AnnouncementChecker.Item item) {
        if (item == null || this.activity.isFinishing()) return;
        String title = TextUtils.isEmpty(item.title) ? "公告" : item.title;
        Runnable acknowledge = () -> {
            if (item.onceOnly) markSeen(item);
        };
        String neutralText = WELCOME_ID.equals(item.id) ? "群二维码" : null;
        Runnable neutralAction = neutralText == null ? null : () -> {
            acknowledge.run();
            showFeedbackGroupQr();
        };
        this.dialogs.show(title, item.content == null ? "" : item.content,
                "知道了", acknowledge, null, null, neutralText, neutralAction);
    }

    private void markSeen(AnnouncementChecker.Item item) {
        if (item != null && !TextUtils.isEmpty(item.id)) {
            this.session.markAnnouncementSeen(item.id);
        }
    }

    private void rememberTestRelease(UpdateChecker.Result result) {
        if (result != null && "test".equals(result.channel) && result.releaseId > 0) {
            this.session.setTestReleaseId(result.releaseId);
        }
    }

    private String limitUpdateNotes(String notes) {
        return notes.length() <= 1800 ? notes
                : notes.substring(0, 1800) + "\n\n内容较长，完整更新日志请打开更新页面查看。";
    }

    private String appVersion() {
        return BuildConfig.VERSION_NAME;
    }

    private void addEntry(LinearLayout parent, String name, int icon, Runnable action) {
        this.settingsUi.addEntry(parent, name, null, null, icon, action);
    }

    private TextView boldText(String value, float size, int color) {
        TextView view = text(value, size, color);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(this.activity, value, size, color, textScale());
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

    private float uiScale() {
        return this.session.uiScale() / 100.0f;
    }

    private float textScale() {
        return this.session.textScale() / 100.0f;
    }
}
