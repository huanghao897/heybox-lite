package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.json.JSONObject;
import org.junit.Test;

public class GameCardDataTest {
    @Test
    public void parsesOfficialGameDetailFields() throws Exception {
        JSONObject body = new JSONObject()
                .put("result", new JSONObject()
                        .put("appid", "1077510")
                        .put("name", "星空")
                        .put("image", "https://img.example/header.jpg")
                        .put("platforms", new org.json.JSONArray().put("steam"))
                        .put("score", "9.2")
                        .put("follow_num", "12万")
                        .put("price", new JSONObject()
                                .put("current", "59.6")
                                .put("initial", "298")
                                .put("discount", "80")));

        GameCardData data = GameCardData.from(body, "");

        assertNotNull(data);
        assertEquals("星空", data.name);
        assertEquals("PC", data.platforms);
        assertEquals("59.6", data.currentPrice);
        assertEquals("298", data.originalPrice);
        assertEquals("80", data.discount);
        assertEquals("12万", data.followers);
    }

    @Test
    public void parsesMobileGameAndFreePrice() throws Exception {
        JSONObject body = new JSONObject()
                .put("result", new JSONObject()
                        .put("app_id", "42")
                        .put("game_name", "测试游戏")
                        .put("appicon", "https://img.example/icon.png")
                        .put("game_type", "mobile")
                        .put("price", new JSONObject()
                                .put("current", "0")
                                .put("is_free", true)));

        GameCardData data = GameCardData.from(body, "42");

        assertNotNull(data);
        assertEquals("42", data.appId);
        assertEquals("移动端", data.platforms);
        org.junit.Assert.assertTrue(data.free);
    }

    @Test
    public void selectsMatchingEntryFromBatchResponse() throws Exception {
        JSONObject body = new JSONObject().put("result", new JSONObject().put("base_infos",
                new org.json.JSONArray()
                        .put(new JSONObject().put("appid", "1").put("name", "other"))
                        .put(new JSONObject().put("appid", "42").put("name", "wanted"))));
        GameCardData data = GameCardData.from(body, "42");
        assertNotNull(data);
        assertEquals("wanted", data.name);
    }

    @Test
    public void doesNotUseUnrelatedBatchEntry() throws Exception {
        JSONObject body = new JSONObject().put("result", new JSONObject().put("base_infos",
                new org.json.JSONArray().put(new JSONObject().put("appid", "1")
                        .put("name", "other"))));
        org.junit.Assert.assertNull(GameCardData.from(body, "42"));
    }
}
