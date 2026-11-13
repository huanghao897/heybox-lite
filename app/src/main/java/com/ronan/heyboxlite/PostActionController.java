package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PostActionController {
    interface Host {
        String currentLinkId();

        String currentLinkHsrc();

        FeedItem currentDetailItem();

        List<FeedItem> feedItems();

        List<FeedItem> searchItems();

        void feedChanged();

        void showToast(String message);
    }

    static final class LikeState {
        final boolean liked;
        final int likes;

        LikeState(boolean liked, int likes) {
            this.liked = liked;
            this.likes = Math.max(0, likes);
        }
    }

    private final Activity activity;
    private final SessionStore session;
    private final WriteActionClient writeActions;
    private final LocalCache localCache;
    private final ThemeTokens tokens;
    private final boolean roundLayout;
    private final Host host;
    private final PostInteractionState interactions;

    PostActionController(Activity activity, SessionStore session,
                         WriteActionClient writeActions, LocalCache localCache,
                         ThemeTokens tokens, boolean roundLayout, Host host) {
        this.activity = activity;
        this.session = session;
        this.writeActions = writeActions;
        this.localCache = localCache;
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.host = host;
        this.interactions = new PostInteractionState(session);
    }

    PostInteractionState interactions() { return interactions; }

    void invalidateSession() {
        interactions.invalidateSession();
    }

    LikeState linkLikeState(JSONObject link, FeedItem fallback) {
        LikeState override = linkLikeOverride(link, fallback);
        return override != null ? override
                : new LikeState(linkLiked(link, fallback), linkLikes(link, fallback));
    }

    boolean linkFavored(JSONObject link) {
        Boolean override = interactions.favorite(linkId(link, this.host.currentDetailItem()));
        return override != null ? override : Json.truthy(link, "is_favour", "is_favor", "is_fav",
                "favored", "has_favour", "has_favor");
    }

    String hsrc(JSONObject link) {
        if (link == null) return "";
        String value = Json.first(link.optString("h_src"), link.optString("hsrc"));
        if (!value.isEmpty()) return value;
        String shareUrl = link.optString("share_url");
        if (shareUrl.isEmpty()) return "";
        try {
            String source = Uri.parse(shareUrl).getQueryParameter("h_src");
            return source == null ? "" : source;
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    boolean requireLogin(String actionName) {
        if (this.session.isLoggedIn()) return true;
        this.host.showToast("游客模式可浏览，登录后可" + actionName);
        return false;
    }

    boolean allowWriteAction(String actionName) {
        String remoteMessage = RemoteConfig.blockedMessage(actionName);
        if (!remoteMessage.isEmpty()) {
            this.host.showToast(remoteMessage);
            return false;
        }
        String message = this.writeActions.begin(actionName);
        if (message == null) return true;
        this.host.showToast(message);
        return false;
    }

    String writeErrorMessage(String actionName, String message) {
        return this.writeActions.errorMessage(actionName, message);
    }

    void toggleLinkLike(FeedItem item, CommentRenderer.LikeControl view) {
        toggleLike(item, view);
    }

    void toggleFeedFavorite(FeedItem item) {
        toggleFavorite(item, null);
    }

    void toggleFavorite(FeedItem item, ImageView view) {
        if (!canWrite("收藏", item)) return;
        if (view != null && interactions.favorite(item.id) == null) {
            item.favorited = Boolean.TRUE.equals(view.getTag());
        }
        interactions.apply(item);
        boolean next = !item.favorited;
        PostInteractionState.Operation<Boolean> operation = interactions.beginFavorite(item, next);
        syncInteractions(item);
        if (view != null) {
            updateFavoriteView(view, item.favorited);
            view.setEnabled(false);
        }
        this.writeActions.favorite(item.id, hsrcFor(item), next, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) { finish(true, ""); }
            @Override public void onError(String message) { finish(false, message); }
            private void finish(boolean success, String message) {
                boolean latest = interactions.isLatest(operation);
                if (!interactions.complete(operation, success)) return;
                syncInteractions(item);
                if (!latest) return;
                if (view != null) {
                    view.setEnabled(true);
                    updateFavoriteView(view, item.favorited);
                }
                host.showToast(success ? (next ? "已收藏" : "已取消收藏")
                        : "收藏操作失败" + writeErrorMessage("收藏", message));
            }
        });
    }

    void toggleFeedCache(FeedItem item) {
        if (item == null || item.id.isEmpty()) return;
        if (localCache.isWatchLater(item.id)) {
            localCache.removeWatchLater(item.id);
            host.showToast("已从稍后看移除");
            return;
        }
        localCache.addWatchLater(item);
        ArrayList<String> images = new ArrayList<>();
        if (item.image != null && !item.image.isEmpty()) images.add(item.image);
        Collections.addAll(images, item.images);
        localCache.updateWatchLater(item, images);
        if (!images.isEmpty()) ImageLoader.prefetchOffline(this.activity, images, 260, null);
        host.showToast("已加入稍后看");
    }

    void toggleFeedLike(FeedItem item) {
        toggleLike(item, null);
    }

    private void toggleLike(FeedItem item, CommentRenderer.LikeControl view) {
        if (!canWrite("点赞", item)) return;
        interactions.apply(item);
        boolean nextLiked = !item.liked;
        PostInteractionState.Operation<PostInteractionState.Like> operation =
                interactions.beginLike(item, nextLiked);
        syncInteractions(item);
        if (view != null) updateLinkLikeView(view, item.liked, item.likes);
        this.writeActions.like(item.id, hsrcFor(item), nextLiked, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) { finish(true, ""); }
            @Override public void onError(String message) { finish(false, message); }
            private void finish(boolean success, String message) {
                boolean latest = interactions.isLatest(operation);
                if (!interactions.complete(operation, success)) return;
                syncInteractions(item);
                if (view != null && latest) updateLinkLikeView(view, item.liked, item.likes);
                if (success) localCache.log("link like ok " + item.id);
                else if (latest) host.showToast("点赞失败" + writeErrorMessage("点赞", message));
            }
        });
    }

    void toggleFeedFollow(FeedItem item, boolean following,
                          FeedAdapter.FollowCallback callback) {
        if (!requireLogin("关注") || !allowWriteAction("关注")) {
            callback.onComplete(false);
            return;
        }
        if (item == null || item.authorId.isEmpty()) {
            this.host.showToast("没有获取到用户 ID");
            callback.onComplete(false);
            return;
        }
        if (item.authorId.equals(this.session.userId())) {
            this.host.showToast("不能关注自己");
            callback.onComplete(false);
            return;
        }
        // Native adapters may already have painted their optimistic state.
        boolean before = item.followPending ? !following : item.following;
        follow(item, item.authorId, before, following, callback);
    }

    private void follow(FeedItem item, String authorId, boolean before, boolean following,
                        FeedAdapter.FollowCallback callback) {
        PostInteractionState.Operation<Boolean> operation = interactions.beginFollow(authorId, before, following);
        syncInteractions(item);
        this.writeActions.follow(authorId, hsrcFor(item), following,
                new ApiClient.Callback() {
                    @Override public void onSuccess(JSONObject body) { finish(true, ""); }
                    @Override public void onError(String message) { finish(false, message); }
                    private void finish(boolean success, String message) {
                        boolean latest = interactions.isLatest(operation);
                        if (!interactions.complete(operation, success)) return;
                        if (latest) {
                            callback.onComplete(success);
                            host.showToast(success ? (following ? "已关注" : "已取消关注")
                                    : "关注操作失败" + writeErrorMessage("关注", message));
                        }
                        syncInteractions(item);
                    }
                });
    }

    void toggleFollow(TextView view, JSONObject link, JSONObject user,
                      String targetUserId) {
        if (!requireLogin("关注") || !allowWriteAction("关注")) return;
        if (targetUserId == null || targetUserId.isEmpty()) {
            this.host.showToast("没有获取到用户 ID");
            return;
        }
        if (targetUserId.equals(this.session.userId())) {
            this.host.showToast("不能关注自己");
            return;
        }
        Boolean override = interactions.following(targetUserId);
        boolean before = override != null ? override : Boolean.TRUE.equals(view.getTag());
        int beforeStatus = followStatus(link, user);
        boolean next = !before;
        updateFollowView(view, next);
        view.setClickable(false);
        view.setAlpha(0.92f);
        follow(this.host.currentDetailItem(), targetUserId, before, next, success -> {
            if (success) applyFollowState(link, user, nextFollowStatus(beforeStatus, next));
            finishFollow(view, Boolean.TRUE.equals(interactions.following(targetUserId)));
        });
    }

    void updateLinkLikeView(CommentRenderer.LikeControl view,
                            boolean liked, int likes) {
        int color = liked ? this.tokens.accent : this.tokens.muted;
        view.icon.setImageResource(liked ? R.drawable.official_comment_like_filled
                : R.drawable.official_comment_like_line);
        view.icon.setColorFilter(color);
        view.count.setText(Format.commentLikeCount(Math.max(0, likes)));
        view.count.setTextColor(color);
        view.root.setContentDescription(liked ? "取消点赞" : "点赞");
        Compat.setBackground(view.root, outlinedAction(
                liked ? this.tokens.softAccent() : this.tokens.panel, 8));
    }

    void updateFavoriteView(ImageView view, boolean favored) {
        view.setTag(favored);
        view.setContentDescription(favored ? "取消收藏" : "收藏");
        view.setImageResource(favored ? R.drawable.official_favorite_filled
                : R.drawable.official_favorite_line);
        view.setColorFilter(favored ? this.tokens.accent : this.tokens.muted);
        Compat.setBackground(view, outlinedAction(
                favored ? this.tokens.softAccent() : this.tokens.panel, 8));
    }

    void syncFeedLike(FeedItem item, boolean liked, int likes) {
        if (item == null) return;
        item.liked = liked;
        item.likes = Math.max(0, likes);
        interactions.apply(item);
        for (FeedItem target : this.host.feedItems()) {
            if (!item.id.equals(target.id)) continue;
            target.liked = item.liked;
            target.likes = item.likes;
        }
        syncInteractions(item);
    }

    void updateFollowView(TextView view, boolean following) {
        view.setTag(following);
        view.setText(following ? "已关注" : "+ 关注");
        view.setContentDescription(following ? "取消关注" : "关注");
        view.setTextColor(this.tokens.accent);
        GradientDrawable drawable = UiComponents.round(this.activity,
                following ? this.tokens.softAccent() : Color.TRANSPARENT,
                15, uiScale());
        drawable.setStroke(Math.max(1, dp(1)), this.tokens.accent);
        Compat.setBackground(view, drawable);
    }

    boolean isFollowing(JSONObject link, JSONObject user) {
        Boolean override = interactions.following(AuthorIdentity.authorId(link, user));
        if (override != null) return override;
        int status = followStatus(link, user);
        return status >= 0 ? status == 1 || status == 3
                : Json.truthy(link, "is_follow", "is_following", "followed")
                || Json.truthy(user, "is_follow", "is_following", "followed");
    }

    String authorUserId(JSONObject link, JSONObject user) {
        return AuthorIdentity.authorId(link, user);
    }

    String hsrcFor(FeedItem item) {
        String value = item == null ? "" : item.hsrc;
        if (value.isEmpty() && item != null
                && item.id.equals(this.host.currentLinkId())) {
            value = this.host.currentLinkHsrc();
        }
        if (value.isEmpty() && item == null) value = this.host.currentLinkHsrc();
        return value;
    }

    private boolean canWrite(String actionName, FeedItem item) {
        return requireLogin(actionName) && allowWriteAction(actionName)
                && item != null && !item.id.isEmpty();
    }

    private boolean linkLiked(JSONObject link, FeedItem fallback) {
        LikeState override = linkLikeOverride(link, fallback);
        if (override != null) return override.liked;
        if (link == null) return fallback != null && fallback.liked;
        return Json.truthy(link, "is_award_link", "is_award", "liked", "is_liked",
                "has_award", "award_state", "like_state");
    }

    private int linkLikes(JSONObject link, FeedItem fallback) {
        LikeState override = linkLikeOverride(link, fallback);
        if (override != null) return override.likes;
        int fallbackLikes = fallback == null ? 0 : fallback.likes;
        return link == null ? fallbackLikes : Json.firstInt(link, fallbackLikes,
                "link_award_num", "like_num", "award_num", "award_count",
                "like_count", "liked_num", "total_award_num", "up_num", "up");
    }

    private LikeState linkLikeOverride(JSONObject link, FeedItem fallback) {
        PostInteractionState.Like value = interactions.like(linkId(link, fallback));
        return value == null ? null : new LikeState(value.liked, value.likes);
    }

    private String linkId(JSONObject link, FeedItem fallback) {
        return Json.first(
                fallback == null ? "" : fallback.id,
                link == null ? "" : link.optString("linkid"),
                link == null ? "" : link.optString("link_id"),
                this.host.currentLinkId());
    }

    private void syncInteractions(FeedItem source) {
        interactions.apply(source);
        interactions.apply(this.host.currentDetailItem());
        interactions.applyAll(this.host.feedItems());
        interactions.applyAll(this.host.searchItems());
        this.host.feedChanged();
    }

    private void finishFollow(TextView view, boolean following) {
        view.setClickable(true);
        view.setAlpha(1.0f);
        updateFollowView(view, following);
    }

    private int followStatus(JSONObject link, JSONObject user) {
        return FollowStatus.resolve(link, user);
    }

    private int nextFollowStatus(int beforeStatus, boolean following) {
        if (following) return beforeStatus == 2 ? 3 : 1;
        return beforeStatus == 3 ? 2 : 0;
    }

    private void applyFollowState(JSONObject link, JSONObject user, int status) {
        boolean following = status == 1 || status == 3;
        putFollowState(link, status, following);
        putFollowState(user, status, following);
    }

    private void putFollowState(JSONObject target, int status, boolean following) {
        if (target == null) return;
        try {
            target.put("follow_status", status);
            target.put("follow_state", status);
            target.put("is_follow", following ? 1 : 0);
            target.put("is_following", following);
            target.put("followed", following);
        } catch (JSONException ignored) {
        }
    }

    private GradientDrawable outlinedAction(int fill, int radius) {
        GradientDrawable drawable = UiComponents.round(
                this.activity, fill, radius, uiScale());
        drawable.setStroke(Math.max(1, dp(1)), this.tokens.hairline);
        return drawable;
    }

    private int dp(int value) {
        return UiComponents.dp(this.activity, value, uiScale());
    }

    private float uiScale() {
        return this.session.uiScale() / 100.0f;
    }
}
