package com.ronan.heyboxlite;

import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 评论 JSON 的无状态取值：id / 时间 / 点赞 / 归属地 / 等级 / 置顶与 Cy 判定、回复对象等。
 * 从 MainActivity 抽出，只依赖 {@link Json}，便于复用与单测；视图渲染仍在 MainActivity。
 */
final class CommentData {
    static final class CommentImage {
        final String url;
        final String mimeType;
        final boolean animated;

        CommentImage(String url, String mimeType, boolean animated) {
            this.url = url;
            this.mimeType = mimeType;
            this.animated = animated || isAnimatedMime(mimeType) || isGifUrl(url);
        }

        private static boolean isAnimatedMime(String value) {
            String mime = value == null ? "" : value.toLowerCase(Locale.ROOT);
            return mime.contains("gif") || mime.contains("animated");
        }

        private static boolean isGifUrl(String value) {
            if (value == null) return false;
            String url = value.toLowerCase(Locale.ROOT);
            int query = url.indexOf('?');
            if (query >= 0) url = url.substring(0, query);
            return url.endsWith(".gif");
        }
    }

    private CommentData() {}

    static int userLevel(JSONObject user) {
        if (user == null) {
            return 0;
        }
        JSONObject levelInfo = user.optJSONObject("level_info");
        return levelInfo != null ? levelInfo.optInt("level", 0) : user.optInt("level", 0);
    }

    static int levelBadgeColor(int level) {
        if (level >= 40) return Color.rgb(223, 153, 45);
        if (level >= 30) return Color.rgb(139, 100, 235);
        if (level >= 20) return Color.rgb(55, 178, 218);
        if (level >= 10) return Color.rgb(238, 70, 112);
        return Color.rgb(70, 168, 240);
    }

    static int commentLikes(JSONObject comment) {
        return comment.optInt("comment_award_num", comment.optInt("award_num", comment.optInt("up")));
    }

    static boolean commentLiked(JSONObject comment) {
        if (comment == null) {
            return false;
        }
        return Json.truthy(comment, "is_support", "supported", "is_award", "liked", "has_support");
    }

    static long commentTime(JSONObject comment) {
        return comment.optLong("create_at", comment.optLong("create_time"));
    }

    static String commentId(JSONObject comment) {
        return Json.first(comment.optString("commentid"), Json.first(comment.optString("comment_id"), comment.optString("id")));
    }

    static String commentRootId(JSONObject comment) {
        String root = Json.first(comment.optString("root_id"), comment.optString("root_comment_id"), comment.optString("rootid"));
        return root.isEmpty() ? commentId(comment) : root;
    }

    static List<CommentImage> commentImages(JSONObject comment) {
        List<CommentImage> result = new ArrayList<>();
        if (comment == null) return result;
        Set<String> seen = new HashSet<>();
        addCommentImages(result, seen, comment.opt("imgs"));
        if (result.isEmpty()) addCommentImages(result, seen, comment.opt("images"));
        if (result.isEmpty()) addCommentImages(result, seen, comment.opt("pictures"));
        return result;
    }

    private static void addCommentImages(List<CommentImage> result, Set<String> seen,
                                         Object value) {
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.startsWith("[")) {
                try {
                    addCommentImages(result, seen, new JSONArray(text));
                    return;
                } catch (Exception ignored) {
                }
            }
            addCommentImage(result, seen, text);
            return;
        }
        if (!(value instanceof JSONArray)) return;
        JSONArray images = (JSONArray) value;
        for (int i = 0; i < images.length(); i++) {
            Object item = images.opt(i);
            if (item instanceof JSONObject) {
                JSONObject image = (JSONObject) item;
                String url = Json.first(image.optString("url"), image.optString("original"),
                        image.optString("origin_url"), image.optString("large_url"),
                        image.optString("src"));
                String mimeType = Json.first(image.optString("mimetype"),
                        image.optString("mime_type"), image.optString("content_type"));
                boolean animated = Json.truthy(image, "is_animated", "animated", "is_gif");
                addCommentImage(result, seen, url, mimeType, animated);
            } else if (item instanceof String) {
                addCommentImage(result, seen, (String) item);
            }
        }
    }

    private static void addCommentImage(List<CommentImage> result, Set<String> seen, String url) {
        addCommentImage(result, seen, url, "", false);
    }

    private static void addCommentImage(List<CommentImage> result, Set<String> seen, String url,
                                        String mimeType, boolean animated) {
        String value = url == null ? "" : url.trim();
        if (!value.isEmpty() && seen.add(value)) {
            result.add(new CommentImage(value, mimeType == null ? "" : mimeType.trim(), animated));
        }
    }

    static String commentLocation(JSONObject comment) {
        if (comment == null) return "";
        String direct = Json.first(comment.optString("ip_location"), comment.optString("ipLocation"),
                comment.optString("ip_region"), comment.optString("ipRegion"),
                comment.optString("location"), comment.optString("area"),
                comment.optString("province"), comment.optString("city"),
                comment.optString("region"), comment.optString("address"));
        if (!direct.isEmpty()) return direct;
        JSONObject ip = Json.firstObject(comment, "ip_info", "ipInfo", "location_info", "locationInfo");
        if (ip == null) return "";
        return Json.first(ip.optString("location"), ip.optString("region"),
                ip.optString("province"), ip.optString("city"), ip.optString("area"));
    }

    static boolean isCyComment(JSONObject comment) {
        if (comment == null) return false;
        if (Json.truthy(comment, "is_cy", "cy", "is_eye", "_group_is_cy")) return true;
        String type = Json.first(comment.optString("comment_type"), comment.optString("type"));
        return "cy".equalsIgnoreCase(type);
    }

    static boolean isPinnedComment(JSONObject comment) {
        // 官方置顶评论判据是 top_comment 字段（BBSCommentObj.top_comment），
        // 角标显隐用 c.x()：等于 "1" 或 "true" 才置顶。
        // 注意不是 is_top —— is_top 每个楼主评论都有，会把整列都误标。
        if (comment == null) return false;
        String flag = comment.optString("top_comment").trim();
        return "1".equals(flag) || "true".equalsIgnoreCase(flag);
    }

    static boolean isPinnedThread(JSONObject group) {
        if (group == null) return false;
        if (isPinnedComment(group)) return true;
        JSONArray comments = group.optJSONArray("comment");
        return comments != null && isPinnedComment(comments.optJSONObject(0));
    }

    static long threadTime(JSONObject group) {
        JSONArray comments = group == null ? null : group.optJSONArray("comment");
        JSONObject root = comments == null ? group : comments.optJSONObject(0);
        return root == null ? 0L : commentTime(root);
    }

    static int threadLikes(JSONObject group) {
        JSONArray comments = group.optJSONArray("comment");
        JSONObject root = comments == null ? group : comments.optJSONObject(0);
        return commentLikes(root == null ? group : root);
    }

    static String replyTarget(JSONObject comment) {
        JSONObject target = comment.optJSONObject("to_user");
        if (target == null) {
            target = comment.optJSONObject("reply_user");
        }
        if (target == null) {
            target = comment.optJSONObject("target_user");
        }
        return target == null ? "" : target.optString("username", target.optString("nickname"));
    }
}
