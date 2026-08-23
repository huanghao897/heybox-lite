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
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(32), 1.0f));
        TextView sort = text("热门", 11.0f, this.tokens.muted);
        sort.setGravity(Gravity.CENTER);
        setSortIcon(sort);
        heading.addView(sort, new LinearLayout.LayoutParams(dp(60), dp(32)));
        surface.addView(heading);

        LinearLayout list = vertical();
        surface.addView(list);
        boolean[] latest = {false};
        render(list, comments, false, rendered);
        sort.setOnClickListener(view -> {
            latest[0] = !latest[0];
            sort.setText(latest[0] ? "最新" : "热门");
            setSortIcon(sort);
            UiComponents.press(sort);
            render(list, comments, latest[0], null);
        });
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

    private void setSortIcon(TextView view) {
        android.graphics.drawable.Drawable icon = Compat.tintedDrawable(
                this.activity, R.drawable.ic_sort, this.tokens.muted);
        if (icon == null) return;
        icon.setBounds(0, 0, dp(11), dp(11));
        view.setCompoundDrawables(icon, null, null, null);
        view.setCompoundDrawablePadding(dp(4));
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
