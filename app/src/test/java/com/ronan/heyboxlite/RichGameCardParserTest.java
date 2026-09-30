package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class RichGameCardParserTest {
    @Test
    public void recognizesTypedGameCardAndKeepsId() throws Exception {
        RichContent.Block block = RichGameCardParser.block(new JSONObject()
                .put("type", "game_card")
                .put("appid", "42"));
        assertTrue(RichGameCardParser.isCard(block));
        assertEquals("42", block.value);
        assertTrue(RichContent.hasGameCards(Collections.singletonList(block)));
    }

    @Test
    public void richTextStreamParsesOfficialGameCardNode() throws Exception {
        JSONObject game = new JSONObject()
                .put("type", "game_card")
                .put("app_info", new JSONObject().put("appid", "42"));
        JSONObject body = new JSONObject().put("text", new org.json.JSONArray()
                .put(game));
        List<RichContent.Block> blocks = RichContent.parse(body, null);
        assertEquals(1, blocks.size());
        assertEquals("42", blocks.get(0).value);
        assertTrue(RichGameCardParser.isCard(blocks.get(0)));
    }

    @Test
    public void parsesTypedTextWrapperAroundOfficialNodes() throws Exception {
        JSONObject game = new JSONObject()
                .put("type", "game_card")
                .put("app_info", new JSONObject().put("appid", "42"));
        JSONObject wrapper = new JSONObject()
                .put("type", "text")
                .put("text", new org.json.JSONArray().put(game).toString());
        JSONObject body = new JSONObject().put("text", wrapper.toString());
        List<RichContent.Block> blocks = RichContent.parse(body, null);
        assertEquals(1, blocks.size());
        assertEquals("42", blocks.get(0).value);
        assertTrue(RichGameCardParser.isCard(blocks.get(0)));
    }

    @Test
    public void leavesWebGameComponentToArticleParser() throws Exception {
        JSONObject game = new JSONObject()
                .put("steam_appid", "77")
                .put("name", "组件游戏")
                .put("image", "https://example.com/component.jpg");
        JSONObject component = new JSONObject()
                .put("cpt", "game")
                .put("data", new org.json.JSONArray().put(game));

        assertTrue(RichGameCardParser.isGameContainer(component));
        List<RichContent.Block> blocks = RichContent.parse(
                new JSONObject().put("text", new org.json.JSONArray().put(component)), null);

        assertEquals(0, blocks.size());
    }

    @Test
    public void recognizesOfficialGameTypeAsCard() throws Exception {
        JSONObject game = new JSONObject()
                .put("type", "game")
                .put("app_info", new JSONObject().put("appid", "42"));
        assertTrue(RichGameCardParser.isGameNode(game));
        List<RichContent.Block> blocks = RichContent.parse(
                new JSONObject().put("text", new org.json.JSONArray().put(game)), null);
        assertEquals(1, blocks.size());
        assertEquals("42", blocks.get(0).value);
        assertTrue(RichGameCardParser.isCard(blocks.get(0)));
    }

    @Test
    public void rejectsSpacedPlatformNameEvenWhenTheDescriptorHasAnIcon() throws Exception {
        JSONObject descriptor = new JSONObject()
                .put("appid", "99")
                .put("name", "PC 游戏")
                .put("image", "https://example.com/platform.png");

        assertTrue(RichGameCardParser.isPlatformDescriptor(descriptor));
    }
}
