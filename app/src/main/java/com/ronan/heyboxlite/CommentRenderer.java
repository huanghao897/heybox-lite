package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CommentRenderer {
    private static final int REPLY_PREVIEW_COUNT = 2;

    interface Host {
        void loadReplies(LinearLayout target, JSONObject root,
                         List<JSONObject> preview, int expected, int shown);

        void toggleLike(JSONObject comment, LikeControl control);

        void reply(JSONObject comment);

        void copy(String text);

        void openOriginalImage(ImageView source, String url);

        boolean isPostAuthor(JSONObject user, String author);
    }

    static final class LikeControl {
        final LinearLayout root;
        final ImageView icon;
        final TextView count;

        LikeControl(LinearLayout root, ImageView icon, TextView count) {
            this.root = root;
            this.icon = icon;
            this.count = count;
        }
    }

    private final Activity activity;
    private final SessionStore session;
    private final LocalCache localCache;
    private final ThemeTokens tokens;
    private final boolean roundLayout;
    private final float uiScale;
    private final float textScale;
    private final Host host;

    CommentRenderer(Activity activity, SessionStore session, LocalCache localCache,
                    ThemeTokens tokens, boolean roundLayout, Host host) {
        this.activity = activity;
        this.session = session;
        this.localCache = localCache;
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.uiScale = session.uiScale() / 100.0f;
        this.textScale = session.textScale() / 100.0f;
        this.host = host;
    }

    int addComments(LinearLayout page, JSONArray groups, boolean latest) {
        if (groups == null) return 0;
        List<JSONObject> threads = CommentOrder.sorted(groups, latest);
        int count = 0;
        for (JSONObject group : threads) {
            JSONArray comments = group.optJSONArray("comment");
            JSONObject root = comments == null ? group : comments.optJSONObject(0);
            if (root == null) continue;
            if (CommentData.isCyComment(group) && !root.has("is_cy")) {
                try {
                    root.put("_group_is_cy", 1);
                } catch (JSONException ignored) {
                }
            }
            LinearLayout card = vertical(this.tokens.background);
            card.setPadding(dp(4), dp(9), dp(4), dp(8));
            addComment(card, root, false, "");
            List<JSONObject> replies = repliesFrom(comments);
            Collections.sort(replies, (left, right) -> Long.compare(
                    CommentData.commentTime(left), CommentData.commentTime(right)));
            int expected = Math.max(root.optInt("child_num"),
                    group.optInt("child_num"));
            if (!replies.isEmpty() || expected > 0) {
                LinearLayout replySection = vertical(Color.TRANSPARENT);
                replySection.setPadding(dp(8), dp(3), dp(8), dp(3));
                Compat.setBackground(replySection, roundStroke(
                        this.tokens.faintAccent(), 12, this.tokens.hairline, 1));
                LinearLayout.LayoutParams sectionParams =
                        new LinearLayout.LayoutParams(-1, -2);
                sectionParams.topMargin = dp(5);
                sectionParams.leftMargin = dp(44);
                card.addView(replySection, sectionParams);
                LinearLayout replyList = vertical(Color.TRANSPARENT);
                replySection.addView(replyList,
                        new LinearLayout.LayoutParams(-1, -2));
                int initial = Math.min(REPLY_PREVIEW_COUNT, replies.size());
                renderReplies(replyList, root, replies, expected, initial,
                        expected <= replies.size());
            }
            addTop(page, CommentData.isPinnedComment(root)
                    ? pinnedCard(card) : card, count == 0 ? 0 : 2);
            count += 1 + replies.size();
        }
        return count;
    }

    void renderReplies(LinearLayout parent, JSONObject root,
                       List<JSONObject> replies, int expected,
                       int visibleCount, boolean allLoaded) {
        parent.removeAllViews();
        int total = Math.max(expected, replies.size());
        int shown = Math.min(Math.max(0, visibleCount), replies.size());
        String rootId = CommentData.commentId(root);
        for (int i = 0; i < shown; i++) {
            addComment(parent, replies.get(i), true, rootId);
        }
        boolean bufferedReplies = shown < replies.size();
        boolean remoteReplies = !allLoaded;
        if (bufferedReplies || remoteReplies) {
            String label = CommentReplyPaging.expansionLabel(
                    total, shown, remoteReplies);
            TextView more = replyControl(label, R.drawable.ic_expand);
            addReplyControl(parent, more);
            if (bufferedReplies) {
                more.setOnClickListener(view -> renderReplies(parent, root, replies,
                        total, shown + CommentReplyPaging.PAGE_SIZE, allLoaded));
            } else {
                more.setOnClickListener(view -> this.host.loadReplies(
                        parent, root, replies, total, shown));
            }
        }
        if (shown > REPLY_PREVIEW_COUNT) {
            TextView collapse = replyControl("收起回复", R.drawable.ic_collapse);
            addReplyControl(parent, collapse);
            collapse.setOnClickListener(view -> renderReplies(parent, root, replies,
                    total, Math.min(REPLY_PREVIEW_COUNT, replies.size()), allLoaded));
        }
    }

    LikeControl createLikeControl() {
        LinearLayout root = new LinearLayout(this.activity);
        root.setGravity(Gravity.CENTER);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(2), 0, dp(2), 0);
        ImageView icon = new ImageView(this.activity);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(icon, new LinearLayout.LayoutParams(dp(15), dp(15)));
        TextView count = text("0", 10.0f, this.tokens.muted);
        count.setGravity(Gravity.CENTER_VERTICAL);
        count.setSingleLine(true);
        LinearLayout.LayoutParams countParams =
                new LinearLayout.LayoutParams(-2, -2);
        countParams.leftMargin = dp(2);
        root.addView(count, countParams);
        return new LikeControl(root, icon, count);
    }

    void updateLikeView(LikeControl view, boolean liked, int likes) {
        int color = liked ? this.tokens.text : this.tokens.muted;
        view.icon.setImageResource(liked
                ? R.drawable.official_comment_like_filled
                : R.drawable.official_comment_like_line);
        view.icon.setColorFilter(color);
        view.count.setText(Format.commentLikeCount(Math.max(0, likes)));
        view.count.setTextColor(color);
    }

    private List<JSONObject> repliesFrom(JSONArray comments) {
        List<JSONObject> replies = new ArrayList<>();
        if (comments == null) return replies;
        for (int i = 1; i < comments.length(); i++) {
            JSONObject reply = comments.optJSONObject(i);
            if (reply != null) replies.add(reply);
        }
        return replies;
    }

    private TextView replyControl(String label, int iconRes) {
        TextView control = text(label, 11.0f, this.tokens.accent);
        control.setGravity(Gravity.CENTER);
        control.setPadding(dp(8), 0, dp(8), 0);
        Compat.setBackground(control, round(this.tokens.softAccent(), 13));
        Drawable icon = Compat.tintedDrawable(
                this.activity, iconRes, this.tokens.accent);
        if (icon != null) {
            icon.setBounds(0, 0, dp(12), dp(12));
            control.setCompoundDrawables(icon, null, null, null);
            control.setCompoundDrawablePadding(dp(4));
        }
        return control;
    }

    private void addReplyControl(LinearLayout parent, TextView control) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-2, dp(26));
        params.topMargin = dp(3);
        parent.addView(control, params);
    }

    private void addComment(LinearLayout parent, JSONObject comment,
                            boolean reply, String rootCommentId) {
        LinearLayout row = new LinearLayout(this.activity);
        row.setGravity(Gravity.TOP);
        int verticalPadding = reply ? 2 : (this.roundLayout ? 6 : 8);
        row.setPadding(0, dp(verticalPadding), 0, dp(verticalPadding));
        JSONObject user = comment.optJSONObject("user");
        String author = user == null
                ? "匿名用户" : user.optString("username", "匿名用户");
        if (!reply) addAvatar(row, user);

        LinearLayout block = vertical(Color.TRANSPARENT);
        LinearLayout.LayoutParams blockParams =
                new LinearLayout.LayoutParams(0, -2, 1.0f);
        blockParams.leftMargin = reply ? 0 : dp(this.roundLayout ? 8 : 10);
        row.addView(block, blockParams);
        String target = CommentData.replyTarget(comment, rootCommentId);
        long created = CommentData.commentTime(comment);
        String visibleComment = RichContent.commentText(
                comment.optString("text"), comment.optString("content"),
                comment.optString("html"), comment.optString("description"),
                comment.optString("desc_extra"), comment.optString("rich_text"),
                comment.optString("hb_rich_texts"));
        if (reply) {
            addCompactReply(block, comment, author, target, visibleComment, created);
        } else {
            block.addView(nameRow(author, user,
                    this.host.isPostAuthor(user, author)));
            String meta = commentMeta(comment, created);
            if (!meta.isEmpty()) addTop(block,
                    text(meta, 10.5f, this.tokens.muted), 1);
            addCommentBody(block, comment, visibleComment);
        }

        List<CommentData.CommentImage> images = CommentData.commentImages(comment);
        if (!this.session.noImage() && !images.isEmpty()) {
            addImages(block, images, reply);
        }
        if (!reply) {
            LikeControl likes = createLikeControl();
            updateLikeView(likes, CommentData.commentLiked(comment),
                    CommentData.commentLikes(comment));
            likes.root.setOnClickListener(view ->
                    this.host.toggleLike(comment, likes));
            LinearLayout.LayoutParams likeParams =
                    new LinearLayout.LayoutParams(-2, dp(26));
            likeParams.leftMargin = dp(3);
            row.addView(likes.root, likeParams);
        }
        View.OnLongClickListener copy = view -> {
            this.host.copy(RichInlineRenderer.plainText(visibleComment));
            return true;
        };
        block.setOnLongClickListener(copy);
        row.setOnLongClickListener(copy);
        attachReplyGesture(row, comment);
        parent.addView(row);
        if (reply) {
            View divider = new View(this.activity);
            divider.setBackgroundColor(ThemeTokens.blend(
                    this.tokens.background, this.tokens.muted,
                    this.session.darkMode() ? 0.10f : 0.05f));
            parent.addView(divider,
                    new LinearLayout.LayoutParams(-1, dp(1)));
        }
    }

    private void addAvatar(LinearLayout row, JSONObject user) {
        ImageView avatar = new ImageView(this.activity);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Compat.setBackground(avatar, round(this.session.darkMode()
                ? Color.rgb(50, 53, 56) : Color.rgb(226, 229, 232), 20));
        Compat.clipToOutline(avatar);
        int size = dp(this.roundLayout ? 30 : 34);
        row.addView(avatar, new LinearLayout.LayoutParams(size, size));
        String url = user == null ? "" : user.optString("avatar");
        if (!this.session.noImage() && !url.isEmpty()) {
            ImageLoader.intoPlain(avatar, url, 96);
        }
    }

    private void addCommentBody(LinearLayout block, JSONObject comment,
                                String visibleComment) {
        String display = CommentData.isCyComment(comment)
                ? "Cy " + visibleComment : visibleComment;
        TextView value = text(display,
                this.roundLayout ? 12.5f : 13.0f, this.tokens.text);
        value.setLineSpacing(dp(1),
                this.session.bodyLineSpacing() / 100.0f);
        Compat.setLetterSpacing(value,
                this.session.bodyLetterSpacing() / 200.0f);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        RichInlineRenderer.set(value, display, this.session.darkMode(),
                this.tokens.link, span -> {
            if (CommentData.isCyComment(comment)) {
                applyInlineBadge(span, 0, 2,
                        R.drawable.official_cy_badge, 15);
            }
        });
        addTop(block, value, 5);
    }

    private void addImages(LinearLayout parent,
                           List<CommentData.CommentImage> images, boolean reply) {
        LinearLayout gallery = vertical(Color.TRANSPARENT);
        int columns = this.roundLayout ? 2 : 3;
        int size = this.roundLayout ? (reply ? 48 : 54) : (reply ? 68 : 82);
        for (int start = 0; start < images.size(); start += columns) {
            LinearLayout row = new LinearLayout(this.activity);
            row.setGravity(Gravity.LEFT);
            int end = Math.min(start + columns, images.size());
            for (int i = start; i < end; i++) {
                ImageView image = commentImage(images.get(i), size);
                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(dp(size), dp(size));
                if (i > start) params.leftMargin = dp(5);
                row.addView(image, params);
            }
            LinearLayout.LayoutParams rowParams =
                    new LinearLayout.LayoutParams(-1, dp(size));
            if (start > 0) rowParams.topMargin = dp(5);
            gallery.addView(row, rowParams);
        }
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(6);
        parent.addView(gallery, params);
    }

    private ImageView commentImage(CommentData.CommentImage source, int sizeDp) {
        ImageView image = new GifImageView(this.activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Compat.setBackground(image, round(this.session.darkMode()
                ? Color.rgb(28, 30, 32) : Color.rgb(235, 237, 240), 6));
        Compat.clipToOutline(image);
        image.setOnClickListener(view ->
                this.host.openOriginalImage(image, source.originalUrl));
        ImageLoader.intoMeasuredRevealStable(image, source.previewUrl,
                Math.max(96, dp(sizeDp)), (success, bitmap) -> {
                    if (success && this.session.playGif()
                            && (source.animated || GifSupport.isGifUrl(source.originalUrl))) {
                        ImageLoader.intoGif(image, source.originalUrl, animated ->
                                this.localCache.log("comment gif "
                                        + (animated ? "started" : "failed")));
                    } else if (!success && image.getDrawable() == null) {
                        image.setImageDrawable(Compat.tintedDrawable(this.activity,
                                R.drawable.il_image, this.tokens.muted));
                        image.setPadding(dp(18), dp(18), dp(18), dp(18));
                    }
                });
        return image;
    }

    private void addCompactReply(LinearLayout block, JSONObject comment,
                                 String author, String target,
                                 String visibleComment, long created) {
        String name = author.isEmpty() ? "匿名用户" : author;
        String authorBadge = this.host.isPostAuthor(
                comment.optJSONObject("user"), name) ? " 作者 " : "";
        String cyBadge = CommentData.isCyComment(comment) ? " Cy " : "";
        boolean hasTarget = !target.isEmpty();
        String replyLabel = hasTarget ? " 回复 " : "";
        String replyName = hasTarget ? target : "";
        String meta = commentMeta(comment, created);
        String metaSegment = meta.isEmpty() ? "" : "  " + meta;
        String full = name + authorBadge + replyLabel + replyName + "："
                + cyBadge + visibleComment + metaSegment;

        int nameEnd = name.length();
        int authorBadgeStart = nameEnd;
        int replyNameStart = nameEnd + authorBadge.length() + replyLabel.length();
        int replyNameEnd = replyNameStart + replyName.length();
        int cyBadgeStart = replyNameEnd + 1;
        int metaStart = full.length() - meta.length();
        int metaEnd = full.length();
        TextView value = text(full, 12.5f, this.tokens.text);
        value.setLineSpacing(dp(1),
                this.session.bodyLineSpacing() / 100.0f);
        value.setPadding(0, dp(2), 0, dp(3));
        RichInlineRenderer.set(value, full, this.session.darkMode(),
                this.tokens.link, span -> {
            span.setSpan(new ForegroundColorSpan(this.tokens.secondary),
                    0, nameEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new StyleSpan(Typeface.BOLD),
                    0, nameEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!authorBadge.isEmpty()) {
                applyInlineBadge(span, authorBadgeStart,
                        authorBadgeStart + authorBadge.length(),
                        R.drawable.official_author_badge, 13);
            }
            if (hasTarget) {
                span.setSpan(new ForegroundColorSpan(this.tokens.secondary),
                        replyNameStart, replyNameEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (!cyBadge.isEmpty()) {
                applyInlineBadge(span, cyBadgeStart,
                        cyBadgeStart + cyBadge.length(),
                        R.drawable.official_cy_badge, 15);
            }
            if (!meta.isEmpty()) {
                span.setSpan(new ForegroundColorSpan(this.tokens.muted),
                        metaStart, metaEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        });
        block.addView(value, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout nameRow(String author, JSONObject user,
                                 boolean postAuthor) {
        LinearLayout row = new LinearLayout(this.activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(nameText(author), new LinearLayout.LayoutParams(-2, -2));
        if (postAuthor) addAuthorBadge(row);
        addLevelBadge(row, user);
        return row;
    }

    private TextView nameText(String author) {
        TextView name = text(author.isEmpty() ? "匿名用户" : author,
                12.5f, this.tokens.text);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMaxWidth(dp(150));
        return name;
    }

    private void addAuthorBadge(LinearLayout row) {
        ImageView badge = new ImageView(this.activity);
        badge.setImageResource(R.drawable.official_author_badge);
        badge.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(dp(28), dp(15));
        params.leftMargin = dp(2);
        row.addView(badge, params);
    }

    private void addLevelBadge(LinearLayout row, JSONObject user) {
        int level = CommentData.userLevel(user);
        if (level <= 0) return;
        int color = CommentData.levelBadgeColor(level);
        TextView badge = text("Lv." + level, 8.0f, color);
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setPadding(dp(4), 0, dp(4), 0);
        Compat.setBackground(badge, round(ThemeTokens.blend(
                this.tokens.panel, color,
                this.session.darkMode() ? 0.30f : 0.14f), 4));
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-2, dp(15));
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        row.addView(badge, params);
    }

    private View pinnedCard(LinearLayout card) {
        card.setPadding(card.getPaddingLeft(), card.getPaddingTop() + dp(9),
                card.getPaddingRight(), card.getPaddingBottom());
        FrameLayout frame = new FrameLayout(this.activity);
        frame.addView(card, new FrameLayout.LayoutParams(-1, -2));
        ImageView corner = new ImageView(this.activity);
        corner.setImageResource(R.drawable.official_comment_pinned_corner);
        corner.setScaleType(ImageView.ScaleType.FIT_XY);
        FrameLayout.LayoutParams cornerParams =
                new FrameLayout.LayoutParams(dp(34), dp(34),
                        Gravity.TOP | Gravity.LEFT);
        frame.addView(corner, cornerParams);
        TextView label = text("置顶", 7.5f, Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setRotation(-45.0f);
        FrameLayout.LayoutParams labelParams =
                new FrameLayout.LayoutParams(dp(30), dp(14));
        labelParams.leftMargin = -dp(4);
        labelParams.topMargin = dp(2);
        frame.addView(label, labelParams);
        return frame;
    }

    private void applyInlineBadge(Spannable span, int start, int end,
                                  int drawableRes, int heightDp) {
        if (start < 0 || end > span.length()) return;
        while (start < end && span.charAt(start) == ' ') start++;
        while (end > start && span.charAt(end - 1) == ' ') end--;
        if (end <= start) return;
        Drawable drawable = this.activity.getResources()
                .getDrawable(drawableRes).mutate();
        int height = dp(heightDp);
        int width = drawable.getIntrinsicHeight() <= 0 ? height
                : Math.max(1, Math.round(height * drawable.getIntrinsicWidth()
                / (float) drawable.getIntrinsicHeight()));
        drawable.setBounds(0, 0, width, height);
        span.setSpan(new CenteredImageSpan(drawable), start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private String commentMeta(JSONObject comment, long created) {
        String time = created > 0 ? Format.relativeTime(created) : "";
        String location = CommentData.commentLocation(comment);
        if (time.isEmpty()) return location;
        return location.isEmpty() ? time : time + " · " + location;
    }

    private void attachReplyGesture(View target, JSONObject comment) {
        target.setOnClickListener(new View.OnClickListener() {
            private long lastTapAt;

            @Override
            public void onClick(View view) {
                if (!session.doubleTapCommentReply()) return;
                long now = System.currentTimeMillis();
                if (now - this.lastTapAt <= 320L) {
                    this.lastTapAt = 0L;
                    host.reply(comment);
                } else {
                    this.lastTapAt = now;
                }
            }
        });
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(
                this.activity, value, size, color, this.textScale);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private LinearLayout vertical(int color) {
        LinearLayout layout = new LinearLayout(this.activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    private void addTop(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(view, params);
    }

    private Drawable round(int color, int radius) {
        return UiComponents.round(this.activity, color, radius, this.uiScale);
    }

    private Drawable roundStroke(int color, int radius,
                                 int strokeColor, int strokeWidth) {
        android.graphics.drawable.GradientDrawable drawable =
                UiComponents.round(this.activity, color, radius, this.uiScale);
        drawable.setStroke(Math.max(1, dp(strokeWidth)), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * this.activity.getResources()
                .getDisplayMetrics().density * this.uiScale);
    }
}
