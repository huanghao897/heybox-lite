package com.ronan.heyboxlite;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

final class AnnouncementListAdapter extends BaseAdapter {
    interface Listener {
        void onOpen(AnnouncementChecker.Item item);
    }

    private final Context context;
    private final ThemeTokens tokens;
    private final float uiScale;
    private final float textScale;
    private final Listener listener;
    private final List<AnnouncementChecker.Item> items = new ArrayList<>();

    AnnouncementListAdapter(Context context, ThemeTokens tokens, float uiScale,
                            float textScale, Listener listener) {
        this.context = context;
        this.tokens = tokens;
        this.uiScale = uiScale;
        this.textScale = textScale;
        this.listener = listener;
    }

    void setItems(List<AnnouncementChecker.Item> value) {
        this.items.clear();
        if (value != null) {
            for (AnnouncementChecker.Item item : value) {
                if (item != null && item.enabled) this.items.add(item);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return this.items.isEmpty() ? 1 : this.items.size();
    }

    @Override
    public AnnouncementChecker.Item getItem(int position) {
        return this.items.isEmpty() ? null : this.items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View reusable, ViewGroup parent) {
        AnnouncementChecker.Item item = getItem(position);
        if (item == null) {
            TextView empty = text("暂无公告", 13.0f, this.tokens.muted);
            empty.setGravity(Gravity.CENTER);
            empty.setMinHeight(dp(88));
            return empty;
        }
        LinearLayout row = vertical(this.tokens.background);
        row.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout card = vertical(this.tokens.panel);
        card.setPadding(dp(12), dp(11), dp(12), dp(11));
        Compat.setBackground(card, UiComponents.groupCard(this.context, this.tokens, this.uiScale));

        TextView title = text(TextUtils.isEmpty(item.title) ? "公告" : item.title, 14.0f, this.tokens.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title);
        String updatedAt = Format.announcementTime(item.updatedAt);
        if (!updatedAt.isEmpty()) addTop(card, text(updatedAt, 10.0f, this.tokens.muted), 4);
        TextView preview = text(Format.announcementPreview(item.content), 12.0f, this.tokens.muted);
        preview.setLineSpacing(0.0f, 1.18f);
        addTop(card, preview, 6);
        card.setOnClickListener(view -> this.listener.onOpen(item));
        row.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private LinearLayout vertical(int color) {
        LinearLayout layout = new LinearLayout(this.context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this.context);
        view.setText(value == null ? "" : value);
        view.setTextSize(size * this.textScale);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT);
        Compat.setLetterSpacing(view, 0.0f);
        return view;
    }

    private void addTop(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(view, params);
    }

    private int dp(int value) {
        return Math.round(value * this.context.getResources().getDisplayMetrics().density * this.uiScale);
    }
}
