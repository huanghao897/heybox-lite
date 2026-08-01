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
}
