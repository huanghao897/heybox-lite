package com.ronan.heyboxlite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
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
    private static final int REPLY_PAGE_SIZE = 5;

    interface PageHost {
        FeedItem currentItem();

        JSONObject currentBody();

        String currentLinkId();

        String currentHsrc();

        String currentAuthCode();

        boolean requireLogin(String actionName);

        boolean allowWriteAction(String actionName);

        String writeErrorMessage(String actionName, String message);

        void reloadDetail();

        void showToast(String message);

        void openImage(ImageView source, String url);
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
    }

    CommentRenderer renderer() {
        return this.renderer;
    }

    void reset() {
        this.replyStates.clear();
    }

    void showDialog(JSONObject replyTo) {
        if (!this.pageHost.requireLogin(replyTo == null ? "评论" : "回复评论")) {
            return;
        }
        if (this.pageHost.currentLinkId().isEmpty()) {
            this.pageHost.showToast("没有打开的帖子");
            return;
        }
        int dialogBackground = this.session.darkMode()
                ? Color.rgb(30, 31, 33) : Color.rgb(250, 250, 251);
        int inputBackground = this.session.darkMode()
                ? Color.rgb(12, 13, 14) : Color.rgb(241, 242, 244);
        int border = this.session.darkMode()
                ? Color.rgb(66, 68, 72) : Color.rgb(218, 221, 225);
        LinearLayout panel = new LinearLayout(this.activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        Compat.setBackground(panel,
                roundStroke(dialogBackground, 16, border, 1));
        TextView title = text(replyTo == null ? "发表评论" : "回复评论",
                16.0f, this.tokens.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = text(replyTo == null ? "写下你的想法" : "回复这条评论",
                11.0f, this.tokens.muted);
        LinearLayout.LayoutParams hintParams =
                new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(4);
        panel.addView(hint, hintParams);

        EditText input = new EditText(this.activity);
        input.setTextColor(this.tokens.text);
        input.setHintTextColor(this.tokens.muted);
        input.setTextSize(sp(14.0f));
        input.setMinLines(3);
        input.setMaxLines(5);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setSingleLine(false);
        input.setHint(replyTo == null ? "友好交流，理性讨论" : "输入回复内容");
        String draftKey = draftKey(replyTo);
        String draft = this.session.commentDraft(draftKey);
        if (!draft.isEmpty()) {
            input.setText(draft);
            input.setSelection(draft.length());
        }
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start,
                                      int before, int count) {
                session.setCommentDraft(draftKey,
                        value == null ? "" : value.toString());
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        Compat.setBackground(input,
                roundStroke(inputBackground, 14, border, 1));
        LinearLayout.LayoutParams inputParams =
                new LinearLayout.LayoutParams(-1, dp(118));
        inputParams.topMargin = dp(12);
        panel.addView(input, inputParams);

        AlertDialog dialog = new AlertDialog.Builder(this.activity)
                .setView(panel).create();
        LinearLayout actions = new LinearLayout(this.activity);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, dp(12), 0, 0);
        TextView cancel = dialogAction("取消", false);
        TextView send = dialogAction("发送", true);
        LinearLayout.LayoutParams cancelParams =
                new LinearLayout.LayoutParams(0, dp(38), 1.0f);
        LinearLayout.LayoutParams sendParams =
                new LinearLayout.LayoutParams(0, dp(38), 1.0f);
        sendParams.leftMargin = dp(9);
        actions.addView(cancel, cancelParams);
        actions.addView(send, sendParams);
        panel.addView(actions);
        cancel.setOnClickListener(view -> {
            UiComponents.press(cancel);
            dialog.dismiss();
        });
        send.setOnClickListener(view -> {
            UiComponents.press(send);
            String value = input.getText().toString().trim();
            if (value.isEmpty()) {
                this.pageHost.showToast("评论不能为空");
            } else {
                send(value, replyTo, dialog, send);
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
            dialog.getWindow().setDimAmount(
                    this.session.darkMode() ? 0.56f : 0.36f);
            int width = this.activity.getResources()
                    .getDisplayMetrics().widthPixels;
            int horizontalMargin = dp(this.roundLayout ? 24 : 14);
            dialog.getWindow().setLayout(Math.max(1,
                    Math.min(width - horizontalMargin * 2, dp(380))), -2);
        }
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
                                shown + REPLY_PAGE_SIZE,
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
    public void openImage(ImageView source, String url) {
        this.pageHost.openImage(source, url);
    }

    @Override
    public boolean isPostAuthor(JSONObject user, String author) {
        String commentUserId = userId(user);
        FeedItem current = this.pageHost.currentItem();
        String postAuthorId = current == null ? "" : current.authorId;
        if (postAuthorId.isEmpty()) {
            JSONObject body = this.pageHost.currentBody();
            JSONObject result = body == null ? null : body.optJSONObject("result");
            JSONObject link = result == null ? null : result.optJSONObject("link");
            postAuthorId = authorUserId(
                    link, link == null ? null : link.optJSONObject("user"));
        }
        if (!commentUserId.isEmpty() && !postAuthorId.isEmpty()) {
            return commentUserId.equals(postAuthorId);
        }
        return current != null && !author.isEmpty()
                && author.equals(current.author);
    }

    private void send(String value, JSONObject replyTo,
                      AlertDialog dialog, View sendButton) {
        if (!this.pageHost.allowWriteAction("评论")) return;
        String replyId = replyTo == null ? null : CommentData.commentId(replyTo);
        String rootId = replyTo == null ? null : CommentData.commentRootId(replyTo);
        sendButton.setEnabled(false);
        sendButton.setAlpha(0.58f);
        this.writeActions.createComment(this.pageHost.currentLinkId(),
                this.pageHost.currentHsrc(), this.pageHost.currentAuthCode(),
                value, rootId, replyId, new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        session.setCommentDraft(draftKey(replyTo), "");
                        if (dialog.isShowing()) dialog.dismiss();
                        pageHost.showToast("评论已发");
                        pageHost.reloadDetail();
                    }

                    @Override
                    public void onError(String message) {
                        if (dialog.isShowing()) {
                            sendButton.setEnabled(true);
                            sendButton.setAlpha(1.0f);
                        }
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

    private String draftKey(JSONObject replyTo) {
        String replyId = replyTo == null
                ? "root" : CommentData.commentId(replyTo);
        return this.pageHost.currentLinkId() + ":" + replyId;
    }

    private String authorUserId(JSONObject link, JSONObject user) {
        return Json.first(
                link == null ? "" : link.optString(SecureStrings.userid()),
                link == null ? "" : link.optString(SecureStrings.userId()),
                link == null ? "" : link.optString(SecureStrings.heyboxId()),
                link == null ? "" : link.optString("heyboxid"),
                link == null ? "" : link.optString("uid"),
                link == null ? "" : link.optString("account_id"),
                link == null ? "" : link.optString("id"), userId(user));
    }

    private String userId(JSONObject user) {
        return user == null ? "" : Json.first(
                user.optString(SecureStrings.userid()),
                user.optString(SecureStrings.userId()),
                user.optString(SecureStrings.heyboxId()),
                user.optString("heyboxid"), user.optString("uid"),
                user.optString("account_id"), user.optString("id"));
    }

    private TextView dialogAction(String label, boolean primary) {
        int fill = primary ? this.tokens.primary : ThemeTokens.blend(
                this.tokens.panel, this.tokens.muted,
                this.session.darkMode() ? 0.22f : 0.09f);
        int stroke = primary ? this.tokens.primary : ThemeTokens.blend(
                this.tokens.panel, this.tokens.muted, 0.28f);
        int color = primary ? ThemeTokens.contrast(fill) : this.tokens.text;
        TextView view = text(label, 13.0f, color);
        view.setTypeface(Typeface.DEFAULT,
                primary ? Typeface.BOLD : Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), 0, dp(10), 0);
        Compat.setBackground(view, roundStroke(fill, 19, stroke, 1));
        return view;
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(
                this.activity, value, size, color, this.textScale);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private android.graphics.drawable.GradientDrawable roundStroke(
            int color, int radius, int strokeColor, int strokeWidth) {
        android.graphics.drawable.GradientDrawable drawable =
                UiComponents.round(this.activity, color, radius, this.uiScale);
        drawable.setStroke(Math.max(1, dp(strokeWidth)), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * this.activity.getResources()
                .getDisplayMetrics().density * this.uiScale);
    }

    private float sp(float value) {
        return value * this.textScale;
    }
}
