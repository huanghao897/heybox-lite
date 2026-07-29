package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class FeedItemTest {
    @Test
    public void parsesPinnedAndMutualFollowState() throws Exception {
        JSONObject user = new JSONObject();
        user.put("userid", "42");
        user.put("follow_status", 3);
        JSONObject value = new JSONObject();
        value.put("linkid", "post-1");
        value.put("is_top", 1);
        value.put("user", user);

        FeedItem item = FeedItem.from(value);

        assertTrue(item.pinned);
        assertTrue(item.following);
    }

    @Test
    public void unfollowedStateSurvivesOfflineSerialization() throws Exception {
        JSONObject user = new JSONObject();
        user.put("userid", "42");
        user.put("is_following", false);
        JSONObject value = new JSONObject();
        value.put("linkid", "post-1");
        value.put("user", user);

        FeedItem restored = FeedItem.from(FeedItem.from(value).toJson());

        assertFalse(restored.following);
    }

    @Test
    public void topicFallbackSurvivesOfflineSerialization() throws Exception {
        JSONObject value = new JSONObject()
                .put("linkid", "post-1")
                .put("topic_name", "硬件");

        FeedItem restored = FeedItem.from(FeedItem.from(value).toJson());

        assertEquals("硬件", restored.topicName);
    }

    @Test
    public void topicNameReadsOfficialTopicVariants() throws Exception {
        JSONObject objectTopic = new JSONObject()
                .put("topics", new JSONArray()
                        .put(new JSONObject().put("title", "硬件交流")));
        assertEquals("硬件交流", FeedItem.topicName(objectTopic));

        JSONObject stringTopic = new JSONObject()
                .put("topics", new JSONArray().put("数码"));
        assertEquals("数码", FeedItem.topicName(stringTopic));

        assertEquals("游戏", FeedItem.topicName(
                new JSONObject().put("tag_name", "游戏")));
    }
}
