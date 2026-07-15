package com.ronan.heyboxlite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SearchState {
    private static final int MAX_STALE_PAGES = 3;
    private final List<FeedItem> items = new ArrayList<>();
    private String keyword = "";
    private int offset;
    private int generation;
    private int stalePages;
    private int listPosition;
    private int listTopOffset;
    private boolean endReached;
    private boolean loadingMore;

    List<FeedItem> items() {
        return this.items;
    }

    String keyword() {
        return this.keyword;
    }

    int offset() {
        return this.offset;
    }

    int generation() {
        return this.generation;
    }

    int listPosition() {
        return this.listPosition;
    }

    int listTopOffset() {
        return this.listTopOffset;
    }

    boolean endReached() {
        return this.endReached;
    }

    boolean hasResults() {
        return !this.keyword.isEmpty() && !this.items.isEmpty();
    }

    int begin(String keyword) {
        this.keyword = keyword == null ? "" : keyword;
        this.items.clear();
        this.offset = 0;
        this.endReached = false;
        this.loadingMore = false;
        this.stalePages = 0;
        return ++this.generation;
    }

    void invalidateRequests() {
        this.generation++;
        this.loadingMore = false;
    }

    boolean isCurrent(int generation) {
        return generation == this.generation;
    }

    void replace(List<FeedItem> values, int serverCount) {
        this.items.clear();
        appendUnique(values);
        this.offset = Math.max(0, serverCount);
        this.endReached = serverCount <= 0;
        this.stalePages = 0;
    }

    boolean beginLoadMore() {
        if (this.loadingMore || this.endReached || this.keyword.isEmpty()) return false;
        this.loadingMore = true;
        return true;
    }

    int appendPage(List<FeedItem> values, int serverCount) {
        this.loadingMore = false;
        if (serverCount <= 0) {
            this.endReached = true;
            return 0;
        }
        this.offset += serverCount;
        int added = appendUnique(values);
        if (added > 0) {
            this.stalePages = 0;
        } else if (++this.stalePages >= MAX_STALE_PAGES) {
            this.endReached = true;
        }
        return added;
    }

    void failLoadMore() {
        this.loadingMore = false;
    }

    void saveListPosition(int position, int topOffset) {
        this.listPosition = Math.max(0, position);
        this.listTopOffset = topOffset;
    }

    private int appendUnique(List<FeedItem> incoming) {
        Set<String> ids = new HashSet<>();
        for (FeedItem item : this.items) ids.add(item.id);
        int added = 0;
        if (incoming == null) return added;
        for (FeedItem item : incoming) {
            if (item != null && !item.id.isEmpty() && ids.add(item.id)) {
                this.items.add(item);
                added++;
            }
        }
        return added;
    }
}
