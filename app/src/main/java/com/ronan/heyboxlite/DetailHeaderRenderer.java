package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;

final class DetailHeaderRenderer {
    interface Host {
        void openUser(String userId, String name, String avatar);
    }

    private final Activity activity;
    private final SessionStore session;
    private final ThemeTokens tokens;
    private final PostActionController postActions;
    private final boolean roundLayout;
    private final Host host;

    DetailHeaderRenderer(Activity activity, SessionStore session, ThemeTokens tokens,
                         PostActionController postActions, boolean roundLayout, Host host) {
        this.activity = activity;
        this.session = session;
        this.tokens = tokens;
        this.postActions = postActions;
        this.roundLayout = roundLayout;
        this.host = host;
    }

    void addAuthor(LinearLayout article, JSONObject link, JSONObject user,
                   String fallbackAuthor) {
        LinearLayout row = new LinearLayout(this.activity);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView avatar = new ImageView(this.activity);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int avatarBackground = this.session.darkMode()
                ? Color.rgb(50, 53, 56) : Color.rgb(226, 229, 232);
        Compat.setBackground(avatar, round(avatarBackground, 18));
        Compat.clipToOutline(avatar);
        int avatarSize = dp(this.roundLayout ? 32 : 36);
        row.addView(avatar, new LinearLayout.LayoutParams(avatarSize, avatarSize));
        String avatarUrl = user == null ? ""
                : user.optString("avatar", user.optString("avartar"));
        if (!this.session.noImage() && !avatarUrl.isEmpty()) {
            ImageLoader.intoPlain(avatar, avatarUrl, 96);
        }

        LinearLayout copy = vertical();
        LinearLayout nameRow = new LinearLayout(this.activity);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        String nameValue = user == null ? fallbackAuthor : Json.first(
                user.optString("username"), user.optString("nickname"),
                user.optString("name"), fallbackAuthor);
        TextView name = text(nameValue.isEmpty() ? "小黑盒用户" : nameValue,
                13.0f, this.tokens.text);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        if (this.roundLayout) {
            nameRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1.0f));
        } else {
            name.setMaxWidth(dp(168));
            nameRow.addView(name, new LinearLayout.LayoutParams(-2, -2));
        }
        addLevelBadge(nameRow, user);
        copy.addView(nameRow);

        long createdAt = link == null ? 0L : link.optLong("create_at",
                link.optLong("create_time", link.optLong("createtime")));
        String published = createdAt > 0L ? Format.relativeTime(createdAt) : "";
        String signature = user == null ? ""
                : Json.first(user.optString("signature"), user.optString("desc"));
        String metadata = published.isEmpty() ? signature
                : signature.isEmpty() ? published : published + " · " + signature;
        if (!metadata.isEmpty()) {
            TextView description = text(metadata, 10.0f, this.tokens.muted);
            description.setSingleLine(true);
            description.setEllipsize(TextUtils.TruncateAt.END);
            addTop(copy, description, 1);
        }
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        copyParams.leftMargin = dp(this.roundLayout ? 8 : 9);
        row.addView(copy, copyParams);

        int followBackground = this.session.darkMode()
                ? ThemeTokens.blend(this.tokens.panel, this.tokens.text, 0.12f)
                : ThemeTokens.blend(this.tokens.panel, this.tokens.text, 0.06f);
        TextView follow = text(this.roundLayout ? "+" : "+ 关注",
                this.roundLayout ? 17.0f : 11.0f, this.tokens.text);
        follow.setGravity(Gravity.CENTER);
        follow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        Compat.setBackground(follow, round(followBackground, 5));
        String targetUserId = this.postActions.authorUserId(link, user);
        View.OnClickListener openUser = view ->
                this.host.openUser(targetUserId, nameValue, avatarUrl);
        avatar.setOnClickListener(openUser);
        copy.setOnClickListener(openUser);
        this.postActions.updateFollowView(follow,
                this.postActions.isFollowing(link, user));
        row.addView(follow, new LinearLayout.LayoutParams(
                dp(this.roundLayout ? 38 : 62), dp(30)));
        follow.setOnClickListener(view ->
                this.postActions.toggleFollow(follow, link, user, targetUserId));
        addTop(article, row, article.getChildCount() == 0 ? 0 : 14);
    }

    void addTopics(LinearLayout article, JSONObject link, FeedItem fallback) {
        FeedMetadata.Section metadata = FeedMetadata.section(link);
        String section = metadata.name.isEmpty() ? fallback.topicName : metadata.name;
        String icon = metadata.icon.isEmpty() && section.equals(fallback.topicName)
                ? fallback.topicIcon : metadata.icon;
        if (!section.isEmpty()) {
            LinearLayout sectionRow = new LinearLayout(this.activity);
            sectionRow.setGravity(Gravity.CENTER_VERTICAL);
            sectionRow.setPadding(dp(8), dp(6), dp(8), dp(6));
            Compat.setBackground(sectionRow, round(this.tokens.panel, 6));
            ImageView image = new ImageView(this.activity);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Compat.setBackground(image, round(this.tokens.panelElevated, 4));
            Compat.clipToOutline(image);
            sectionRow.addView(image, new LinearLayout.LayoutParams(dp(24), dp(24)));
            if (!this.session.noImage() && !icon.isEmpty()) {
                ImageLoader.intoPlain(image, icon, dp(24));
            } else {
                image.setImageDrawable(Compat.tintedDrawable(
                        this.activity, R.drawable.il_globe, this.tokens.muted));
            }
            TextView name = text(section, 11.5f, this.tokens.text);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
            nameParams.leftMargin = dp(7);
            sectionRow.addView(name, nameParams);
            addTop(article, sectionRow, 9);
        }

        List<String> tags = FeedMetadata.tags(link);
        tags.remove(section);
        if (!tags.isEmpty()) {
            StringBuilder copy = new StringBuilder();
            for (String tag : tags) {
                if (copy.length() > 0) copy.append("  ");
                copy.append(tag.startsWith("#") ? tag : "#" + tag);
            }
            TextView tagRow = text(copy.toString(), 10.5f, this.tokens.muted);
            tagRow.setLineSpacing(dp(3), 1f);
            addTop(article, tagRow, 7);
        }
    }

    String firstTopicName(JSONObject link, String fallbackName) {
        return Json.first(FeedItem.topicName(link), fallbackName);
    }

    private void addLevelBadge(LinearLayout row, JSONObject user) {
        int level = CommentData.userLevel(user);
        if (level <= 0) return;
        int color = CommentData.levelBadgeColor(level);
        TextView badge = text("Lv." + level, 8.0f, color);
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        int fill = ThemeTokens.blend(this.tokens.panel, color,
                this.session.darkMode() ? 0.30f : 0.14f);
        Compat.setBackground(badge, round(fill, 4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(32), dp(15));
        params.leftMargin = dp(5);
        row.addView(badge, params);
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

    private void addTop(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(view, params);
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        return UiComponents.round(
                this.activity, color, radius, this.session.uiScale() / 100.0f);
    }

    private int dp(int value) {
        return UiComponents.dp(this.activity, value, this.session.uiScale() / 100.0f);
    }
}
