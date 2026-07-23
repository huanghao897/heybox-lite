package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

public class FeedCollectionTest {
    @Test
    public void appendUniqueReportsOnlyNewPosts() throws Exception {
        List<FeedItem> target = new ArrayList<>();
        target.add(item("1"));

        int added = FeedCollection.appendUnique(target,
                Arrays.asList(item("1"), item("2"), item("3")));

        assertEquals(2, added);
        assertEquals(3, target.size());
        assertEquals("2", target.get(1).id);
        assertEquals("3", target.get(2).id);
    }

    @Test
    public void cursorlessPageWithNewPostsCanContinueLoading() {
        assertFalse(FeedCollection.loadMoreExhausted(10, 10));
        assertTrue(FeedCollection.loadMoreExhausted(0, 0));
        assertTrue(FeedCollection.loadMoreExhausted(10, 0));
    }

    private static FeedItem item(String id) throws Exception {
        JSONObject value = new JSONObject();
        value.put("linkid", id);
        value.put("title", "post " + id);
        return FeedItem.from(value);
    }
}
