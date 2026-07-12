package com.openzen.heyboxcommunity;

import android.content.Context;
import android.graphics.Color;
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
            Compat.setBackground(card, cardBackground());
            outer.addView(card, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout body = new LinearLayout(context);
            body.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(body, new LinearLayout.LayoutParams(-1, dp(74)));

            ImageView cover = new ImageView(context);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Compat.setBackground(cover, round(coverPlaceholderColor(), 8));
            Compat.clipToOutline(cover);
            body.addView(cover, new LinearLayout.LayoutParams(dp(102), dp(66)));

            LinearLayout copy = new LinearLayout(context);
            copy.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, dp(66), 1);
            copyParams.leftMargin = dp(10);
            body.addView(copy, copyParams);

            LinearLayout titleLine = new LinearLayout(context);
            titleLine.setGravity(Gravity.CENTER_VERTICAL);
            copy.addView(titleLine, new LinearLayout.LayoutParams(-1, -1));

            TextView badge = label(9, tokens.text);
            badge.setGravity(Gravity.CENTER);
            badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            badge.setText("文章");
            Compat.setBackground(badge, UiComponents.softPill(context, tokens, uiScale));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(34), dp(20));
            badgeParams.rightMargin = dp(6);
            titleLine.addView(badge, badgeParams);

            TextView title = label(14, textColor);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setMaxLines(3);
            title.setLineSpacing(0, 1.08f);
            titleLine.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

            TextView description = label(11, darkMode
                    ? Color.rgb(203, 205, 207) : Color.rgb(63, 67, 72));
            description.setMaxLines(2);
            description.setLineSpacing(dp(1), 1.08f);
            card.addView(description);

            View divider = new View(context);
            divider.setBackgroundColor(tokens.hairline);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
            dividerParams.topMargin = dp(7);
            card.addView(divider, dividerParams);

            LinearLayout meta = new LinearLayout(context);
            meta.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, dp(24));
            metaParams.topMargin = dp(5);
            card.addView(meta, metaParams);

            TextView author = label(10, mutedColor);
            author.setSingleLine(true);
            meta.addView(author, new LinearLayout.LayoutParams(0, -2, 1));
            TextView likes = stat(R.drawable.official_comment_like_line);
            meta.addView(likes, new LinearLayout.LayoutParams(dp(48), dp(24)));
            TextView comments = stat(R.drawable.official_detail_comment);
            meta.addView(comments, new LinearLayout.LayoutParams(dp(48), dp(24)));

            holder = new Holder(card, copy, badge, title, description, author, likes, comments, cover);
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
        updateStatView(holder.likes, item.likes, item.liked, item.liked
                ? R.drawable.official_comment_like_filled
                : R.drawable.official_comment_like_line);
        updateStatView(holder.comments, item.comments, false, R.drawable.official_detail_comment);
        boolean showImage = !noImage && !item.image.isEmpty();
        holder.cover.setVisibility(showImage ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams copyParams =
                (LinearLayout.LayoutParams) holder.copy.getLayoutParams();
        copyParams.leftMargin = showImage ? dp(10) : 0;
        holder.copy.setLayoutParams(copyParams);
        if (showImage) {
            Compat.setBackground(holder.cover, round(coverPlaceholderColor(), 8));
            ImageLoader.intoPlain(holder.cover, item.image, 320);
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
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(4), 0, dp(4), 0);
        setStatIcon(view, icon, mutedColor, 14);
        return view;
    }

    private void updateStatView(TextView view, int count, boolean active, int icon) {
        int bg = active ? activeStatBackground() : Color.TRANSPARENT;
        int fg = active ? contrast(bg) : mutedColor;
        view.setText(String.valueOf(Math.max(0, count)));
        view.setTextColor(fg);
        GradientDrawable drawable = round(bg, 8);
        drawable.setStroke(dp(1), active ? blend(bg, fg, 0.24f) : Color.TRANSPARENT);
        Compat.setBackground(view, drawable);
        setStatIcon(view, icon, fg, active ? 16 : 14);
    }

    private int activeStatBackground() {
        return darkMode ? tokens.text : tokens.primary;
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

    private static int contrast(int color) {
        return ThemeTokens.contrast(color);
    }

    private static int blend(int base, int overlay, float amount) {
        return ThemeTokens.blend(base, overlay, amount);
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
               TextView title, TextView description,
               TextView author, TextView likes, TextView comments, ImageView cover) {
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
