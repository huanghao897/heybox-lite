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

    interface FollowCallback {
        void onComplete(boolean success);
    }

    interface FollowListener {
        void onFollow(FeedItem item, boolean following, FollowCallback callback);
    }

    private final Context context;
    private final List<FeedItem> items;
    private final Listener listener;
    private final LikeListener likeListener;
    private final FollowListener followListener;
    private final String currentUserId;
    private final boolean noImage;
    private final float uiScale;
    private final float textScale;
    private final boolean darkMode;
    private final boolean roundLayout;
    private final int roundHorizontalPaddingPx;
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
                primaryColor, secondaryColor, listener, null, null, "",
                false, 0);
    }

    FeedAdapter(Context context, List<FeedItem> items, boolean noImage,
                float uiScale, float textScale, boolean darkMode,
                int primaryColor, int secondaryColor, Listener listener,
                LikeListener likeListener) {
        this(context, items, noImage, uiScale, textScale, darkMode,
                primaryColor, secondaryColor, listener, likeListener, null, "",
                false, 0);
    }

    FeedAdapter(Context context, List<FeedItem> items, boolean noImage,
                float uiScale, float textScale, boolean darkMode,
                int primaryColor, int secondaryColor, Listener listener,
                LikeListener likeListener, FollowListener followListener,
                String currentUserId, boolean roundLayout,
                int roundHorizontalPaddingPx) {
        this.context = context;
        this.items = items;
        this.noImage = noImage;
        this.uiScale = uiScale;
        this.textScale = textScale;
        this.darkMode = darkMode;
        this.roundLayout = roundLayout;
        this.roundHorizontalPaddingPx = Math.max(0, roundHorizontalPaddingPx);
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.listener = listener;
        this.likeListener = likeListener;
        this.followListener = followListener;
        this.currentUserId = currentUserId == null ? "" : currentUserId;
        tokens = ThemeTokens.of(darkMode, primaryColor, secondaryColor);
        textColor = tokens.text;
        mutedColor = tokens.muted;
        cardColor = tokens.panel;
        float widthDp = context.getResources().getDisplayMetrics().widthPixels
                / context.getResources().getDisplayMetrics().density;
        compactScreen = roundLayout || widthDp <= 390f;
        avatarTargetPx = dp(28);
        coverTargetPx = dp(roundLayout ? 76 : compactScreen ? 88 : 104);
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
            int horizontalPadding = roundLayout
                    ? Math.max(dp(8), roundHorizontalPaddingPx)
                    : dp(compactScreen ? 8 : 10);
            outer.setPadding(horizontalPadding, dp(roundLayout ? 3 : 4),
                    horizontalPadding, dp(roundLayout ? 3 : 4));

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(roundLayout ? 10 : compactScreen ? 10 : 12),
                    dp(roundLayout ? 9 : 10),
                    dp(roundLayout ? 10 : compactScreen ? 10 : 12),
                    dp(roundLayout ? 8 : 9));
            Compat.setBackground(card, cardBackground());
            outer.addView(card, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout authorRow = new LinearLayout(context);
            authorRow.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(authorRow, new LinearLayout.LayoutParams(
                    -1, dp(roundLayout ? 28 : 30)));

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
            badge.setPadding(dp(7), 0, dp(7), 0);
            Compat.setBackground(badge, UiComponents.softPill(context, tokens, uiScale));
            authorRow.addView(badge, new LinearLayout.LayoutParams(-2, dp(20)));

            TextView follow = label(18, textColor);
            follow.setGravity(Gravity.CENTER);
            follow.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            follow.setContentDescription("关注");
            LinearLayout.LayoutParams followParams =
                    new LinearLayout.LayoutParams(dp(30), dp(26));
            followParams.leftMargin = dp(7);
            authorRow.addView(follow, followParams);

            LinearLayout body = new LinearLayout(context);
            body.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
            bodyParams.topMargin = dp(8);
            card.addView(body, bodyParams);

            LinearLayout copy = new LinearLayout(context);
            copy.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
            body.addView(copy, copyParams);

            TextView title = label(roundLayout ? 15 : compactScreen ? 15 : 16,
                    textColor);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setLineSpacing(0, 1.06f);
            copy.addView(title);

            TextView description = label(roundLayout ? 11f
                    : compactScreen ? 11.5f : 12f, mutedColor);
            description.setMaxLines(roundLayout ? 1 : 2);
            description.setEllipsize(TextUtils.TruncateAt.END);
            description.setLineSpacing(dp(1), 1.08f);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
            descriptionParams.topMargin = dp(4);
            copy.addView(description, descriptionParams);

            int coverWidth = roundLayout ? 76 : compactScreen ? 88 : 104;
            int coverHeight = roundLayout ? 58 : compactScreen ? 64 : 74;
            ImageView cover = new ImageView(context);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Compat.setBackground(cover, round(coverPlaceholderColor(), 8));
            Compat.clipToOutline(cover);
            LinearLayout.LayoutParams coverParams =
                    new LinearLayout.LayoutParams(dp(coverWidth), dp(coverHeight));
            coverParams.leftMargin = dp(roundLayout ? 7 : compactScreen ? 8 : 10);
            body.addView(cover, coverParams);

            LinearLayout actions = new LinearLayout(context);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                    -1, dp(roundLayout ? 25 : 26));
            actionsParams.topMargin = dp(roundLayout ? 4 : 5);
            card.addView(actions, actionsParams);

            TextView topic = label(9.5f, mutedColor);
            topic.setGravity(Gravity.CENTER);
            topic.setSingleLine(true);
            topic.setEllipsize(TextUtils.TruncateAt.END);
            topic.setMaxWidth(dp(roundLayout ? 76 : 110));
            topic.setPadding(dp(7), 0, dp(7), 0);
            Compat.setBackground(topic, UiComponents.softPill(context, tokens, uiScale));
            actions.addView(topic, new LinearLayout.LayoutParams(-2, dp(20)));
            actions.addView(new View(context),
                    new LinearLayout.LayoutParams(0, 1, 1.0f));
            TextView likes = stat(R.drawable.official_comment_like_line);
            LinearLayout.LayoutParams likesParams =
                    new LinearLayout.LayoutParams(-2, dp(24));
            likesParams.rightMargin = dp(12);
            actions.addView(likes, likesParams);
            TextView comments = stat(R.drawable.official_detail_comment);
            actions.addView(comments, new LinearLayout.LayoutParams(-2, dp(24)));

            holder = new Holder(card, copy, avatar, badge, follow, topic, title, description,
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
        EmojiRenderer.set(holder.description, description, darkMode);
        holder.description.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);
        holder.author.setText(item.author.isEmpty() ? "小黑盒社区" : item.author);
        holder.meta.setText(feedMeta(item));
        String contentType = item.article ? "文章" : "帖子";
        holder.badge.setText(item.pinned ? "置顶 · " + contentType : contentType);
        holder.badge.setTextColor(item.pinned ? tokens.accent : mutedColor);
        holder.badge.setVisibility(View.VISIBLE);
        holder.topic.setText(item.topicName);
        holder.topic.setVisibility(item.topicName.isEmpty() ? View.GONE : View.VISIBLE);
        updateFollowView(holder.follow, item);
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
        holder.follow.setOnClickListener(view -> {
            if (followListener == null || item.followPending) return;
            boolean before = item.following;
            boolean next = !before;
            item.following = next;
            item.followPending = true;
            updateFollowView(holder.follow, item);
            UiComponents.press(holder.follow);
            followListener.onFollow(item, next, success -> {
                if (!success) item.following = before;
                item.followPending = false;
                notifyDataSetChanged();
            });
        });
        return reusable;
    }

    private void updateFollowView(TextView view, FeedItem item) {
        boolean visible = followListener != null
                && !item.authorId.isEmpty()
                && !item.authorId.equals(currentUserId);
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        view.setText(item.following ? "✓" : "+");
        view.setTextColor(item.following ? tokens.muted : textColor);
        view.setAlpha(item.followPending ? 0.55f : 1f);
        view.setEnabled(!item.followPending);
        view.setContentDescription(item.following ? "取消关注" : "关注");
        GradientDrawable background = round(tokens.panelElevated, 13);
        background.setStroke(Math.max(1, dp(1)), tokens.hairline);
        Compat.setBackground(view, background);
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
        return relativeTime(item.createdAt);
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
        return UiComponents.round(context, tokens.panel,
                roundLayout ? 9 : 10, uiScale);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density * uiScale);
    }

    private static final class Holder {
        final LinearLayout card;
        final LinearLayout copy;
        final ImageView avatar;
        final TextView badge;
        final TextView follow;
        final TextView topic;
        final TextView title;
        final TextView description;
        final TextView author;
        final TextView meta;
        final TextView likes;
        final TextView comments;
        final ImageView cover;

        Holder(LinearLayout card, LinearLayout copy, ImageView avatar, TextView badge,
               TextView follow, TextView topic,
               TextView title, TextView description,
               TextView author, TextView meta, TextView likes, TextView comments,
               ImageView cover) {
            this.card = card;
            this.copy = copy;
            this.avatar = avatar;
            this.badge = badge;
            this.follow = follow;
            this.topic = topic;
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
