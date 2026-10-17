package com.ronan.heyboxlite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class FeedPagingStateTest {
    @Test
    public void blocksConcurrentRequests() {
        FeedPagingState state = new FeedPagingState();

        int refresh = state.begin(true);

        assertTrue(refresh > 0);
        assertEquals(FeedPagingState.NO_REQUEST, state.begin(true));
        assertEquals(FeedPagingState.NO_REQUEST, state.begin(false));
        state.finish(true, refresh);
        assertTrue(state.begin(false) > refresh);
    }

    @Test
    public void failedAndExhaustedPagesBlockPrefetch() {
        FeedPagingState state = new FeedPagingState();

        state.markLoadMoreFailed();
        assertFalse(state.canPrefetch());
        state.resetForNewFeed();
        assertTrue(state.canPrefetch());
        state.setNoMore(true);
        assertFalse(state.canPrefetch());
        assertEquals(FeedPagingState.NO_REQUEST, state.begin(false));
    }

    @Test
    public void tracksAndResetsOfficialCursor() {
        FeedPagingState state = new FeedPagingState();

        state.recordResponseCursor(0, "next-page");
        assertFalse(state.firstRequest());
        assertEquals(Integer.valueOf(0), state.lastPull());
        assertEquals("next-page", state.lastval());

        state.resetForNewFeed();
        assertTrue(state.firstRequest());
        assertNull(state.lastPull());
        assertEquals("", state.lastval());
    }

    @Test
    public void acceptsOnlyResponsesAtOrAfterLatestReset() {
        FeedPagingState state = new FeedPagingState();
        int refresh = state.begin(true);

        assertFalse(state.accepts(refresh - 1));
        assertTrue(state.accepts(refresh));
        state.finish(true, refresh);
    }

    @Test
    public void resettingDuringRefreshReleasesBusyStateAndRejectsTheOldCompletion() {
        FeedPagingState state = new FeedPagingState();
        int oldRefresh = state.begin(true);

        state.resetForNewFeed();

        assertFalse(state.refreshing());
        assertFalse(state.accepts(oldRefresh));
        int newRefresh = state.begin(true);
        assertTrue(newRefresh > oldRefresh);
        state.finish(true, oldRefresh);
        assertTrue(state.refreshing());
        state.finish(true, newRefresh);
        assertFalse(state.refreshing());
    }

    @Test
    public void oldLoadMoreCompletionCannotFinishTheNewSessionsLoadMore() {
        FeedPagingState state = new FeedPagingState();
        int oldPage = state.begin(false);

        state.resetForNewFeed();

        assertFalse(state.loadingMore());
        assertFalse(state.accepts(oldPage));
        int newPage = state.begin(false);
        assertTrue(newPage > oldPage);
        state.finish(false, oldPage);
        assertTrue(state.loadingMore());
        state.finish(false, newPage);
        assertFalse(state.loadingMore());
    }
}
