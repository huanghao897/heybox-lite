package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Handler;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.WeakHashMap;

final class DetailCommentsSection {
    private static final int COMMENT_BATCH_SIZE = 8;
    interface Host {
        boolean isCurrent(DetailPager pager);
    }

    private final Activity activity;
    private final SessionStore session;
    private final ThemeTokens tokens;
    private final CommentRenderer renderer;
    private final Handler handler;
    private final Host host;
    private final WeakHashMap<LinearLayout, Integer> renderGenerations = new WeakHashMap<>();
    private int nextRenderGeneration;

    DetailCommentsSection(Activity activity, SessionStore session, ThemeTokens tokens,
                          CommentRenderer renderer, Handler handler, Host host) {
        this.activity = activity;
        this.session = session;
        this.tokens = tokens;
        this.renderer = renderer;
        this.handler = handler;
        this.host = host;
    }

    LinearLayout placeholder(LinearLayout page, JSONArray comments) {
        LinearLayout container = vertical();
        TextView loading = text("评论 " + count(comments), 14.0f, this.tokens.text);
        loading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        loading.setPadding(0, dp(10), 0, dp(10));
        container.addView(loading);
        page.addView(container, new LinearLayout.LayoutParams(-1, -2));
        return container;
    }

    void populate(LinearLayout container, JSONArray comments, DetailPager pager,
                  long delayMs, ScrollView scroll, int restoreScroll) {
        this.handler.postDelayed(() -> {
            if (!this.host.isCurrent(pager) || container.getParent() == null
                    || this.activity.isFinishing()) return;
            container.removeAllViews();
            addContent(container, comments, () -> {
                if (restoreScroll <= 0) return;
                scroll.post(() -> {
                    if (this.host.isCurrent(pager) && !this.activity.isFinishing()) {
                        scroll.scrollTo(0, restoreScroll);
                    }
                });
            });
        }, delayMs);
    }

    private void addContent(LinearLayout page, JSONArray comments, Runnable rendered) {
        LinearLayout surface = vertical();
        surface.setPadding(0, dp(8), 0, dp(12));
        LinearLayout heading = new LinearLayout(this.activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("评论 " + count(comments), 14.0f, this.tokens.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(28), 1.0f));
        LinearLayout tabs = new LinearLayout(this.activity);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(dp(2), dp(2), dp(2), dp(2));
        Compat.setBackground(tabs, UiComponents.round(this.activity,
                this.tokens.panelElevated, 7, this.session.uiScale() / 100f));
        TextView hot = sortTab("热门");
        TextView recent = sortTab("最新");
        tabs.addView(hot, new LinearLayout.LayoutParams(dp(34), dp(22)));
        tabs.addView(recent, new LinearLayout.LayoutParams(dp(34), dp(22)));
        heading.addView(tabs, new LinearLayout.LayoutParams(-2, dp(26)));
        surface.addView(heading);
        LinearLayout list = vertical();
        surface.addView(list);
        boolean[] latest = {false};
        render(list, comments, false, rendered);
        selectTab(hot, true);
        selectTab(recent, false);
        android.view.View.OnClickListener select = view -> {
            boolean next = view == recent;
            if (next == latest[0]) return;
            latest[0] = next;
            selectTab(hot, !next);
            selectTab(recent, next);
            UiComponents.press(view);
            render(list, comments, latest[0], null);
        };
        hot.setOnClickListener(select);
        recent.setOnClickListener(select);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        page.addView(surface, params);
    }

    private void render(LinearLayout list, JSONArray comments, boolean latest,
                        Runnable complete) {
        int generation = ++this.nextRenderGeneration;
        this.renderGenerations.put(list, generation);
        list.removeAllViews();
        List<JSONObject> ordered = CommentOrder.sorted(comments, latest);
        if (!ordered.isEmpty()) {
            appendBatch(list, ordered, latest, 0, generation, complete);
            return;
        }
        TextView empty = text("暂无评论", 13.0f, this.tokens.muted);
        empty.setPadding(dp(4), dp(8), dp(4), dp(12));
        list.addView(empty);
        if (complete != null) complete.run();
    }

    private void appendBatch(LinearLayout list, List<JSONObject> ordered, boolean latest,
                             int start, int generation, Runnable complete) {
        Integer current = this.renderGenerations.get(list);
        if (current == null || current != generation || list.getParent() == null
                || this.activity.isFinishing()) return;
        int end = Math.min(ordered.size(), start + COMMENT_BATCH_SIZE);
        JSONArray batch = new JSONArray();
        for (int index = start; index < end; index++) batch.put(ordered.get(index));
        this.renderer.addComments(list, batch, latest);
        if (end < ordered.size()) {
            this.handler.post(() -> appendBatch(
                    list, ordered, latest, end, generation, complete));
        } else if (complete != null) {
            complete.run();
        }
    }

    private TextView sortTab(String label) {
        TextView view = text(label, 10.5f, this.tokens.text);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(0);
        view.setMinimumWidth(0);
        view.setSingleLine(true);
        return view;
    }

    private void selectTab(TextView view, boolean selected) {
        view.setSelected(selected);
        view.setTextColor(selected ? ThemeTokens.contrast(this.tokens.text) : this.tokens.muted);
        view.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        Compat.setBackground(view, UiComponents.round(this.activity,
                selected ? this.tokens.text : android.graphics.Color.TRANSPARENT,
                6, this.session.uiScale() / 100f));
    }

    private static int count(JSONArray comments) {
        return comments == null ? 0 : comments.length();
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this.activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(
                this.activity, value, size, color, this.session.textScale() / 100.0f);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private int dp(int value) {
        return UiComponents.dp(this.activity, value, this.session.uiScale() / 100.0f);
    }
}
