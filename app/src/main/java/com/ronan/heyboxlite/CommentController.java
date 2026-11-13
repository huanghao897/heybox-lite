package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CommentController implements CommentRenderer.Host {
    interface PageHost {
        FeedItem currentItem();

        JSONObject currentBody();

        String currentLinkId();

        String currentHsrc();

        String currentAuthCode();

        default Object currentEntry() { return currentItem(); }

        boolean requireLogin(String actionName);

        boolean allowWriteAction(String actionName);

        String writeErrorMessage(String actionName, String message);

        void reloadDetail();

        void showToast(String message);

        void openOriginalImage(ImageView source, String url);
    }

    private static final class ReplyState {
        String lastval = "";
        boolean hasMore = true;
        boolean loading;
    }

    private final Activity activity;
    private final SessionStore session;
    private final ApiClient api;
    private final WriteActionClient writeActions;
    private final ThemeTokens tokens;
    private final boolean roundLayout;
    private final float uiScale;
    private final float textScale;
    private final PageHost pageHost;
    private final Map<String, ReplyState> replyStates = new HashMap<>();
    private final CommentRenderer renderer;
    private final NativeCommentDrafts drafts;
    private Object fallbackEntry = new Object();

    CommentController(Activity activity, SessionStore session, ApiClient api,
                      WriteActionClient writeActions, LocalCache localCache,
                      ThemeTokens tokens, boolean roundLayout,
                      PageHost pageHost) {
        this.activity = activity;
        this.session = session;
        this.api = api;
        this.writeActions = writeActions;
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.uiScale = session.uiScale() / 100.0f;
        this.textScale = session.textScale() / 100.0f;
        this.pageHost = pageHost;
        this.renderer = new CommentRenderer(activity, session, localCache,
                tokens, roundLayout, this);
        this.drafts = new NativeCommentDrafts(session);
    }

    CommentRenderer renderer() {
        return this.renderer;
    }

    void reset() {
        this.replyStates.clear();
        this.fallbackEntry = new Object();
    }

    void showDialog(JSONObject replyTo) {
        if (!this.pageHost.requireLogin(replyTo == null ? "评论" : "回复评论")) {
            return;
        }
        if (this.pageHost.currentLinkId().isEmpty()) {
            this.pageHost.showToast("没有打开的帖子");
            return;
        }
        String linkId = this.pageHost.currentLinkId();
        String replyId = replyTo == null ? null : CommentData.commentId(replyTo);
        String rootId = replyTo == null ? null : CommentData.commentRootId(replyTo);
        NativeCommentDrafts.Editor editor = drafts.open(currentEntry(),
                linkId + ":" + (replyTo == null ? "root" : replyId));
        new NativeCommentEditor(activity, session, tokens).show(replyTo != null,
                roundLayout, session.commentDraft(editor.key), new NativeCommentEditor.Events() {
                    @Override public void changed(String value) { drafts.edit(editor, value); }
                    @Override public void send(String value, AlertDialog dialog, View button) {
                        CommentController.this.send(editor, linkId, value, rootId, replyId, dialog, button);
                    }
                });
    }

    @Override
    public void loadReplies(LinearLayout target, JSONObject root,
                            List<JSONObject> preview, int expected, int shown) {
        String id = CommentData.commentId(root);
        ReplyState state = this.replyStates.get(id);
        if (state == null) {
            state = new ReplyState();
            this.replyStates.put(id, state);
        }
        if (state.loading || !state.hasMore) return;
        state.loading = true;
        ReplyState requestState = state;
        String requestedLastval = state.lastval;
        TextView loading = text("正在加载更多回复", 11.0f, this.tokens.muted);
        loading.setPadding(0, dp(8), 0, dp(8));
        target.addView(loading);
        this.api.get(EndpointProvider.subComments(),
                OfficialRequestParams.subComments(id, requestedLastval,
                        this.pageHost.currentHsrc()), new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        requestState.loading = false;
                        JSONObject result = body.optJSONObject("result");
                        String nextLastval = result == null
                                ? "" : result.optString("lastval", "");
                        boolean cursorAdvanced = !nextLastval.isEmpty()
                                && !nextLastval.equals(requestedLastval);
                        requestState.lastval = nextLastval;
                        requestState.hasMore = result != null
                                && Json.truthy(result, "has_more")
                                && cursorAdvanced;
                        List<JSONObject> replies = extractReplies(body, id);
                        List<JSONObject> merged = mergeReplies(preview, replies);
                        Collections.sort(merged, (left, right) -> Long.compare(
                                CommentData.commentTime(left),
                                CommentData.commentTime(right)));
                        renderer.renderReplies(target, root, merged,
                                Math.max(expected, merged.size()),
                                shown + CommentReplyPaging.PAGE_SIZE,
                                !requestState.hasMore);
                        if (replies.isEmpty()) {
                            pageHost.showToast("没有更多回复了");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        requestState.loading = false;
                        target.removeView(loading);
                        pageHost.showToast("回复加载失败：" + message);
                    }
                });
    }

    @Override
    public void toggleLike(JSONObject comment,
                           CommentRenderer.LikeControl control) {
        if (!this.pageHost.requireLogin("评论点赞")
                || !this.pageHost.allowWriteAction("评论点赞")) {
            return;
        }
        String id = CommentData.commentId(comment);
        if (id.isEmpty()) {
            this.pageHost.showToast("没有获取到评论 ID");
            return;
        }
        boolean beforeLiked = CommentData.commentLiked(comment);
        int beforeLikes = CommentData.commentLikes(comment);
        boolean nextLiked = !beforeLiked;
        int nextLikes = Math.max(0, beforeLikes + (nextLiked ? 1 : -1));
        setLikeState(comment, nextLiked, nextLikes);
        this.renderer.updateLikeView(control, nextLiked, nextLikes);
        control.root.setEnabled(false);
        this.writeActions.commentLike(id, this.pageHost.currentHsrc(),
                nextLiked, new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        control.root.setEnabled(true);
                    }

                    @Override
                    public void onError(String message) {
                        control.root.setEnabled(true);
                        setLikeState(comment, beforeLiked, beforeLikes);
                        renderer.updateLikeView(control, beforeLiked, beforeLikes);
                        pageHost.showToast("评论点赞失败"
                                + pageHost.writeErrorMessage("评论点赞", message));
                    }
                });
    }

    @Override
    public void reply(JSONObject comment) {
        showDialog(comment);
    }

    @Override
    public void copy(String text) {
        if (text == null || text.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) this.activity
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("评论", text));
        this.pageHost.showToast("评论已复制");
    }

    @Override
    public void openOriginalImage(ImageView source, String url) {
        this.pageHost.openOriginalImage(source, url);
    }

    @Override
    public boolean isPostAuthor(JSONObject user, String author) {
        String commentUserId = AuthorIdentity.userId(user);
        FeedItem current = this.pageHost.currentItem();
        String postAuthorId = current == null ? "" : current.authorId;
        if (postAuthorId.isEmpty()) {
            JSONObject body = this.pageHost.currentBody();
            JSONObject result = body == null ? null : body.optJSONObject("result");
            JSONObject link = result == null ? null : result.optJSONObject("link");
            postAuthorId = AuthorIdentity.authorId(
                    link, link == null ? null : link.optJSONObject("user"));
        }
        if (!commentUserId.isEmpty() && !postAuthorId.isEmpty()) {
            return commentUserId.equals(postAuthorId);
        }
        return current != null && !author.isEmpty()
                && author.equals(current.author);
    }

    private void send(NativeCommentDrafts.Editor editor, String linkId, String value,
                      String rootId, String replyId, AlertDialog dialog, View sendButton) {
        if (!drafts.active(editor) || !current(editor.entry.get(), linkId)) return;
        if (value.isEmpty()) {
            this.pageHost.showToast("评论不能为空");
            return;
        }
        if (!this.pageHost.allowWriteAction("评论")) return;
        NativeCommentDrafts.Submission sent = drafts.submit(editor);
        long account = session.sessionGeneration();
        sendButton.setEnabled(false);
        sendButton.setAlpha(0.58f);
        this.writeActions.createComment(linkId,
                this.pageHost.currentHsrc(), this.pageHost.currentAuthCode(),
                value, rootId, replyId, new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        if (!session.isCurrentSession(account)) return;
                        boolean cleared = drafts.clear(sent);
                        if (!drafts.active(editor) || !current(editor.entry.get(), linkId)) return;
                        if (cleared && dialog.isShowing()) dialog.dismiss();
                        if (!cleared) enableSend(sendButton);
                        pageHost.showToast("评论已发");
                        if (cleared) pageHost.reloadDetail();
                    }

                    @Override
                    public void onError(String message) {
                        if (!session.isCurrentSession(account) || !drafts.active(editor)
                                || !current(editor.entry.get(), linkId)) return;
                        if (dialog.isShowing()) enableSend(sendButton);
                        pageHost.showToast("评论发送失败："
                                + pageHost.writeErrorMessage("评论", message));
                    }
                });
    }

    private List<JSONObject> mergeReplies(List<JSONObject> first,
                                          List<JSONObject> second) {
        List<JSONObject> merged = new ArrayList<>(first);
        for (JSONObject candidate : second) {
            String id = CommentData.commentId(candidate);
            boolean duplicate = false;
            for (JSONObject existing : merged) {
                if (!id.isEmpty() && id.equals(CommentData.commentId(existing))) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) merged.add(candidate);
        }
        return merged;
    }

    private List<JSONObject> extractReplies(JSONObject body, String rootId) {
        List<JSONObject> values = new ArrayList<>();
        JSONObject result = body.optJSONObject("result");
        if (result == null) return values;
        JSONArray array = Json.firstArray(result,
                "comments", "comment", "list", "sub_comments");
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            JSONArray nested = item.optJSONArray("comment");
            if (nested == null) {
                addReply(values, item, rootId);
                continue;
            }
            for (int j = 0; j < nested.length(); j++) {
                addReply(values, nested.optJSONObject(j), rootId);
            }
        }
        return values;
    }

    private void addReply(List<JSONObject> values, JSONObject reply,
                          String rootId) {
        if (reply != null
                && !rootId.equals(CommentData.commentId(reply))) {
            values.add(reply);
        }
    }

    private void setLikeState(JSONObject comment, boolean liked, int likes) {
        try {
            comment.put("is_support", liked ? 1 : 0);
            comment.put("comment_award_num", Math.max(0, likes));
        } catch (JSONException ignored) {
        }
    }

    private Object currentEntry() {
        Object entry = pageHost.currentEntry();
        return entry == null ? fallbackEntry : entry;
    }

    private boolean current(Object entry, String linkId) {
        return !activity.isFinishing() && !activity.isDestroyed()
                && entry != null && entry == currentEntry() && linkId.equals(pageHost.currentLinkId());
    }

    private void enableSend(View button) {
        button.setEnabled(true);
        button.setAlpha(1f);
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(
                this.activity, value, size, color, this.textScale);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * this.activity.getResources()
                .getDisplayMetrics().density * this.uiScale);
    }

}
