package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

final class DetailPageAssembler {
    interface LayoutHost {
        int dp(int value);
        boolean watchLayout();
        boolean roundLayout();
        int pageHorizontalPadding();
        int subpageTopPadding();
        int roundHeaderInnerInset();
        LinearLayout articleSurface();
        View returnPreview();
        View backButton();
    }

    static final class Result {
        final JSONObject body;
        final String hsrc;
        final String authCode;
        final String diagnostics;
        final DetailPager pager;
        final ScrollView articleScroll;
        final ScrollView commentScroll;
        final FrameLayout root;
        final LinearLayout articleCommentHost;
        final LinearLayout commentPageHost;
        final JSONArray comments;
        final int pagePadding;
        final int roundHeaderTop;

        Result(JSONObject body, String hsrc, String authCode, String diagnostics,
               DetailPager pager, ScrollView articleScroll, ScrollView commentScroll,
               FrameLayout root, LinearLayout articleCommentHost,
               LinearLayout commentPageHost, JSONArray comments, int pagePadding,
               int roundHeaderTop) {
            this.body = body;
            this.hsrc = hsrc;
            this.authCode = authCode;
            this.diagnostics = diagnostics;
            this.pager = pager;
            this.articleScroll = articleScroll;
            this.commentScroll = commentScroll;
            this.root = root;
            this.articleCommentHost = articleCommentHost;
            this.commentPageHost = commentPageHost;
            this.comments = comments;
            this.pagePadding = pagePadding;
            this.roundHeaderTop = roundHeaderTop;
        }
    }

    private final Activity activity;
    private final SessionStore session;
    private final ThemeTokens tokens;
    private final LocalCache cache;
    private final PostActionController postActions;
    private final DetailHeaderRenderer headerRenderer;
    private final DetailContentRenderer contentRenderer;
    private final DetailActionBar actionBar;
    private final DetailCommentsSection commentsSection;
    private final ReadingTimeTracker readingTimeTracker;
    private final LayoutHost layout;

    DetailPageAssembler(Activity activity, SessionStore session, ThemeTokens tokens,
                        LocalCache cache, PostActionController postActions,
                        DetailHeaderRenderer headerRenderer,
                        DetailContentRenderer contentRenderer, DetailActionBar actionBar,
                        DetailCommentsSection commentsSection,
                        ReadingTimeTracker readingTimeTracker, LayoutHost layout) {
        this.activity = activity;
        this.session = session;
        this.tokens = tokens;
        this.cache = cache;
        this.postActions = postActions;
        this.headerRenderer = headerRenderer;
        this.contentRenderer = contentRenderer;
        this.actionBar = actionBar;
        this.commentsSection = commentsSection;
        this.readingTimeTracker = readingTimeTracker;
        this.layout = layout;
    }

    Result assemble(JSONObject source, FeedItem fallback, String currentHsrc,
                    String screen, String linkId, DetailPager.Listener listener) {
        JSONObject body = DetailResponseNormalizer.normalize(source);
        JSONObject result = body.optJSONObject("result");
        JSONObject link = result == null ? null : result.optJSONObject("link");
        String hsrc = Json.first(postActions.hsrc(link), fallback.hsrc, currentHsrc);
        String authCode = authCode(body, result, link);

        DetailPager pager = new DetailPager(activity, layout::dp,
                layout.watchLayout(), listener);
        pager.setBackgroundColor(tokens.background);
        boolean round = layout.roundLayout();
        int pagePadding = round ? layout.pageHorizontalPadding()
                : Math.max(layout.dp(10), layout.dp(session.pagePadding()));
        int roundHeaderTop = round ? layout.subpageTopPadding() : 0;
        int detailTopPadding = round ? roundHeaderTop + layout.dp(40) : layout.dp(50);

        ScrollView articleScroll = scroll();
        LinearLayout page = column();
        page.setPadding(pagePadding, detailTopPadding, pagePadding, layout.dp(18));
        articleScroll.addView(page);
        LinearLayout article = layout.articleSurface();
        JSONObject user = link == null ? null : link.optJSONObject("user");
        String author = user == null ? fallback.author
                : user.optString("username", fallback.author);
        String heading = link == null ? fallback.title
                : link.optString("title", fallback.title);
        TextView headline = UiComponents.label(activity, "", 19f, tokens.text,
                session.textScale() / 100f);
        EmojiRenderer.set(headline, RichContent.plainText(heading), session.darkMode());
        headline.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        headline.setLineSpacing(layout.dp(2), 1.08f);
        article.addView(headline);
        headerRenderer.addAuthor(article, link, user, author);
        if (readingTimeTracker != null) {
            readingTimeTracker.tagTopic(headerRenderer.firstTopicName(link, fallback.topicName));
        }
        if (fallback.article) headerRenderer.addTopics(article, link, fallback.topicName);
        addFallbackNotice(article, body.optString("_fallback_notice"));

        JSONArray fallbackImages = link == null ? null : link.optJSONArray("imgs");
        JSONArray comments = result == null ? null : result.optJSONArray("comments");
        String diagnostics = DetailDiagnostics.build(screen, linkId, session.playGif(),
                body, fallback, link, fallbackImages, comments);
        cache.log("detail diagnostics captured link=" + fallback.id
                + " title=" + DetailDiagnostics.compactText(heading, 48));
        contentRenderer.add(article, link, fallback.description, fallbackImages);
        if (!fallback.article) headerRenderer.addTopics(article, link, fallback.topicName);
        actionBar.add(article, fallback, link);
        page.addView(article);
        LinearLayout articleComments = commentsSection.placeholder(page, comments);

        ScrollView commentScroll = scroll();
        LinearLayout commentPage = column();
        commentPage.setPadding(pagePadding, detailTopPadding, pagePadding, layout.dp(18));
        commentScroll.addView(commentPage);
        LinearLayout commentPageHost = commentsSection.placeholder(commentPage, comments);
        pager.setPages(layout.returnPreview(), articleScroll, commentScroll);

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(tokens.background);
        root.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(
                layout.dp(36), layout.dp(36), 51);
        backParams.leftMargin = pagePadding
                + (round ? layout.roundHeaderInnerInset() : 0);
        backParams.topMargin = round ? roundHeaderTop : layout.dp(8);
        root.addView(layout.backButton(), backParams);
        return new Result(body, hsrc, authCode, diagnostics, pager, articleScroll,
                commentScroll, root, articleComments, commentPageHost, comments,
                pagePadding, roundHeaderTop);
    }

    private void addFallbackNotice(LinearLayout article, String notice) {
        if (notice.isEmpty()) return;
        TextView view = UiComponents.label(activity, notice, 11f, tokens.secondary,
                session.textScale() / 100f);
        view.setLineSpacing(0f, 1.16f);
        int background = ThemeTokens.blend(tokens.panel, tokens.secondary,
                session.darkMode() ? 0.22f : 0.12f);
        GradientDrawable drawable = UiComponents.round(activity, background, 7,
                session.uiScale() / 100f);
        drawable.setStroke(layout.dp(1), ThemeTokens.blend(tokens.secondary, tokens.text,
                session.darkMode() ? 0.2f : 0.12f));
        view.setPadding(layout.dp(8), layout.dp(6), layout.dp(8), layout.dp(6));
        Compat.setBackground(view, drawable);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = layout.dp(7);
        article.addView(view, params);
    }

    private ScrollView scroll() {
        ScrollView view = new ScrollView(activity);
        view.setBackgroundColor(tokens.background);
        return view;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackgroundColor(tokens.background);
        return view;
    }

    private static String authCode(JSONObject body, JSONObject result, JSONObject link) {
        String value = link == null ? "" : link.optString("auth_code");
        if (value.isEmpty() && result != null) value = result.optString("auth_code");
        return value.isEmpty() ? body.optString("auth_code") : value;
    }
}
