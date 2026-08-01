package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;

final class SearchPage {
    interface Host {
        boolean isSearchActive();

        FeedAdapter createFeedAdapter(List<FeedItem> items);

        void showToast(String message);
    }

    private final Activity activity;
    private final SessionStore session;
    private final ApiClient api;
    private final LocalCache localCache;
    private final SearchBarController searchBars;
    private final Host host;
    private final SearchState state = new SearchState();

    private ThemeTokens tokens;
    private float uiScale;
    private float textScale;
    private ListView listView;

    private static final class SearchBar {
        final LinearLayout root;
        final EditText input;
        final ImageView submit;

        SearchBar(LinearLayout root, EditText input, ImageView submit) {
            this.root = root;
            this.input = input;
            this.submit = submit;
        }
    }

    SearchPage(Activity activity, SessionStore session, ApiClient api,
               LocalCache localCache, SearchBarController searchBars, Host host) {
        this.activity = activity;
        this.session = session;
        this.api = api;
        this.localCache = localCache;
        this.searchBars = searchBars;
        this.host = host;
    }

    View create(boolean restoreResults, View header, ThemeTokens tokens,
                boolean roundLayout, int horizontalPadding,
                int subpageTopPadding, int roundSearchInset) {
        this.tokens = tokens;
        this.uiScale = this.session.uiScale() / 100.0f;
        this.textScale = this.session.textScale() / 100.0f;

        FrameLayout root = new FrameLayout(this.activity);
        root.setBackgroundColor(tokens.background);
        int headerHeight = dp(roundLayout ? 40 : 44);
        int searchHeight = dp(roundLayout ? 35 : 42);
        int headerTop = roundLayout ? subpageTopPadding : dp(5);
        int searchTop = headerTop + headerHeight + dp(4);
        int contentTop = searchTop + searchHeight + dp(8);
        int searchHorizontal = roundLayout ? roundSearchInset : horizontalPadding;

        SearchBar controls = searchBar(searchHeight, roundLayout);
        LinearLayout searchBar = controls.root;
        EditText input = controls.input;
        ImageView submit = controls.submit;
        LinearLayout recent = historyList();
        FrameLayout results = new FrameLayout(this.activity);
        results.setTag(searchBar);
        root.addView(results, match());
        TextView hint = text("输入关键词开始搜索", 13.0f, tokens.muted);
        hint.setGravity(Gravity.CENTER);
        results.addView(hint, match());

        FrameLayout.LayoutParams recentParams = new FrameLayout.LayoutParams(-1, -2);
        recentParams.leftMargin = horizontalPadding;
        recentParams.rightMargin = horizontalPadding;
        recentParams.topMargin = contentTop;
        root.addView(recent, recentParams);

        FrameLayout.LayoutParams searchParams = new FrameLayout.LayoutParams(
                -1, searchHeight, Gravity.TOP);
        searchParams.leftMargin = searchHorizontal;
        searchParams.rightMargin = searchHorizontal;
        searchParams.topMargin = searchTop;
        root.addView(searchBar, searchParams);

        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(
                -1, headerHeight, Gravity.TOP);
        headerParams.leftMargin = horizontalPadding;
        headerParams.rightMargin = horizontalPadding;
        headerParams.topMargin = headerTop;
        root.addView(header, headerParams);
        this.searchBars.prepare(searchBar, contentTop);

        Runnable search = () -> {
            String keyword = input.getText().toString().trim();
            if (keyword.isEmpty()) {
                this.host.showToast("请输入搜索关键词");
                return;
            }
            this.session.addSearchHistory(keyword);
            recent.setVisibility(View.GONE);
            performSearch(keyword, results, searchBar);
        };
        submit.setOnClickListener(view -> {
            UiComponents.press(submit);
            search.run();
        });
        input.setOnEditorActionListener((view, actionId, event) -> {
            search.run();
            return true;
        });
        renderHistory(recent, input, results);

        if (restoreResults && this.state.hasResults()) {
            input.setText(this.state.keyword());
            input.setSelection(input.length());
            recent.setVisibility(View.GONE);
            this.state.invalidateRequests();
            renderResultList(results, searchBar, "");
            restoreListPosition();
        }
        return root;
    }

    ListView listView() {
        return this.listView;
    }

    List<FeedItem> items() {
        return this.state.items();
    }

    void saveListPosition() {
        if (this.listView == null) return;
        View first = this.listView.getChildCount() == 0
                ? null : this.listView.getChildAt(0);
        this.state.saveListPosition(this.listView.getFirstVisiblePosition(),
                first == null ? 0 : first.getTop() - this.listView.getPaddingTop());
    }

    void restoreListPosition() {
        ListView list = this.listView;
        if (list == null || !this.state.hasResults()) return;
        int position = this.state.listPosition();
        int offset = this.state.listTopOffset();
        list.post(() -> list.setSelectionFromTop(position, offset));
    }

    void close() {
        this.state.invalidateRequests();
        this.listView = null;
    }

    private SearchBar searchBar(int searchHeight, boolean roundLayout) {
        LinearLayout bar = new LinearLayout(this.activity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), 0, dp(5), 0);
        Compat.setBackground(bar, UiComponents.groupCard(
                this.activity, this.tokens, this.uiScale));

        ImageView glyph = new ImageView(this.activity);
        glyph.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Drawable glyphIcon = Compat.tintedDrawable(
                this.activity, R.drawable.il_search, this.tokens.muted);
        if (glyphIcon != null) glyph.setImageDrawable(glyphIcon);
        bar.addView(glyph, new LinearLayout.LayoutParams(dp(20), dp(20)));

        EditText input = new EditText(this.activity);
        input.setHint("搜索帖子、作者或关键词");
        input.setHintTextColor(this.tokens.muted);
        input.setTextColor(this.tokens.text);
        input.setSingleLine(true);
        input.setTextSize(sp(13.0f));
        input.setPadding(dp(8), 0, dp(4), 0);
        Compat.setBackground(input, null);
        bar.addView(input, new LinearLayout.LayoutParams(0, searchHeight, 1.0f));

        ImageView submit = new ImageView(this.activity);
        submit.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Drawable submitIcon = Compat.tintedDrawable(
                this.activity, R.drawable.il_search, this.tokens.text);
        if (submitIcon != null) submit.setImageDrawable(submitIcon);
        int padding = dp(roundLayout ? 7 : 8);
        submit.setPadding(padding, padding, padding, padding);
        submit.setContentDescription("搜索");
        bar.addView(submit, new LinearLayout.LayoutParams(
                dp(roundLayout ? 29 : 32), dp(roundLayout ? 29 : 32)));
        return new SearchBar(bar, input, submit);
    }

    private LinearLayout historyList() {
        LinearLayout list = new LinearLayout(this.activity);
        list.setOrientation(LinearLayout.VERTICAL);
        Compat.setBackground(list, UiComponents.groupCard(
                this.activity, this.tokens, this.uiScale));
        return list;
    }

    private void renderHistory(LinearLayout parent, EditText input,
                               FrameLayout results) {
        parent.removeAllViews();
        List<String> history = this.session.searchHistory();
        if (history.isEmpty()) {
            parent.setVisibility(View.GONE);
            return;
        }
        parent.setVisibility(View.VISIBLE);
        LinearLayout header = new LinearLayout(this.activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(7), dp(3), dp(4));
        TextView label = text("最近搜索", 12.5f, this.tokens.text);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(label, new LinearLayout.LayoutParams(0, dp(30), 1.0f));
        TextView clear = text("清空", 11.0f, this.tokens.muted);
        clear.setGravity(Gravity.CENTER);
        clear.setOnClickListener(view -> {
            this.session.clearSearchHistory();
            parent.removeAllViews();
            parent.setVisibility(View.GONE);
        });
        header.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(30)));
        parent.addView(header);
        int visibleCount = Math.min(4, history.size());
        for (int i = 0; i < visibleCount; i++) {
            String value = history.get(i);
            addDivider(parent);
            TextView item = text(value, 13.0f, this.tokens.text);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setSingleLine(true);
            item.setEllipsize(TextUtils.TruncateAt.END);
            item.setPadding(dp(8), 0, dp(8), 0);
            item.setOnClickListener(view -> {
                input.setText(value);
                input.setSelection(input.length());
                this.session.addSearchHistory(value);
                parent.setVisibility(View.GONE);
                performSearch(value, results, (View) results.getTag());
            });
            parent.addView(item, new LinearLayout.LayoutParams(-1, dp(40)));
        }
    }

    private void performSearch(String keyword, FrameLayout results, View searchBar) {
        results.setTag(searchBar);
        results.removeAllViews();
        this.searchBars.setVisible(searchBar, true);
        LoadingSpinnerView progress = new LoadingSpinnerView(this.activity);
        progress.setColor(this.tokens.primary);
        results.addView(progress, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));
        int request = this.state.begin(keyword);
        this.api.get(EndpointProvider.search(),
                OfficialRequestParams.search(keyword, 0, 20), new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        if (!host.isSearchActive() || !state.isCurrent(request)) return;
                        List<FeedItem> parsed = FeedCollection.parse(body);
                        state.replace(FeedCollection.filter(parsed,
                                session.blockKeywordList()), parsed.size());
                        renderResultList(results, searchBar, "没有找到相关帖子");
                    }

                    @Override
                    public void onError(String message) {
                        if (!host.isSearchActive() || !state.isCurrent(request)) return;
                        localCache.log("search failed: " + message);
                        searchBars.setVisible(searchBar, true);
                        results.removeAllViews();
                        TextView error = text("搜索失败\n" + message,
                                13.0f, tokens.muted);
                        error.setGravity(Gravity.CENTER);
                        results.addView(error, match());
                    }
                });
    }

    private void renderResultList(FrameLayout parent, View searchBar, String emptyText) {
        parent.removeAllViews();
        if (this.state.items().isEmpty()) {
            this.searchBars.setVisible(searchBar, true);
            TextView empty = text(emptyText, 13.0f, this.tokens.muted);
            empty.setGravity(Gravity.CENTER);
            parent.addView(empty, match());
            return;
        }
        ListView list = new ListView(this.activity);
        this.listView = list;
        list.setBackgroundColor(this.tokens.background);
        list.setDivider(new ColorDrawable(0));
        list.setDividerHeight(dp(2));
        list.setPadding(0, this.searchBars.contentTop(searchBar, dp(56)), 0, dp(4));
        list.setClipToPadding(false);
        TextView footer = text(this.state.endReached()
                ? "没有更多了" : "上滑加载更多", 11.5f, this.tokens.muted);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(10), 0, dp(12));
        list.addFooterView(footer, null, false);
        FeedAdapter adapter = this.host.createFeedAdapter(this.state.items());
        list.setAdapter((ListAdapter) adapter);
        footer.setOnClickListener(view -> loadMore(adapter, footer));
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
                int remaining = totalItemCount - firstVisibleItem - visibleItemCount;
                if (totalItemCount > 0 && remaining <= 5) loadMore(adapter, footer);
            }
        });
        parent.addView(list, match());
    }

    private void loadMore(FeedAdapter adapter, TextView footer) {
        if (!this.state.beginLoadMore()) return;
        footer.setText("正在加载更多…");
        int request = this.state.generation();
        this.api.get(EndpointProvider.search(), OfficialRequestParams.search(
                this.state.keyword(), this.state.offset(), 20), new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject body) {
                        if (!state.isCurrent(request)) return;
                        List<FeedItem> parsed = FeedCollection.parse(body);
                        int added = state.appendPage(FeedCollection.filter(parsed,
                                session.blockKeywordList()), parsed.size());
                        if (added > 0) {
                            footer.setText("上滑加载更多");
                            adapter.notifyDataSetChanged();
                            return;
                        }
                        footer.setText(state.endReached()
                                ? "没有更多了" : "上滑加载更多");
                    }

                    @Override
                    public void onError(String message) {
                        if (!state.isCurrent(request)) return;
                        state.failLoadMore();
                        footer.setText("加载失败，点击重试");
                    }
                });
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this.activity);
        divider.setBackgroundColor(this.tokens.hairline);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                -1, Math.max(1, dp(1) / 2));
        params.leftMargin = dp(60);
        parent.addView(divider, params);
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(
                this.activity, value, size, color, this.textScale);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    private int dp(int value) {
        return Math.round(value * this.activity.getResources()
                .getDisplayMetrics().density * this.uiScale);
    }

    private float sp(float value) {
        return value * this.textScale;
    }
}
