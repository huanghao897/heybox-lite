package com.ronan.heyboxlite;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

final class ReadingStatsPage {
    private final Context context;
    private final ThemeTokens tokens;
    private final boolean roundLayout;
    private final float uiScale;
    private final float textScale;

    ReadingStatsPage(Context context, ThemeTokens tokens, boolean roundLayout,
                     float uiScale, float textScale) {
        this.context = context;
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.uiScale = uiScale;
        this.textScale = textScale;
    }

    ScrollView build(ReadingTimeTracker.Stats stats, View header,
                     int horizontalPadding, int topPadding) {
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(tokens.background);
        LinearLayout page = vertical(tokens.background);
        page.setPadding(horizontalPadding, topPadding, horizontalPadding, dp(18));
        scroll.addView(page);
        page.addView(header);
        addSection(page, "今日");
        page.addView(heroCard(stats));
        addSection(page, "近 7 天");
        page.addView(weekCard(stats));
        addSection(page, "构成");
        page.addView(splitCard(stats));
        addSection(page, "常看社区");
        page.addView(topicsCard(stats));
        return scroll;
    }

    private View heroCard(ReadingTimeTracker.Stats stats) {
        LinearLayout panel = card();
        panel.addView(durationView(stats.todayMs(), 30.0f, 13.0f));
        TextView meta = text(new SimpleDateFormat("M 月 d 日", Locale.getDefault())
                .format(new Date()) + " · 看过 " + stats.todayCount + " 篇",
                10.5f, tokens.muted);
        addTop(panel, meta, 4);
        return panel;
    }

    private View weekCard(ReadingTimeTracker.Stats stats) {
        LinearLayout panel = card();
        LinearLayout chart = new LinearLayout(context);
        long max = 1L;
        for (long value : stats.weekMs) max = Math.max(max, value);
        int chartHeight = dp(52);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -6);
        String[] names = {"日", "一", "二", "三", "四", "五", "六"};
        for (int i = 0; i < 7; i++) {
            boolean today = i == 6;
            LinearLayout cell = vertical(0);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout barBox = new LinearLayout(context);
            barBox.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            View bar = new View(context);
            int barColor = today ? tokens.accent : ThemeTokens.blend(
                    tokens.panel, tokens.text, tokens.dark ? 0.10f : 0.08f);
            Compat.setBackground(bar, UiComponents.round(context, barColor, 3, uiScale));
            int height = (int) Math.max(dp(3), stats.weekMs[i] * chartHeight / max);
            barBox.addView(bar, new LinearLayout.LayoutParams(dp(10), height));
            cell.addView(barBox, new LinearLayout.LayoutParams(-1, chartHeight));
            String dayName = today ? "今天"
                    : names[calendar.get(Calendar.DAY_OF_WEEK) - 1];
            TextView label = text(dayName, 8.5f,
                    today ? tokens.accent : tokens.subtle);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
            labelParams.topMargin = dp(4);
            cell.addView(label, labelParams);
            chart.addView(cell, new LinearLayout.LayoutParams(0, -2, 1.0f));
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        addTop(panel, chart, 4);
        long weekTotal = stats.weekTotalMs();
        TextView meta = text("本周 " + Format.readingDuration(weekTotal)
                + " · 日均 " + Format.readingDuration(weekTotal / 7L),
                10.5f, tokens.muted);
        addTop(panel, meta, 9);
        return panel;
    }

    private View splitCard(ReadingTimeTracker.Stats stats) {
        LinearLayout panel = card();
        panel.addView(durationView(stats.totalMs(), 20.0f, 11.0f));
        addTop(panel, text("累计 · 看过 " + stats.totalCount + " 篇",
                10.5f, tokens.muted), 2);
        long total = stats.totalMs();
        long articleMs = stats.totalArticleMs;
        long postMs = stats.totalPostMs;
        int postColor = ThemeTokens.blend(tokens.panel, tokens.text,
                tokens.dark ? 0.24f : 0.20f);
        LinearLayout bar = new LinearLayout(context);
        Compat.setBackground(bar, UiComponents.round(context,
                ThemeTokens.blend(tokens.panel, tokens.text, 0.06f), 3, uiScale));
        if (total > 0L) {
            if (articleMs > 0L) {
                View article = new View(context);
                Compat.setBackground(article, UiComponents.round(
                        context, tokens.accent, 3, uiScale));
                bar.addView(article, new LinearLayout.LayoutParams(0, -1, (float) articleMs));
            }
            if (postMs > 0L) {
                View post = new View(context);
                Compat.setBackground(post, UiComponents.round(
                        context, postColor, 3, uiScale));
                bar.addView(post, new LinearLayout.LayoutParams(0, -1, (float) postMs));
            }
        }
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, dp(6));
        barParams.topMargin = dp(10);
        panel.addView(bar, barParams);
        panel.addView(legendRow("文章", tokens.accent, articleMs, total));
        panel.addView(legendRow("帖子", postColor, postMs, total));
        return panel;
    }

    private View legendRow(String label, int color, long milliseconds, long total) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(context);
        Compat.setBackground(dot, UiComponents.round(context, color, 4, uiScale));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotParams.rightMargin = dp(7);
        row.addView(dot, dotParams);
        row.addView(text(label, 11.5f, tokens.text),
                new LinearLayout.LayoutParams(0, -2, 1.0f));
        int percent = total == 0L ? 0
                : (int) Math.round(milliseconds * 100.0d / total);
        row.addView(text(percent + "% · " + Format.readingDuration(milliseconds),
                10.5f, tokens.muted), new LinearLayout.LayoutParams(-2, -2));
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(30)));
        return row;
    }

    private View topicsCard(ReadingTimeTracker.Stats stats) {
        LinearLayout panel = card();
        if (stats.topics.isEmpty()) {
            TextView empty = text("多看几篇帖子，这里会按社区统计你的阅读分布",
                    10.5f, tokens.muted);
            empty.setLineSpacing(0.0f, 1.3f);
            empty.setPadding(0, dp(6), 0, dp(6));
            panel.addView(empty);
            return panel;
        }
        long max = Math.max(1L, stats.topics.get(0).ms);
        int shown = Math.min(5, stats.topics.size());
        for (int i = 0; i < shown; i++) {
            ReadingTimeTracker.TopicStat topic = stats.topics.get(i);
            LinearLayout item = vertical(0);
            LinearLayout head = new LinearLayout(context);
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(topic.name, 11.5f, tokens.text);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            head.addView(name, new LinearLayout.LayoutParams(0, -2, 1.0f));
            head.addView(text(Format.readingDuration(topic.ms), 10.5f, tokens.muted),
                    new LinearLayout.LayoutParams(-2, -2));
            item.addView(head, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout track = new LinearLayout(context);
            Compat.setBackground(track, UiComponents.round(context,
                    ThemeTokens.blend(tokens.panel, tokens.text, 0.06f), 2, uiScale));
            View fill = new View(context);
            Compat.setBackground(fill, UiComponents.round(
                    context, tokens.accent, 2, uiScale));
            track.addView(fill, new LinearLayout.LayoutParams(0, -1, (float) topic.ms));
            track.addView(new View(context),
                    new LinearLayout.LayoutParams(0, -1, (float) (max - topic.ms)));
            LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(-1, dp(3));
            trackParams.topMargin = dp(6);
            item.addView(track, trackParams);
            addTop(panel, item, i == 0 ? 2 : 12);
        }
        return panel;
    }

    private TextView durationView(long milliseconds, float numberSp, float unitSp) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        long minutes = Math.max(0L, milliseconds / 60_000L);
        long hours = minutes / 60L;
        long remainder = minutes % 60L;
        if (milliseconds > 0L && minutes == 0L) {
            appendSegment(builder, "不足 ", unitSp, tokens.muted, false);
            appendSegment(builder, "1", numberSp, tokens.text, true);
            appendSegment(builder, " 分钟", unitSp, tokens.muted, false);
        } else if (hours > 0L) {
            appendSegment(builder, String.valueOf(hours), numberSp, tokens.text, true);
            appendSegment(builder, " 时 ", unitSp, tokens.muted, false);
            appendSegment(builder, String.valueOf(remainder), numberSp, tokens.text, true);
            appendSegment(builder, " 分", unitSp, tokens.muted, false);
        } else {
            appendSegment(builder, String.valueOf(minutes), numberSp, tokens.text, true);
            appendSegment(builder, " 分钟", unitSp, tokens.muted, false);
        }
        TextView view = new TextView(context);
        view.setText(builder);
        view.setTypeface(Typeface.DEFAULT);
        Compat.setLetterSpacing(view, 0.0f);
        return view;
    }

    private void appendSegment(SpannableStringBuilder builder, String value,
                               float sizeSp, int color, boolean bold) {
        int start = builder.length();
        builder.append(value);
        int end = builder.length();
        builder.setSpan(new AbsoluteSizeSpan(Math.round(sp(sizeSp)), true),
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new ForegroundColorSpan(color),
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) {
            builder.setSpan(new StyleSpan(Typeface.BOLD),
                    start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void addSection(LinearLayout page, String label) {
        TextView view = text(label, 10.0f, tokens.subtle);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(6), 0, 0, dp(4));
        addTop(page, view, roundLayout ? 10 : 12);
    }

    private LinearLayout card() {
        LinearLayout card = vertical(tokens.panel);
        int horizontal = dp(roundLayout ? 11 : 12);
        card.setPadding(horizontal, dp(11), horizontal, dp(11));
        Compat.setBackground(card, UiComponents.round(context, tokens.panel,
                roundLayout ? 9 : 10, uiScale));
        return card;
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(context, value, size, color, textScale);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private LinearLayout vertical(int color) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    private void addTop(LinearLayout parent, View view, int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(marginDp);
        parent.addView(view, params);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density * uiScale);
    }

    private float sp(float value) {
        return value * textScale;
    }
}
