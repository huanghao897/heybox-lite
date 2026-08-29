package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SavedContentController {
    interface Host {
        void prepareReadingCenter();

        boolean isReadingCenterScreen();

        void prepareSavedPage(String title);

        void showContent(View view);

        void retainPage(String key, View view);

        void showLoading();

        void hideLoading();

        void showMessage(String message);

        void showToast(String message);

        void showProfile();

        void showLogin();

        void showReadingStats();

        void showDetail(FeedItem item);

        FeedAdapter createFeedAdapter(List<FeedItem> items);

        void addBottomNavSafeSpace(LinearLayout page);

        String readingSummary();

        boolean isSavedScreen();

        int pageHorizontalPadding();

        int subpageTopPadding();

        int roundSearchInset();
    }

    static final String FAVORITES_TITLE = "我的收藏";

    private static final String OFFLINE_CACHE_MESSAGE = "已显示离线缓存";
    private static final String EMPTY_CONTENT_MESSAGE = "暂无内容";

    private final Activity activity;
    private final SessionStore session;
    private final ApiClient api;
    private final LocalCache localCache;
    private final SearchBarController searchBars;
    private final SettingsUi settingsUi;
    private final UserSpacePage userSpacePage;
    private final ThemeTokens tokens;
    private final boolean roundLayout;
    private final Host host;
    private final ReadingCenterPage readingCenterPage;

    private int requestSerial;
    private String returnScreen = "profile";

    SavedContentController(Activity activity, SessionStore session, ApiClient api,
                           LocalCache localCache, SearchBarController searchBars,
                           SettingsUi settingsUi, UserSpacePage userSpacePage,
                           ThemeTokens tokens, boolean roundLayout, Host host) {
        this.activity = activity;
        this.session = session;
        this.api = api;
        this.localCache = localCache;
        this.searchBars = searchBars;
        this.settingsUi = settingsUi;
        this.userSpacePage = userSpacePage;
        this.tokens = tokens;
        this.roundLayout = roundLayout;
        this.host = host;
        this.readingCenterPage = new ReadingCenterPage(activity, session, localCache,
                settingsUi, tokens, new ReadingCenterPage.Host() {
                    @Override public boolean isActive() { return host.isReadingCenterScreen(); }
                    @Override public void prepare() { host.prepareReadingCenter(); }
                    @Override public void showContent(View view) { host.showContent(view); }
                    @Override public void retain(View view) {
                        host.retainPage("reading_center", view);
                    }
                    @Override public void showDetail(FeedItem item) { host.showDetail(item); }
                    @Override public void showReadingStats() { host.showReadingStats(); }
                    @Override public void showWatchLater() { SavedContentController.this.showWatchLater(); }
                    @Override public void showCloudHistory() { SavedContentController.this.showCloudHistory(); }
                    @Override public void addBottomSpace(LinearLayout page) {
                        host.addBottomNavSafeSpace(page);
                    }
                    @Override public void showToast(String message) { host.showToast(message); }
                    @Override public String readingSummary() { return host.readingSummary(); }
                    @Override public int horizontalPadding() {
                        return host.pageHorizontalPadding();
                    }
                    @Override public int topPadding() { return host.subpageTopPadding(); }
                });
    }

    String returnScreen() {
        return this.returnScreen;
    }

    void cancelRequests() {
        this.requestSerial++;
    }

    void close() {
        this.readingCenterPage.close();
    }

    void showReadingCenter() {
        this.readingCenterPage.show();
    }

    void showCloudHistory() {
        if (!this.session.isLoggedIn()) {
            this.host.showLogin();
            return;
        }
        showSavedList("历史记录", EndpointProvider.history(),
                OfficialRequestParams.history(0, 30), "reading_center");
    }

    void showWatchLater() {
        prepareSavedPage("稍后看", "reading_center");
        List<LocalCache.OfflineItem> items = this.localCache.watchLaterItems();
        if (items.isEmpty()) {
            this.host.showMessage("还没有稍后看的帖子\n打开帖子后点“稍后看”即可离线保存");
            return;
        }
        ScrollView scroll = new ScrollView(this.activity);
        LinearLayout page = page();
        scroll.addView(page);
        for (LocalCache.OfflineItem item : items) page.addView(watchLaterCard(item));
        this.host.addBottomNavSafeSpace(page);
        this.host.showContent(scroll);
    }

    void showFavorites() {
        prepareSavedPage(FAVORITES_TITLE, "profile");
        int serial = this.requestSerial;
        this.host.showLoading();
        this.api.get(EndpointProvider.favoriteTabs(), Collections.emptyMap(),
                new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        if (!isCurrentRequest(serial)) return;
                        host.hideLoading();
                        localCache.log("favorite tabs loaded: "
                                + SavedPostParser.favoriteTabSummary(body));
                        renderFavoriteHub(SavedPostParser.favoriteFolders(body), serial);
                    }

                    @Override
                    public void onError(String message) {
                        if (!isCurrentRequest(serial)) return;
                        host.hideLoading();
                        localCache.log("favorite tabs failed: " + message);
                        renderFavoriteHub(Collections.emptyList(), serial);
                    }
                });
    }

    private View watchLaterCard(LocalCache.OfflineItem entry) {
        LinearLayout block = vertical(this.tokens.panel);
        Compat.setBackground(block, UiComponents.round(
                this.activity, this.tokens.panel, 6, uiScale()));
        block.addView(this.userSpacePage.postCard(entry.item));
        View divider = new View(this.activity);
        divider.setBackgroundColor(this.tokens.hairline);
        block.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        LinearLayout footer = new LinearLayout(this.activity);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(12), dp(7), dp(8), dp(7));
        long bytes = entry.detailBytes + ImageLoader.offlineBytes(entry.imageUrls);
        String size = bytes > 0L ? Format.offlineSize(bytes) : "缓存已过期";
        TextView info = text(size + " · 更新于 " + Format.offlineTime(entry.updatedAt),
                10.0f, this.tokens.muted);
        footer.addView(info, new LinearLayout.LayoutParams(0, dp(28), 1.0f));
        TextView remove = text("移除", 11.0f, this.tokens.accent);
        remove.setGravity(Gravity.CENTER);
        remove.setPadding(dp(10), 0, dp(10), 0);
        remove.setOnClickListener(view -> {
            this.localCache.removeWatchLater(entry.item.id);
            showWatchLater();
        });
        footer.addView(remove, new LinearLayout.LayoutParams(-2, dp(28)));
        block.addView(footer);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(7);
        block.setLayoutParams(params);
        return block;
    }

    private void renderFavoriteHub(List<JSONObject> folders, int serial) {
        LinearLayout root = vertical(this.tokens.background);
        root.setPadding(this.host.pageHorizontalPadding(), this.host.subpageTopPadding(),
                this.host.pageHorizontalPadding(), 0);
        root.addView(this.settingsUi.topCard(FAVORITES_TITLE));

        LinearLayout segment = new LinearLayout(this.activity);
        segment.setGravity(Gravity.CENTER);
        segment.setPadding(dp(3), dp(3), dp(3), dp(3));
        Compat.setBackground(segment, UiComponents.round(
                this.activity, this.tokens.panel, 10, uiScale()));
        TextView posts = segment("帖子");
        TextView folderTab = segment("收藏夹");
        segment.addView(posts, new LinearLayout.LayoutParams(0, dp(34), 1.0f));
        segment.addView(folderTab, new LinearLayout.LayoutParams(0, dp(34), 1.0f));
        LinearLayout.LayoutParams segmentParams = new LinearLayout.LayoutParams(-1, dp(40));
        segmentParams.topMargin = dp(6);
        root.addView(segment, segmentParams);

        FrameLayout pane = new FrameLayout(this.activity);
        LinearLayout.LayoutParams paneParams = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        paneParams.topMargin = dp(6);
        root.addView(pane, paneParams);
        this.host.showContent(root);

        int[] paneToken = {0};
        Runnable showPosts = () -> {
            selectSegment(posts, folderTab, true);
            loadFavoritePosts(pane, serial, ++paneToken[0]);
        };
        Runnable showFolders = () -> {
            paneToken[0]++;
            pane.setTag(paneToken[0]);
            selectSegment(posts, folderTab, false);
            renderFavoriteFolders(pane, folders);
        };
        posts.setOnClickListener(view -> run(posts, showPosts));
        folderTab.setOnClickListener(view -> run(folderTab, showFolders));
        showPosts.run();
    }

    private TextView segment(String label) {
        TextView view = text(label, 12.5f, this.tokens.muted);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void selectSegment(TextView posts, TextView folders, boolean postsSelected) {
        posts.setTextColor(postsSelected ? this.tokens.text : this.tokens.muted);
        folders.setTextColor(postsSelected ? this.tokens.muted : this.tokens.text);
        Compat.setBackground(posts, postsSelected
                ? UiComponents.navSelection(this.activity, this.tokens, uiScale()) : null);
        Compat.setBackground(folders, postsSelected ? null
                : UiComponents.navSelection(this.activity, this.tokens, uiScale()));
    }

    private void loadFavoritePosts(FrameLayout pane, int serial, int paneToken) {
        pane.removeAllViews();
        LoadingSpinnerView loading = new LoadingSpinnerView(this.activity);
        loading.setColor(this.tokens.primary);
        pane.addView(loading, new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER));
        this.api.get(EndpointProvider.favoriteLinks(),
                OfficialRequestParams.favorites(null, 0, 30), new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        if (!isCurrentRequest(serial) || paneToken(pane) != paneToken) return;
                        List<FeedItem> items = filtered(SavedPostParser.feedItems(body));
                        localCache.saveSavedList(cacheKey(
                                FAVORITES_TITLE, EndpointProvider.favoriteLinks()), items);
                        renderFavoritePosts(pane, items);
                    }

                    @Override
                    public void onError(String message) {
                        if (!isCurrentRequest(serial) || paneToken(pane) != paneToken) return;
                        List<FeedItem> cached = filtered(localCache.savedList(cacheKey(
                                FAVORITES_TITLE, EndpointProvider.favoriteLinks())));
                        if (!cached.isEmpty()) {
                            host.showToast(OFFLINE_CACHE_MESSAGE);
                            renderFavoritePosts(pane, cached);
                            return;
                        }
                        renderPaneMessage(pane, FAVORITES_TITLE + "加载失败\n" + message);
                    }
                });
        pane.setTag(paneToken);
    }

    private int paneToken(FrameLayout pane) {
        Object value = pane.getTag();
        return value instanceof Integer ? (Integer) value : -1;
    }

    private void renderFavoritePosts(FrameLayout pane, List<FeedItem> items) {
        pane.removeAllViews();
        if (items.isEmpty()) renderPaneMessage(pane, EMPTY_CONTENT_MESSAGE);
        else pane.addView(feedList(items), match());
    }

    private void renderFavoriteFolders(FrameLayout pane, List<JSONObject> folders) {
        pane.removeAllViews();
        if (folders == null || folders.isEmpty()) {
            renderPaneMessage(pane, "暂无收藏夹");
            return;
        }
        ScrollView scroll = new ScrollView(this.activity);
        LinearLayout page = vertical(this.tokens.background);
        page.setPadding(0, 0, 0, dp(16));
        scroll.addView(page);
        LinearLayout list = this.settingsUi.list();
        for (JSONObject folder : folders) {
            String folderId = SavedPostParser.favoriteFolderId(folder);
            String folderName = SavedPostParser.favoriteFolderName(folder);
            if (folderName.isEmpty()) folderName = "默认收藏夹";
            int count = SavedPostParser.favoriteFolderCount(folder);
            String title = folderName;
            addEntry(list, title, null, count > 0 ? String.valueOf(count) : null,
                    R.drawable.il_bookmark, () -> showSavedList(
                            title, EndpointProvider.favoriteLinks(),
                            OfficialRequestParams.favorites(folderId, 0, 30), "favorites"));
        }
        page.addView(list);
        pane.addView(scroll, match());
    }

    private void renderPaneMessage(FrameLayout pane, String message) {
        pane.removeAllViews();
        TextView empty = text(message, 13.0f, this.tokens.muted);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(18), dp(16), dp(18), dp(16));
        pane.addView(empty, match());
    }

    private void showSavedList(String title, String path, Map<String, String> params,
                               String targetOnBack) {
        prepareSavedPage(title, targetOnBack);
        int serial = this.requestSerial;
        this.host.showLoading();
        String cacheKey = cacheKey(title, path);
        Map<String, String> requestParams = new LinkedHashMap<>(params);
        this.localCache.log(title + " request path=" + path
                + " keys=" + requestParams.keySet());
        this.api.get(path, requestParams, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                if (!isCurrentRequest(serial)) return;
                host.hideLoading();
                List<FeedItem> items = filtered(SavedPostParser.feedItems(body));
                localCache.saveSavedList(cacheKey, items);
                renderSavedItems(title, items);
            }

            @Override
            public void onError(String message) {
                if (!isCurrentRequest(serial)) return;
                localCache.log(title + " failed: " + message);
                showSavedListError(title, path, message);
            }
        });
    }

    private void showSavedListError(String title, String path, String message) {
        this.host.hideLoading();
        List<FeedItem> cached = filtered(this.localCache.savedList(cacheKey(title, path)));
        if (!cached.isEmpty()) {
            this.host.showToast(OFFLINE_CACHE_MESSAGE);
            renderSavedItems(title, cached);
            return;
        }
        this.host.showMessage(title + "加载失败\n" + message);
    }

    private void renderSavedItems(String title, List<FeedItem> items) {
        if (title != null && title.contains("历史")) {
            showHistoryList(items);
            return;
        }
        LinearLayout page = vertical(this.tokens.background);
        page.setPadding(this.host.pageHorizontalPadding(), this.host.subpageTopPadding(),
                this.host.pageHorizontalPadding(), 0);
        page.addView(this.settingsUi.topCard(title));
        if (items.isEmpty()) {
            TextView empty = text(EMPTY_CONTENT_MESSAGE, 13.0f, this.tokens.muted);
            empty.setGravity(Gravity.CENTER);
            page.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        } else {
            ListView list = feedList(items);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 0, 1.0f);
            params.topMargin = dp(4);
            page.addView(list, params);
        }
        this.host.showContent(page);
    }

    private void showHistoryList(List<FeedItem> allItems) {
        FrameLayout root = new FrameLayout(this.activity);
        root.setBackgroundColor(this.tokens.background);
        int searchHeight = dp(this.roundLayout ? 35 : 40);
        int searchTop = this.roundLayout ? this.host.subpageTopPadding() : dp(5);
        int contentTop = searchTop + searchHeight + dp(6);

        EditText search = new EditText(this.activity);
        search.setHint("搜索历史：标题、摘要或作者");
        search.setHintTextColor(this.tokens.muted);
        search.setTextColor(this.tokens.text);
        search.setSingleLine(true);
        search.setTextSize(sp(12.0f));
        search.setPadding(dp(10), 0, dp(10), 0);
        setLeftIcon(search, R.drawable.il_search, this.tokens.muted, 16);
        Compat.setBackground(search, UiComponents.groupCard(
                this.activity, this.tokens, uiScale()));

        FrameLayout results = new FrameLayout(this.activity);
        root.addView(results, match());
        FrameLayout.LayoutParams searchParams = new FrameLayout.LayoutParams(
                -1, searchHeight, Gravity.TOP);
        int horizontal = this.roundLayout ? this.host.roundSearchInset() : dp(7);
        searchParams.leftMargin = horizontal;
        searchParams.rightMargin = horizontal;
        searchParams.topMargin = searchTop;
        root.addView(search, searchParams);
        this.searchBars.prepare(search, contentTop);

        List<FeedItem> filtered = new ArrayList<>(allItems);
        FeedAdapter adapter = this.host.createFeedAdapter(filtered);
        ListView list = feedList(filtered);
        list.setPadding(0, contentTop, 0, dp(4));
        list.setClipToPadding(false);
        list.setAdapter((ListAdapter) adapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int state) {
                searchBars.setVisible(search, state == AbsListView.OnScrollListener.SCROLL_STATE_IDLE);
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
            }
        });
        results.addView(list, match());

        TextView empty = text(allItems.isEmpty()
                ? EMPTY_CONTENT_MESSAGE : "没有找到相关历史记录", 13.0f, this.tokens.muted);
        empty.setGravity(Gravity.CENTER);
        results.addView(empty, match());
        empty.setVisibility(allItems.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(allItems.isEmpty() ? View.GONE : View.VISIBLE);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                String query = value.toString().trim().toLowerCase(Locale.US);
                filtered.clear();
                for (FeedItem item : allItems) {
                    String searchable = (item.title + "\n" + item.description + "\n"
                            + item.author).toLowerCase(Locale.US);
                    if (query.isEmpty() || searchable.contains(query)) filtered.add(item);
                }
                adapter.notifyDataSetChanged();
                empty.setText("没有找到相关历史记录");
                boolean noResults = filtered.isEmpty();
                if (noResults) searchBars.setVisible(search, true);
                empty.setVisibility(noResults ? View.VISIBLE : View.GONE);
                list.setVisibility(noResults ? View.GONE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        this.host.showContent(root);
    }

    private void prepareSavedPage(String title, String targetOnBack) {
        EmojiStore.load(this.api, () -> {
        });
        this.requestSerial++;
        this.returnScreen = TextUtils.isEmpty(targetOnBack) ? "profile" : targetOnBack;
        this.host.prepareSavedPage(title);
    }

    private boolean isCurrentRequest(int serial) {
        return !this.activity.isFinishing() && this.host.isSavedScreen()
                && serial == this.requestSerial;
    }

    private List<FeedItem> filtered(List<FeedItem> items) {
        return FeedCollection.filter(items, this.session.blockKeywordList());
    }

    private String cacheKey(String title, String path) {
        return title + "_" + path;
    }

    private ListView feedList(List<FeedItem> items) {
        ListView list = new ListView(this.activity);
        list.setBackgroundColor(this.tokens.background);
        list.setDivider(new ColorDrawable(0));
        list.setDividerHeight(dp(2));
        list.setAdapter((ListAdapter) this.host.createFeedAdapter(items));
        return list;
    }

    private LinearLayout page() {
        LinearLayout page = vertical(this.tokens.background);
        page.setPadding(this.host.pageHorizontalPadding(), this.host.subpageTopPadding(),
                this.host.pageHorizontalPadding(), dp(18));
        return page;
    }

    private void addEntry(LinearLayout parent, String name, String description,
                          String value, int icon, Runnable action) {
        this.settingsUi.addEntry(parent, name, description, value, icon, action);
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(
                this.activity, value, size, color, this.session.textScale() / 100.0f);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private LinearLayout vertical(int color) {
        LinearLayout layout = new LinearLayout(this.activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    private void addTop(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(view, params);
    }

    private void setLeftIcon(TextView view, int resource, int color, int size) {
        Drawable drawable = Compat.tintedDrawable(this.activity, resource, color);
        if (drawable == null) return;
        drawable.setBounds(0, 0, dp(size), dp(size));
        view.setCompoundDrawables(drawable, null, null, null);
        view.setCompoundDrawablePadding(dp(5));
    }

    private void run(View view, Runnable action) {
        UiComponents.press(view);
        if (action != null && !this.activity.isFinishing()) action.run();
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private int dp(int value) {
        return UiComponents.dp(this.activity, value, uiScale());
    }

    private float sp(float value) {
        return value * this.session.textScale() / 100.0f;
    }

    private float uiScale() {
        return this.session.uiScale() / 100.0f;
    }
}
