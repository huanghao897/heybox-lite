package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class DetailActionBar {
    interface Host {
        JSONObject currentDetailBody();

        void showToast(String message);
    }

    private final Activity activity;
    private final SessionStore session;
    private final LocalCache localCache;
    private final ThemeTokens tokens;
    private final PostActionController postActions;
    private final CommentController comments;
    private final CommentRenderer commentRenderer;
    private final DetailContentRenderer contentRenderer;
    private final boolean roundLayout;
    private final Host host;

    DetailActionBar(Activity activity, SessionStore session, LocalCache localCache,
                    ThemeTokens tokens, PostActionController postActions,
                    CommentController comments, CommentRenderer commentRenderer,
                    DetailContentRenderer contentRenderer, boolean roundLayout, Host host) {
        this.activity = activity;
        this.session = session;
        this.localCache = localCache;
        this.tokens = tokens;
        this.postActions = postActions;
        this.comments = comments;
        this.commentRenderer = commentRenderer;
        this.contentRenderer = contentRenderer;
        this.roundLayout = roundLayout;
        this.host = host;
    }

    void add(LinearLayout article, FeedItem item, JSONObject link) {
        LinearLayout row = new LinearLayout(this.activity);
        row.setGravity(Gravity.CENTER_VERTICAL);

        CommentRenderer.LikeControl like = countAction(
                R.drawable.official_comment_like_line, Math.max(0, item.likes));
        PostActionController.LikeState state = this.postActions.linkLikeState(link, item);
        this.postActions.syncFeedLike(item, state.liked, state.likes);
        item.liked = state.liked;
        item.likes = state.likes;
        this.postActions.updateLinkLikeView(like, state.liked, state.likes);
        like.root.setOnClickListener(view -> this.postActions.toggleLinkLike(item, like));
        addItem(row, like.root);

        ImageView favorite = iconAction();
        boolean favored = this.postActions.linkFavored(link);
        this.postActions.updateFavoriteView(favorite, favored);
        favorite.setOnClickListener(view ->
                this.postActions.toggleFavorite(item, favorite));
        addItem(row, favorite);

        TextView watchLater = actionLabel("", R.drawable.ic_history);
        updateWatchLater(watchLater, this.localCache.isWatchLater(item.id), false);
        watchLater.setOnClickListener(view -> toggleWatchLater(item, watchLater));
        addItem(row, watchLater);

        CommentRenderer.LikeControl comment = countAction(
                R.drawable.official_detail_comment, Math.max(0, item.comments));
        comment.root.setContentDescription("评论");
        comment.root.setOnClickListener(view -> this.comments.showDialog(null));
        addItem(row, comment.root);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        article.addView(row, params);
    }

    void refreshOffline(FeedItem item, JSONObject body) {
        cacheOffline(item, body, null);
    }

    private void addItem(LinearLayout row, View item) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(this.roundLayout ? 36 : 40), 1.0f);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        row.addView(item, params);
    }

    private CommentRenderer.LikeControl countAction(int iconResource, int count) {
        CommentRenderer.LikeControl control = this.commentRenderer.createLikeControl();
        control.icon.setImageResource(iconResource);
        control.icon.setColorFilter(this.tokens.muted);
        control.count.setText(Format.commentLikeCount(count));
        control.count.setTextColor(this.tokens.muted);
        Compat.setBackground(control.root, outlinedSurface(this.tokens.panel));
        return control;
    }

    private ImageView iconAction() {
        ImageView view = new ImageView(this.activity);
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setPadding(dp(11), dp(11), dp(11), dp(11));
        Compat.setBackground(view, outlinedSurface(this.tokens.panel));
        return view;
    }

    private TextView actionLabel(String label, int icon) {
        TextView view = text(label, 10.5f, this.tokens.muted);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(4), 0, dp(4), 0);
        updateAction(view, false, icon);
        return view;
    }

    private void toggleWatchLater(FeedItem item, TextView button) {
        if (item == null || item.id.isEmpty()) return;
        if (this.localCache.isWatchLater(item.id)) {
            this.localCache.removeWatchLater(item.id);
            updateWatchLater(button, false, false);
            this.host.showToast("已从稍后看移除");
            return;
        }
        this.localCache.addWatchLater(item);
        updateWatchLater(button, true, true);
        JSONObject body = this.host.currentDetailBody();
        if (body == null) body = this.localCache.detail(item.id);
        if (body == null) {
            List<String> images = imageUrls(item, null);
            this.localCache.updateWatchLater(item, images);
            ImageLoader.prefetchOffline(this.activity, images, 260, bytes ->
                    finishWatchLater(item, button));
        } else {
            cacheOffline(item, body, () -> finishWatchLater(item, button));
        }
        this.host.showToast("已加入稍后看");
    }

    private void finishWatchLater(FeedItem item, TextView button) {
        if (!this.activity.isFinishing() && this.localCache.isWatchLater(item.id)) {
            updateWatchLater(button, true, false);
        }
    }

    private void updateWatchLater(TextView view, boolean saved, boolean loading) {
        view.setText(loading ? "缓存中" : saved ? "已缓存" : "缓存");
        view.setContentDescription(saved ? "移出稍后看" : "稍后看");
        updateAction(view, saved || loading, R.drawable.ic_history);
    }

    private void updateAction(TextView view, boolean active, int iconResource) {
        int color = active ? this.tokens.accent : this.tokens.muted;
        view.setTextColor(color);
        int background = active ? this.tokens.softAccent() : this.tokens.panel;
        Compat.setBackground(view, outlinedSurface(background));
        Drawable icon = Compat.tintedDrawable(this.activity, iconResource, color);
        if (icon != null) {
            icon.setBounds(0, 0, dp(15), dp(15));
            view.setCompoundDrawables(icon, null, null, null);
            view.setCompoundDrawablePadding(dp(4));
        }
    }

    private void cacheOffline(FeedItem item, JSONObject body, Runnable complete) {
        List<String> images = imageUrls(item, body);
        this.localCache.updateWatchLater(item, images);
        if (!TextUtils.isEmpty(item.image)) {
            ImageLoader.prefetchOffline(this.activity,
                    Collections.singletonList(item.image), 260, null);
        }
        ImageLoader.prefetchOffline(this.activity, images,
                this.contentRenderer.imageTargetPx(), bytes -> {
                    if (complete != null) complete.run();
                });
    }

    private List<String> imageUrls(FeedItem item, JSONObject body) {
        Set<String> values = new HashSet<>();
        if (!TextUtils.isEmpty(item.image)) values.add(item.image);
        Collections.addAll(values, item.images);
        JSONObject result = body == null ? null : body.optJSONObject("result");
        JSONObject link = result == null ? null : result.optJSONObject("link");
        JSONArray fallbackImages = link == null ? null : link.optJSONArray("imgs");
        for (RichContent.Block block : RichContent.parse(link, fallbackImages)) {
            if (block.image && !TextUtils.isEmpty(block.value)) values.add(block.value);
            if (values.size() >= 48) break;
        }
        return new ArrayList<>(values);
    }

    private android.graphics.drawable.GradientDrawable outlinedSurface(int color) {
        android.graphics.drawable.GradientDrawable background = UiComponents.round(
                this.activity, color, 8, this.session.uiScale() / 100.0f);
        background.setStroke(dp(1), this.tokens.hairline);
        return background;
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(
                this.activity, value, size, color, this.session.textScale() / 100.0f);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private int dp(int value) {
        return UiComponents.dp(this.activity, value, this.session.uiScale() / 100.0f);
    }
}
