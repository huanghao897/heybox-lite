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

    @Test
    public void topicNameReadsCurrentFeedContentTags() throws Exception {
        JSONObject value = new JSONObject()
                .put("content_tags", new JSONArray()
                        .put(new JSONObject().put("text", "硬件交流")))
                .put("link_tag", 27);

        assertEquals("硬件交流", FeedItem.topicName(value));

        JSONObject uiKitTag = new JSONObject()
                .put("link_extra_tag_v2", new JSONObject()
                        .put("children", new JSONArray()
                                .put(new JSONObject().put("text", "评测"))));
        assertEquals("评测", FeedItem.topicName(uiKitTag));
    }

    @Test
    public void distinguishesOfficialArticleAndPostTypes() throws Exception {
        assertTrue(FeedItem.from(new JSONObject()
                .put("linkid", "article")
                .put("is_article", 1)
                .put("use_concept_type", 1)).article);
        assertTrue(FeedItem.from(new JSONObject()
                .put("linkid", "news")
                .put("content_type", 101)).article);
        assertTrue(FeedItem.from(new JSONObject()
                .put("linkid", "published-work")
                .put("content_type", -1)).article);
        assertFalse(FeedItem.from(new JSONObject()
                .put("linkid", "post")
                .put("content_type", 102)).article);
        assertTrue(FeedItem.from(new JSONObject()
                .put("linkid", "concept-article")
                .put("use_concept_type", 0)).article);
        assertFalse(FeedItem.from(new JSONObject()
                .put("linkid", "concept-post")
                .put("use_concept_type", 1)).article);
    }

    @Test
    public void ignoresEmptyArticleFlagAndUsesTheNextOfficialField() throws Exception {
        assertTrue(FeedItem.from(new JSONObject()
                .put("linkid", "empty-flag")
                .put("is_article", "")
                .put("content_type", 101)).article);
        assertTrue(FeedItem.from(new JSONObject()
                .put("linkid", "null-flag")
                .put("is_article", JSONObject.NULL)
                .put("link_info", new JSONObject().put("is_article", 1))).article);
    }

    @Test
    public void recognizesTextArticleTypesAndOfficialConceptMapping() throws Exception {
        assertTrue(FeedItem.from(new JSONObject()
                .put("linkid", "link-type")
                .put("link_type", "article")).article);
        assertTrue(FeedItem.from(new JSONObject()
                .put("linkid", "type")
                .put("type", "news")).article);
        assertTrue(FeedItem.from(new JSONObject()
                .put("linkid", "concept-article")
                .put("use_concept_type", 0)).article);
        assertFalse(FeedItem.from(new JSONObject()
                .put("linkid", "concept-post")
                .put("use_concept_type", 1)).article);
        assertTrue(FeedItem.from(new JSONObject()
                .put("linkid", "post-with-article-child")
                .put("use_concept_type", 1)
                .put("link_info", new JSONObject().put("is_article", 1))).article);
    }

    @Test
    public void recognizesOfficialConceptTypeAsString() throws Exception {
        assertTrue(FeedItem.from(new JSONObject()
                .put("linkid", "string-article")
                .put("use_concept_type", "0")).article);
        assertFalse(FeedItem.from(new JSONObject()
                .put("linkid", "string-post")
                .put("use_concept_type", "1")).article);
    }

    @Test
    public void readsArticleMetadataFromCurrentNestedFeedShape() throws Exception {
        FeedItem item = FeedItem.from(new JSONObject()
                .put("linkid", "nested-article")
                .put("link_info", new JSONObject().put("is_article", 1)));

        assertTrue(item.article);
    }

    @Test
    public void nestedArticleMarkerOverridesWrapperPostMarker() throws Exception {
        assertTrue(FeedItem.from(new JSONObject()
                .put("linkid", "wrapped-article")
                .put("is_article", 0)
                .put("content_type", 102)
                .put("link_info", new JSONObject().put("is_article", 1))).article);
        assertTrue(FeedItem.from(new JSONObject()
                .put("linkid", "wrapped-article-string")
                .put("is_article", 0)
                .put("link_info", "{\"is_article\":1}")).article);
    }

    @Test
    public void articleTypeSurvivesOfflineSerialization() throws Exception {
        FeedItem article = FeedItem.from(new JSONObject()
                .put("linkid", "article")
                .put("is_article", 1));

        assertTrue(FeedItem.from(article.toJson()).article);
    }

    @Test
    public void recognizesVideoFeedAndPreservesItOffline() throws Exception {
        FeedItem video = FeedItem.from(new JSONObject()
                .put("linkid", "video")
                .put("has_video", 1)
                .put("video_url", "https://video.example/test.mp4"));

        assertTrue(video.video);
        assertTrue(FeedItem.from(video.toJson()).video);
        assertEquals("https://video.example/test.mp4",
                FeedItem.from(video.toJson()).videos.get(0).url);
    }

    @Test
    public void recognizesNestedVideoFeed() throws Exception {
        FeedItem video = FeedItem.from(new JSONObject()
                .put("linkid", "nested-video")
                .put("link_info", new JSONObject()
                        .put("type", "video")
                        .put("play_url", "https://video.example/test.mp4")));

        assertTrue(video.video);
    }

    @Test
    public void doesNotTreatARegularImagePostAsVideo() throws Exception {
        FeedItem post = FeedItem.from(new JSONObject()
                .put("linkid", "image-post")
                .put("has_video", 1)
                .put("imgs", new JSONArray().put("https://img.example/post.jpg")));

        assertFalse(post.video);
        assertTrue(post.videos.isEmpty());
    }

    @Test
    public void likesUseFirstValidOfficialField() throws Exception {
        FeedItem fallback = FeedItem.from(new JSONObject()
                .put("linkid", "fallback")
                .put("link_award_num", "invalid")
                .put("like_num", 12)
                .put("up", 99));
        FeedItem priority = FeedItem.from(new JSONObject()
                .put("linkid", "priority")
                .put("link_award_num", 0)
                .put("like_num", 12));

        assertEquals(12, fallback.likes);
        assertEquals(0, priority.likes);
    }
}
