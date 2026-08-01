package com.ronan.heyboxlite;

final class FeedPagingState {
    static final int NO_REQUEST = -1;

    private boolean loadingMore;
    private boolean refreshing;
    private boolean noMore;
    private boolean loadMoreFailed;
    private String lastval = "";
    private Integer lastPull;
    private boolean firstRequest = true;
    private int requestSerial;
    private int resetSerial;

    int begin(boolean reset) {
        if (reset) {
            if (refreshing || loadingMore) return NO_REQUEST;
            refreshing = true;
            noMore = false;
            loadMoreFailed = false;
        } else {
            if (noMore || refreshing || loadingMore) return NO_REQUEST;
            loadingMore = true;
            loadMoreFailed = false;
        }
        int serial = ++requestSerial;
        if (reset) resetSerial = serial;
        return serial;
    }

    void finish(boolean reset, int serial) {
        if (reset) {
            if (accepts(serial)) refreshing = false;
        } else {
            loadingMore = false;
        }
    }

    boolean accepts(int serial) {
        return serial >= resetSerial;
    }

    void resetForNewFeed() {
        lastval = "";
        lastPull = null;
        firstRequest = true;
        noMore = false;
        loadMoreFailed = false;
    }

    void recordResponseCursor(int pull, String value) {
        firstRequest = false;
        lastPull = pull;
        lastval = value;
    }

    boolean canPrefetch() {
        return !noMore && !loadMoreFailed;
    }

    boolean loadingMore() {
        return loadingMore;
    }

    boolean noMore() {
        return noMore;
    }

    void setNoMore(boolean value) {
        noMore = value;
    }

    boolean loadMoreFailed() {
        return loadMoreFailed;
    }

    void markLoadMoreFailed() {
        loadMoreFailed = true;
    }

    String lastval() {
        return lastval;
    }

    Integer lastPull() {
        return lastPull;
    }

    boolean firstRequest() {
        return firstRequest;
    }
}
