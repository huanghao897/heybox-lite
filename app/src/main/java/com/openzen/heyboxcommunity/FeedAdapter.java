package com.openzen.heyboxcommunity;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class FeedAdapter extends BaseAdapter {
    interface Listener {
        void onOpen(FeedItem item);
    }

    interface LikeListener {
        void onLike(FeedItem item);
    }

    private static final int ENTER_LIMIT = 6;

    private final Context context;
    private final List<FeedItem> items;
    private final Listener listener;
    private final LikeListener likeListener;
    private final boolean noImage;
    private final float uiScale;
    private final float textScale;
    private final boolean darkMode;
    private final ThemeTokens tokens;
    private final Set<String> enteredIds = new HashSet<>();

    FeedAdapter(Context context, List<FeedItem> items, boolean noImage,
                float uiScale, float textScale, boolean darkMode,
                int primaryColor, int secondaryColor, Listener listener) {
        this(context, items, noImage, uiScale, textScale, darkMode,
                primaryColor, secondaryColor, listener, null);
    }

    FeedAdapter(Context context, List<FeedItem> items, boolean noImage,
                float uiScale, float textScale, boolean darkMode,
                int primaryColor, int secondaryColor, Listener listener,
                LikeListener likeListener) {
        this.context = context;
        this.items = items;
        this.noImage = noImage;
        this.uiScale = uiScale;
        this.textScale = textScale;
        this.darkMode = darkMode;
        this.listener = listener;
        this.likeListener = likeListener;
        this.tokens = ThemeTokens.of(darkMode, primaryColor, secondaryColor);
        if (!EmojiStore.isLoaded()) EmojiStore.whenReady(this::notifyDataSetChanged);
    }

    @Override public int getCount() { return items.size(); }
    @Override public FeedItem getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View reusable, ViewGroup parent) {
        Holder holder;
        if (reusable == null) {
            LinearLayout outer = new LinearLayout(context);
            outer.setPadding(dp(7), dp(4), dp(7), dp(4));

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(9));
            Compat.setBackground(card, UiComponents.card(context, tokens, uiScale));
            outer.addView(card, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout content = new LinearLayout(context);
            content.setGravity(Gravity.CENTER_VERTICAL);
            content.setMinimumHeight(dp(70));
            card.addView(content, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout copy = new LinearLayout(context);
            copy.setOrientation(LinearLayout.VERTICAL);
            content.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

            LinearLayout titleLine = new LinearLayout(context);
            titleLine.setGravity(Gravity.TOP);
            copy.addView(titleLine, new LinearLayout.LayoutParams(-1, -2));

            TextView badge = label(9, tokens.accent);
            badge.setGravity(Gravity.CENTER);
            badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            badge.setPadding(dp(6), 0, dp(6), 0);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, dp(19));
            badgeParams.rightMargin = dp(5);
            titleLine.addView(badge, badgeParams);

            TextView title = label(14, tokens.text);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setMaxLines(2);
            title.setLineSpacing(0, 1.08f);
            titleLine.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

            TextView description = label(11, tokens.muted);
            description.setSingleLine(true);
            description.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
            descriptionParams.topMargin = dp(4);
            copy.addView(description, descriptionParams);

            ImageView cover = new ImageView(context);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Compat.setBackground(cover, rounded(tokens.panelElevated, 11));
            Compat.clipToOutline(cover);
            LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(dp(64), dp(64));
            coverParams.leftMargin = dp(10);
            content.addView(cover, coverParams);

            LinearLayout meta = new LinearLayout(context);
            meta.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, dp(24));
            metaParams.topMargin = dp(5);
            card.addView(meta, metaParams);

            TextView author = label(10, tokens.muted);
            author.setSingleLine(true);
            meta.addView(author, new LinearLayout.LayoutParams(0, -2, 1));
            TextView likes = stat(R.drawable.ic_thumb_up);
            meta.addView(likes, new LinearLayout.LayoutParams(dp(48), -1));
            TextView comments = stat(R.drawable.ic_comment);
            meta.addView(comments, new LinearLayout.LayoutParams(dp(48), -1));

            holder = new Holder(card, cover, badge, title, description, author, likes, comments);
            outer.setTag(holder);
            reusable = outer;
        } else {
            holder = (Holder) reusable.getTag();
        }

        Motions.reset(reusable);
        FeedItem item = getItem(position);
        if (!Motions.off() && position < ENTER_LIMIT && item.id != null
                && !item.id.isEmpty() && enteredIds.add(item.id)) {
            Motions.listEnter(reusable, position, dp(8));
        }

        String title = RichContent.plainText(item.title);
        String description = RichContent.plainText(item.description);
        EmojiRenderer.set(holder.title, title.isEmpty() ? "无标题内容" : title, darkMode);
        EmojiRenderer.set(holder.description, description, darkMode);
        holder.description.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);
        holder.author.setText(item.author.isEmpty() ? "小黑盒社区" : item.author);
        updateBadge(holder.badge, item);
        updateStat(holder.likes, item.likes, item.liked, R.drawable.ic_thumb_up);
        updateStat(holder.comments, item.comments, false, R.drawable.ic_comment);

        boolean showImage = !noImage && !item.image.isEmpty();
        holder.cover.setVisibility(showImage ? View.VISIBLE : View.GONE);
        if (showImage) {
            Compat.setBackground(holder.cover, rounded(tokens.panelElevated, 11));
            ImageLoader.intoPlain(holder.cover, item.image, 256);
        } else {
            ImageLoader.cancel(holder.cover);
            holder.cover.setImageDrawable(null);
        }

        View.OnClickListener open = view -> listener.onOpen(item);
        reusable.setOnClickListener(open);
        holder.card.setOnClickListener(view -> {
            UiComponents.press(holder.card);
            open.onClick(view);
        });
        holder.likes.setOnClickListener(view -> {
            if (likeListener == null) listener.onOpen(item);
            else {
                UiComponents.press(view);
                likeListener.onLike(item);
                notifyDataSetChanged();
            }
        });
        return reusable;
    }

    private void updateBadge(TextView badge, FeedItem item) {
        if (!item.pinned && !item.article) {
            badge.setVisibility(View.GONE);
            return;
        }
        badge.setVisibility(View.VISIBLE);
        if (item.pinned) {
            int red = Color.rgb(232, 88, 105);
            badge.setText("置顶");
            badge.setTextColor(red);
            Compat.setBackground(badge, rounded(ThemeTokens.blend(tokens.panel, red, 0.18f), 7));
        } else {
            badge.setText("文章");
            badge.setTextColor(tokens.accent);
            Compat.setBackground(badge, rounded(tokens.softAccent(), 7));
        }
    }

    private TextView stat(int icon) {
        TextView view = label(10, tokens.muted);
        view.setGravity(Gravity.CENTER);
        setStatIcon(view, icon, tokens.muted, 14);
        return view;
    }

    private void updateStat(TextView view, int count, boolean active, int icon) {
        int color = active ? tokens.accent : tokens.muted;
        view.setText(String.valueOf(Math.max(0, count)));
        view.setTextColor(color);
        Compat.setBackground(view, active ? rounded(tokens.softAccent(), 10) : null);
        setStatIcon(view, icon, color, active ? 15 : 14);
    }

    private void setStatIcon(TextView view, int icon, int color, int size) {
        Drawable drawable = Compat.tintedDrawable(context, icon, color);
        if (drawable == null) return;
        drawable.setBounds(0, 0, dp(size), dp(size));
        view.setCompoundDrawables(drawable, null, null, null);
        view.setCompoundDrawablePadding(dp(3));
    }

    private TextView label(float size, int color) {
        TextView view = new TextView(context);
        view.setTextSize(size * textScale);
        view.setTextColor(color);
        Compat.setLetterSpacing(view, 0);
        return view;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density * uiScale);
    }

    private static final class Holder {
        final LinearLayout card;
        final ImageView cover;
        final TextView badge;
        final TextView title;
        final TextView description;
        final TextView author;
        final TextView likes;
        final TextView comments;

        Holder(LinearLayout card, ImageView cover, TextView badge, TextView title,
               TextView description, TextView author, TextView likes,
               TextView comments) {
            this.card = card;
            this.cover = cover;
            this.badge = badge;
            this.title = title;
            this.description = description;
            this.author = author;
            this.likes = likes;
            this.comments = comments;
        }
    }
}
