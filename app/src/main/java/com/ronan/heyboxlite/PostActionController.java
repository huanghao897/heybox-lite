package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final Map<String, LikeState> likeOverrides = new HashMap<>();

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
    }

    LikeState linkLikeState(JSONObject link, FeedItem fallback) {
        LikeState override = linkLikeOverride(link, fallback);
        return override != null ? override
                : new LikeState(linkLiked(link, fallback), linkLikes(link, fallback));
    }

    boolean linkFavored(JSONObject link) {
        return link != null && Json.truthy(link, "is_favour", "is_favor", "is_fav",
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
        if (!canWrite("点赞", item)) return;
        boolean beforeLiked = item.liked;
        int beforeLikes = Math.max(0, item.likes);
        boolean nextLiked = !beforeLiked;
        int nextLikes = Math.max(0, beforeLikes + (nextLiked ? 1 : -1));
        setLike(item, nextLiked, nextLikes);
        updateLinkLikeView(view, nextLiked, nextLikes);
        updateFeedLike(item.id, nextLiked, nextLikes);
        this.writeActions.like(item.id, hsrcFor(item), nextLiked, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                rememberLinkLike(item.id, nextLiked, nextLikes);
                updateFeedLike(item.id, nextLiked, nextLikes);
                localCache.log("link like ok " + item.id + ": "
                        + beforeLikes + " -> " + nextLikes);
            }

            @Override
            public void onError(String message) {
                setLike(item, beforeLiked, beforeLikes);
                updateLinkLikeView(view, beforeLiked, beforeLikes);
                updateFeedLike(item.id, beforeLiked, beforeLikes);
                host.showToast("点赞失败" + writeErrorMessage("点赞", message));
            }
        });
    }

    void toggleFeedLike(FeedItem item) {
        if (!canWrite("点赞", item)) return;
        boolean beforeLiked = item.liked;
        int beforeLikes = Math.max(0, item.likes);
        boolean nextLiked = !beforeLiked;
        int nextLikes = Math.max(0, beforeLikes + (nextLiked ? 1 : -1));
        setLike(item, nextLiked, nextLikes);
        updateFeedLike(item.id, nextLiked, nextLikes);
        this.writeActions.like(item.id, hsrcFor(item), nextLiked, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                rememberLinkLike(item.id, nextLiked, nextLikes);
                updateFeedLike(item.id, nextLiked, nextLikes);
                localCache.log("feed like ok " + item.id + ": "
                        + beforeLikes + " -> " + nextLikes);
            }

            @Override
            public void onError(String message) {
                setLike(item, beforeLiked, beforeLikes);
                updateFeedLike(item.id, beforeLiked, beforeLikes);
                host.showToast("点赞失败" + writeErrorMessage("点赞", message));
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
        this.writeActions.follow(item.authorId, hsrcFor(item), following,
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        updateAuthorFollowing(item.authorId, following);
                        host.showToast(following ? "已关注" : "已取消关注");
                        callback.onComplete(true);
                    }

                    @Override
                    public void onError(String message) {
                        host.showToast("关注操作失败"
                                + writeErrorMessage("关注", message));
                        callback.onComplete(false);
                    }
                });
    }

    void toggleFavorite(FeedItem item, ImageView view) {
        if (!canWrite("收藏", item)) return;
        boolean favored = Boolean.TRUE.equals(view.getTag());
        boolean nextFavored = !favored;
        updateFavoriteView(view, nextFavored);
        view.setEnabled(false);
        this.writeActions.favorite(item.id, hsrcFor(item), nextFavored,
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        view.setEnabled(true);
                        host.showToast(nextFavored ? "已收藏" : "已取消收藏");
                    }

                    @Override
                    public void onError(String message) {
                        view.setEnabled(true);
                        updateFavoriteView(view, favored);
                        host.showToast("收藏操作失败"
                                + writeErrorMessage("收藏", message));
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
        boolean before = Boolean.TRUE.equals(view.getTag());
        int beforeStatus = followStatus(link, user);
        boolean next = !before;
        updateFollowView(view, next);
        view.setClickable(false);
        view.setAlpha(0.92f);
        this.writeActions.follow(targetUserId, hsrcFor(this.host.currentDetailItem()), next,
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        applyFollowState(link, user, nextFollowStatus(beforeStatus, next));
                        finishFollow(view, next);
                        host.showToast(next ? "已关注" : "已取消关注");
                    }

                    @Override
                    public void onError(String message) {
                        finishFollow(view, before);
                        host.showToast("关注操作失败"
                                + writeErrorMessage("关注", message));
                    }
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
        updateFeedLike(item.id, item.liked, item.likes);
    }

    void updateFollowView(TextView view, boolean following) {
        view.setTag(following);
        view.setText(this.roundLayout
                ? following ? "✓" : "+"
                : following ? "已关注" : "+ 关注");
        view.setTextColor(this.tokens.accent);
        GradientDrawable drawable = UiComponents.round(this.activity,
                following ? this.tokens.softAccent() : Color.TRANSPARENT,
                15, uiScale());
        drawable.setStroke(Math.max(1, dp(1)), this.tokens.accent);
        Compat.setBackground(view, drawable);
    }

    boolean isFollowing(JSONObject link, JSONObject user) {
        int status = followStatus(link, user);
        return status >= 0 ? status == 1 || status == 3
                : Json.truthy(link, "is_follow", "is_following", "followed")
                || Json.truthy(user, "is_follow", "is_following", "followed");
    }

    String authorUserId(JSONObject link, JSONObject user) {
        return Json.first(
                link == null ? "" : link.optString(SecureStrings.userid()),
                link == null ? "" : link.optString(SecureStrings.userId()),
                link == null ? "" : link.optString(SecureStrings.heyboxId()),
                link == null ? "" : link.optString("heyboxid"),
                link == null ? "" : link.optString("uid"),
                link == null ? "" : link.optString("account_id"),
                link == null ? "" : link.optString("id"),
                userId(user));
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
        String id = Json.first(
                fallback == null ? "" : fallback.id,
                link == null ? "" : link.optString("linkid"),
                link == null ? "" : link.optString("link_id"),
                this.host.currentLinkId());
        return id.isEmpty() ? null : this.likeOverrides.get(id);
    }

    private void setLike(FeedItem item, boolean liked, int likes) {
        item.liked = liked;
        item.likes = likes;
        rememberLinkLike(item.id, liked, likes);
    }

    private void rememberLinkLike(String linkId, boolean liked, int likes) {
        if (linkId != null && !linkId.isEmpty()) {
            this.likeOverrides.put(linkId, new LikeState(liked, likes));
        }
    }

    private void updateFeedLike(String linkId, boolean liked, int likes) {
        if (linkId == null || linkId.isEmpty()) return;
        for (FeedItem item : this.host.feedItems()) {
            if (!linkId.equals(item.id)) continue;
            item.liked = liked;
            item.likes = likes;
            break;
        }
        this.host.feedChanged();
    }

    private void updateAuthorFollowing(String authorId, boolean following) {
        updateAuthorFollowing(this.host.feedItems(), authorId, following);
        updateAuthorFollowing(this.host.searchItems(), authorId, following);
        this.host.feedChanged();
    }

    private void updateAuthorFollowing(List<FeedItem> items, String authorId,
                                       boolean following) {
        if (items == null || authorId == null || authorId.isEmpty()) return;
        for (FeedItem item : items) {
            if (authorId.equals(item.authorId)) item.following = following;
        }
    }

    private void finishFollow(TextView view, boolean following) {
        view.setClickable(true);
        view.setAlpha(1.0f);
        updateFollowView(view, following);
    }

    private int followStatus(JSONObject link, JSONObject user) {
        int linkStatus = followStatusValue(link);
        return linkStatus >= 0 ? linkStatus : followStatusValue(user);
    }

    private int followStatusValue(JSONObject source) {
        if (source == null) return -1;
        String[] keys = {"follow_status", "follow_state", "follow_state_v2",
                "is_follow", "is_following", "followed"};
        for (String key : keys) {
            if (!source.has(key)) continue;
            Object value = source.opt(key);
            if (value instanceof Boolean) return (Boolean) value ? 1 : 0;
            if (value instanceof Number) return ((Number) value).intValue();
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) continue;
            if ("true".equalsIgnoreCase(text) || "followed".equalsIgnoreCase(text)
                    || "following".equalsIgnoreCase(text)) return 1;
            if ("mutual".equalsIgnoreCase(text)) return 3;
            if ("false".equalsIgnoreCase(text) || "none".equalsIgnoreCase(text)
                    || "unfollowed".equalsIgnoreCase(text)) return 0;
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
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

    private String userId(JSONObject user) {
        return user == null ? "" : Json.first(
                user.optString(SecureStrings.userid()),
                user.optString(SecureStrings.userId()),
                user.optString(SecureStrings.heyboxId()),
                user.optString("heyboxid"), user.optString("uid"),
                user.optString("account_id"), user.optString("id"));
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
