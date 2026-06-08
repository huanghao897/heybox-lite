package com.openzen.heyboxcommunity;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

final class FeedAdapter extends BaseAdapter {
    interface Listener {
        void onOpen(FeedItem item);
    }

    private final Context context;
    private final List<FeedItem> items;
    private final Listener listener;
    private final boolean noImage;
    private final float uiScale;
    private final float textScale;
    private final boolean darkMode;
    private final int textColor;
    private final int mutedColor;
    private final int cardColor;
    private final int primaryColor;
    private final int secondaryColor;

    FeedAdapter(Context context, List<FeedItem> items, boolean noImage,
                float uiScale, float textScale, boolean darkMode,
                int primaryColor, int secondaryColor, Listener listener) {
        this.context = context;
        this.items = items;
        this.noImage = noImage;
        this.uiScale = uiScale;
        this.textScale = textScale;
        this.darkMode = darkMode;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.listener = listener;
        textColor = darkMode ? Color.rgb(241, 243, 245) : Color.rgb(28, 30, 33);
        mutedColor = darkMode ? Color.rgb(157, 163, 169) : Color.rgb(101, 107, 113);
        cardColor = blend(darkMode ? Color.rgb(29, 31, 33) : Color.WHITE,
                primaryColor, darkMode ? 0.07f : 0.035f);
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
            outer.setPadding(dp(6), dp(3), dp(6), dp(3));

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(9), dp(10), dp(8));
            Compat.setBackground(card, cardBackground());
            outer.addView(card, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout body = new LinearLayout(context);
            body.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(body, new LinearLayout.LayoutParams(-1, dp(76)));

            ImageView cover = new ImageView(context);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Compat.setBackground(cover, round(darkMode ? Color.rgb(43, 46, 49)
                    : Color.rgb(232, 235, 238), 6));
            Compat.clipToOutline(cover);
            body.addView(cover, new LinearLayout.LayoutParams(dp(106), dp(68)));

            LinearLayout copy = new LinearLayout(context);
            copy.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, dp(68), 1);
            copyParams.leftMargin = dp(9);
            body.addView(copy, copyParams);

            LinearLayout titleLine = new LinearLayout(context);
            titleLine.setGravity(Gravity.CENTER_VERTICAL);
            copy.addView(titleLine, new LinearLayout.LayoutParams(-1, -1));

            TextView badge = label(9, contrast(secondaryColor));
            badge.setText("文");
            badge.setGravity(Gravity.CENTER);
            badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            Compat.setBackground(badge, round(secondaryColor, 4));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(20), dp(18));
            badgeParams.rightMargin = dp(4);
            titleLine.addView(badge, badgeParams);

            TextView title = label(14, textColor);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setMaxLines(3);
            title.setLineSpacing(0, 1.08f);
            titleLine.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

            TextView description = label(11, darkMode
                    ? Color.rgb(194, 198, 202) : Color.rgb(69, 74, 79));
            description.setMaxLines(2);
            description.setLineSpacing(0, 1.1f);
            card.addView(description);

            LinearLayout meta = new LinearLayout(context);
            meta.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, dp(24));
            metaParams.topMargin = dp(3);
            card.addView(meta, metaParams);

            TextView author = label(10, mutedColor);
            author.setSingleLine(true);
            meta.addView(author, new LinearLayout.LayoutParams(0, -2, 1));
            TextView likes = stat(R.drawable.ic_thumb_up);
            meta.addView(likes, new LinearLayout.LayoutParams(dp(48), dp(24)));
            TextView comments = stat(R.drawable.ic_comment);
            meta.addView(comments, new LinearLayout.LayoutParams(dp(48), dp(24)));

            holder = new Holder(card, badge, title, description, author, likes, comments, cover);
            outer.setTag(holder);
            reusable = outer;
            reusable.setAlpha(0f);
            reusable.setTranslationY(dp(8));
            reusable.animate().alpha(1f).translationY(0).setDuration(210)
                    .setInterpolator(new DecelerateInterpolator()).start();
        } else {
            holder = (Holder) reusable.getTag();
        }

        FeedItem item = getItem(position);
        String title = RichContent.plainText(item.title);
        String description = RichContent.plainText(item.description);
        EmojiRenderer.set(holder.title, title.isEmpty() ? "无标题内容" : title, darkMode);
        holder.badge.setVisibility(item.article ? View.VISIBLE : View.GONE);
        EmojiRenderer.set(holder.description, description, darkMode);
        holder.description.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);
        holder.author.setText(item.author.isEmpty() ? "小黑盒社区" : item.author);
        holder.likes.setText(String.valueOf(item.likes));
        setStatIcon(holder.likes, R.drawable.ic_thumb_up,
                item.liked ? primaryColor : mutedColor, item.liked ? 16 : 14);
        holder.likes.setTextColor(item.liked ? primaryColor : mutedColor);
        holder.comments.setText(String.valueOf(item.comments));
        boolean showImage = !noImage && !item.image.isEmpty();
        holder.cover.setVisibility(showImage ? View.VISIBLE : View.GONE);
        if (showImage) {
            ImageLoader.into(holder.cover, item.image, 320);
        } else {
            ImageLoader.cancel(holder.cover);
            holder.cover.setImageDrawable(null);
        }
        View.OnClickListener open = view -> listener.onOpen(item);
        reusable.setOnClickListener(open);
        holder.card.setOnClickListener(open);
        return reusable;
    }

    private TextView stat(int icon) {
        TextView view = label(10, mutedColor);
        view.setGravity(Gravity.CENTER);
        setStatIcon(view, icon, mutedColor, 14);
        return view;
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
        return round(cardColor, 8);
    }

    private static int contrast(int color) {
        int luminance = (Color.red(color) * 299 + Color.green(color) * 587
                + Color.blue(color) * 114) / 1000;
        return luminance >= 150 ? Color.BLACK : Color.WHITE;
    }

    private static int blend(int base, int overlay, float amount) {
        float keep = 1f - amount;
        return Color.rgb(
                Math.round(Color.red(base) * keep + Color.red(overlay) * amount),
                Math.round(Color.green(base) * keep + Color.green(overlay) * amount),
                Math.round(Color.blue(base) * keep + Color.blue(overlay) * amount));
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density * uiScale);
    }

    private static final class Holder {
        final LinearLayout card;
        final TextView badge;
        final TextView title;
        final TextView description;
        final TextView author;
        final TextView likes;
        final TextView comments;
        final ImageView cover;

        Holder(LinearLayout card, TextView badge, TextView title, TextView description,
               TextView author, TextView likes, TextView comments, ImageView cover) {
            this.card = card;
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
