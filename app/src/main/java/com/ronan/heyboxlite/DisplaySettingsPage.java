package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class DisplaySettingsPage {
    interface Host {
        LinearLayout openPage(String key, String title, Runnable back);

        void rebuildDisplayShell(String destination);

        void showDialog(String title, String message, String positiveText,
                        Runnable positiveAction, String negativeText,
                        Runnable negativeAction, String neutralText,
                        Runnable neutralAction);

        void cancelAllMotion();

        void showToast(String message);
    }

    private static final String[] THEME_NAMES = {
            "蓝色", "红色", "粉色", "紫色", "绿色", "青色",
            "橙色", "黄色", "灰色", "深蓝", "黑金", "薄荷绿"
    };
    private static final int[][] THEME_COLORS = {
            {-14386760, -9193242}, {-3982790, -1083529},
            {-2597743, -1006399}, {-9022795, -4744481},
            {-14185897, -9320552}, {-15299695, -9713717},
            {-2921692, -1007516}, {-3958250, -995480},
            {-7894890, -5327686}, {-15253642, -10646588},
            {-15263977, -3102658}, {-13530253, -7808833}
    };

    private final Activity activity;
    private final SessionStore session;
    private final SettingsUi settingsUi;
    private final ThemeTokens tokens;
    private final boolean roundLayout;
    private final Host host;

    DisplaySettingsPage(Activity activity, SessionStore session,
                        SettingsUi settingsUi, ThemeTokens tokens,
                        boolean roundLayout, Host host) {
        this.activity = activity;
        this.session = session;
        this.settingsUi = settingsUi;
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.host = host;
    }

    void showSettings() {
        LinearLayout page = this.host.openPage("display_settings", "显示", null);
        this.settingsUi.addSection(page, "外观");
        LinearLayout panel = this.settingsUi.list();
        addTop(panel, this.settingsUi.toggle("夜间模式", "", null,
                this.session.darkMode(), value -> {
                    this.session.setDarkMode(value);
                    refresh("display_settings");
                }), 0);
        addEntry(panel, "颜色主题", currentThemeCaption().replace("当前 · ", ""),
                R.drawable.il_palette, this::showThemePicker);
        this.settingsUi.addRangeEntry(panel, "界面大小", "%", R.drawable.ic_expand,
                SessionStore.MIN_UI_SCALE, SessionStore.MAX_UI_SCALE, 1,
                this.session.configuredUiScale(), this.session::setUiScale,
                () -> refresh("display_settings"));
        this.settingsUi.addRangeEntry(panel, "文字大小", "%", R.drawable.il_info,
                SessionStore.MIN_TEXT_SCALE, SessionStore.MAX_TEXT_SCALE, 1,
                this.session.configuredTextScale(), this.session::setTextScale,
                () -> refresh("display_settings"));
        this.settingsUi.addRangeEntry(panel, "左右边距", "dp", R.drawable.ic_expand,
                0, 30, 1, this.session.pagePadding(), this.session::setPagePadding,
                () -> refresh("display_settings"));
        addEntry(panel, "界面预览", null, R.drawable.il_eye, this::showPreview);
        page.addView(panel);

        this.settingsUi.addSection(page, screenAdaptationLabel());
        panel = this.settingsUi.list();
        if (isSystemRoundScreen()) {
            this.settingsUi.addInfoEntry(panel, "屏幕形状", null, "圆屏（自动适配）",
                    R.drawable.il_round_screen);
        } else {
            addTop(panel, this.settingsUi.toggle("圆屏适配", "", null,
                    this.session.roundScreen(), value -> {
                        this.session.setRoundScreen(value);
                        refresh("display_settings");
                    }), 0);
        }
        page.addView(panel);

        this.settingsUi.addSection(page, "正文排版");
        panel = this.settingsUi.list();
        this.settingsUi.addRangeEntry(panel, "正文字号", "%", R.drawable.il_info,
                75, 170, 1, this.session.bodyTextScale(),
                this.session::setBodyTextScale, null);
        this.settingsUi.addRangeEntry(panel, "字间", "", R.drawable.il_info,
                0, 20, 1, this.session.bodyLetterSpacing(),
                this.session::setBodyLetterSpacing, null);
        this.settingsUi.addRangeEntry(panel, "段落间距", "dp", R.drawable.ic_expand,
                0, 24, 1, this.session.bodyParagraphSpacing(),
                this.session::setBodyParagraphSpacing, null);
        this.settingsUi.addRangeEntry(panel, "行距", "%", R.drawable.ic_expand,
                100, 180, 1, this.session.bodyLineSpacing(),
                this.session::setBodyLineSpacing, null);
        addTop(panel, this.settingsUi.toggle("正文与一级评论加粗", "", null,
                this.session.bodyBold(), this.session::setBodyBold), 0);
        page.addView(panel);

        this.settingsUi.addSection(page, "动画");
        panel = this.settingsUi.list();
        this.settingsUi.addChoiceEntry(panel, "动画效果", R.drawable.il_splash,
                new String[]{"关闭", "精简", "完整"}, this.session.motionLevel(),
                this::setMotionLevel, null);
        addEntry(panel, "恢复默认设置", null, R.drawable.il_refresh, () ->
                this.host.showDialog("恢复默认显示设置",
                        "主题、字体、间距和界面大小都将恢复为默认值", "恢复", () -> {
                            this.session.resetDisplaySettings();
                            refresh("display_settings");
                            this.host.showToast("已恢复默认显示设置");
                        }, "取消", null, null, null));
        page.addView(panel);
    }

    void showPreview() {
        LinearLayout page = this.host.openPage(
                "display_preview", "界面预览", this::showSettings);
        TextView hint = text("预览使用当前已保存的显示参数", 10.0f, this.tokens.muted);
        hint.setGravity(Gravity.CENTER);
        page.addView(hint, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout feedCard = card();
        TextView articleBadge = text("文章", 9.0f, ThemeTokens.contrast(this.tokens.accent));
        articleBadge.setGravity(Gravity.CENTER);
        Compat.setBackground(articleBadge, UiComponents.round(
                this.activity, this.tokens.accent, 4, uiScale()));
        feedCard.addView(articleBadge, new LinearLayout.LayoutParams(dp(42), dp(20)));
        TextView title = boldText("方屏上的社区，也可以清晰又从容", 15.0f, this.tokens.text);
        addTop(feedCard, title, 6);
        TextView summary = text("这是一条帖子列表摘要，用来观察整体字号、卡片间距和主题颜色",
                11.0f, this.tokens.muted);
        summary.setLineSpacing(0.0f, 1.12f);
        addTop(feedCard, summary, 5);
        addTop(feedCard, text("Ronan   👍 128   评论 36", 10.0f, this.tokens.accent), 7);
        page.addView(feedCard);

        LinearLayout detail = card();
        detail.addView(boldText("帖子正文预览", 16.0f, this.tokens.text));
        TextView body = text("这是正文第一段，用于预览文字大小、字间距与行距。\n\n"
                        + "这是正文第二段。调整设置后保存，再回到这里就能查看最终效果",
                14.0f * this.session.bodyTextScale() / 100.0f, this.tokens.text);
        body.setLineSpacing(0.0f, this.session.bodyLineSpacing() / 100.0f);
        Compat.setLetterSpacing(body, this.session.bodyLetterSpacing() / 200.0f);
        if (this.session.bodyBold()) {
            body.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        addTop(detail, body, this.session.bodyParagraphSpacing());
        addTop(page, detail, 7);

        LinearLayout comments = card();
        comments.addView(boldText("评论层级预览", 14.0f, this.tokens.text));
        TextView first = boldText("一级评论会稍微加粗，方便快速浏览主要观点",
                13.0f, this.tokens.text);
        first.setLineSpacing(0.0f, this.session.bodyLineSpacing() / 100.0f);
        addTop(comments, first, 7);
        LinearLayout reply = new LinearLayout(this.activity);
        View rail = new View(this.activity);
        rail.setBackgroundColor(this.tokens.accent);
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(2), -1);
        railParams.rightMargin = dp(8);
        reply.addView(rail, railParams);
        TextView second = text("二级评论使用稍轻的字重，并通过主题色竖线建立层级",
                12.0f, this.tokens.muted);
        second.setLineSpacing(0.0f, 1.16f);
        reply.addView(second, new LinearLayout.LayoutParams(0, -2, 1.0f));
        addTop(comments, reply, 7);
        addTop(page, comments, 7);

        Button back = commandButton("返回继续调整", R.drawable.ic_arrow_back);
        back.setOnClickListener(view -> showSettings());
        addTop(page, back, 9);
    }

    private void showThemePicker() {
        LinearLayout box = this.settingsUi.dialogPanel("颜色主题");
        LinearLayout grid = vertical(0);
        int columns = this.roundLayout ? 3 : 4;
        int[] selected = {-1};
        AlertDialog[] holder = new AlertDialog[1];
        for (int start = 0; start < THEME_NAMES.length; start += columns) {
            LinearLayout row = new LinearLayout(this.activity);
            row.setGravity(Gravity.CENTER);
            for (int i = start; i < start + columns; i++) {
                LinearLayout cell = vertical(0);
                cell.setGravity(Gravity.CENTER);
                if (i < THEME_NAMES.length) {
                    int index = i;
                    View swatch = themeSwatch(index);
                    swatch.setOnClickListener(view -> {
                        selected[0] = index;
                        holder[0].dismiss();
                    });
                    cell.addView(swatch, new LinearLayout.LayoutParams(dp(36), dp(36)));
                    TextView name = text(THEME_NAMES[i], 9.0f, this.tokens.muted);
                    name.setGravity(Gravity.CENTER);
                    cell.addView(name, new LinearLayout.LayoutParams(-1, dp(22)));
                }
                row.addView(cell, new LinearLayout.LayoutParams(0, dp(58), 1.0f));
            }
            addTop(grid, row, start == 0 ? 2 : 3);
        }
        box.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this.activity).setView(box).create();
        holder[0] = dialog;
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(value -> {
            if (selected[0] < 0 || this.activity.isFinishing()) return;
            this.session.setTheme(Format.colorHex(THEME_COLORS[selected[0]][0]),
                    Format.colorHex(THEME_COLORS[selected[0]][1]));
            refresh("display_settings");
        });
        this.settingsUi.present(dialog, box);
    }

    private View themeSwatch(int index) {
        int primary = THEME_COLORS[index][0];
        int secondary = THEME_COLORS[index][1];
        boolean selected = currentPrimary() == primary && currentSecondary() == secondary;
        View swatch = new View(this.activity) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rect = new RectF();

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float cx = getWidth() / 2.0f;
                float cy = getHeight() / 2.0f;
                float radius = dp(12);
                this.rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
                this.paint.setStyle(Paint.Style.FILL);
                this.paint.setColor(primary);
                canvas.drawArc(this.rect, 135.0f, 180.0f, true, this.paint);
                this.paint.setColor(secondary);
                canvas.drawArc(this.rect, 315.0f, 180.0f, true, this.paint);
                if (!selected) return;
                this.paint.setStyle(Paint.Style.STROKE);
                this.paint.setStrokeWidth(dp(2));
                canvas.drawCircle(cx, cy, radius + dp(3), this.paint);
                this.paint.setStyle(Paint.Style.FILL);
                this.paint.setColor(Color.WHITE);
                this.paint.setTextAlign(Paint.Align.CENTER);
                this.paint.setTextSize(dp(12));
                this.paint.setFakeBoldText(true);
                canvas.drawText("✓", cx, cy + dp(4), this.paint);
            }
        };
        swatch.setContentDescription(THEME_NAMES[index]);
        return swatch;
    }

    private String currentThemeCaption() {
        if (this.session.primaryColor().isEmpty()
                && this.session.secondaryColor().isEmpty()) {
            return "当前 · 黑灰";
        }
        for (int i = 0; i < THEME_NAMES.length; i++) {
            if (currentPrimary() == THEME_COLORS[i][0]
                    && currentSecondary() == THEME_COLORS[i][1]) {
                return "当前 · " + THEME_NAMES[i];
            }
        }
        return "当前 · 自定义配色";
    }

    private int currentPrimary() {
        String saved = this.session.primaryColor();
        if (saved.isEmpty()) return this.session.darkMode() ? Color.WHITE : Color.BLACK;
        try {
            return Color.parseColor(saved);
        } catch (IllegalArgumentException ignored) {
            return this.session.darkMode() ? Color.WHITE : Color.BLACK;
        }
    }

    private int currentSecondary() {
        String saved = this.session.secondaryColor();
        if (saved.isEmpty()) {
            return this.session.darkMode()
                    ? Color.rgb(196, 198, 201) : Color.rgb(87, 91, 96);
        }
        try {
            return Color.parseColor(saved);
        } catch (IllegalArgumentException ignored) {
            return this.session.darkMode()
                    ? Color.rgb(196, 198, 201) : Color.rgb(87, 91, 96);
        }
    }

    private void setMotionLevel(int level) {
        this.host.cancelAllMotion();
        this.session.setMotionLevel(level);
        Motions.setLevel(level);
    }

    private String motionLevelLabel() {
        int level = this.session.motionLevel();
        return level == MotionLevel.OFF ? "关闭"
                : level == MotionLevel.FULL ? "完整" : "精简";
    }

    private void refresh(String destination) {
        this.host.rebuildDisplayShell(destination);
    }

    private boolean isSystemRoundScreen() {
        return RoundLayoutMetrics.isRoundDisplay(this.activity);
    }

    private String screenAdaptationLabel() {
        if (isSystemRoundScreen()) return "屏幕适配 · 已识别圆屏手表";
        if (RoundLayoutMetrics.isRectangularWatchDisplay(this.activity)) {
            return "屏幕适配 · 已识别方屏手表";
        }
        return "屏幕适配";
    }

    private void addEntry(LinearLayout parent, String name, String description,
                          int icon, Runnable action) {
        this.settingsUi.addEntry(parent, name, description, null, icon, action);
    }

    private LinearLayout card() {
        LinearLayout card = vertical(this.tokens.panel);
        int horizontal = dp(this.roundLayout ? 11 : 12);
        card.setPadding(horizontal, dp(11), horizontal, dp(11));
        Compat.setBackground(card, UiComponents.round(this.activity, this.tokens.panel,
                this.roundLayout ? 9 : 10, uiScale()));
        return card;
    }

    private Button commandButton(String value, int iconResource) {
        Button button = UiComponents.button(this.activity);
        button.setText(value);
        button.setTextSize(sp(12.0f));
        button.setTextColor(this.tokens.accent);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(40));
        if (Build.VERSION.SDK_INT >= 21) button.setStateListAnimator(null);
        Compat.setBackground(button, null);
        button.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) UiComponents.press(view);
            return false;
        });
        android.graphics.drawable.Drawable icon = Compat.tintedDrawable(
                this.activity, iconResource, this.tokens.accent);
        if (icon != null) {
            icon.setBounds(0, 0, dp(15), dp(15));
            button.setCompoundDrawables(icon, null, null, null);
            button.setCompoundDrawablePadding(dp(4));
        }
        return button;
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
