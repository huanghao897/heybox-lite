package com.ronan.heyboxlite;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.AbsListView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class FeedPage {
    interface Host {
        void prepareFeedChrome();

        boolean isFeedActive();

        void showPage(View page);

        void showSearch();

        void openDetail(FeedItem item);

        void showLoading();

        void hideLoading();

        void showMessage(String message);

        void showToast(String message);

        void setRefreshBusy(boolean busy);

        int pageTopPadding();

        int roundHeaderTopPadding();

        int roundHeaderInset();

        int roundCardInset();

        int roundSearchInset();
    }

    private static final String OFFLINE_CACHE_MESSAGE = "已显示离线缓存";

    private final Activity activity;
    private final SessionStore session;
    private final ApiClient api;
    private final LocalCache localCache;
    private final ThemeTokens tokens;
    private final PostActionController postActions;
    private final boolean roundLayout;
    private final Host host;
    private final FeedExposureTracker exposureTracker = new FeedExposureTracker();
    private final FeedPagingState paging = new FeedPagingState();
    private final List<FeedItem> items = new ArrayList<>();

    private FeedAdapter adapter;
    private ListView listView;
    private ListView cachedListView;
    private View cachedContainer;
    private FeedPaginationView paginationView;
    private boolean coldLaunchPending = true;
    private boolean suppressNextCacheFallback;
    private int firstVisible;
    private int firstTop;

    FeedPage(Activity activity, SessionStore session, ApiClient api,
             LocalCache localCache, ThemeTokens tokens,
             PostActionController postActions, boolean roundLayout, Host host) {
        this.activity = activity;
        this.session = session;
        this.api = api;
        this.localCache = localCache;
        this.tokens = tokens;
        this.postActions = postActions;
        this.roundLayout = roundLayout;
        this.host = host;
    }

    void show() {
        this.host.prepareFeedChrome();
        boolean coldLaunch = this.coldLaunchPending;
        this.coldLaunchPending = false;
        if (coldLaunch) resetForColdLaunch();
        if (!coldLaunch && canReuseCachedView()) {
            this.host.showPage(this.cachedContainer);
            restoreScroll();
            updatePagination();
            return;
        }
        restoreCachedItems(coldLaunch);
        buildList();
        this.host.showPage(this.paginationView);
        if (!coldLaunch) restoreScroll();
        if (coldLaunch || this.items.isEmpty()) load(true, null);
    }

    boolean load(boolean reset) {
        return load(reset, null);
    }

    boolean load(boolean reset, String refreshType) {
        int requestSerial = this.paging.begin(reset);
        if (requestSerial == FeedPagingState.NO_REQUEST) return false;
        updatePagination();
        long loadStartedAt = SystemClock.elapsedRealtime();
        List<FeedItem> previous = reset ? new ArrayList<>(this.items) : null;
        boolean suppressCacheFallback = reset && this.suppressNextCacheFallback;
        if (reset) this.suppressNextCacheFallback = false;
        PullRefreshListView refreshList = reset && this.listView instanceof PullRefreshListView
                ? (PullRefreshListView) this.listView : null;
        if (reset) {
            setRefreshBusy(true, refreshList);
            if (this.items.isEmpty()) this.host.showLoading();
        }
        int pull = reset ? 1 : 0;
        String requestedLastval = this.paging.lastval();
        String unexposed = this.exposureTracker.valueForRequest(
                reset, System.currentTimeMillis());
        Map<String, String> params = OfficialRequestParams.feed(
                pull, this.paging.lastPull(), requestedLastval,
                this.paging.firstRequest(), unexposed, reset ? refreshType : null);
        this.api.get(EndpointProvider.feeds(), params, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject body) {
                boolean accepted = paging.accepts(requestSerial);
                paging.finish(reset, requestSerial);
                if (!accepted) return;
                host.hideLoading();
                applyResponse(body, reset, pull, previous, loadStartedAt);
                if (reset) setRefreshBusy(false, refreshList, requestSerial);
            }

            @Override
            public void onError(String message) {
                boolean accepted = paging.accepts(requestSerial);
                paging.finish(reset, requestSerial);
                if (!accepted) return;
                host.hideLoading();
                applyFailure(message, reset, previous, suppressCacheFallback);
                if (reset) setRefreshBusy(false, refreshList, requestSerial);
            }
        });
        return true;
    }

    void scrollToTopAndRefresh() {
        this.firstVisible = 0;
        this.firstTop = 0;
        if (this.listView != null) {
            this.listView.smoothScrollToPosition(0);
            this.listView.postDelayed(() -> {
                if (this.listView != null) this.listView.setSelection(0);
            }, 160L);
        }
        load(true, "icon");
    }

    void saveScroll() {
        if (this.listView == null) return;
        this.firstVisible = Math.max(0, this.listView.getFirstVisiblePosition());
        View first = this.listView.getChildAt(0);
        this.firstTop = first == null ? 0 : first.getTop();
    }

    void restoreScroll() {
        if (this.listView == null || this.items.isEmpty()) return;
        int maxPosition = Math.max(0,
                this.items.size() + this.listView.getHeaderViewsCount() - 1);
        int position = Math.max(0, Math.min(this.firstVisible, maxPosition));
        int top = this.firstTop;
        this.listView.post(() -> {
            this.listView.setSelectionFromTop(position, top);
            this.listView.post(() -> this.listView.setSelectionFromTop(position, top));
        });
    }

    void resetContent(boolean resetPaging) {
        this.items.clear();
        if (resetPaging) this.paging.resetForNewFeed();
        invalidateView();
    }

    void clearItems() {
        this.items.clear();
    }

    void notifyItemsChanged() {
        this.localCache.saveFeed(this.items);
        notifyAdapter();
    }

    void notifyAdapter() {
        if (this.adapter != null) this.adapter.notifyDataSetChanged();
    }

    void invalidateView() {
        if (this.listView instanceof PullRefreshListView) {
            ((PullRefreshListView) this.listView).cancelRefresh();
        }
        this.cachedListView = null;
        this.cachedContainer = null;
        this.listView = null;
        this.adapter = null;
        closePagination();
    }

    void finishMotion() {
        if (this.paginationView != null) this.paginationView.finishMotion();
    }

    void close() {
        closePagination();
    }

    FeedAdapter createAdapter(List<FeedItem> source) {
        return new FeedAdapter(this.activity, source, this.session.noImage(),
                this.session.uiScale() / 100.0f,
                this.session.textScale() / 100.0f,
                this.session.darkMode(), this.tokens.primary, this.tokens.secondary,
                this.host::openDetail, this.postActions::toggleFeedLike,
                this.postActions::toggleFeedFollow, this.session.userId(),
                this.roundLayout, this.roundLayout ? this.host.roundCardInset() : 0);
    }

    List<FeedItem> items() {
        return this.items;
    }

    FeedPagingState paging() {
        return this.paging;
    }

    ListView listView() {
        return this.listView;
    }

    View cachedView() {
        return this.cachedContainer;
    }

    int itemCount() {
        return this.items.size();
    }

    private void resetForColdLaunch() {
        this.items.clear();
        this.paging.resetForNewFeed();
        this.suppressNextCacheFallback = true;
        this.firstVisible = 0;
        this.firstTop = 0;
        this.cachedContainer = null;
        this.cachedListView = null;
    }

    private boolean canReuseCachedView() {
        return this.cachedContainer != null
                && this.listView == this.cachedListView
                && this.cachedContainer.getParent() == null;
    }

    private void restoreCachedItems(boolean coldLaunch) {
        if (coldLaunch || !this.items.isEmpty()) return;
        List<FeedItem> cached = FeedCollection.filter(
                this.localCache.feedItems(), this.session.blockKeywordList());
        if (cached.isEmpty()) return;
        this.items.addAll(cached);
        this.localCache.log("feed restored from offline cache: " + this.items.size());
    }

    private void buildList() {
        PullRefreshListView list = new PullRefreshListView(this.activity,
                this.tokens.background, this.tokens.muted, this.tokens.secondary,
                this.session.uiScale() / 100.0f,
                this.session.textScale() / 100.0f);
        this.listView = list;
        this.cachedListView = list;
        list.setBackgroundColor(this.tokens.background);
        list.setCacheColorHint(this.tokens.background);
        list.setDivider(new ColorDrawable(0));
        list.setDividerHeight(dp(2));
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setSelector(new ColorDrawable(0));
        list.setPullRefreshAction(() -> {
            if (!load(true)) list.setRefreshing(false);
        });
        list.addHeaderView(topBar(), null, false);
        int bannerTopMargin = this.roundLayout ? this.host.pageTopPadding() : dp(10);
        this.paginationView = new FeedPaginationView(this.activity, list, this.tokens,
                this.session.uiScale() / 100.0f,
                this.session.textScale() / 100.0f,
                bannerTopMargin, () -> load(false));
        this.adapter = createAdapter(this.items);
        list.setAdapter((ListAdapter) this.adapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int state) {
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
                if (visible > 0) {
                    firstVisible = Math.max(0, first);
                    View child = view.getChildAt(0);
                    firstTop = child == null ? 0 : child.getTop();
                    exposureTracker.markVisible(
                            items, first, visible, list.getHeaderViewsCount());
                }
                if (total > 0 && total - first - visible <= 5 && paging.canPrefetch()) {
                    load(false);
                }
            }
        });
        updatePagination();
        this.cachedContainer = this.paginationView;
    }

    private View topBar() {
        LinearLayout container = vertical(this.tokens.background);
        int outerHorizontal = this.roundLayout ? 0 : dp(10);
        int topPadding = this.roundLayout ? this.host.roundHeaderTopPadding() : dp(6);
        container.setPadding(outerHorizontal, topPadding, outerHorizontal, dp(4));

        LinearLayout heading = new LinearLayout(this.activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        int headingHorizontal = this.roundLayout ? this.host.roundHeaderInset() : dp(2);
        heading.setPadding(headingHorizontal, 0, headingHorizontal, 0);
        TextView title = text("社区", this.roundLayout ? 21.0f : 23.0f, this.tokens.text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(
                -1, dp(this.roundLayout ? 34 : 38)));
        container.addView(heading);

        LinearLayout searchRow = new LinearLayout(this.activity);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(11), 0, dp(8), 0);
        Compat.setBackground(searchRow, UiComponents.round(this.activity,
                this.tokens.panel, 12, this.session.uiScale() / 100.0f));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                -1, dp(this.roundLayout ? 35 : 40));
        if (this.roundLayout) {
            int inset = this.host.roundSearchInset();
            searchParams.leftMargin = inset;
            searchParams.rightMargin = inset;
        }
        container.addView(searchRow, searchParams);
        TextView search = text("搜索帖子、作者或关键词", 12.5f, this.tokens.muted);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setSingleLine(true);
        setLeftIcon(search, R.drawable.il_search, this.tokens.muted, 16);
        search.setOnClickListener(view -> this.host.showSearch());
        searchRow.addView(search, new LinearLayout.LayoutParams(0, -1, 1.0f));
        return container;
    }

    private void applyResponse(JSONObject body, boolean reset, int pull,
                               List<FeedItem> previous, long loadStartedAt) {
        JSONObject result = body.optJSONObject("result");
        JSONArray links = result == null ? null : result.optJSONArray("links");
        String responseLastval = result == null ? "" : result.optString("lastval", "");
        boolean keepPrevious = Json.truthy(result, "keep_previous");
        List<FeedItem> fresh = new ArrayList<>();
        List<FeedItem> loaded = new ArrayList<>();
        int returned = 0;
        if (links != null) {
            for (int index = 0; index < links.length(); index++) {
                JSONObject value = links.optJSONObject(index);
                if (value == null) continue;
                FeedItem item = FeedItem.from(value);
                loaded.add(item);
                if (!FeedCollection.isBlocked(item, this.session.blockKeywordList())) {
                    fresh.add(item);
                }
                returned++;
            }
        }
        this.exposureTracker.recordLoaded(loaded, System.currentTimeMillis());
        int added = applyFreshItems(reset, returned, fresh, previous, keepPrevious);
        if (!reset && returned > 0 && fresh.isEmpty()) {
            this.host.showToast("本页内容已被关键词过滤");
        }
        this.paging.recordResponseCursor(pull, responseLastval);
        if (!reset) {
            this.paging.setNoMore(FeedCollection.loadMoreExhausted(returned, added));
        }
        this.localCache.log("feed " + (reset ? "refresh" : "load more")
                + ": returned=" + returned + ", added=" + added
                + ", cursor=" + !responseLastval.isEmpty()
                + ", noMore=" + this.paging.noMore());
        updatePagination();
        notifyAdapter();
        if (this.listView != null) {
            this.listView.post(() -> this.localCache.log(
                    "perf app stage=feed-first-frame mode="
                            + (reset ? "refresh" : "prefetch")
                            + " totalMs=" + Math.max(0L,
                            SystemClock.elapsedRealtime() - loadStartedAt)));
        }
        if (this.items.isEmpty()) this.host.showMessage("暂时没有获取到社区内容");
    }

    private int applyFreshItems(boolean reset, int returned, List<FeedItem> fresh,
                                List<FeedItem> previous, boolean keepPrevious) {
        if (reset && fresh.isEmpty() && previous != null && !previous.isEmpty()) {
            this.host.showToast("没有获取到新内容，已保留原列表");
            return 0;
        }
        if (!reset && returned == 0) {
            this.paging.setNoMore(true);
            return 0;
        }
        if (reset) this.items.clear();
        int added = FeedCollection.appendUnique(this.items, fresh);
        if (reset && keepPrevious && previous != null) {
            FeedCollection.appendUnique(this.items, previous);
        }
        this.localCache.saveFeed(this.items);
        return added;
    }

    private void applyFailure(String message, boolean reset, List<FeedItem> previous,
                              boolean suppressCacheFallback) {
        this.localCache.log((reset ? "feed refresh failed: " : "feed load more failed: ")
                + message);
        if (reset && previous != null && this.items.isEmpty()) this.items.addAll(previous);
        if (reset && this.items.isEmpty() && !suppressCacheFallback) {
            List<FeedItem> cached = FeedCollection.filter(
                    this.localCache.feedItems(), this.session.blockKeywordList());
            if (!cached.isEmpty()) {
                this.items.addAll(cached);
                this.host.showToast(OFFLINE_CACHE_MESSAGE);
            }
        } else if (!reset) {
            this.paging.markLoadMoreFailed();
        }
        updatePagination();
        notifyAdapter();
        if (!this.items.isEmpty()) {
            this.host.showToast("刷新失败，已保留原内容");
        } else {
            this.host.showMessage("加载失败\n" + message);
        }
    }

    private void setRefreshBusy(boolean busy, PullRefreshListView list) {
        if (list != null) list.setRefreshing(busy);
        if (this.host.isFeedActive() && this.listView == list) {
            this.host.setRefreshBusy(busy);
        }
    }

    private void setRefreshBusy(boolean busy, PullRefreshListView list,
                                int requestSerial) {
        if (list == null) return;
        if (list != this.listView || this.paging.accepts(requestSerial)) {
            list.setRefreshing(busy);
        }
        if (list == this.listView && this.paging.accepts(requestSerial)) {
            this.host.setRefreshBusy(busy);
        }
    }

    private void updatePagination() {
        if (this.paginationView != null) {
            this.paginationView.render(
                    this.paging, !this.items.isEmpty(), this.host.isFeedActive());
        }
    }

    private void closePagination() {
        if (this.paginationView == null) return;
        this.paginationView.close();
        this.paginationView = null;
    }

    private LinearLayout vertical(int color) {
        LinearLayout layout = new LinearLayout(this.activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    private TextView text(String value, float size, int color) {
        TextView view = UiComponents.label(
                this.activity, value, size, color, this.session.textScale() / 100.0f);
        view.setTypeface(Typeface.DEFAULT);
        return view;
    }

    private void setLeftIcon(TextView view, int resource, int color, int size) {
        android.graphics.drawable.Drawable icon = Compat.tintedDrawable(
                this.activity, resource, color);
        if (icon == null) return;
        icon.setBounds(0, 0, dp(size), dp(size));
        view.setCompoundDrawables(icon, null, null, null);
        view.setCompoundDrawablePadding(dp(7));
    }

    private int dp(int value) {
        return UiComponents.dp(this.activity, value, this.session.uiScale() / 100.0f);
    }
}
