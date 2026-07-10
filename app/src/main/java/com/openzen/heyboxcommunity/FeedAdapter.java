package com.openzen.heyboxcommunity;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
            LinearLayout item = new LinearLayout(context);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(12), dp(10), dp(12), 0);
            item.setBackgroundColor(tokens.background);

            TextView author = label(10, tokens.muted);
            author.setSingleLine(true);
            item.addView(author, new LinearLayout.LayoutParams(-1, dp(20)));

            LinearLayout body = new LinearLayout(context);
            body.setGravity(Gravity.CENTER_VERTICAL);
            item.addView(body, new LinearLayout.LayoutParams(-1, dp(76)));

            LinearLayout copy = new LinearLayout(context);
            copy.setOrientation(LinearLayout.VERTICAL);
            body.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));

            LinearLayout titleLine = new LinearLayout(context);
            titleLine.setGravity(Gravity.TOP);
            copy.addView(titleLine, new LinearLayout.LayoutParams(-1, -2));

            TextView badge = label(9, tokens.secondary);
            badge.setText("文章");
            badge.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(28), dp(20));
            badgeParams.rightMargin = dp(4);
            titleLine.addView(badge, badgeParams);

            TextView title = label(14, tokens.text);
            title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            title.setMaxLines(2);
            title.setLineSpacing(0, 1.08f);
            titleLine.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

            TextView description = label(11, tokens.muted);
            description.setMaxLines(2);
            description.setLineSpacing(dp(1), 1.06f);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
            descriptionParams.topMargin = dp(3);
            copy.addView(description, descriptionParams);

            ImageView cover = new ImageView(context);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Compat.setBackground(cover, placeholder());
            Compat.clipToOutline(cover);
            LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(dp(92), dp(66));
            coverParams.leftMargin = dp(10);
            body.addView(cover, coverParams);

            LinearLayout stats = new LinearLayout(context);
            stats.setGravity(Gravity.CENTER_VERTICAL);
            item.addView(stats, new LinearLayout.LayoutParams(-1, dp(28)));
            TextView spacer = label(10, tokens.muted);
            stats.addView(spacer, new LinearLayout.LayoutParams(0, -1, 1));
            TextView likes = stat(R.drawable.ic_thumb_up);
            stats.addView(likes, new LinearLayout.LayoutParams(dp(58), -1));
            TextView comments = stat(R.drawable.ic_comment);
            stats.addView(comments, new LinearLayout.LayoutParams(dp(58), -1));

            View divider = new View(context);
            divider.setBackgroundColor(tokens.hairline);
            item.addView(divider, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));

            holder = new Holder(item, copy, badge, title, description, author, likes, comments, cover);
            item.setTag(holder);
            reusable = item;
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
        holder.badge.setVisibility(item.article ? View.VISIBLE : View.GONE);
        EmojiRenderer.set(holder.description, description, darkMode);
        holder.description.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);
        holder.author.setText(meta(item));
        updateStat(holder.likes, item.likes, item.liked, R.drawable.ic_thumb_up);
        updateStat(holder.comments, item.comments, false, R.drawable.ic_comment);

        boolean showImage = !noImage && !item.image.isEmpty();
        holder.cover.setVisibility(showImage ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams coverParams = (LinearLayout.LayoutParams) holder.cover.getLayoutParams();
        coverParams.leftMargin = showImage ? dp(10) : 0;
        holder.cover.setLayoutParams(coverParams);
        if (showImage) {
            Compat.setBackground(holder.cover, placeholder());
            ImageLoader.intoPlain(holder.cover, item.image, 320);
        } else {
            ImageLoader.cancel(holder.cover);
            holder.cover.setImageDrawable(null);
        }

        reusable.setOnClickListener(view -> {
            UiComponents.press(view);
            listener.onOpen(item);
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

    private String meta(FeedItem item) {
        String author = item.author.isEmpty() ? "小黑盒社区" : item.author;
        StringBuilder value = new StringBuilder(author);
        if (!item.topicName.isEmpty()) value.append(" · ").append(item.topicName);
        String time = relativeTime(item.createdAt);
        if (!time.isEmpty()) value.append(" · ").append(time);
        return value.toString();
    }

    private String relativeTime(long seconds) {
        if (seconds <= 0L) return "";
        long age = Math.max(0L, System.currentTimeMillis() / 1000L - seconds);
        if (age < 60L) return "刚刚";
        if (age < 3600L) return age / 60L + " 分钟前";
        if (age < 86400L) return age / 3600L + " 小时前";
        return new SimpleDateFormat("MM-dd", Locale.getDefault()).format(new Date(seconds * 1000L));
    }

    private TextView stat(int icon) {
        TextView view = label(10, tokens.muted);
        view.setGravity(Gravity.CENTER);
        setStatIcon(view, icon, tokens.muted, 14);
        return view;
    }

    private void updateStat(TextView view, int count, boolean active, int icon) {
        int color = active ? tokens.secondary : tokens.muted;
        view.setText(String.valueOf(Math.max(0, count)));
        view.setTextColor(color);
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        setStatIcon(view, icon, color, active ? 16 : 14);
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

    private GradientDrawable placeholder() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(tokens.hairline);
        drawable.setCornerRadius(dp(6));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density * uiScale);
    }

    private static final class Holder {
        final LinearLayout item;
        final LinearLayout copy;
        final TextView badge;
        final TextView title;
        final TextView description;
        final TextView author;
        final TextView likes;
        final TextView comments;
        final ImageView cover;

        Holder(LinearLayout item, LinearLayout copy, TextView badge, TextView title,
               TextView description, TextView author, TextView likes,
               TextView comments, ImageView cover) {
            this.item = item;
            this.copy = copy;
            this.badge = badge;
            this.title = title;
            this.description = description;
            this.author = author;
            this.likes = likes;
            this.comments = comments;
            this.cover = cover;
        }
    }
}
