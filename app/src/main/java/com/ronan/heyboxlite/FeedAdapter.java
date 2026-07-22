package com.ronan.heyboxlite;

import android.content.Context;
import android.graphics.Typeface;
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

    private final Context context;
    private final List<FeedItem> items;
    private final Listener listener;
    private final LikeListener likeListener;
    private final boolean noImage;
    private final float uiScale;
    private final float textScale;
    private final boolean darkMode;
    private final ThemeTokens tokens;
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
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setBackgroundColor(tokens.background);
            outer.setPadding(dp(9), dp(2), dp(9), 0);

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(3), dp(7), dp(3), dp(7));
            Compat.setBackground(card, UiComponents.selectable(context,
                    tokens.background, tokens.surfaceContainerLow,
                    tokens.primary, 4, uiScale));
            outer.addView(card, new LinearLayout.LayoutParams(-1, -2));

            View divider = new View(context);
            divider.setBackgroundColor(tokens.hairline);
            LinearLayout.LayoutParams dividerParams =
                    new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2));
            dividerParams.leftMargin = dp(3);
            dividerParams.rightMargin = dp(3);
            outer.addView(divider, dividerParams);

            LinearLayout info = new LinearLayout(context);
            info.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(info, new LinearLayout.LayoutParams(-1, dp(18)));

            TextView author = label(10.0f, tokens.muted);
            author.setSingleLine(true);
            info.addView(author, new LinearLayout.LayoutParams(0, -2, 1.0f));

            TextView badge = label(8.0f, tokens.onPrimaryContainer);
            badge.setGravity(Gravity.CENTER);
            badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            badge.setPadding(dp(5), 0, dp(5), 0);
            Compat.setBackground(badge, UiComponents.softPill(context, tokens, uiScale));
            info.addView(badge, new LinearLayout.LayoutParams(-2, dp(15)));

            LinearLayout body = new LinearLayout(context);
            body.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
            bodyParams.topMargin = dp(3);
            card.addView(body, bodyParams);

            LinearLayout copy = new LinearLayout(context);
            copy.setOrientation(LinearLayout.VERTICAL);
            body.addView(copy, new LinearLayout.LayoutParams(0, -2, 1.0f));

            TextView title = label(13.5f, tokens.text);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setMaxLines(2);
            title.setLineSpacing(0, 1.08f);
            copy.addView(title, new LinearLayout.LayoutParams(-1, -2));

            TextView description = label(10.5f, tokens.muted);
            description.setMaxLines(2);
            description.setLineSpacing(dp(1), 1.08f);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
            descriptionParams.topMargin = dp(2);
            copy.addView(description, descriptionParams);

            ImageView cover = new ImageView(context);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Compat.setBackground(cover, round(tokens.surfaceContainerHighest, 5));
            Compat.clipToOutline(cover);
            LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(dp(82), dp(62));
            coverParams.leftMargin = dp(8);
            body.addView(cover, coverParams);

            LinearLayout meta = new LinearLayout(context);
            meta.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, dp(23));
            metaParams.topMargin = dp(3);
            card.addView(meta, metaParams);

            View space = new View(context);
            meta.addView(space, new LinearLayout.LayoutParams(0, 1, 1.0f));
            TextView likes = stat(R.drawable.official_comment_like_line);
            meta.addView(likes, new LinearLayout.LayoutParams(-2, dp(23)));
            TextView comments = stat(R.drawable.official_detail_comment);
            LinearLayout.LayoutParams commentsParams = new LinearLayout.LayoutParams(-2, dp(23));
            commentsParams.leftMargin = dp(5);
            meta.addView(comments, commentsParams);

            holder = new Holder(card, copy, badge, title, description,
                    author, likes, comments, cover);
            outer.setTag(holder);
            reusable = outer;
        } else {
            holder = (Holder) reusable.getTag();
        }

        FeedItem item = getItem(position);
        Motions.reset(reusable);
        String animationKey = item.id.isEmpty() ? "position:" + position : item.id;
        if (initialAnimationCount < 5 && animatedItems.add(animationKey)) {
            Motions.listEnter(reusable, initialAnimationCount++, dp(12));
        }

        String title = RichContent.plainText(item.title);
        String description = RichContent.plainText(item.description);
        if (description.equals(title)) description = "";
        EmojiRenderer.set(holder.title, title.isEmpty() ? "无标题内容" : title, darkMode);
        EmojiRenderer.set(holder.description, description, darkMode);
        holder.description.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);

        holder.author.setText(infoLine(item));
        holder.badge.setText(item.pinned ? "置顶" : "文章");
        holder.badge.setVisibility(item.pinned || item.article ? View.VISIBLE : View.GONE);

        updateStatView(holder.likes, item.likes, item.liked, item.liked
                ? R.drawable.official_comment_like_filled
                : R.drawable.official_comment_like_line);
        updateStatView(holder.comments, item.comments, false, R.drawable.official_detail_comment);

        boolean showImage = !noImage && !item.image.isEmpty();
        holder.cover.setVisibility(showImage ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams copyParams =
                (LinearLayout.LayoutParams) holder.copy.getLayoutParams();
        copyParams.rightMargin = showImage ? 0 : dp(2);
        holder.copy.setLayoutParams(copyParams);
        if (showImage) {
            Compat.setBackground(holder.cover, round(tokens.surfaceContainerHighest, 5));
            ImageLoader.intoPlain(holder.cover, item.image, 320);
        } else {
            ImageLoader.cancel(holder.cover);
            holder.cover.setImageDrawable(null);
        }

        holder.card.setOnClickListener(view -> listener.onOpen(item));
        holder.likes.setOnClickListener(view -> {
            if (likeListener == null) {
                listener.onOpen(item);
                return;
            }
            Motions.selected(holder.likes);
            likeListener.onLike(item);
            notifyDataSetChanged();
        });
        return reusable;
    }

    private String infoLine(FeedItem item) {
        String author = item.author.isEmpty() ? "小黑盒社区" : item.author;
        StringBuilder value = new StringBuilder(author);
        if (!item.topicName.isEmpty()) value.append(" · ").append(item.topicName);
        String time = relativeTime(item.createdAt);
        if (!time.isEmpty()) value.append(" · ").append(time);
        return value.toString();
    }

    private TextView stat(int icon) {
        TextView view = label(10.0f, tokens.muted);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(dp(30));
        view.setPadding(dp(2), 0, dp(2), 0);
        setStatIcon(view, icon, tokens.muted, 14);
        return view;
    }

    private void updateStatView(TextView view, int count, boolean active, int icon) {
        int color = active ? tokens.primary : tokens.muted;
        view.setText(compactCount(count));
        view.setTextColor(color);
        Compat.setBackground(view, UiComponents.selectable(context,
                android.graphics.Color.TRANSPARENT,
                tokens.faintAccent(), tokens.primary, 8, uiScale));
        setStatIcon(view, icon, color, active ? 15 : 14);
    }

    private void setStatIcon(TextView view, int icon, int color, int size) {
        Drawable drawable = Compat.tintedDrawable(context, icon, color);
        if (drawable != null) {
            drawable.setBounds(0, 0, dp(size), dp(size));
            view.setCompoundDrawables(drawable, null, null, null);
            view.setCompoundDrawablePadding(dp(3));
        }
    }

    private TextView label(float size, int color) {
        TextView view = new TextView(context);
        view.setTextSize(size * textScale);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT);
        Compat.setLetterSpacing(view, 0);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private static String compactCount(int count) {
        int safe = Math.max(0, count);
        if (safe >= 10000) {
            return String.format(Locale.getDefault(), "%.1f万", safe / 10000.0f)
                    .replace(".0万", "万");
        }
        if (safe >= 1000) {
            return String.format(Locale.US, "%.1fK", safe / 1000.0f).replace(".0K", "K");
        }
        return String.valueOf(safe);
    }

    private static String relativeTime(long seconds) {
        if (seconds <= 0L) return "";
        long millis = seconds > 100000000000L ? seconds : seconds * 1000L;
        long diff = Math.max(0L, System.currentTimeMillis() - millis);
        if (diff < 60_000L) return "刚刚";
        if (diff < 3_600_000L) return Math.max(1L, diff / 60_000L) + "分钟前";
        if (diff < 86_400_000L) return Math.max(1L, diff / 3_600_000L) + "小时前";
        return new SimpleDateFormat("MM-dd", Locale.getDefault()).format(new Date(millis));
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density * uiScale);
    }

    private static final class Holder {
        final LinearLayout card;
        final LinearLayout copy;
        final TextView badge;
        final TextView title;
        final TextView description;
        final TextView author;
        final TextView likes;
        final TextView comments;
        final ImageView cover;

        Holder(LinearLayout card, LinearLayout copy, TextView badge,
               TextView title, TextView description, TextView author,
               TextView likes, TextView comments, ImageView cover) {
            this.card = card;
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
