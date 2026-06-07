package com.openzen.heyboxcommunity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int REPLY_PREVIEW_COUNT = 2;
    private static final int REPLY_PAGE_SIZE = 5;

    private int BG;
    private int PANEL;
    private int TEXT;
    private int MUTED;
    private int PINK;
    private int CYAN;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<FeedItem> feed = new ArrayList<>();
    private SessionStore session;
    private ApiClient api;
    private FrameLayout content;
    private LinearLayout bottom;
    private TextView title;
    private TextView leading;
    private TextView action;
    private FeedAdapter feedAdapter;
    private String screen = "feed";
    private String qrKey;
    private boolean pollingQr;
    private boolean feedLoading;
    private int feedOffset;
    private String detailReturn = "feed";
    private String currentLinkId = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!AppIntegrityCheck.isTrusted(this)) {
            TextView blocked = new TextView(this);
            blocked.setText("应用签名校验失败，请安装官方构建版本。");
            blocked.setTextColor(Color.WHITE);
            blocked.setTextSize(16);
            blocked.setGravity(Gravity.CENTER);
            blocked.setPadding(32, 32, 32, 32);
            blocked.setBackgroundColor(Color.rgb(14, 15, 16));
            setContentView(blocked);
            return;
        }
        session = new SessionStore(this);
        applyPalette();
        Compat.colorSystemBars(getWindow(), BG);
        getWindow().getDecorView().setSystemUiVisibility(Compat.fullscreenFlags());
        api = new ApiClient(session);
        buildShell();
        if (session.isLoggedIn()) {
            EmojiStore.load(api, () -> {
                if ("feed".equals(screen) && feedAdapter != null) feedAdapter.notifyDataSetChanged();
            });
            showFeed();
        }
        else showLogin();
    }

    private void buildShell() {
        LinearLayout root = vertical(BG);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), 0, dp(6), 0);
        bar.setBackgroundColor(session.darkMode()
                ? Color.rgb(20, 20, 20) : Color.rgb(232, 234, 237));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(38)));

        leading = icon("");
        setIcon(leading, R.drawable.ic_arrow_back, TEXT, 20);
        leading.setVisibility(View.INVISIBLE);
        bar.addView(leading, new LinearLayout.LayoutParams(dp(36), dp(38)));

        title = text("heybox Lite", 15, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(38), 1));

        TextView clock = text("", 12, MUTED);
        clock.setGravity(Gravity.CENTER);
        updateClock(clock);
        bar.addView(clock, new LinearLayout.LayoutParams(dp(48), dp(38)));

        action = icon("");
        setIcon(action, R.drawable.ic_refresh, TEXT, 19);
        bar.addView(action, new LinearLayout.LayoutParams(dp(38), dp(38)));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setBackgroundColor(session.darkMode()
                ? Color.rgb(20, 20, 20) : Color.rgb(232, 234, 237));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(42)));
        addNav("社区", "feed", R.drawable.ic_home, this::showFeed);
        addNav("我的", "profile", R.drawable.ic_person, this::showProfile);
        setContentView(root);
    }

    private void updateClock(TextView clock) {
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        handler.postDelayed(() -> {
            if (!isFinishing()) updateClock(clock);
        }, 30_000);
    }

    private void applyPalette() {
        if (session.darkMode()) {
            BG = Color.rgb(14, 15, 16);
            PANEL = Color.rgb(29, 31, 33);
            TEXT = Color.rgb(241, 243, 245);
            MUTED = Color.rgb(157, 163, 169);
            CYAN = Color.rgb(206, 211, 216);
            PINK = parseAccent(Color.rgb(188, 193, 198));
        } else {
            BG = Color.rgb(246, 247, 249);
            PANEL = Color.WHITE;
            TEXT = Color.rgb(30, 32, 35);
            MUTED = Color.rgb(105, 110, 116);
            CYAN = Color.rgb(35, 125, 178);
            PINK = parseAccent(Color.rgb(74, 80, 86));
        }
    }

    private int parseAccent(int fallback) {
        try {
            String value = session.accentColor();
            return value.isEmpty() ? fallback : Color.parseColor(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void addNav(String label, String key, int drawable, Runnable click) {
        TextView item = text(label, 10, MUTED);
        item.setGravity(Gravity.CENTER);
        setIcon(item, drawable, MUTED, 17);
        item.setCompoundDrawablePadding(dp(1));
        item.setTag(key);
        item.setOnClickListener(view -> click.run());
        bottom.addView(item, new LinearLayout.LayoutParams(0, dp(42), 1));
    }

    private void activate(String key) {
        stopQrPolling();
        screen = key;
        bottom.setVisibility(View.VISIBLE);
        leading.setVisibility(View.INVISIBLE);
        for (int i = 0; i < bottom.getChildCount(); i++) {
            TextView item = (TextView) bottom.getChildAt(i);
            boolean active = key.equals(item.getTag());
            item.setTextColor(active ? PINK : MUTED);
            item.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
            Drawable icon = item.getCompoundDrawables()[1];
            Compat.tintDrawable(icon, active ? PINK : MUTED);
        }
    }

    private void showLogin() {
        stopQrPolling();
        screen = "login";
        bottom.setVisibility(View.GONE);
        leading.setVisibility(View.INVISIBLE);
        action.setVisibility(View.INVISIBLE);
        title.setText("扫码登录");
        content.removeAllViews();

        LinearLayout page = vertical(BG);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView heading = text("heybox Lite", 20, TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        page.addView(heading, new LinearLayout.LayoutParams(-1, dp(34)));

        TextView status = text("正在获取二维码…", 12, MUTED);
        status.setGravity(Gravity.CENTER);
        status.setTag("qr_status");
        page.addView(status, new LinearLayout.LayoutParams(-1, dp(28)));

        int qrSize = Math.round(getResources().getDisplayMetrics().widthPixels * 0.54f);
        ImageView qr = new ImageView(this);
        qr.setTag("qr_image");
        qr.setPadding(dp(7), dp(7), dp(7), dp(7));
        qr.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.topMargin = dp(4);
        page.addView(qr, qrParams);

        TextView hint = text("请使用小黑盒 App 扫码", 12, CYAN);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, dp(34));
        hintParams.topMargin = dp(4);
        page.addView(hint, hintParams);

        Button retry = button("重新获取", R.drawable.ic_refresh);
        retry.setOnClickListener(view -> requestQr());
        page.addView(retry, new LinearLayout.LayoutParams(dp(150), dp(38)));
        content.addView(page, match());
        requestQr();
    }

    private void requestQr() {
        stopQrPolling();
        setQrStatus("正在获取二维码…", MUTED);
        Map<String, String> params = new HashMap<>();
        params.put("app", "web");
        params.put(SecureStrings.heyboxId(), "");
        api.get(EndpointProvider.qrUrl(), params, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                JSONObject result = body.optJSONObject("result");
                String url = result == null ? "" : result.optString("qr_url");
                if (url.isEmpty()) {
                    setQrStatus("二维码获取失败", PINK);
                    return;
                }
                qrKey = Uri.parse(url).getQueryParameter("qr");
                if (qrKey == null || qrKey.isEmpty()) qrKey = result.optString("qr");
                try {
                    int size = Math.round(getResources().getDisplayMetrics().widthPixels * 0.50f);
                    ImageView image = content.findViewWithTag("qr_image");
                    if (image != null) image.setImageBitmap(QrCode.create(url, size));
                    setQrStatus("等待扫码", CYAN);
                    pollingQr = true;
                    handler.postDelayed(MainActivity.this::pollQr, 900);
                } catch (Exception error) {
                    setQrStatus("二维码生成失败", PINK);
                }
            }

            @Override public void onError(String message) {
                setQrStatus("获取失败：" + message, PINK);
            }
        });
    }

    private void pollQr() {
        if (!pollingQr || qrKey == null || qrKey.isEmpty()) return;
        Map<String, String> params = new HashMap<>();
        params.put("qr", qrKey);
        params.put("app", "web");
        api.get(EndpointProvider.qrState(), params, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                if (!pollingQr) return;
                JSONObject result = body.optJSONObject("result");
                String state = result == null ? "" : result.optString("error");
                if ("ok".equals(state)) {
                    session.saveLogin(result);
                    pollingQr = false;
                    feed.clear();
                    toast("登录成功");
                    EmojiStore.load(api, () -> {});
                    showFeed();
                    return;
                }
                if ("ready".equals(state)) setQrStatus("已扫码，请在手机确认", PINK);
                else if ("cancel".equals(state)) {
                    pollingQr = false;
                    setQrStatus("登录已取消，请重新获取", PINK);
                    return;
                } else setQrStatus("等待扫码", CYAN);
                handler.postDelayed(MainActivity.this::pollQr, 1300);
            }

            @Override public void onError(String message) {
                if (!pollingQr) return;
                setQrStatus("网络波动，正在重试", MUTED);
                handler.postDelayed(MainActivity.this::pollQr, 2200);
            }
        });
    }

    private void setQrStatus(String value, int color) {
        TextView view = content.findViewWithTag("qr_status");
        if (view != null) {
            view.setText(value);
            view.setTextColor(color);
        }
    }

    private void stopQrPolling() {
        pollingQr = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void showFeed() {
        if (!session.isLoggedIn()) {
            showLogin();
            return;
        }
        activate("feed");
        title.setText("社区");
        action.setText("");
        setIcon(action, R.drawable.ic_refresh, TEXT, 19);
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(view -> loadFeed(true));
        content.removeAllViews();

        ListView list = new ListView(this);
        list.setBackgroundColor(BG);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(2));
        list.setSelector(new ColorDrawable(session.darkMode()
                ? Color.rgb(50, 50, 50) : Color.rgb(225, 228, 232)));
        feedAdapter = new FeedAdapter(this, feed, session.noImage(),
                session.uiScale() / 100f, session.textScale() / 100f,
                session.darkMode(), this::showDetail);
        list.setAdapter(feedAdapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int state) {}
            @Override public void onScroll(AbsListView view, int first, int visible, int total) {
                if (total > 0 && first + visible >= total - 2) loadFeed(false);
            }
        });
        content.addView(list, match());
        if (feed.isEmpty()) loadFeed(true);
    }

    private void loadFeed(boolean reset) {
        if (feedLoading) return;
        feedLoading = true;
        if (reset) {
            feedOffset = 0;
            feed.clear();
            if (feedAdapter != null) feedAdapter.notifyDataSetChanged();
            showLoading();
        }
        Map<String, String> params = new HashMap<>();
        params.put("offset", String.valueOf(feedOffset));
        params.put("pull", reset ? "1" : "0");
        api.get(EndpointProvider.feeds(), params, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                feedLoading = false;
                hideLoading();
                JSONObject result = body.optJSONObject("result");
                JSONArray links = result == null ? null : result.optJSONArray("links");
                int added = 0;
                if (links != null) {
                    for (int i = 0; i < links.length(); i++) {
                        JSONObject item = links.optJSONObject(i);
                        if (item != null) {
                            feed.add(FeedItem.from(item));
                            added++;
                        }
                    }
                }
                feedOffset += Math.max(added, 30);
                if (feedAdapter != null) feedAdapter.notifyDataSetChanged();
                if (feed.isEmpty()) showMessage("暂时没有获取到社区内容");
            }

            @Override public void onError(String message) {
                feedLoading = false;
                hideLoading();
                if (feed.isEmpty()) showMessage("加载失败\n" + message);
                else toast(message);
            }
        });
    }

    private void showDetail(FeedItem item) {
        stopQrPolling();
        detailReturn = screen;
        screen = "detail";
        currentLinkId = item.id;
        bottom.setVisibility(View.GONE);
        leading.setVisibility(View.VISIBLE);
        leading.setOnClickListener(view -> showFeed());
        title.setText("帖子详情");
        action.setVisibility(View.INVISIBLE);
        content.removeAllViews();
        showLoading();
        Map<String, String> params = new HashMap<>();
        params.put("link_id", item.id);
        params.put("page", "1");
        params.put("limit", "30");
        params.put("index", "1");
        params.put("is_first", "1");
        params.put("owner_only", "0");
        api.get(EndpointProvider.linkTree(), params, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                hideLoading();
                renderDetail(body, item);
            }

            @Override public void onError(String message) {
                hideLoading();
                showMessage("详情加载失败\n" + message);
            }
        });
    }

    private void renderDetail(JSONObject body, FeedItem fallback) {
        JSONObject result = body.optJSONObject("result");
        JSONObject link = result == null ? null : result.optJSONObject("link");
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout page = vertical(BG);
        int pagePadding = dp(session.pagePadding());
        page.setPadding(pagePadding, dp(4), pagePadding, dp(16));
        scroll.addView(page);

        LinearLayout article = vertical(BG);
        article.setPadding(dp(4), dp(6), dp(4), dp(10));
        String heading = link == null ? fallback.title : link.optString("title", fallback.title);
        TextView headline = text(heading, 17, TEXT);
        headline.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        article.addView(headline);
        JSONObject user = link == null ? null : link.optJSONObject("user");
        String author = user == null ? fallback.author : user.optString("username", fallback.author);
        addTop(article, text(author + "  " + formatTime(
                link == null ? 0 : link.optLong("create_at")), 11, MUTED), 6);
        String value = link == null ? fallback.description
                : first(link.optString("text"), link.optString("description"));
        JSONArray fallbackImages = link == null ? null : link.optJSONArray("imgs");
        addRichContent(article, value, fallbackImages);
        page.addView(article);

        View section = new View(this);
        section.setBackgroundColor(session.darkMode()
                ? Color.rgb(22, 22, 22) : Color.rgb(225, 228, 232));
        addTop(page, section, 2);
        section.getLayoutParams().height = dp(6);

        TextView commentTitle = text("评论", 14, TEXT);
        commentTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        commentTitle.setPadding(dp(4), dp(8), dp(4), dp(5));
        page.addView(commentTitle);
        LinearLayout comments = vertical(BG);
        page.addView(comments);
        int count = addComments(comments, result == null ? null : result.optJSONArray("comments"));
        if (count == 0) {
            TextView empty = text("暂无评论", 13, MUTED);
            empty.setPadding(dp(4), dp(8), dp(4), dp(12));
            comments.addView(empty);
        }
        content.addView(scroll, match());
    }

    private void addRichContent(LinearLayout parent, String source, JSONArray fallbackImages) {
        List<RichContent.Block> blocks = RichContent.parse(source, fallbackImages);
        if (blocks.isEmpty()) {
            addTop(parent, text("正文为空", 13, MUTED), 9);
            return;
        }
        int imageCount = 0;
        for (RichContent.Block block : blocks) {
            if (block.image) {
                if (session.noImage() || imageCount >= 12) continue;
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                Compat.setBackground(image, round(session.darkMode()
                        ? Color.rgb(28, 30, 32) : Color.rgb(235, 237, 240), 7));
                Compat.clipToOutline(image);
                LinearLayout.LayoutParams imageParams =
                        new LinearLayout.LayoutParams(-1, dp(150));
                imageParams.topMargin = dp(8);
                parent.addView(image, imageParams);
                if (session.originalImages()) ImageLoader.intoOriginal(image, block.value, 1440);
                else ImageLoader.into(image, block.value, 1080);
                image.setOnClickListener(view -> {
                    Intent intent = new Intent(this, ImageViewerActivity.class);
                    intent.putExtra(ImageViewerActivity.EXTRA_URL, block.value);
                    startActivity(intent);
                });
                imageCount++;
            } else {
                TextView bodyText = text(block.value, 14, TEXT);
                bodyText.setLineSpacing(0, 1.22f);
                bodyText.setTextIsSelectable(true);
                EmojiRenderer.set(bodyText, block.value);
                addTop(parent, bodyText, 9);
            }
        }
    }

    private int addComments(LinearLayout page, JSONArray groups) {
        if (groups == null) return 0;
        List<JSONObject> threads = new ArrayList<>();
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            if (group != null) threads.add(group);
        }
        Collections.sort(threads,
                (a, b) -> Integer.compare(threadLikes(b), threadLikes(a)));
        int count = 0;
        for (JSONObject group : threads) {
            JSONArray comments = group == null ? null : group.optJSONArray("comment");
            JSONObject root = comments == null ? group : comments.optJSONObject(0);
            if (root == null) continue;

            LinearLayout threadCard = card();
            threadCard.setPadding(dp(8), dp(5), dp(8), dp(7));
            addComment(threadCard, root, false);

            List<JSONObject> replies = new ArrayList<>();
            if (comments != null) {
                for (int j = 1; j < comments.length(); j++) {
                    JSONObject reply = comments.optJSONObject(j);
                    if (reply != null) replies.add(reply);
                }
            }
            Collections.sort(replies,
                    (a, b) -> Long.compare(commentTime(a), commentTime(b)));
            int expected = Math.max(root.optInt("child_num"),
                    group == null ? 0 : group.optInt("child_num"));
            if (!replies.isEmpty() || expected > 0) {
                LinearLayout replySection = new LinearLayout(this);
                replySection.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
                sectionParams.topMargin = dp(3);
                threadCard.addView(replySection, sectionParams);

                View rail = new View(this);
                rail.setBackgroundColor(session.darkMode()
                        ? Color.rgb(75, 79, 83) : Color.rgb(205, 209, 213));
                LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(2), -1);
                railParams.leftMargin = dp(14);
                railParams.rightMargin = dp(8);
                replySection.addView(rail, railParams);

                LinearLayout replyList = new LinearLayout(this);
                replyList.setOrientation(LinearLayout.VERTICAL);
                replySection.addView(replyList, new LinearLayout.LayoutParams(0, -2, 1));
                int initial = Math.min(REPLY_PREVIEW_COUNT, replies.size());
                boolean allLoaded = expected <= replies.size();
                renderReplies(replyList, root, replies, expected, initial, allLoaded);
            }
            addTop(page, threadCard, count == 0 ? 0 : 7);
            animateIn(threadCard);
            count += 1 + replies.size();
        }
        return count;
    }

    private void renderReplies(LinearLayout parent, JSONObject root,
                               List<JSONObject> replies, int expected,
                               int visibleCount, boolean allLoaded) {
        parent.removeAllViews();
        int total = Math.max(expected, replies.size());
        int shown = Math.min(Math.max(0, visibleCount), replies.size());
        TextView heading = text(total + " 条回复", 11, MUTED);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setPadding(0, dp(3), 0, dp(2));
        parent.addView(heading);
        for (int i = 0; i < shown; i++) addComment(parent, replies.get(i), true);

        if (shown < replies.size()) {
            int nextCount = Math.min(REPLY_PAGE_SIZE, replies.size() - shown);
            TextView more = replyControl("再展开 " + nextCount + " 条", R.drawable.ic_expand);
            addTop(parent, more, 4);
            more.getLayoutParams().height = dp(36);
            more.setOnClickListener(view -> {
                renderReplies(parent, root, replies, total,
                        shown + REPLY_PAGE_SIZE, allLoaded);
                animateIn(parent);
            });
        } else if (!allLoaded && total > replies.size()) {
            TextView more = replyControl("再展开 5 条", R.drawable.ic_expand);
            addTop(parent, more, 4);
            more.getLayoutParams().height = dp(36);
            more.setOnClickListener(view ->
                    loadSubComments(parent, root, replies, total, shown));
        }
        if (shown > REPLY_PREVIEW_COUNT) {
            TextView collapse = replyControl("收起回复", R.drawable.ic_arrow_back);
            addTop(parent, collapse, 3);
            collapse.getLayoutParams().height = dp(34);
            collapse.setOnClickListener(view ->
                    renderReplies(parent, root, replies, total,
                            Math.min(REPLY_PREVIEW_COUNT, replies.size()), allLoaded));
        }
    }

    private TextView replyControl(String label, int icon) {
        TextView control = text(label, 12, TEXT);
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setPadding(dp(8), 0, dp(8), 0);
        Compat.setBackground(control, round(session.darkMode()
                ? Color.rgb(42, 45, 48) : Color.rgb(237, 239, 241), 7));
        setLeftIcon(control, icon, MUTED, 16);
        return control;
    }

    private void loadSubComments(LinearLayout target, JSONObject root,
                                 List<JSONObject> preview, int expected, int shown) {
        TextView loading = text("正在加载更多回复…", 11, MUTED);
        loading.setPadding(0, dp(8), 0, dp(8));
        target.addView(loading);
        Map<String, String> params = new HashMap<>();
        String id = commentId(root);
        params.put("comment_id", id);
        params.put("commentid", id);
        params.put("root_comment_id", id);
        params.put("link_id", currentLinkId);
        params.put("offset", String.valueOf(preview.size()));
        params.put("page", String.valueOf(preview.size() / 50 + 1));
        params.put("limit", "50");
        api.get(EndpointProvider.subComments(), params, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                List<JSONObject> replies = extractSubComments(body, id);
                if (replies.isEmpty()) {
                    target.removeView(loading);
                    toast("没有获取到更多回复");
                    return;
                }
                List<JSONObject> merged = mergeReplies(preview, replies);
                Collections.sort(merged,
                        (a, b) -> Long.compare(commentTime(a), commentTime(b)));
                int total = Math.max(expected, merged.size());
                renderReplies(target, root, merged, total,
                        shown + REPLY_PAGE_SIZE, merged.size() >= total);
                animateIn(target);
            }

            @Override public void onError(String message) {
                target.removeView(loading);
                toast("回复加载失败：" + message);
            }
        });
    }

    private List<JSONObject> mergeReplies(List<JSONObject> first, List<JSONObject> second) {
        List<JSONObject> merged = new ArrayList<>(first);
        for (JSONObject candidate : second) {
            String id = commentId(candidate);
            boolean duplicate = false;
            for (JSONObject existing : merged) {
                if (!id.isEmpty() && id.equals(commentId(existing))) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) merged.add(candidate);
        }
        return merged;
    }

    private List<JSONObject> extractSubComments(JSONObject body, String rootId) {
        List<JSONObject> values = new ArrayList<>();
        JSONObject result = body.optJSONObject("result");
        if (result == null) return values;
        JSONArray array = result.optJSONArray("comments");
        if (array == null) array = result.optJSONArray("comment");
        if (array == null) array = result.optJSONArray("list");
        if (array == null) array = result.optJSONArray("sub_comments");
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            JSONArray nested = item.optJSONArray("comment");
            if (nested != null) {
                for (int j = 0; j < nested.length(); j++) {
                    JSONObject reply = nested.optJSONObject(j);
                    if (reply != null && !rootId.equals(commentId(reply))) values.add(reply);
                }
            } else if (!rootId.equals(commentId(item))) {
                values.add(item);
            }
        }
        return values;
    }

    private int threadLikes(JSONObject group) {
        JSONArray comments = group.optJSONArray("comment");
        JSONObject root = comments == null ? group : comments.optJSONObject(0);
        return commentLikes(root == null ? group : root);
    }

    private int commentLikes(JSONObject comment) {
        return comment.optInt("comment_award_num",
                comment.optInt("award_num", comment.optInt("up")));
    }

    private long commentTime(JSONObject comment) {
        return comment.optLong("create_at", comment.optLong("create_time"));
    }

    private String commentId(JSONObject comment) {
        return first(comment.optString("commentid"),
                first(comment.optString("comment_id"), comment.optString("id")));
    }

    private void addComment(LinearLayout page, JSONObject comment, boolean reply) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(reply ? 5 : 7), 0, dp(reply ? 6 : 8));
        JSONObject user = comment.optJSONObject("user");
        String author = user == null ? "匿名用户" : user.optString("username", "匿名用户");

        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Compat.setBackground(avatar, round(session.darkMode()
                ? Color.rgb(50, 53, 56) : Color.rgb(226, 229, 232), 20));
        Compat.clipToOutline(avatar);
        int avatarSize = reply ? 24 : 30;
        row.addView(avatar, new LinearLayout.LayoutParams(dp(avatarSize), dp(avatarSize)));
        String avatarUrl = user == null ? "" : user.optString("avatar");
        if (!session.noImage() && !avatarUrl.isEmpty()) ImageLoader.into(avatar, avatarUrl, 96);

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(0, -2, 1);
        blockParams.leftMargin = dp(reply ? 7 : 8);
        row.addView(block, blockParams);
        String target = replyTarget(comment);
        String meta = author + (target.isEmpty() ? "" : "  回复 @" + target);
        long created = commentTime(comment);
        if (created > 0) meta += "  ·  " + formatTime(created);
        TextView metaView = text(meta, reply ? 10 : 11, reply ? MUTED : TEXT);
        if (!reply) metaView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        block.addView(metaView);
        String rawComment = first(comment.optString("text"), comment.optString("content"));
        String visibleComment = RichContent.plainText(rawComment);
        TextView value = text(visibleComment, 13, TEXT);
        value.setLineSpacing(0, 1.18f);
        EmojiRenderer.set(value, visibleComment);
        addTop(block, value, 4);
        String commentImage = commentImage(comment);
        if (!session.noImage() && !commentImage.isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Compat.setBackground(image, round(session.darkMode()
                    ? Color.rgb(39, 42, 45) : Color.rgb(236, 238, 240), 6));
            Compat.clipToOutline(image);
            LinearLayout.LayoutParams imageParams =
                    new LinearLayout.LayoutParams(-1, dp(reply ? 92 : 115));
            imageParams.topMargin = dp(6);
            block.addView(image, imageParams);
            if (session.originalImages()) ImageLoader.intoOriginal(image, commentImage, 1080);
            else ImageLoader.into(image, commentImage, 720);
            image.setOnClickListener(view -> {
                Intent intent = new Intent(this, ImageViewerActivity.class);
                intent.putExtra(ImageViewerActivity.EXTRA_URL, commentImage);
                startActivity(intent);
            });
        }

        if (!reply) {
            TextView likes = text(String.valueOf(commentLikes(comment)), 10, MUTED);
            setLeftIcon(likes, R.drawable.ic_heart, MUTED, 13);
            likes.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams likeParams = new LinearLayout.LayoutParams(dp(44), dp(24));
            likeParams.leftMargin = dp(3);
            row.addView(likes, likeParams);
        }
        page.addView(row);

        if (reply) {
            View divider = new View(this);
            divider.setBackgroundColor(session.darkMode()
                    ? Color.rgb(45, 48, 51) : Color.rgb(226, 229, 232));
            page.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
    }

    private String commentImage(JSONObject comment) {
        JSONArray images = comment.optJSONArray("imgs");
        if (images == null || images.length() == 0) return "";
        JSONObject object = images.optJSONObject(0);
        if (object != null) {
            return first(object.optString("url"),
                    first(object.optString("src"), object.optString("original")));
        }
        return images.optString(0);
    }

    private String replyTarget(JSONObject comment) {
        JSONObject target = comment.optJSONObject("to_user");
        if (target == null) target = comment.optJSONObject("reply_user");
        if (target == null) target = comment.optJSONObject("target_user");
        return target == null ? "" : target.optString("username", target.optString("nickname"));
    }

    private void showProfile() {
        if (!session.isLoggedIn()) {
            showLogin();
            return;
        }
        activate("profile");
        title.setText("我的");
        action.setText("");
        setIcon(action, R.drawable.ic_refresh, TEXT, 19);
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(view -> showProfile());
        content.removeAllViews();
        showLoading();
        api.get(EndpointProvider.profile(),
                Collections.singletonMap(SecureStrings.userid(), session.userId()),
                new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                hideLoading();
                renderProfile(body);
            }

            @Override public void onError(String message) {
                hideLoading();
                toast("个人资料加载失败：" + message);
                renderProfile(new JSONObject());
            }
        });
    }

    private void renderProfile(JSONObject body) {
        JSONObject result = body.optJSONObject("result");
        JSONObject account = result == null ? null : result.optJSONObject("account_detail");
        if (account == null && result != null) account = result.optJSONObject("user");
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = vertical(BG);
        page.setPadding(dp(8), dp(8), dp(8), dp(12));
        scroll.addView(page);
        LinearLayout profile = card();

        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        profile.addView(avatar, new LinearLayout.LayoutParams(dp(66), dp(66)));
        String avatarUrl = account == null ? session.avatar() : account.optString("avatar", session.avatar());
        if (!session.noImage()) ImageLoader.into(avatar, avatarUrl, 160);

        String nameValue = account == null ? session.userName() : account.optString("username", session.userName());
        TextView name = text(nameValue, 20, TEXT);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addTop(profile, name, 10);
        profile.addView(text("ID " + session.userId(), 11, MUTED));
        String signature = account == null ? "" : account.optString("signature");
        if (!signature.isEmpty()) addTop(profile, text(signature, 13, TEXT), 9);
        JSONObject bbs = account == null ? null : account.optJSONObject("bbs_info");
        if (bbs != null) {
            addTop(profile, text("关注 " + bbs.optInt("follow_num")
                    + "   粉丝 " + bbs.optInt("fan_num")
                    + "   获赞 " + bbs.optInt("up_num"), 12, CYAN), 10);
        }
        page.addView(profile);
        animateIn(profile);

        LinearLayout shortcuts = new LinearLayout(this);
        Button favorites = button("收藏", R.drawable.ic_bookmark);
        favorites.setOnClickListener(view -> showFavorites());
        shortcuts.addView(favorites, new LinearLayout.LayoutParams(0, dp(40), 1));
        Button history = button("历史", R.drawable.ic_history);
        history.setOnClickListener(view -> {
            Map<String, String> params = new HashMap<>();
            params.put("type", "all");
            params.put("dw", "636");
            params.put("no_more", "false");
            showSavedList("浏览历史", EndpointProvider.history(), params);
        });
        LinearLayout.LayoutParams historyParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        historyParams.leftMargin = dp(6);
        shortcuts.addView(history, historyParams);
        addTop(page, shortcuts, 8);

        addSettings(page);
        content.addView(scroll, match());
    }

    private void showFavorites() {
        showLoading();
        Map<String, String> folderParams = new HashMap<>();
        folderParams.put(SecureStrings.userid(), session.userId());
        folderParams.put("enable_new_style_collect", "1");
        folderParams.put("x_os_type", "Windows");
        folderParams.put("device_info", "Edge");
        api.get(EndpointProvider.favoriteFolders(), folderParams, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                JSONObject result = body.optJSONObject("result");
                JSONArray folders = result == null ? null : result.optJSONArray("folders");
                if (folders == null) folders = body.optJSONArray("folders");
                JSONObject firstFolder = folders == null ? null : folders.optJSONObject(0);
                if (firstFolder == null) {
                    hideLoading();
                    showMessage("收藏夹里暂时没有内容");
                    return;
                }
                Map<String, String> params = new HashMap<>();
                params.put("folder_id", firstFolder.optString("id",
                        firstFolder.optString("folder_id")));
                params.put("enable_new_style_collect", "1");
                params.put("dw", "604");
                params.put("no_more", "false");
                showSavedList("我的收藏", EndpointProvider.favoriteLinks(), params);
            }

            @Override public void onError(String message) {
                hideLoading();
                toast("收藏夹加载失败：" + message);
            }
        });
    }

    private void showSavedList(String pageTitle, String path, Map<String, String> params) {
        stopQrPolling();
        screen = "saved";
        bottom.setVisibility(View.GONE);
        leading.setVisibility(View.VISIBLE);
        leading.setOnClickListener(view -> showProfile());
        title.setText(pageTitle);
        action.setVisibility(View.INVISIBLE);
        content.removeAllViews();
        showLoading();
        params.put("offset", "0");
        params.put("limit", "20");
        params.put(SecureStrings.userid(), session.userId());
        params.put("x_os_type", "Windows");
        params.put("device_info", "Edge");
        api.get(path, params, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                hideLoading();
                JSONObject result = body.optJSONObject("result");
                JSONArray links = findLinks(result == null ? body : result);
                List<FeedItem> items = new ArrayList<>();
                if (links != null) {
                    for (int i = 0; i < links.length(); i++) {
                        JSONObject item = links.optJSONObject(i);
                        if (item != null) {
                            JSONObject value = unwrapSavedItem(item);
                            if (value != null) items.add(FeedItem.from(value));
                        }
                    }
                }
                ListView list = new ListView(MainActivity.this);
                list.setBackgroundColor(BG);
                list.setDivider(new ColorDrawable(Color.TRANSPARENT));
                list.setDividerHeight(dp(2));
                list.setAdapter(new FeedAdapter(MainActivity.this, items, session.noImage(),
                        session.uiScale() / 100f, session.textScale() / 100f,
                        session.darkMode(), MainActivity.this::showDetail));
                content.addView(list, match());
                if (items.isEmpty()) showMessage("这里暂时没有内容");
            }

            @Override public void onError(String message) {
                hideLoading();
                showMessage(pageTitle + "加载失败\n" + message);
            }
        });
    }

    private JSONArray findLinks(JSONObject result) {
        if (result == null) return null;
        JSONArray links = result.optJSONArray("links");
        if (links == null) links = result.optJSONArray("list");
        if (links == null) links = result.optJSONArray("history_visit");
        if (links == null) links = result.optJSONArray("visits");
        if (links == null) links = result.optJSONArray("history");
        if (links == null) links = result.optJSONArray("data");
        return links;
    }

    private JSONObject unwrapSavedItem(JSONObject item) {
        JSONObject current = item;
        for (int depth = 0; depth < 4; depth++) {
            if (!current.optString("linkid",
                    current.optString("link_id")).isEmpty()) return current;
            JSONObject next = current.optJSONObject("link");
            if (next == null) next = current.optJSONObject("content");
            if (next == null) next = current.optJSONObject("data");
            if (next == null) next = current.optJSONObject("item");
            if (next == null || next == current) break;
            current = next;
        }
        return current;
    }

    private void addSettings(LinearLayout page) {
        TextView settingsTitle = text("设置", 15, TEXT);
        settingsTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addTop(page, settingsTitle, 12);

        Switch images = new Switch(this);
        images.setText("无图模式");
        images.setTextColor(TEXT);
        images.setTextSize(sp(14));
        images.setChecked(session.noImage());
        Compat.tint(images, PINK);
        images.setOnCheckedChangeListener((button, checked) -> {
            session.setNoImage(checked);
            feed.clear();
        });
        addTop(page, images, 8);

        Switch originals = new Switch(this);
        originals.setText("正文图片显示原图");
        originals.setTextColor(TEXT);
        originals.setTextSize(sp(14));
        originals.setChecked(session.originalImages());
        Compat.tint(originals, PINK);
        originals.setOnCheckedChangeListener((button, checked) ->
                session.setOriginalImages(checked));
        addTop(page, originals, 2);

        Switch theme = new Switch(this);
        theme.setText(session.darkMode() ? "夜间模式" : "白天模式");
        theme.setTextColor(TEXT);
        theme.setTextSize(sp(14));
        theme.setChecked(session.darkMode());
        Compat.tint(theme, PINK);
        theme.setOnCheckedChangeListener((button, checked) -> {
            session.setDarkMode(checked);
            recreate();
        });
        addTop(page, theme, 2);

        LinearLayout display = card();
        TextView displayTitle = text("显示", 15, TEXT);
        displayTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        display.addView(displayTitle);
        EditText uiScale = numberField(display, "界面大小 (%)", session.uiScale());
        EditText textScale = numberField(display, "文字大小 (%)", session.textScale());
        EditText padding = numberField(display, "左右边距 (dp)", session.pagePadding());
        EditText accent = textField(display, "强调色", session.accentColor().isEmpty()
                ? (session.darkMode() ? "#BCC1C6" : "#4A5056") : session.accentColor());
        Button save = button("保存显示设置", R.drawable.ic_settings);
        save.setOnClickListener(view -> {
            Integer ui = parseNumber(uiScale, 70, 160);
            Integer text = parseNumber(textScale, 70, 180);
            Integer pad = parseNumber(padding, 0, 30);
            String accentValue = accent.getText().toString().trim();
            if (ui == null || text == null || pad == null || !validColor(accentValue)) {
                toast("请输入有效数字：界面 70-160，文字 70-180，边距 0-30");
                return;
            }
            session.setUiScale(ui);
            session.setTextScale(text);
            session.setPagePadding(pad);
            session.setAccentColor(accentValue);
            toast("显示设置已保存");
            recreate();
        });
        addTop(display, save, 8);
        addTop(page, display, 8);

        Button clearCache = button("清除图片缓存", R.drawable.ic_trash);
        clearCache.setOnClickListener(view -> {
            int before = ImageLoader.cacheSizeKb();
            ImageLoader.clear();
            toast("已清除图片缓存 " + Math.max(1, before) + " KB");
        });
        addTop(page, clearCache, 8);

        Button login = button(session.isLoggedIn() ? "退出登录" : "二维码登录",
                R.drawable.ic_logout);
        login.setOnClickListener(view -> {
            if (session.isLoggedIn()) {
                session.clearSession();
                feed.clear();
                toast("已退出登录");
            }
            showLogin();
        });
        addTop(page, login, 12);
    }

    private EditText numberField(LinearLayout parent, String label, int current) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(label, 12, TEXT), new LinearLayout.LayoutParams(0, dp(40), 1));
        EditText input = new EditText(this);
        input.setText(String.valueOf(current));
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTextSize(sp(13));
        input.setGravity(Gravity.CENTER);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        Compat.tint(input, PINK);
        row.addView(input, new LinearLayout.LayoutParams(dp(72), dp(40)));
        addTop(parent, row, 3);
        return input;
    }

    private EditText textField(LinearLayout parent, String label, String current) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(label, 12, TEXT), new LinearLayout.LayoutParams(0, dp(40), 1));
        EditText input = new EditText(this);
        input.setText(current);
        input.setTextColor(TEXT);
        input.setTextSize(sp(12));
        input.setGravity(Gravity.CENTER);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        Compat.tint(input, PINK);
        row.addView(input, new LinearLayout.LayoutParams(dp(96), dp(40)));
        addTop(parent, row, 3);
        return input;
    }

    private boolean validColor(String value) {
        try {
            Color.parseColor(value);
            return value.matches("#[0-9a-fA-F]{6}");
        } catch (Exception ignored) {
            return false;
        }
    }

    private Integer parseNumber(EditText input, int min, int max) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            return value < min || value > max ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        if ("detail".equals(screen)) {
            if ("saved".equals(detailReturn)) showProfile();
            else showFeed();
        }
        else if ("saved".equals(screen)) showProfile();
        else if (!"feed".equals(screen) && session.isLoggedIn()) showFeed();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        stopQrPolling();
        handler.removeCallbacksAndMessages(null);
        if (api != null) api.close();
        super.onDestroy();
    }

    private void showLoading() {
        hideLoading();
        ProgressBar progress = new ProgressBar(this);
        progress.setTag("loading");
        Compat.tint(progress, PINK);
        content.addView(progress, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
    }

    private void hideLoading() {
        View loading = content.findViewWithTag("loading");
        if (loading != null) content.removeView(loading);
    }

    private void showMessage(String message) {
        content.removeAllViews();
        TextView view = text(message, 13, MUTED);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(20), dp(20), dp(20), dp(20));
        content.addView(view, match());
    }

    private LinearLayout card() {
        LinearLayout card = vertical(PANEL);
        card.setPadding(dp(10), dp(9), dp(10), dp(9));
        Compat.setBackground(card, round(PANEL, 8));
        return card;
    }

    private TextView icon(String value) {
        TextView view = text(value, 23, TEXT);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(value);
        return view;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(sp(size));
        view.setTextColor(color);
        Compat.setLetterSpacing(view, 0);
        return view;
    }

    private Button button(String value) {
        return button(value, 0);
    }

    private Button button(String value, int iconRes) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(sp(12));
        button.setTextColor(session.darkMode() ? Color.WHITE : Color.rgb(28, 30, 33));
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setGravity(Gravity.CENTER);
        Compat.setBackground(button, round(session.darkMode()
                ? Color.rgb(49, 52, 55) : Color.rgb(230, 233, 236), 8));
        if (iconRes != 0) setLeftIcon(button, iconRes,
                session.darkMode() ? Color.WHITE : Color.rgb(45, 48, 51), 17);
        return button;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private void setIcon(TextView view, int resource, int color, int size) {
        Drawable drawable = Compat.tintedDrawable(this, resource, color);
        if (drawable == null) return;
        drawable.setBounds(0, 0, dp(size), dp(size));
        view.setCompoundDrawables(null, drawable, null, null);
    }

    private void setLeftIcon(TextView view, int resource, int color, int size) {
        Drawable drawable = Compat.tintedDrawable(this, resource, color);
        if (drawable == null) return;
        drawable.setBounds(0, 0, dp(size), dp(size));
        view.setCompoundDrawables(drawable, null, null, null);
        view.setCompoundDrawablePadding(dp(4));
    }

    private void animateIn(View view) {
        view.setAlpha(0f);
        view.setTranslationY(dp(7));
        view.animate().alpha(1f).translationY(0).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private LinearLayout vertical(int color) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    private void addTop(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(view, params);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private int dp(int value) {
        float scale = session == null ? 1f : session.uiScale() / 100f;
        return Math.round(value * getResources().getDisplayMetrics().density * scale);
    }

    private float sp(float value) {
        float scale = session == null ? 1f : session.textScale() / 100f;
        return value * scale;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String formatTime(long seconds) {
        if (seconds <= 0) return "";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date(seconds * 1000L));
    }

    private static String first(String first, String second) {
        return first == null || first.isEmpty() ? (second == null ? "" : second) : first;
    }
}
