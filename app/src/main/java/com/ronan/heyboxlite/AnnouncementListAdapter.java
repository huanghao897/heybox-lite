package com.ronan.heyboxlite;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
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
        Holder holder;
        if (reusable == null || !(reusable.getTag() instanceof Holder)) {
            LinearLayout row = vertical(this.tokens.background);
            row.setPadding(0, dp(3), 0, dp(3));
            LinearLayout card = new LinearLayout(this.context);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10), dp(10), dp(10), dp(10));
            Compat.setBackground(card,
                    UiComponents.groupCard(this.context, this.tokens, this.uiScale));
            row.addView(card, new LinearLayout.LayoutParams(-1, -2));

            ImageView icon = new ImageView(this.context);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setPadding(dp(5), dp(5), dp(5), dp(5));
            Drawable info = Compat.tintedDrawable(
                    this.context, R.drawable.il_info, this.tokens.text);
            if (info != null) icon.setImageDrawable(info);
            Compat.setBackground(icon,
                    UiComponents.monoChip(this.context, this.tokens, this.uiScale));
            LinearLayout.LayoutParams iconParams =
                    new LinearLayout.LayoutParams(dp(28), dp(28));
            iconParams.rightMargin = dp(10);
            card.addView(icon, iconParams);

            LinearLayout copy = vertical(0);
            TextView title = text("", 14.0f, this.tokens.text);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(title);
            TextView date = text("", 10.0f, this.tokens.muted);
            date.setPadding(0, dp(1), 0, 0);
            copy.addView(date);
            TextView preview = text("", 11.5f, this.tokens.muted);
            preview.setLineSpacing(0.0f, 1.16f);
            preview.setMaxLines(2);
            preview.setEllipsize(TextUtils.TruncateAt.END);
            addTop(copy, preview, 4);
            card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1.0f));

            ImageView arrow = new ImageView(this.context);
            arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            Drawable chevron = Compat.tintedDrawable(
                    this.context, R.drawable.il_chevron, this.tokens.muted);
            if (chevron != null) arrow.setImageDrawable(chevron);
            arrow.setAlpha(0.55f);
            LinearLayout.LayoutParams arrowParams =
                    new LinearLayout.LayoutParams(dp(18), dp(18));
            arrowParams.leftMargin = dp(6);
            card.addView(arrow, arrowParams);

            holder = new Holder(card, title, date, preview);
            row.setTag(holder);
            reusable = row;
        } else {
            holder = (Holder) reusable.getTag();
        }

        holder.title.setText(TextUtils.isEmpty(item.title) ? "公告" : item.title);
        String updatedAt = Format.announcementTime(item.updatedAt);
        holder.date.setText(updatedAt);
        holder.date.setVisibility(updatedAt.isEmpty() ? View.GONE : View.VISIBLE);
        holder.preview.setText(Format.announcementPreview(item.content));
        holder.card.setOnClickListener(view -> {
            UiComponents.press(holder.card);
            this.listener.onOpen(item);
        });
        return reusable;
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

    private static final class Holder {
        final LinearLayout card;
        final TextView title;
        final TextView date;
        final TextView preview;

        Holder(LinearLayout card, TextView title, TextView date, TextView preview) {
            this.card = card;
            this.title = title;
            this.date = date;
            this.preview = preview;
        }
    }
}
