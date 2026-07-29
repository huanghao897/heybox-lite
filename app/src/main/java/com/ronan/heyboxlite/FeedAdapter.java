package com.ronan.heyboxlite;

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

    private final Context context;
    private final List<FeedItem> items;
    private final Listener listener;
    private final LikeListener likeListener;
    private final boolean noImage;
    private final float uiScale;
    private final float textScale;
    private final boolean darkMode;
    private final int textColor;
    private final int mutedColor;
    private final int cardColor;
    private final int primaryColor;
    private final int secondaryColor;
    private final ThemeTokens tokens;
    private final boolean compactScreen;
    private final int avatarTargetPx;
    private final int coverTargetPx;
    private final Set<String> animatedItems = new HashSet<>();
    private int initialAnimationCount;

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
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.listener = listener;
        this.likeListener = likeListener;
        tokens = ThemeTokens.of(darkMode, primaryColor, secondaryColor);
        textColor = tokens.text;
        mutedColor = tokens.muted;
        cardColor = tokens.panel;
        float widthDp = context.getResources().getDisplayMetrics().widthPixels
                / context.getResources().getDisplayMetrics().density;
        compactScreen = widthDp <= 390f;
        avatarTargetPx = dp(28);
        coverTargetPx = dp(compactScreen ? 88 : 104);
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
            outer.setBackgroundColor(tokens.background);
            outer.setPadding(dp(compactScreen ? 8 : 10), dp(4),
                    dp(compactScreen ? 8 : 10), dp(4));

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(compactScreen ? 10 : 12), dp(10),
                    dp(compactScreen ? 10 : 12), dp(9));
            Compat.setBackground(card, cardBackground());
            outer.addView(card, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout authorRow = new LinearLayout(context);
            authorRow.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(authorRow, new LinearLayout.LayoutParams(-1, dp(30)));

            ImageView avatar = new ImageView(context);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Compat.setBackground(avatar, round(coverPlaceholderColor(), 14));
            Compat.clipToOutline(avatar);
            authorRow.addView(avatar, new LinearLayout.LayoutParams(dp(28), dp(28)));

            LinearLayout authorCopy = new LinearLayout(context);
            authorCopy.setOrientation(LinearLayout.VERTICAL);
            authorCopy.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams authorCopyParams =
                    new LinearLayout.LayoutParams(0, dp(30), 1f);
            authorCopyParams.leftMargin = dp(8);
            authorRow.addView(authorCopy, authorCopyParams);

            TextView author = label(12, textColor);
            author.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            author.setSingleLine(true);
            author.setEllipsize(TextUtils.TruncateAt.END);
            authorCopy.addView(author);

            TextView meta = label(10, tokens.subtle);
            meta.setSingleLine(true);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            authorCopy.addView(meta);

            TextView badge = label(9, mutedColor);
            badge.setGravity(Gravity.CENTER);
            badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            badge.setText("文章");
            Compat.setBackground(badge, round(tokens.panelElevated, 6));
            authorRow.addView(badge, new LinearLayout.LayoutParams(dp(34), dp(20)));

            LinearLayout body = new LinearLayout(context);
            body.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
            bodyParams.topMargin = dp(8);
            card.addView(body, bodyParams);

            LinearLayout copy = new LinearLayout(context);
            copy.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
            body.addView(copy, copyParams);

            TextView title = label(compactScreen ? 15 : 16, textColor);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setLineSpacing(0, 1.06f);
            copy.addView(title);

            TextView description = label(compactScreen ? 11.5f : 12f, mutedColor);
            description.setMaxLines(2);
            description.setEllipsize(TextUtils.TruncateAt.END);
            description.setLineSpacing(dp(1), 1.08f);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
            descriptionParams.topMargin = dp(4);
            copy.addView(description, descriptionParams);

            int coverWidth = compactScreen ? 88 : 104;
            int coverHeight = compactScreen ? 64 : 74;
            ImageView cover = new ImageView(context);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Compat.setBackground(cover, round(coverPlaceholderColor(), 8));
            Compat.clipToOutline(cover);
            LinearLayout.LayoutParams coverParams =
                    new LinearLayout.LayoutParams(dp(coverWidth), dp(coverHeight));
            coverParams.leftMargin = dp(compactScreen ? 8 : 10);
            body.addView(cover, coverParams);

            LinearLayout actions = new LinearLayout(context);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(26));
            actionsParams.topMargin = dp(5);
            card.addView(actions, actionsParams);

            TextView likes = stat(R.drawable.official_comment_like_line);
            LinearLayout.LayoutParams likesParams =
                    new LinearLayout.LayoutParams(-2, dp(24));
            likesParams.rightMargin = dp(15);
            actions.addView(likes, likesParams);
            TextView comments = stat(R.drawable.official_detail_comment);
            actions.addView(comments, new LinearLayout.LayoutParams(-2, dp(24)));

            holder = new Holder(card, copy, avatar, badge, title, description,
                    author, meta, likes, comments, cover);
            outer.setTag(holder);
            reusable = outer;
        } else {
            holder = (Holder) reusable.getTag();
        }

        FeedItem item = getItem(position);
        Motions.reset(reusable);
        String animationKey = item.id.isEmpty() ? "position:" + position : item.id;
        if (initialAnimationCount < 6 && animatedItems.add(animationKey)) {
            Motions.listEnter(reusable, initialAnimationCount++, dp(6));
        }
        String title = RichContent.plainText(item.title);
        String description = RichContent.plainText(item.description);
        EmojiRenderer.set(holder.title, title.isEmpty() ? "无标题内容" : title, darkMode);
        holder.badge.setVisibility(item.article ? View.VISIBLE : View.GONE);
        EmojiRenderer.set(holder.description, description, darkMode);
        holder.description.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);
        holder.author.setText(item.author.isEmpty() ? "小黑盒社区" : item.author);
        holder.meta.setText(feedMeta(item));
        holder.badge.setVisibility(item.article ? View.VISIBLE : View.GONE);
        boolean showAvatar = !noImage && !item.authorAvatar.isEmpty();
        if (showAvatar) {
            Compat.setBackground(holder.avatar, round(coverPlaceholderColor(), 14));
            ImageLoader.intoPlain(holder.avatar, item.authorAvatar, avatarTargetPx);
        } else {
            ImageLoader.cancel(holder.avatar);
            holder.avatar.setImageDrawable(null);
        }
        updateStatView(holder.likes, item.likes, item.liked, item.liked
                ? R.drawable.official_comment_like_filled
                : R.drawable.official_comment_like_line);
        updateStatView(holder.comments, item.comments, false, R.drawable.official_detail_comment);
        boolean showImage = !noImage && !item.image.isEmpty();
        holder.cover.setVisibility(showImage ? View.VISIBLE : View.GONE);
        if (showImage) {
            Compat.setBackground(holder.cover, round(coverPlaceholderColor(), 8));
            ImageLoader.intoPlain(holder.cover, item.image, coverTargetPx);
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
                UiComponents.press(holder.likes);
                likeListener.onLike(item);
                notifyDataSetChanged();
            }
        });
        return reusable;
    }

    private TextView stat(int icon) {
        TextView view = label(10, mutedColor);
        view.setGravity(Gravity.CENTER_VERTICAL);
        setStatIcon(view, icon, mutedColor, 14);
        return view;
    }

    private void updateStatView(TextView view, int count, boolean active, int icon) {
        int fg = active ? textColor : mutedColor;
        view.setText(formatCount(count));
        view.setTextColor(fg);
        Compat.setBackground(view, null);
        setStatIcon(view, icon, fg, active ? 16 : 14);
    }

    private String feedMeta(FeedItem item) {
        String category = item.topicName;
        if (item.article) {
            category = category.isEmpty() ? "文章" : "文章 · " + category;
        }
        String time = relativeTime(item.createdAt);
        if (category.isEmpty()) return time;
        return time.isEmpty() ? category : category + " · " + time;
    }

    private String relativeTime(long seconds) {
        if (seconds <= 0L) return "";
        long millis = seconds > 100000000000L ? seconds : seconds * 1000L;
        long diff = Math.max(0L, System.currentTimeMillis() - millis);
        long minute = 60L * 1000L;
        long hour = 60L * minute;
        long day = 24L * hour;
        if (diff < minute) return "刚刚";
        if (diff < hour) return Math.max(1L, diff / minute) + "分钟前";
        if (diff < day) return Math.max(1L, diff / hour) + "小时前";
        return new SimpleDateFormat("MM-dd", Locale.getDefault()).format(new Date(millis));
    }

    private String formatCount(int value) {
        int count = Math.max(0, value);
        if (count >= 10000) {
            return compactDecimal(count / 10000f) + "万";
        }
        if (count >= 1000) {
            return compactDecimal(count / 1000f) + "K";
        }
        return String.valueOf(count);
    }

    private String compactDecimal(float value) {
        if (value >= 100f || Math.abs(value - Math.round(value)) < 0.05f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private int coverPlaceholderColor() {
        return darkMode ? Color.rgb(42, 43, 45) : Color.rgb(232, 234, 236);
    }

    private void setStatIcon(TextView view, int icon, int color, int size) {
        Drawable drawable = Compat.tintedDrawable(context, icon, color);
        if (drawable != null) {
            drawable.setBounds(0, 0, dp(size), dp(size));
            view.setCompoundDrawables(drawable, null, null, null);
            view.setCompoundDrawablePadding(dp(2));
        }
    }

    private TextView label(float size, int color) {
        TextView view = new TextView(context);
        view.setTextSize(size * textScale);
        view.setTextColor(color);
        Compat.setLetterSpacing(view, 0);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable cardBackground() {
        return UiComponents.card(context, tokens, uiScale);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density * uiScale);
    }

    private static final class Holder {
        final LinearLayout card;
        final LinearLayout copy;
        final ImageView avatar;
        final TextView badge;
        final TextView title;
        final TextView description;
        final TextView author;
        final TextView meta;
        final TextView likes;
        final TextView comments;
        final ImageView cover;

        Holder(LinearLayout card, LinearLayout copy, ImageView avatar, TextView badge,
               TextView title, TextView description,
               TextView author, TextView meta, TextView likes, TextView comments,
               ImageView cover) {
            this.card = card;
            this.copy = copy;
            this.avatar = avatar;
            this.badge = badge;
            this.title = title;
            this.description = description;
            this.author = author;
            this.meta = meta;
            this.likes = likes;
            this.comments = comments;
            this.cover = cover;
        }
    }
}
