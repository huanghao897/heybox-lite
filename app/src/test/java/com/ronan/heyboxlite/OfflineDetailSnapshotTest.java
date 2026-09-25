package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class OfflineDetailSnapshotTest {
    @Test public void trimsBeforeCopyingAndDoesNotMutateOnlineComments() throws Exception {
        JSONArray comments = new JSONArray();
        for (int i = 0; i < 1000; i++) comments.put(new JSONObject().put("text", "item" + i));
        JSONObject online = new JSONObject().put("result",
                new JSONObject().put("comments", comments).put("link",
                        new JSONObject().put("title", "title")));
        JSONObject offline = OfflineDetailSnapshot.copy(online);
        JSONArray saved = offline.getJSONObject("result").getJSONArray("comments");
        assertEquals(10, saved.length());
        assertEquals(1000, comments.length());
        comments.getJSONObject(0).put("text", "changed");
        assertEquals("item0", saved.getJSONObject(0).getString("text"));
        assertEquals("title", offline.getJSONObject("result").getJSONObject("link").getString("title"));
    }

    @Test public void doesNotSerializeDiscardedComments() throws Exception {
        JSONArray comments = new JSONArray();
        for (int i = 0; i < 10; i++) comments.put(new JSONObject().put("text", "kept"));
        comments.put(new JSONObject() {
            @Override public String toString() { throw new AssertionError("Do not serialize me"); }
        });
        JSONObject offline = OfflineDetailSnapshot.copy(new JSONObject().put("result",
                new JSONObject().put("comments", comments)));
        assertEquals(10, offline.getJSONObject("result").getJSONArray("comments").length());
    }
}
