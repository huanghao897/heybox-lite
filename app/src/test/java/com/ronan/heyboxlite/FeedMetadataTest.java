package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FeedMetadataTest {
    @Test public void currentOfficialFeedStyleWinsOverCardLayoutAndWrapperFlag() throws Exception {
        for (int type : new int[]{-1, 101, 102, 103}) {
            JSONObject article = new JSONObject().put("content_type", type)
                    .put("link_style", "101").put("is_article", 0);
            assertTrue(FeedItem.from(article).article);
            assertTrue(FeedItem.from(FeedItem.from(article).toCacheJson()).article);
            for (int style : new int[]{100, 102, 200, 300, 301, 400}) {
                assertFalse(FeedItem.from(new JSONObject().put("content_type", type)
                        .put("link_style", style)).article);
            }
        }
        assertTrue(FeedMetadata.isArticle(new JSONObject().put("news_content",
                new JSONObject().put("link_style", 101))));
    }

    @Test public void officialNewsCardsWorkWithoutArticleFlag() throws Exception {
        // BBSLinkObj constants and FeedsFlowItemDtoDeserializer, not title guesses.
        for (int type : new int[]{0, 1, 2, 11, 14, 15, 16, 21, 33, 35, 36,
                37, 38, 39, 43, 50, 54, 101, 103}) {
            JSONObject card = new JSONObject().put("content_type", String.valueOf(type));
            assertTrue("content_type=" + type, FeedItem.from(card).article);
            assertTrue(FeedItem.from(FeedItem.from(card).toCacheJson()).article);
        }
    }

    @Test public void configAndPublishedWorkCardsUseTheirLinkStyle() throws Exception {
        for (int type : new int[]{-1, 102}) {
            JSONObject card = new JSONObject().put("content_type", type).put("link_tag", "1");
            assertTrue(FeedItem.from(card).article);
            for (int tag : new int[]{27, 28}) {
                assertFalse(FeedItem.from(card.put("link_tag", tag)).article);
            }
        }
    }

    @Test public void explicitFalseOverridesNewsStyleAndExplicitNestedArticleWins() throws Exception {
        JSONObject card = new JSONObject().put("content_type", 101).put("is_article", false);
        assertFalse(FeedMetadata.isArticle(card));
        card.put("link_content", new JSONObject().put("is_article", true));
        assertTrue(FeedMetadata.isArticle(card));
        assertTrue(FeedMetadata.isArticle(new JSONObject().put("basic_info",
                "{\"link_tag\":1}")));
    }

    @Test public void commonCardsAndMissingMetadataAreNotArticles() throws Exception {
        for (int type : new int[]{-3, -2, -1, 3, 4, 23, 42, 45, 102, 104, 105}) {
            assertFalse("content_type=" + type,
                    FeedMetadata.isArticle(new JSONObject().put("content_type", type)));
        }
        assertFalse(FeedMetadata.isArticle(null));
        assertFalse(FeedMetadata.isArticle(new JSONObject()));
    }

    @Test public void communityIsSeparatedFromHashtagsAndIconsSurviveCaching() throws Exception {
        JSONObject card = new JSONObject()
                .put("topics", new JSONArray().put(new JSONObject()
                        .put("topic_id", "1").put("name", "硬件")
                        .put("pic_url", "https://img.example/hardware.png")))
                .put("tags", new JSONArray().put("硬件").put("评测"))
                .put("hashtags", new JSONArray().put("日常"));
        assertEquals("硬件", FeedItem.topicName(card));
        assertEquals(Arrays.asList("评测", "日常"), FeedMetadata.tags(card));
        for (JSONObject cached : new JSONObject[]{FeedItem.from(card).toJson(),
                FeedItem.from(card).toCacheJson()}) {
            FeedItem item = FeedItem.from(cached);
            assertEquals("硬件", item.topicName);
            assertEquals("https://img.example/hardware.png", item.topicIcon);
        }
    }

    @Test public void contentTagsNeedCommunityEvidence() throws Exception {
        JSONObject card = new JSONObject().put("content_tags", new JSONArray()
                .put(new JSONObject().put("text", "攻略"))
                .put(new JSONObject().put("text", "游戏社区")
                        .put("protocol", "heybox://{\"path\":\"/bbs/topic\",\"params\":{\"topic_id\":\"2\"}}")));
        assertEquals("游戏社区", FeedItem.topicName(card));
        assertEquals(Collections.singletonList("攻略"), FeedMetadata.tags(card));
        assertEquals("", FeedItem.topicName(
                new JSONObject().put("tags", new JSONArray().put("攻略"))));
    }

    @Test public void feedbackCanSupplyCommunityButNotArbitraryLabels() throws Exception {
        JSONObject card = new JSONObject().put("feedback", new JSONArray()
                .put(new JSONObject().put("options", new JSONArray()
                        .put(new JSONObject().put("text", "不感兴趣"))
                        .put(new JSONObject().put("topic_id", "3").put("topic_name", "数码")))));
        assertEquals("数码", FeedItem.topicName(card));
        assertTrue(FeedMetadata.tags(card).isEmpty());
    }

    @Test public void nestedCommunityAndUiTagsStayDistinct() throws Exception {
        JSONObject card = new JSONObject().put("link_info", new JSONObject()
                .put("topic", new JSONObject().put("name", "游戏"))
                .put("link_extra_tag_v2", new JSONObject().put("children", new JSONArray()
                        .put(new JSONObject().put("text", "评测")))));
        assertEquals("游戏", FeedItem.topicName(card));
        assertEquals(Collections.singletonList("评测"), FeedMetadata.tags(card));
    }

    @Test public void malformedOrCyclicMetadataIsBounded() throws Exception {
        JSONObject card = new JSONObject().put("is_article", JSONObject.NULL)
                .put("link", "{invalid").put("topics", new JSONArray().put(JSONObject.NULL));
        card.put("link_info", card);
        assertFalse(FeedMetadata.isArticle(card));
        assertEquals("", FeedMetadata.section(card).name);
        assertTrue(FeedMetadata.tags(card).isEmpty());
    }
}
