package com.openzen.heyboxcommunity;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.text.Editable;
import android.text.TextWatcher;
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
import android.widget.SeekBar;
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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int REPLY_PREVIEW_COUNT = 2;
    private static final int REPLY_PAGE_SIZE = 5;
    private static final String[] THEME_NAMES = {
            "默认蓝", "红色", "粉色", "紫色", "绿色", "青色",
            "橙色", "黄色", "灰色", "深蓝", "黑金", "薄荷绿"
    };
    private static final int[][] THEME_COLORS = {
            {0xFF2479B8, 0xFF73B8E6}, {0xFFC33A3A, 0xFFEF7777},
            {0xFFD85C91, 0xFFF0A4C1}, {0xFF7652B5, 0xFFB79ADF},
            {0xFF278A57, 0xFF71C798}, {0xFF168B91, 0xFF6BC7CB},
            {0xFFD36B24, 0xFFF0A064}, {0xFFC39A16, 0xFFF0CF68},
            {0xFF5D636A, 0xFFAEB4BA}, {0xFF173F76, 0xFF5D8BC4},
            {0xFF171717, 0xFFD0A83E}, {0xFF318B73, 0xFF88D8BF}
    };

    private int BG;
    private int PANEL;
    private int TEXT;
    private int MUTED;
    private int PRIMARY;
    private int SECONDARY;

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
    private ListView feedListView;
    private Button feedRefreshButton;
    private final Map<View, Integer> searchBarHeights = new HashMap<>();
    private final Map<View, Boolean> searchBarStates = new HashMap<>();
    private String screen = "feed";
    private String qrKey;
    private boolean pollingQr;
    private boolean feedLoadingMore;
    private boolean feedRefreshing;
    private int feedOffset;
    private int feedRequestSerial;
    private int feedResetSerial;
    private int feedFirstVisible;
    private int feedFirstTop;
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
        if (session.autoUpdateCheck()) {
            handler.postDelayed(this::checkUpdateOnLaunch, 650);
        }
    }

    private void checkUpdateOnLaunch() {
        UpdateChecker.check(appVersion(), new UpdateChecker.Callback() {
            @Override public void onResult(UpdateChecker.Result result) {
                if (!result.updateAvailable || isFinishing()) return;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("发现新版本 " + result.version)
                        .setMessage("heybox Lite 有新版本可用，是否前往下载？")
                        .setNegativeButton("稍后", null)
                        .setPositiveButton("下载", (dialog, which) -> openUrl(
                                result.downloadUrl.isEmpty()
                                        ? result.releaseUrl : result.downloadUrl))
                        .show();
            }

            @Override public void onError(String message) {
                // 启动检查失败时保持安静，避免网络不稳定影响正常使用。
            }
        });
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
        addNav("搜索", "search", R.drawable.ic_search, this::showSearch);
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
            PRIMARY = parseThemeColor(session.primaryColor(), Color.rgb(36, 121, 184));
            SECONDARY = parseThemeColor(session.secondaryColor(), Color.rgb(115, 184, 230));
            BG = blend(Color.rgb(14, 15, 16), SECONDARY, 0.025f);
            PANEL = blend(Color.rgb(29, 31, 33), PRIMARY, 0.075f);
            TEXT = Color.rgb(241, 243, 245);
            MUTED = Color.rgb(157, 163, 169);
        } else {
            PRIMARY = parseThemeColor(session.primaryColor(), Color.rgb(36, 121, 184));
            SECONDARY = parseThemeColor(session.secondaryColor(), Color.rgb(115, 184, 230));
            BG = blend(Color.rgb(246, 247, 249), SECONDARY, 0.025f);
            PANEL = blend(Color.WHITE, PRIMARY, 0.035f);
            TEXT = Color.rgb(30, 32, 35);
            MUTED = Color.rgb(105, 110, 116);
        }
    }

    private int parseThemeColor(String value, int fallback) {
        try {
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
        int activeColor = TEXT;
        int inactiveColor = MUTED;
        for (int i = 0; i < bottom.getChildCount(); i++) {
            TextView item = (TextView) bottom.getChildAt(i);
            boolean active = key.equals(item.getTag());
            item.setAlpha(active ? 1f : 0.62f);
            item.setTextColor(active ? activeColor : inactiveColor);
            item.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
            Drawable icon = item.getCompoundDrawables()[1];
            Compat.tintDrawable(icon, active ? activeColor : inactiveColor);
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

        TextView hint = text("请使用小黑盒 App 扫码", 12, SECONDARY);
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
                    setQrStatus("二维码获取失败", PRIMARY);
                    return;
                }
                qrKey = Uri.parse(url).getQueryParameter("qr");
                if (qrKey == null || qrKey.isEmpty()) qrKey = result.optString("qr");
                try {
                    int size = Math.round(getResources().getDisplayMetrics().widthPixels * 0.50f);
                    ImageView image = content.findViewWithTag("qr_image");
                    if (image != null) image.setImageBitmap(QrCode.create(url, size));
                    setQrStatus("等待扫码", SECONDARY);
                    pollingQr = true;
                    handler.postDelayed(MainActivity.this::pollQr, 900);
                } catch (Exception error) {
                    setQrStatus("二维码生成失败", PRIMARY);
                }
            }

            @Override public void onError(String message) {
                setQrStatus("获取失败：" + message, PRIMARY);
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
                if ("ready".equals(state)) setQrStatus("已扫码，请在手机确认", PRIMARY);
                else if ("cancel".equals(state)) {
                    pollingQr = false;
                    setQrStatus("登录已取消，请重新获取", PRIMARY);
                    return;
                } else setQrStatus("等待扫码", SECONDARY);
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
        action.setVisibility(View.INVISIBLE);
        action.setOnClickListener(view -> loadFeed(true));
        content.removeAllViews();

        LinearLayout page = vertical(BG);
        page.setPadding(dp(7), dp(5), dp(7), 0);
        LinearLayout refreshRow = new LinearLayout(this);
        refreshRow.setGravity(Gravity.CENTER);
        feedRefreshButton = button("刷新", R.drawable.ic_refresh);
        feedRefreshButton.setOnClickListener(view -> loadFeed(true));
        refreshRow.addView(feedRefreshButton, new LinearLayout.LayoutParams(dp(96), dp(32)));
        page.addView(refreshRow, new LinearLayout.LayoutParams(-1, dp(38)));

        ListView list = new ListView(this);
        feedListView = list;
        list.setBackgroundColor(BG);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(2));
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setSelector(new ColorDrawable(session.darkMode()
                ? Color.rgb(50, 50, 50) : Color.rgb(225, 228, 232)));
        feedAdapter = new FeedAdapter(this, feed, session.noImage(),
                session.uiScale() / 100f, session.textScale() / 100f,
                session.darkMode(), PRIMARY, SECONDARY, this::showDetail);
        list.setAdapter(feedAdapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int state) {}
            @Override public void onScroll(AbsListView view, int first, int visible, int total) {
                if (total > 0 && first + visible >= total - 2) loadFeed(false);
            }
        });
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(page, match());
        restoreFeedScroll();
        if (feed.isEmpty()) loadFeed(true);
    }

    private boolean loadFeed(boolean reset) {
        if (reset) {
            if (feedRefreshing) return false;
            feedRefreshing = true;
        } else {
            if (feedRefreshing || feedLoadingMore) return false;
            feedLoadingMore = true;
        }
        final int requestSerial = ++feedRequestSerial;
        if (reset) feedResetSerial = requestSerial;
        final List<FeedItem> previous = reset ? new ArrayList<>(feed) : null;
        if (reset) {
            feedOffset = 0;
            setFeedRefreshBusy(true);
            if (feed.isEmpty()) showLoading();
        }
        Map<String, String> params = new HashMap<>();
        params.put("offset", String.valueOf(feedOffset));
        params.put("pull", reset ? "1" : "0");
        api.get(EndpointProvider.feeds(), params, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                if (reset) feedRefreshing = false;
                else feedLoadingMore = false;
                if (!reset && requestSerial < feedResetSerial) return;
                hideLoading();
                JSONObject result = body.optJSONObject("result");
                JSONArray links = result == null ? null : result.optJSONArray("links");
                List<FeedItem> fresh = new ArrayList<>();
                int added = 0;
                if (links != null) {
                    for (int i = 0; i < links.length(); i++) {
                        JSONObject item = links.optJSONObject(i);
                        if (item != null) {
                            fresh.add(FeedItem.from(item));
                            added++;
                        }
                    }
                }
                if (reset && fresh.isEmpty() && previous != null && !previous.isEmpty()) {
                    toast("没有获取到新内容，已保留原列表");
                } else {
                    if (reset) feed.clear();
                    feed.addAll(fresh);
                }
                feedOffset += Math.max(added, 30);
                setFeedRefreshBusy(false);
                if (feedAdapter != null) feedAdapter.notifyDataSetChanged();
                if (feed.isEmpty()) showMessage("暂时没有获取到社区内容");
            }

            @Override public void onError(String message) {
                if (reset) feedRefreshing = false;
                else feedLoadingMore = false;
                if (!reset && requestSerial < feedResetSerial) return;
                hideLoading();
                setFeedRefreshBusy(false);
                if (reset && previous != null && feed.isEmpty()) feed.addAll(previous);
                if (feedAdapter != null) feedAdapter.notifyDataSetChanged();
                if (feed.isEmpty()) showMessage("加载失败\n" + message);
                else toast("刷新失败，已保留原内容");
            }
        });
        return true;
    }

    private void showSearch() {
        if (!session.isLoggedIn()) {
            showLogin();
            return;
        }
        ensureEmojiCatalog(() -> {});
        activate("search");
        title.setText("搜索");
        action.setVisibility(View.INVISIBLE);
        content.removeAllViews();

        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(BG);
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        EditText input = new EditText(this);
        input.setHint("搜索帖子");
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setSingleLine(true);
        input.setTextSize(sp(13));
        Compat.tint(input, PRIMARY);
        searchBar.addView(input, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button submit = button("搜索", R.drawable.ic_search);
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(dp(82), dp(38));
        submitParams.leftMargin = dp(5);
        searchBar.addView(submit, submitParams);

        LinearLayout recent = vertical(BG);
        FrameLayout results = new FrameLayout(this);
        results.setTag(searchBar);
        page.addView(results, match());
        TextView hint = text("输入关键词搜索社区帖子", 13, MUTED);
        hint.setGravity(Gravity.CENTER);
        results.addView(hint, match());
        FrameLayout.LayoutParams recentParams = new FrameLayout.LayoutParams(-1, -2);
        recentParams.leftMargin = dp(7);
        recentParams.rightMargin = dp(7);
        recentParams.topMargin = dp(54);
        page.addView(recent, recentParams);
        FrameLayout.LayoutParams searchParams =
                new FrameLayout.LayoutParams(-1, dp(42), Gravity.TOP);
        searchParams.leftMargin = dp(7);
        searchParams.rightMargin = dp(7);
        searchParams.topMargin = dp(6);
        page.addView(searchBar, searchParams);
        prepareSearchBar(searchBar, dp(42));

        Runnable search = () -> {
            String keyword = input.getText().toString().trim();
            if (keyword.isEmpty()) {
                toast("请输入搜索关键词");
                return;
            }
            session.addSearchHistory(keyword);
            recent.setVisibility(View.GONE);
            performSearch(keyword, results, searchBar);
        };
        submit.setOnClickListener(view -> search.run());
        input.setOnEditorActionListener((view, actionId, event) -> {
            search.run();
            return true;
        });
        renderSearchHistory(recent, input, results);
        content.addView(page, match());
    }

    private void renderSearchHistory(LinearLayout parent, EditText input, FrameLayout results) {
        parent.removeAllViews();
        List<String> history = session.searchHistory();
        if (history.isEmpty()) return;
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("最近搜索", 11, MUTED);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(label, new LinearLayout.LayoutParams(0, dp(30), 1));
        TextView clear = text("清空", 10, SECONDARY);
        clear.setGravity(Gravity.CENTER);
        clear.setOnClickListener(view -> {
            session.clearSearchHistory();
            parent.removeAllViews();
        });
        header.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(30)));
        parent.addView(header);

        LinearLayout rows = vertical(BG);
        int visibleCount = Math.min(4, history.size());
        for (int i = 0; i < visibleCount; i++) {
            String value = history.get(i);
            TextView item = text(value, 12, TEXT);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(9), 0, dp(9), 0);
            Compat.setBackground(item, round(blend(PANEL, SECONDARY,
                    session.darkMode() ? 0.16f : 0.09f), 7));
            item.setOnClickListener(view -> {
                input.setText(value);
                input.setSelection(input.length());
                session.addSearchHistory(value);
                parent.setVisibility(View.GONE);
                performSearch(value, results, (View) results.getTag());
            });
            addTop(rows, item, 4);
            item.getLayoutParams().height = dp(34);
        }
        parent.addView(rows);
    }

    private void performSearch(String keyword, FrameLayout results, View searchBar) {
        results.setTag(searchBar);
        results.removeAllViews();
        setSearchBarVisible(searchBar, true);
        ProgressBar progress = new ProgressBar(this);
        Compat.tint(progress, PRIMARY);
        results.addView(progress,
                new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
        Map<String, String> params = new HashMap<>();
        params.put("q", keyword);
        params.put("keyword", keyword);
        params.put("search_type", "link");
        params.put("page", "1");
        params.put("limit", "30");
        api.get(EndpointProvider.search(), params, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) {
                if (!"search".equals(screen)) return;
                List<FeedItem> items = new ArrayList<>();
                collectFeedItems(body, items, new HashMap<>(), 0);
                showSearchResults(results, items, searchBar,
                        items.isEmpty() ? "没有找到相关帖子" : "");
            }

            @Override public void onError(String message) {
                if (!"search".equals(screen)) return;
                setSearchBarVisible(searchBar, true);
                results.removeAllViews();
                TextView error = text("搜索失败\n" + message, 13, MUTED);
                error.setGravity(Gravity.CENTER);
                results.addView(error, match());
            }
        });
    }

    private void showSearchResults(FrameLayout parent, List<FeedItem> items, View searchBar,
                                   String emptyText) {
        parent.removeAllViews();
        if (items.isEmpty()) {
            setSearchBarVisible(searchBar, true);
            TextView empty = text(emptyText, 13, MUTED);
            empty.setGravity(Gravity.CENTER);
            parent.addView(empty, match());
            return;
        }
        ListView list = feedList(items);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int scrollState) {
                setSearchBarVisible(searchBar, scrollState == SCROLL_STATE_IDLE);
            }

            @Override public void onScroll(AbsListView view, int firstVisibleItem,
                                           int visibleItemCount, int totalItemCount) {}
        });
        parent.addView(list, match());
    }

    private void setSearchBarVisible(View searchBar, boolean visible) {
        if (searchBar == null) return;
        Boolean state = searchBarStates.get(searchBar);
        if (state != null && state == visible) return;
        searchBarStates.put(searchBar, visible);
        searchBar.animate().cancel();
        if (visible) {
            searchBar.setVisibility(View.VISIBLE);
            searchBar.setEnabled(true);
            setChildrenEnabled(searchBar, true);
            searchBar.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(120)
                    .start();
        } else {
            clearFocusTree(searchBar);
            searchBar.setEnabled(false);
            setChildrenEnabled(searchBar, false);
            searchBar.animate()
                    .alpha(0f)
                    .translationY(-dp(12))
                    .setDuration(110)
                    .start();
            handler.postDelayed(() -> {
                Boolean current = searchBarStates.get(searchBar);
                if (current != null && !current) searchBar.setVisibility(View.GONE);
            }, 120);
        }
    }

    private void prepareSearchBar(View searchBar, int height) {
        searchBarHeights.put(searchBar, height);
        searchBarStates.put(searchBar, true);
        searchBar.setVisibility(View.VISIBLE);
        searchBar.setAlpha(1f);
        searchBar.setTranslationY(0f);
    }

    private void clearFocusTree(View view) {
        view.clearFocus();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                clearFocusTree(group.getChildAt(i));
            }
        }
    }

    private void setChildrenEnabled(View view, boolean enabled) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                child.setEnabled(enabled);
                setChildrenEnabled(child, enabled);
            }
        }
    }

    private void collectFeedItems(Object node, List<FeedItem> output,
                                  Map<String, Boolean> ids, int depth) {
        if (node == null || depth > 7) return;
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                collectFeedItems(array.opt(i), output, ids, depth + 1);
            }
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject object = (JSONObject) node;
        String id = object.optString("linkid", object.optString("link_id"));
        String titleValue = object.optString("title");
        if (!id.isEmpty() && (!titleValue.isEmpty() || object.has("text")
                || object.has("description") || object.has("content"))) {
            if (!ids.containsKey(id)) {
                ids.put(id, true);
                output.add(FeedItem.from(object));
            }
            return;
        }
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            collectFeedItems(object.opt(keys.next()), output, ids, depth + 1);
        }
    }

    private void setFeedRefreshBusy(boolean busy) {
        if (feedRefreshButton == null) return;
        feedRefreshButton.setEnabled(!busy);
        feedRefreshButton.setAlpha(busy ? 0.72f : 1f);
        feedRefreshButton.setText(busy ? "刷新中" : "刷新");
    }

    private void saveFeedScroll() {
        if (feedListView == null) return;
        feedFirstVisible = Math.max(0, feedListView.getFirstVisiblePosition());
        View first = feedListView.getChildAt(0);
        feedFirstTop = first == null ? 0 : first.getTop();
    }

    private void restoreFeedScroll() {
        if (feedListView == null || feed.isEmpty()) return;
        final int position = Math.max(0, Math.min(feedFirstVisible, feed.size() - 1));
        final int top = feedFirstTop;
        feedListView.post(() -> feedListView.setSelectionFromTop(position, top));
    }

    private void showDetail(FeedItem item) {
        stopQrPolling();
        ensureEmojiCatalog(() -> {});
        if ("feed".equals(screen)) saveFeedScroll();
        detailReturn = screen;
        screen = "detail";
        currentLinkId = item.id;
        bottom.setVisibility(View.GONE);
        leading.setVisibility(View.VISIBLE);
        leading.setOnClickListener(view -> returnFromDetail());
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
        section.setBackgroundColor(blend(PANEL, SECONDARY, session.darkMode() ? 0.32f : 0.18f));
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
                ImageLoader.into(image, block.value, 1080);
                image.setOnClickListener(view -> openImage(image, block.value));
                imageCount++;
            } else {
                TextView bodyText = text(block.value,
                        14 * session.bodyTextScale() / 100f, TEXT);
                bodyText.setLineSpacing(0, session.bodyLineSpacing() / 100f);
                Compat.setLetterSpacing(bodyText, session.bodyLetterSpacing() / 200f);
                if (session.bodyBold()) {
                    bodyText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                }
                bodyText.setTextIsSelectable(true);
                EmojiRenderer.set(bodyText, block.value, session.darkMode());
                addTop(parent, bodyText, session.bodyParagraphSpacing());
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
                rail.setBackgroundColor(SECONDARY);
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
        int background = blend(PANEL, SECONDARY, session.darkMode() ? 0.42f : 0.22f);
        int foreground = readableOn(background);
        TextView control = text(label, 12, foreground);
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable drawable = round(background, 7);
        drawable.setStroke(dp(1), SECONDARY);
        Compat.setBackground(control, drawable);
        setLeftIcon(control, icon, foreground, 16);
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
        String rawComment = first(comment.optString("text"), comment.optString("content"),
                comment.optString("html"), comment.optString("description"));
        String visibleComment = RichContent.plainText(rawComment);
        TextView value = text(visibleComment, 13, TEXT);
        value.setLineSpacing(0, reply ? 1.16f : session.bodyLineSpacing() / 100f);
        Compat.setLetterSpacing(value, reply ? 0 : session.bodyLetterSpacing() / 200f);
        if (!reply) {
            value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        EmojiRenderer.set(value, visibleComment, session.darkMode());
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
            ImageLoader.into(image, commentImage, 720);
            image.setOnClickListener(view -> openImage(image, commentImage));
        }

        if (!reply) {
            TextView likes = text(String.valueOf(commentLikes(comment)), 10, MUTED);
            setLeftIcon(likes, R.drawable.ic_thumb_up, SECONDARY, 13);
            likes.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams likeParams = new LinearLayout.LayoutParams(dp(44), dp(24));
            likeParams.leftMargin = dp(3);
            row.addView(likes, likeParams);
        }
        page.addView(row);

        if (reply) {
            View divider = new View(this);
            divider.setBackgroundColor(blend(PANEL, SECONDARY,
                    session.darkMode() ? 0.28f : 0.16f));
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
                    + "   获赞 " + bbs.optInt("up_num"), 12, SECONDARY), 10);
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

        addSettingsMenu(page);
        content.addView(scroll, match());
    }

    private void addSettingsMenu(LinearLayout page) {
        TextView heading = text("设置", 15, TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addTop(page, heading, 12);

        Button settings = button("设置中心", R.drawable.ic_settings);
        settings.setOnClickListener(view -> showSettingsHome());
        addTop(page, settings, 7);
    }

    private void showSettingsHome() {
        LinearLayout page = settingsPage("settings_home", "设置中心", this::showProfile);
        LinearLayout panel = card();
        addSettingEntry(panel, "显示与主题", "主题、字号、间距与界面预览",
                R.drawable.ic_settings, this::showDisplaySettings);
        addSettingEntry(panel, "启动与更新", "开屏动画、开屏文字与自动更新",
                R.drawable.ic_refresh, this::showStartupSettings);
        addSettingEntry(panel, "内容与网络", "图片加载、缓存与登录状态",
                R.drawable.ic_home, this::showAppSettings);
        addSettingEntry(panel, "关于 heybox Lite", "版本、项目说明与免责声明",
                R.drawable.ic_info, this::showAbout);
        page.addView(panel);
    }

    private void addSettingEntry(LinearLayout parent, String name, String description,
                                 int icon, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(5), dp(7), dp(5));
        TextView marker = icon("");
        setIcon(marker, icon, SECONDARY, 18);
        row.addView(marker, new LinearLayout.LayoutParams(dp(34), dp(42)));
        LinearLayout copy = vertical(Color.TRANSPARENT);
        TextView titleView = text(name, 13, TEXT);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(titleView);
        copy.addView(text(description, 10, MUTED));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text("›", 20, MUTED);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(42)));
        row.setOnClickListener(view -> runWithPressFeedback(view, action));
        addTop(parent, row, parent.getChildCount() == 0 ? 0 : 4);
        animateIn(row);
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
        ensureEmojiCatalog(() -> {});
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
                            JSONObject value = savedFeedValue(item);
                            if (value != null) items.add(FeedItem.from(value));
                        }
                    }
                }
                if (pageTitle.contains("历史")) showHistoryList(items);
                else {
                    content.addView(feedList(items), match());
                    if (items.isEmpty()) showMessage("这里暂时没有内容");
                }
            }

            @Override public void onError(String message) {
                hideLoading();
                showMessage(pageTitle + "加载失败\n" + message);
            }
        });
    }

    private ListView feedList(List<FeedItem> items) {
        ListView list = new ListView(this);
        list.setBackgroundColor(BG);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(2));
        list.setAdapter(new FeedAdapter(this, items, session.noImage(),
                session.uiScale() / 100f, session.textScale() / 100f,
                session.darkMode(), PRIMARY, SECONDARY, this::showDetail));
        return list;
    }

    private void ensureEmojiCatalog(Runnable ready) {
        if (api == null) return;
        EmojiStore.load(api, ready);
    }

    private void showHistoryList(List<FeedItem> allItems) {
        content.removeAllViews();
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(BG);
        EditText search = new EditText(this);
        search.setHint("搜索历史：标题、摘要或作者");
        search.setHintTextColor(MUTED);
        search.setTextColor(TEXT);
        search.setSingleLine(true);
        search.setTextSize(sp(12));
        Compat.tint(search, PRIMARY);

        FrameLayout results = new FrameLayout(this);
        page.addView(results, match());
        FrameLayout.LayoutParams searchParams =
                new FrameLayout.LayoutParams(-1, dp(40), Gravity.TOP);
        searchParams.leftMargin = dp(7);
        searchParams.rightMargin = dp(7);
        searchParams.topMargin = dp(5);
        page.addView(search, searchParams);
        prepareSearchBar(search, dp(40));
        List<FeedItem> filtered = new ArrayList<>(allItems);
        FeedAdapter adapter = new FeedAdapter(this, filtered, session.noImage(),
                session.uiScale() / 100f, session.textScale() / 100f,
                session.darkMode(), PRIMARY, SECONDARY, this::showDetail);
        ListView list = new ListView(this);
        list.setBackgroundColor(BG);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(2));
        list.setAdapter(adapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int scrollState) {
                setSearchBarVisible(search, scrollState == SCROLL_STATE_IDLE);
            }

            @Override public void onScroll(AbsListView view, int firstVisibleItem,
                                           int visibleItemCount, int totalItemCount) {}
        });
        results.addView(list, match());

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start,
                                                    int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start,
                                                int before, int count) {
                String query = value.toString().trim().toLowerCase(Locale.US);
                filtered.clear();
                for (FeedItem item : allItems) {
                    String haystack = (item.title + "\n" + item.description + "\n" + item.author)
                            .toLowerCase(Locale.US);
                    if (query.isEmpty() || haystack.contains(query)) filtered.add(item);
                }
                adapter.notifyDataSetChanged();
                results.removeAllViews();
                if (filtered.isEmpty()) {
                    setSearchBarVisible(search, true);
                    TextView empty = text("没有找到相关历史记录", 13, MUTED);
                    empty.setGravity(Gravity.CENTER);
                    results.addView(empty, match());
                } else {
                    results.addView(list, match());
                }
            }
            @Override public void afterTextChanged(Editable value) {}
        });
        if (allItems.isEmpty()) {
            results.removeAllViews();
            TextView empty = text("这里暂时没有内容", 13, MUTED);
            empty.setGravity(Gravity.CENTER);
            results.addView(empty, match());
        }
        content.addView(page, match());
    }

    private JSONArray findLinks(JSONObject result) {
        if (result == null) return null;
        JSONArray links = result.optJSONArray("links");
        if (links == null) links = result.optJSONArray("list");
        if (links == null) links = result.optJSONArray("moments");
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
            if (next == null) next = current.optJSONObject("link_info");
            if (next == null) next = current.optJSONObject("moment");
            if (next == null) next = current.optJSONObject("post");
            if (next == null) next = current.optJSONObject("content");
            if (next == null) next = current.optJSONObject("data");
            if (next == null) next = current.optJSONObject("item");
            if (next == null || next == current) break;
            current = next;
        }
        return current;
    }

    private JSONObject savedFeedValue(JSONObject wrapper) {
        JSONObject value = unwrapSavedItem(wrapper);
        if (value == null) return null;
        try {
            JSONObject merged = new JSONObject(value.toString());
            copyFirstInt(wrapper, merged, "link_award_num",
                    "link_award_num", "like_num", "award_num", "award_count",
                    "like_count", "liked_num", "praise_num", "praise_count",
                    "total_award_num", "award", "awards", "up_num", "up");
            copyFirstInt(wrapper, merged, "comment_num",
                    "comment_num", "comment_count", "reply_num", "reply_count",
                    "comments_count", "total_comment_num", "comment", "comments");
            if (merged.optJSONObject("user") == null) {
                JSONObject user = findObject(wrapper, 0, "user", "author", "account");
                if (user != null) merged.put("user", user);
                else {
                    String author = findString(wrapper, 0, "author_name", "username",
                            "nickname", "author");
                    if (!author.isEmpty()) {
                        JSONObject fallbackUser = new JSONObject();
                        fallbackUser.put("username", author);
                        merged.put("user", fallbackUser);
                    }
                }
            }
            return merged;
        } catch (Exception ignored) {
            return value;
        }
    }

    private void copyFirstInt(JSONObject source, JSONObject target, String targetKey,
                              String... keys) {
        if (target.optInt(targetKey, 0) > 0) return;
        int value = findInt(source, keys, 0);
        if (value > 0) {
            try {
                target.put(targetKey, value);
            } catch (Exception ignored) {
            }
        }
    }

    private int findInt(JSONObject source, String[] keys, int depth) {
        if (source == null || depth > 4) return 0;
        for (String key : keys) {
            if (source.has(key)) {
                int value = source.optInt(key, 0);
                if (value > 0) return value;
            }
        }
        Iterator<String> names = source.keys();
        while (names.hasNext()) {
            Object value = source.opt(names.next());
            if (value instanceof JSONObject) {
                int found = findInt((JSONObject) value, keys, depth + 1);
                if (found > 0) return found;
            } else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    int found = findInt(item, keys, depth + 1);
                    if (found > 0) return found;
                }
            }
        }
        return 0;
    }

    private JSONObject findObject(JSONObject source, int depth, String... keys) {
        if (source == null || depth > 4) return null;
        for (String key : keys) {
            JSONObject value = source.optJSONObject(key);
            if (value != null) return value;
        }
        Iterator<String> names = source.keys();
        while (names.hasNext()) {
            Object child = source.opt(names.next());
            if (child instanceof JSONObject) {
                JSONObject found = findObject((JSONObject) child, depth + 1, keys);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String findString(JSONObject source, int depth, String... keys) {
        if (source == null || depth > 4) return "";
        for (String key : keys) {
            String value = source.optString(key);
            if (!value.isEmpty() && !value.startsWith("{")) return value;
        }
        Iterator<String> names = source.keys();
        while (names.hasNext()) {
            Object child = source.opt(names.next());
            if (child instanceof JSONObject) {
                String found = findString((JSONObject) child, depth + 1, keys);
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }

    private LinearLayout settingsPage(String key, String pageTitle) {
        return settingsPage(key, pageTitle, this::showSettingsHome);
    }

    private LinearLayout settingsPage(String key, String pageTitle, Runnable back) {
        stopQrPolling();
        screen = key;
        bottom.setVisibility(View.GONE);
        leading.setVisibility(View.VISIBLE);
        leading.setOnClickListener(view -> back.run());
        title.setText(pageTitle);
        action.setVisibility(View.INVISIBLE);
        content.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = vertical(BG);
        page.setPadding(dp(8), dp(8), dp(8), dp(14));
        scroll.addView(page);
        content.addView(scroll, match());
        animateIn(scroll);
        return page;
    }

    private void showDisplaySettings() {
        LinearLayout page = settingsPage("display_settings", "显示设置");
        LinearLayout panel = card();

        final boolean[] dark = {session.darkMode()};
        final boolean[] bodyBold = {session.bodyBold()};
        addTop(panel, toggleRow("夜间模式", dark[0], value -> dark[0] = value), 0);

        Button fullPreview = button("查看界面预览", R.drawable.ic_search);
        fullPreview.setOnClickListener(view -> showDisplayPreview());
        addTop(panel, fullPreview, 6);

        LinearLayout livePreview = vertical(PANEL);
        TextView previewTitle = text("显示效果预览", 14, TEXT);
        previewTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView previewBody = text("帖子正文会跟随下方设置实时变化。\n第二段用于预览段落间距。", 13, TEXT);
        previewBody.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextView previewAction = text("主色按钮", 11, contrast(PRIMARY));
        previewAction.setGravity(Gravity.CENTER);
        Compat.setBackground(previewAction, round(PRIMARY, 7));
        addTop(livePreview, previewTitle, 0);
        addTop(livePreview, previewBody, 3);
        addTop(livePreview, previewAction, 6);
        addTop(panel, livePreview, 8);

        ScaleControl uiScale = settingSlider(panel, "界面大小", "%", 70, 160,
                session.uiScale(), value -> updateDisplayPreview(livePreview, previewTitle,
                        previewBody, previewAction, value, -1, -1));
        ScaleControl textScale = settingSlider(panel, "文字大小", "%", 70, 180,
                session.textScale(), value -> updateDisplayPreview(livePreview, previewTitle,
                        previewBody, previewAction, -1, value, -1));
        ScaleControl padding = settingSlider(panel, "左右边距", "dp", 0, 30,
                session.pagePadding(), value -> updateDisplayPreview(livePreview, previewTitle,
                        previewBody, previewAction, -1, -1, value));
        ScaleControl bodyText = settingSlider(panel, "正文字号", "%", 75, 170,
                session.bodyTextScale(), value -> {
                    previewBody.setTextSize(13 * value / 100f);
                });
        ScaleControl letterSpacing = settingSlider(panel, "字间距", "", 0, 20,
                session.bodyLetterSpacing(), value ->
                        Compat.setLetterSpacing(previewBody, value / 200f));
        ScaleControl paragraphSpacing = settingSlider(panel, "段落间距", "dp", 0, 24,
                session.bodyParagraphSpacing(), value ->
                        previewBody.setPadding(0, dp(value), 0, 0));
        ScaleControl lineSpacing = settingSlider(panel, "行距", "%", 100, 180,
                session.bodyLineSpacing(), value ->
                        previewBody.setLineSpacing(0, value / 100f));
        addTop(panel, toggleRow("正文与一级评论稍加粗", bodyBold[0], value -> {
            bodyBold[0] = value;
            previewBody.setTypeface(value
                    ? Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    : Typeface.DEFAULT);
        }), 4);
        updateDisplayPreview(livePreview, previewTitle, previewBody, previewAction,
                session.uiScale(), session.textScale(), session.pagePadding());
        previewBody.setTextSize(13 * session.bodyTextScale() / 100f);
        Compat.setLetterSpacing(previewBody, session.bodyLetterSpacing() / 200f);
        previewBody.setPadding(0, dp(session.bodyParagraphSpacing()), 0, 0);
        previewBody.setLineSpacing(0, session.bodyLineSpacing() / 100f);

        TextView themeTitle = text("预设颜色主题", 13, TEXT);
        themeTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addTop(panel, themeTitle, 12);
        LinearLayout themeGrid = vertical(PANEL);
        for (int rowIndex = 0; rowIndex < THEME_NAMES.length; rowIndex += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(themePreset(rowIndex), new LinearLayout.LayoutParams(0, dp(44), 1));
            if (rowIndex + 1 < THEME_NAMES.length) {
                LinearLayout.LayoutParams right =
                        new LinearLayout.LayoutParams(0, dp(44), 1);
                right.leftMargin = dp(5);
                row.addView(themePreset(rowIndex + 1), right);
            } else {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(44), 1));
            }
            addTop(themeGrid, row, rowIndex == 0 ? 4 : 5);
        }
        panel.addView(themeGrid);

        Button save = button("保存显示设置", R.drawable.ic_settings);
        save.setOnClickListener(view -> {
            Integer ui = parseNumber(uiScale.input, 70, 160);
            Integer text = parseNumber(textScale.input, 70, 180);
            Integer pad = parseNumber(padding.input, 0, 30);
            Integer bodySize = parseNumber(bodyText.input, 75, 170);
            Integer letters = parseNumber(letterSpacing.input, 0, 20);
            Integer paragraphs = parseNumber(paragraphSpacing.input, 0, 24);
            Integer lines = parseNumber(lineSpacing.input, 100, 180);
            if (ui == null || text == null || pad == null || bodySize == null
                    || letters == null || paragraphs == null || lines == null) {
                toast("请检查输入数值是否在滑杆范围内");
                return;
            }
            session.setDarkMode(dark[0]);
            session.setUiScale(ui);
            session.setTextScale(text);
            session.setPagePadding(pad);
            session.setBodyTextScale(bodySize);
            session.setBodyLetterSpacing(letters);
            session.setBodyParagraphSpacing(paragraphs);
            session.setBodyLineSpacing(lines);
            session.setBodyBold(bodyBold[0]);
            applyPalette();
            Compat.colorSystemBars(getWindow(), BG);
            buildShell();
            showDisplaySettings();
            toast("显示设置已保存");
        });
        addTop(panel, save, 10);

        Button reset = button("恢复默认设置", R.drawable.ic_refresh);
        reset.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("恢复默认显示设置")
                .setMessage("主题、字体、间距和界面大小都将恢复为默认值。")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复", (dialog, which) -> {
                    session.resetDisplaySettings();
                    applyPalette();
                    Compat.colorSystemBars(getWindow(), BG);
                    buildShell();
                    showDisplaySettings();
                    toast("已恢复默认显示设置");
                })
                .show());
        addTop(panel, reset, 7);
        page.addView(panel);
    }

    private void showDisplayPreview() {
        LinearLayout page = settingsPage("display_preview", "界面预览",
                this::showDisplaySettings);

        TextView hint = text("预览使用当前已保存的显示参数", 10, MUTED);
        hint.setGravity(Gravity.CENTER);
        page.addView(hint, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout feedCard = card();
        TextView articleBadge = text("文章", 9, contrast(SECONDARY));
        articleBadge.setGravity(Gravity.CENTER);
        Compat.setBackground(articleBadge, round(SECONDARY, 4));
        feedCard.addView(articleBadge, new LinearLayout.LayoutParams(dp(42), dp(20)));
        TextView previewTitle = text("方屏上的社区，也可以清晰又从容", 15, TEXT);
        previewTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addTop(feedCard, previewTitle, 6);
        TextView summary = text("这是一条帖子列表摘要，用来观察整体字号、卡片间距和主题颜色。", 11, MUTED);
        summary.setLineSpacing(0, 1.12f);
        addTop(feedCard, summary, 5);
        TextView stats = text("Ronan   👍 128   评论 36", 10, SECONDARY);
        addTop(feedCard, stats, 7);
        page.addView(feedCard);

        LinearLayout detail = card();
        TextView detailTitle = text("帖子正文预览", 16, TEXT);
        detailTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        detail.addView(detailTitle);
        TextView body = text(
                "这是正文第一段，用于预览文字大小、字间距与行距。\n\n"
                        + "这是正文第二段。调整设置后保存，再回到这里就能查看最终效果。",
                14 * session.bodyTextScale() / 100f, TEXT);
        body.setLineSpacing(0, session.bodyLineSpacing() / 100f);
        Compat.setLetterSpacing(body, session.bodyLetterSpacing() / 200f);
        body.setTypeface(session.bodyBold()
                ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
        addTop(detail, body, session.bodyParagraphSpacing());
        addTop(page, detail, 7);

        LinearLayout comments = card();
        TextView commentTitle = text("评论层级预览", 14, TEXT);
        commentTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        comments.addView(commentTitle);
        TextView first = text("一级评论会稍微加粗，方便快速浏览主要观点。", 13, TEXT);
        first.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        first.setLineSpacing(0, session.bodyLineSpacing() / 100f);
        addTop(comments, first, 7);

        LinearLayout reply = new LinearLayout(this);
        View rail = new View(this);
        rail.setBackgroundColor(SECONDARY);
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(2), -1);
        railParams.rightMargin = dp(8);
        reply.addView(rail, railParams);
        TextView second = text("二级评论使用稍轻的字重，并通过主题色竖线建立层级。", 12, MUTED);
        second.setLineSpacing(0, 1.16f);
        reply.addView(second, new LinearLayout.LayoutParams(0, -2, 1));
        addTop(comments, reply, 7);
        addTop(page, comments, 7);

        Button backToSettings = button("返回继续调整", R.drawable.ic_arrow_back);
        backToSettings.setOnClickListener(view -> showDisplaySettings());
        addTop(page, backToSettings, 9);
    }

    private View themePreset(int index) {
        int primary = THEME_COLORS[index][0];
        int secondary = THEME_COLORS[index][1];
        boolean selected = currentPrimary() == primary && currentSecondary() == secondary;
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(7), 0, dp(7), 0);
        int background = blend(PANEL, selected ? secondary : MUTED,
                selected ? (session.darkMode() ? 0.34f : 0.18f) : 0.06f);
        GradientDrawable shape = round(background, 7);
        shape.setStroke(dp(1), selected ? primary : blend(PANEL, MUTED, 0.34f));
        Compat.setBackground(item, shape);

        LinearLayout swatches = new LinearLayout(this);
        View first = new View(this);
        Compat.setBackground(first, round(primary, 9));
        swatches.addView(first, new LinearLayout.LayoutParams(dp(18), dp(18)));
        View second = new View(this);
        Compat.setBackground(second, round(secondary, 9));
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        secondParams.leftMargin = -dp(5);
        swatches.addView(second, secondParams);
        item.addView(swatches, new LinearLayout.LayoutParams(dp(34), dp(22)));

        TextView name = text(THEME_NAMES[index], 11, TEXT);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        item.addView(name, new LinearLayout.LayoutParams(0, -1, 1));
        if (selected) {
            TextView marker = text("✓", 13, primary);
            marker.setGravity(Gravity.CENTER);
            item.addView(marker, new LinearLayout.LayoutParams(dp(20), -1));
        }
        item.setOnClickListener(view -> {
            session.setTheme(colorHex(primary), colorHex(secondary));
            applyPalette();
            Compat.colorSystemBars(getWindow(), BG);
            buildShell();
            showDisplaySettings();
        });
        return item;
    }

    private void showAppSettings() {
        LinearLayout page = settingsPage("app_settings", "应用设置");
        LinearLayout panel = card();
        addTop(panel, toggleRow("无图模式", session.noImage(), value -> {
            session.setNoImage(value);
            feed.clear();
        }), 0);
        addTop(panel, toggleRow("图片查看器允许查看原图", session.originalImages(),
                session::setOriginalImages), 4);

        Button clearTempCache = button("清除临时缓存 " + formatCacheMb(tempCacheBytes()),
                R.drawable.ic_trash);
        clearTempCache.setOnClickListener(view -> {
            long before = tempCacheBytes();
            clearTempCacheFiles(getCacheDir());
            EmojiRenderer.clear();
            clearTempCache.setText("清除临时缓存 " + formatCacheMb(tempCacheBytes()));
            toast("已清除临时缓存 " + formatCacheMb(before));
        });
        addTop(panel, clearTempCache, 10);

        Button clearCache = button("清除图片缓存 " + formatCacheMb(ImageLoader.cacheSizeKb() * 1024L),
                R.drawable.ic_trash);
        clearCache.setOnClickListener(view -> {
            int before = ImageLoader.cacheSizeKb();
            ImageLoader.clear();
            clearCache.setText("清除图片缓存 " + formatCacheMb(ImageLoader.cacheSizeKb() * 1024L));
            toast("已清除图片缓存 " + formatCacheMb(before * 1024L));
        });
        addTop(panel, clearCache, 7);

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
        addTop(panel, login, 7);
        page.addView(panel);
    }

    private void showStartupSettings() {
        LinearLayout page = settingsPage("startup_settings", "启动与更新");
        LinearLayout panel = card();
        final boolean[] autoUpdate = {session.autoUpdateCheck()};
        final boolean[] splashEnabled = {session.splashEnabled()};
        addTop(panel, toggleRow("进入软件时检查更新", autoUpdate[0],
                value -> autoUpdate[0] = value), 0);
        addTop(panel, toggleRow("显示开屏动画", splashEnabled[0],
                value -> splashEnabled[0] = value), 4);

        EditText splashText = textField(panel, "开屏文字", session.splashText());
        ScaleControl duration = settingSlider(panel, "开屏时长", "ms", 500, 2600,
                session.splashDuration(), value -> {});

        Button preview = button("预览开屏动画", R.drawable.ic_search);
        preview.setOnClickListener(view ->
                showSplashPreview(splashText.getText().toString().trim(),
                        parseNumber(duration.input, 500, 2600)));
        addTop(panel, preview, 8);

        Button save = button("保存启动设置", R.drawable.ic_settings);
        save.setOnClickListener(view -> {
            Integer durationValue = parseNumber(duration.input, 500, 2600);
            if (durationValue == null) {
                toast("开屏时长请输入 500-2600");
                return;
            }
            session.setAutoUpdateCheck(autoUpdate[0]);
            session.setSplashEnabled(splashEnabled[0]);
            session.setSplashText(splashText.getText().toString());
            session.setSplashDuration(durationValue);
            toast("启动设置已保存");
        });
        addTop(panel, save, 7);
        page.addView(panel);
    }

    private void showSplashPreview(String value, Integer duration) {
        final String textValue = value == null || value.isEmpty()
                ? SessionStore.DEFAULT_SPLASH_TEXT : value;
        final int durationValue = duration == null ? session.splashDuration() : duration;
        FrameLayout overlay = new FrameLayout(this);
        overlay.setTag("splash_preview");
        boolean dark = session.darkMode();
        overlay.setBackgroundColor(dark ? Color.rgb(14, 15, 16) : Color.rgb(246, 247, 249));
        if (!dark) {
            ImageView mark = new ImageView(this);
            mark.setImageResource(R.drawable.splash_logo);
            mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            FrameLayout.LayoutParams markParams =
                    new FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER);
            markParams.bottomMargin = dp(54);
            overlay.addView(mark, markParams);
        }
        TextView message = text("", 14, TEXT);
        message.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        message.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams messageParams =
                new FrameLayout.LayoutParams(-1, dp(52), Gravity.CENTER);
        messageParams.topMargin = dp(dark ? 0 : 58);
        messageParams.leftMargin = dp(18);
        messageParams.rightMargin = dp(18);
        overlay.addView(message, messageParams);
        TextView close = text("点击任意位置退出预览", 10, MUTED);
        close.setGravity(Gravity.CENTER);
        overlay.addView(close, new FrameLayout.LayoutParams(
                -1, dp(34), Gravity.BOTTOM));
        overlay.setOnClickListener(view -> content.removeView(overlay));
        content.addView(overlay, match());

        final int[] frame = {0};
        Runnable animation = new Runnable() {
            @Override public void run() {
                if (overlay.getParent() == null) return;
                int count = Math.min(frame[0], textValue.length());
                message.setText(textValue.substring(0, count)
                        + (count < textValue.length() ? "_" : ""));
                frame[0]++;
                if (count < textValue.length()) {
                    message.postDelayed(this,
                            Math.max(28, durationValue / Math.max(1, textValue.length() + 4)));
                }
            }
        };
        animation.run();
    }

    private void showAbout() {
        LinearLayout page = settingsPage("about", "关于");
        LinearLayout panel = card();
        TextView appName = text("heybox Lite", 20, TEXT);
        appName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(appName);
        addTop(panel, text("版本 " + appVersion(), 13, MUTED), 6);
        addTop(panel, text("开发者：Ronan", 13, TEXT), 5);
        addTop(panel, text("支持 Android 4.0 及以上系统", 12, MUTED), 5);
        TextView basedOn = text("基于 HeyWear 进行二次开发与方屏适配，非官方应用。", 12, TEXT);
        basedOn.setLineSpacing(0, 1.18f);
        addTop(panel, basedOn, 7);

        TextView disclaimer = text(
                "免责声明：本项目仅用于学习、研究与个人使用，不代表小黑盒、HeyWear 或相关官方立场。"
                        + "本项目基于 HeyWear 进行二次开发与适配，开发者不对因使用本项目造成的账号、"
                        + "数据、设备或其他风险承担额外责任。请在遵守相关法律法规及平台规则的前提下使用。",
                11, MUTED);
        disclaimer.setLineSpacing(0, 1.22f);
        addTop(panel, disclaimer, 10);

        TextView updateStatus = text("可从 GitHub 检查最新版本", 12, MUTED);
        addTop(panel, updateStatus, 12);
        Button update = button("检查更新", R.drawable.ic_refresh);
        update.setOnClickListener(view -> {
            update.setEnabled(false);
            update.setText("正在检查...");
            UpdateChecker.check(appVersion(), new UpdateChecker.Callback() {
                @Override public void onResult(UpdateChecker.Result result) {
                    if (isFinishing()) return;
                    update.setEnabled(true);
                    if (result.updateAvailable) {
                        updateStatus.setText("发现新版本 " + result.version);
                        update.setText("前往下载 " + result.version);
                        update.setOnClickListener(button -> openUrl(
                                result.downloadUrl.isEmpty() ? result.releaseUrl : result.downloadUrl));
                    } else {
                        updateStatus.setText("当前已是最新版本");
                        update.setText("重新检查");
                    }
                }

                @Override public void onError(String message) {
                    if (isFinishing()) return;
                    update.setEnabled(true);
                    update.setText("重新检查");
                    updateStatus.setText("检查失败：" + message);
                }
            });
        });
        addTop(panel, update, 8);

        Button repository = button("打开 GitHub 项目", R.drawable.ic_info);
        repository.setOnClickListener(view ->
                openUrl("https://github.com/huanghao897/heybox-lite"));
        addTop(panel, repository, 7);
        page.addView(panel);
    }

    private LinearLayout toggleRow(String label, boolean initial, ToggleListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(label, 13, TEXT);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView state = text("", 11, TEXT);
        state.setGravity(Gravity.CENTER);
        row.addView(state, new LinearLayout.LayoutParams(dp(48), dp(28)));
        final boolean[] value = {initial};
        Runnable render = () -> {
            state.setText(value[0] ? "开" : "关");
            int foreground = value[0]
                    ? (session.darkMode() ? Color.BLACK : Color.WHITE)
                    : (session.darkMode() ? Color.WHITE : Color.BLACK);
            int background = value[0]
                    ? (session.darkMode() ? Color.WHITE : Color.BLACK)
                    : (session.darkMode() ? Color.rgb(55, 57, 60) : Color.rgb(220, 222, 225));
            state.setTextColor(foreground);
            Compat.setBackground(state, round(background, 14));
        };
        render.run();
        row.setOnClickListener(view -> {
            value[0] = !value[0];
            render.run();
            listener.onChanged(value[0]);
        });
        return row;
    }

    private ScaleControl settingSlider(LinearLayout parent, String label, String unit,
                                       int min, int max, int current, IntListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 12, TEXT);
        row.addView(name, new LinearLayout.LayoutParams(dp(74), dp(38)));

        SeekBar slider = new SeekBar(this);
        slider.setMax(max - min);
        slider.setProgress(Math.max(0, Math.min(max - min, current - min)));
        Compat.tint(slider, PRIMARY);
        row.addView(slider, new LinearLayout.LayoutParams(0, dp(38), 1));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(String.valueOf(current));
        input.setTextSize(sp(11));
        input.setTextColor(TEXT);
        input.setGravity(Gravity.CENTER);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        Compat.tint(input, PRIMARY);
        row.addView(input, new LinearLayout.LayoutParams(dp(48), dp(38)));

        TextView suffix = text(unit, 10, MUTED);
        suffix.setGravity(Gravity.CENTER);
        row.addView(suffix, new LinearLayout.LayoutParams(dp(24), dp(38)));
        addTop(parent, row, 4);

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                    boolean fromUser) {
                int value = min + progress;
                input.setText(String.valueOf(value));
                input.setSelection(input.length());
                listener.onChanged(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        return new ScaleControl(input, slider);
    }

    private void updateDisplayPreview(LinearLayout preview, TextView heading, TextView body,
                                      TextView actionView, int ui, int textValue, int padding) {
        if (ui >= 0) {
            float scale = ui / 100f;
            preview.setScaleX(scale);
            preview.setScaleY(scale);
        }
        if (textValue >= 0) {
            float scale = textValue / 100f;
            heading.setTextSize(14 * scale);
            body.setTextSize(11 * scale);
            actionView.setTextSize(11 * scale);
        }
        if (padding >= 0) {
            int value = Math.round(padding * getResources().getDisplayMetrics().density);
            preview.setPadding(value, dp(7), value, dp(7));
        }
    }

    private int currentPrimary() {
        try {
            String saved = session.primaryColor();
            return saved.isEmpty() ? (session.darkMode() ? Color.WHITE : Color.BLACK)
                    : Color.parseColor(saved);
        } catch (Exception ignored) {
            return session.darkMode() ? Color.WHITE : Color.BLACK;
        }
    }

    private int currentSecondary() {
        try {
            String saved = session.secondaryColor();
            return saved.isEmpty() ? (session.darkMode()
                    ? Color.rgb(150, 190, 220) : Color.rgb(35, 125, 178))
                    : Color.parseColor(saved);
        } catch (Exception ignored) {
            return session.darkMode() ? Color.rgb(150, 190, 220)
                    : Color.rgb(35, 125, 178);
        }
    }

    private static String colorHex(int color) {
        return String.format(Locale.US, "#%02X%02X%02X",
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int contrast(int color) {
        int luminance = (Color.red(color) * 299 + Color.green(color) * 587
                + Color.blue(color) * 114) / 1000;
        return luminance >= 150 ? Color.BLACK : Color.WHITE;
    }

    private static int readableOn(int color) {
        return contrast(color);
    }

    private static int blend(int base, int overlay, float amount) {
        float value = Math.max(0f, Math.min(1f, amount));
        float keep = 1f - value;
        return Color.rgb(
                Math.round(Color.red(base) * keep + Color.red(overlay) * value),
                Math.round(Color.green(base) * keep + Color.green(overlay) * value),
                Math.round(Color.blue(base) * keep + Color.blue(overlay) * value));
    }

    @SuppressWarnings("deprecation")
    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "1.57";
        }
    }

    private void openImage(ImageView source, String url) {
        int[] location = new int[2];
        source.getLocationOnScreen(location);
        Intent intent = new Intent(this, ImageViewerActivity.class);
        intent.putExtra(ImageViewerActivity.EXTRA_URL, url);
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_X,
                location[0] + source.getWidth() / 2);
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_Y,
                location[1] + source.getHeight() / 2);
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_WIDTH, source.getWidth());
        intent.putExtra(ImageViewerActivity.EXTRA_ORIGIN_HEIGHT, source.getHeight());
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            toast("无法打开链接");
        }
    }

    private interface ToggleListener {
        void onChanged(boolean value);
    }

    private interface IntListener {
        void onChanged(int value);
    }

    private static final class ScaleControl {
        final EditText input;
        final SeekBar slider;

        ScaleControl(EditText input, SeekBar slider) {
            this.input = input;
            this.slider = slider;
        }
    }

    private void addLegacySettings(LinearLayout page) {
        TextView settingsTitle = text("设置", 15, TEXT);
        settingsTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addTop(page, settingsTitle, 12);

        Switch images = new Switch(this);
        images.setText("无图模式");
        images.setTextColor(TEXT);
        images.setTextSize(sp(14));
        images.setChecked(session.noImage());
        Compat.tint(images, PRIMARY);
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
        Compat.tint(originals, PRIMARY);
        originals.setOnCheckedChangeListener((button, checked) ->
                session.setOriginalImages(checked));
        addTop(page, originals, 2);

        Switch theme = new Switch(this);
        theme.setText(session.darkMode() ? "夜间模式" : "白天模式");
        theme.setTextColor(TEXT);
        theme.setTextSize(sp(14));
        theme.setChecked(session.darkMode());
        Compat.tint(theme, PRIMARY);
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
        EditText accent = textField(display, "主色调", session.primaryColor().isEmpty()
                ? (session.darkMode() ? "#FFFFFF" : "#000000") : session.primaryColor());
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
            session.setPrimaryColor(accentValue);
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
        Compat.tint(input, PRIMARY);
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
        Compat.tint(input, PRIMARY);
        row.addView(input, new LinearLayout.LayoutParams(dp(96), dp(40)));
        addTop(parent, row, 3);
        return input;
    }

    private long tempCacheBytes() {
        return dirSize(getCacheDir()) + EmojiRenderer.cacheSizeKb() * 1024L;
    }

    private long dirSize(java.io.File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return Math.max(0L, file.length());
        java.io.File[] children = file.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (java.io.File child : children) total += dirSize(child);
        return total;
    }

    private void clearTempCacheFiles(java.io.File dir) {
        if (dir == null || !dir.isDirectory()) return;
        java.io.File[] children = dir.listFiles();
        if (children == null) return;
        for (java.io.File child : children) deleteFileTree(child);
    }

    private void deleteFileTree(java.io.File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            java.io.File[] children = file.listFiles();
            if (children != null) {
                for (java.io.File child : children) deleteFileTree(child);
            }
        }
        file.delete();
    }

    private String formatCacheMb(long bytes) {
        return String.format(Locale.US, "%.1f MB", Math.max(0L, bytes) / 1048576f);
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
            returnFromDetail();
        }
        else if ("saved".equals(screen)) showProfile();
        else if ("display_preview".equals(screen)) showDisplaySettings();
        else if ("display_settings".equals(screen)
                || "startup_settings".equals(screen)
                || "app_settings".equals(screen)
                || "about".equals(screen)) showSettingsHome();
        else if ("settings_home".equals(screen)) showProfile();
        else if (!"feed".equals(screen) && session.isLoggedIn()) showFeed();
        else super.onBackPressed();
    }

    private void returnFromDetail() {
        if ("saved".equals(detailReturn)) showProfile();
        else if ("search".equals(detailReturn)) showSearch();
        else showFeed();
    }

    @Override
    protected void onDestroy() {
        stopQrPolling();
        ImageLoader.cancelTree(content);
        handler.removeCallbacksAndMessages(null);
        if (api != null) api.close();
        super.onDestroy();
    }

    private void showLoading() {
        hideLoading();
        ProgressBar progress = new ProgressBar(this);
        progress.setTag("loading");
        Compat.tint(progress, PRIMARY);
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
        int foreground = contrast(PRIMARY);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setGravity(Gravity.CENTER);
        Compat.setBackground(button, round(PRIMARY, 8));
        if (iconRes != 0) setLeftIcon(button, iconRes, foreground, 17);
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

    private void runWithPressFeedback(View view, Runnable action) {
        if (action == null) return;
        view.setEnabled(false);
        view.animate().cancel();
        view.animate().scaleX(0.98f).scaleY(0.98f).setDuration(65).start();
        handler.postDelayed(() -> {
            view.animate().scaleX(1f).scaleY(1f).setDuration(90)
                    .setInterpolator(new DecelerateInterpolator()).start();
            view.setEnabled(true);
            if (!isFinishing()) action.run();
        }, 75);
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

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }
}
