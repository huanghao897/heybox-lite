package com.ronan.heyboxlite;

import java.util.LinkedHashMap;
import java.util.Map;

final class OfficialRequestParams {
    private OfficialRequestParams() {}

    static Map<String, String> feed(int pull, Integer lastPull, String lastval,
                                    boolean firstRequest) {
        return feed(pull, lastPull, lastval, firstRequest, null, null);
    }

    static Map<String, String> feed(int pull, Integer lastPull, String lastval,
                                    boolean firstRequest, String unexposed,
                                    String refreshType) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pull", String.valueOf(pull));
        putIfPresent(params, "last_pull", lastPull == null ? null : String.valueOf(lastPull));
        putIfPresent(params, "lastval", lastval);
        putIfPresent(params, "unexposed", unexposed);
        params.put("is_first", firstRequest ? "1" : "0");
        putIfPresent(params, "refresh_type", refreshType);
        params.put("list_ver", "2");
        return params;
    }

    static Map<String, String> detail(String linkId, String hsrc) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "h_src", hsrc);
        params.put("link_id", linkId);
        params.put("page", "1");
        params.put("limit", "20");
        params.put("is_first", "1");
        params.put("owner_only", "0");
        return params;
    }

    static Map<String, String> subComments(String rootCommentId, String lastval,
                                           String hsrc) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("root_comment_id", rootCommentId);
        putIfPresent(params, "lastval", lastval);
        putIfPresent(params, "h_src", hsrc);
        params.put("hide_cy", "0");
        return params;
    }

    static Map<String, String> search(String keyword, int offset, int limit) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", keyword);
        params.put("search_type", "link");
        params.put("offset", String.valueOf(Math.max(0, offset)));
        params.put("limit", String.valueOf(Math.max(1, limit)));
        return params;
    }

    static Map<String, String> profileLinks(String userId, int offset, int limit) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put(SecureStrings.userid(), userId);
        params.put("offset", String.valueOf(Math.max(0, offset)));
        params.put("limit", String.valueOf(Math.max(1, limit)));
        params.put("no_banner", "1");
        return params;
    }

    static Map<String, String> history(int offset, int limit) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("type", "link");
        params.put("offset", String.valueOf(Math.max(0, offset)));
        params.put("limit", String.valueOf(Math.max(1, limit)));
        return params;
    }

    static Map<String, String> favorites(String folderId, int offset, int limit) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "folder_id", folderId);
        params.put("offset", String.valueOf(Math.max(0, offset)));
        params.put("limit", String.valueOf(Math.max(1, limit)));
        params.put("enable_new_style_collect", "1");
        return params;
    }

    static Map<String, String> hsrcQuery(String hsrc) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "h_src", hsrc);
        return params;
    }

    static Map<String, String> linkLike(String linkId, boolean liked) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("link_id", linkId);
        body.put("award_type", liked ? "1" : "0");
        return body;
    }

    static Map<String, String> favorite(String linkId, boolean favored) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("link_id", linkId);
        body.put("favour_type", favored ? "1" : "2");
        return body;
    }

    static Map<String, String> follow(String userId) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("following_id", userId);
        return body;
    }

    static Map<String, String> commentLike(String commentId, boolean liked) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("comment_id", commentId);
        body.put("support_type", liked ? "1" : "2");
        return body;
    }

    static Map<String, String> createComment(String linkId, String text, String rootId,
                                              String replyId) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("link_id", linkId);
        body.put("text", text);
        putCommentId(body, "root_id", rootId);
        putCommentId(body, "reply_id", replyId);
        body.put("imgs", "");
        body.put("is_cy", "0");
        return body;
    }

    private static void putCommentId(Map<String, String> values, String key, String value) {
        if (value == null || value.isEmpty() || "-1".equals(value)) return;
        values.put(key, value);
    }

    private static void putIfPresent(Map<String, String> values, String key, String value) {
        if (value != null && !value.isEmpty()) values.put(key, value);
    }
}
