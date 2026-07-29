package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FeedExposureTrackerTest {
    @Test
    public void reportsOnlyUnseenPostsFromEarlierPagesOnRefresh() throws Exception {
        FeedExposureTracker tracker = new FeedExposureTracker();
        long now = 1_000_000L;
        List<FeedItem> firstPage = items("1", "2", "3");
        tracker.recordLoaded(firstPage, now);

        assertNull(tracker.valueForRequest(true, now));

        tracker.markVisible(firstPage, 1, 2, 1);
        List<FeedItem> secondPage = items("4", "5");
        tracker.recordLoaded(secondPage, now + 1);

        assertEquals("3", tracker.valueForRequest(true, now + 2));
        assertNull(tracker.valueForRequest(false, now + 2));
    }

    @Test
    public void expiresOldEntriesAndCapsPayloadAtOfficialLimit() throws Exception {
        FeedExposureTracker tracker = new FeedExposureTracker();
        List<FeedItem> oldPage = items("old");
        tracker.recordLoaded(oldPage, 0L);
        tracker.recordLoaded(items("latest"), FeedExposureTracker.MAX_AGE_MS + 1L);
        assertNull(tracker.valueForRequest(
                true, FeedExposureTracker.MAX_AGE_MS + 1L));

        List<FeedItem> many = new ArrayList<>();
        for (int index = 0; index < 70; index++) {
            many.add(item("post-" + index));
        }
        tracker.recordLoaded(many, 2L * FeedExposureTracker.MAX_AGE_MS);
        tracker.recordLoaded(items("next"), 2L * FeedExposureTracker.MAX_AGE_MS + 1L);

        String value = tracker.valueForRequest(
                true, 2L * FeedExposureTracker.MAX_AGE_MS + 2L);
        assertTrue(value.startsWith("post-10"));
        assertTrue(value.endsWith("post-59"));
        assertEquals(FeedExposureTracker.MAX_REPORTED, value.split(",").length);
        assertFalse(value.contains("post-0,"));
    }

    private static List<FeedItem> items(String... ids) throws Exception {
        List<FeedItem> result = new ArrayList<>();
        for (String id : Arrays.asList(ids)) result.add(item(id));
        return result;
    }

    private static FeedItem item(String id) throws Exception {
        return FeedItem.from(new JSONObject().put("linkid", id));
    }
}
