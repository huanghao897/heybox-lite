package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ArticleGameCardsTest {
    @Test
    public void insertsEmbeddedCardsAfterMatchingArticleHeading() throws Exception {
        JSONObject link = new JSONObject()
                .put("is_article", 1)
                .put("game_details", new JSONArray()
                        .put(new JSONObject().put("appid", "10")
                                .put("name", "荒野大镖客：救赎2")
                                .put("image", "https://example.com/rdr2.jpg")));
        List<RichContent.Block> blocks = new ArrayList<>();
        List<RichContent.Block> result = ArticleGameCards.merge(link, blocks);
        assertEquals(1, result.size());
        assertTrue(RichGameCardParser.isCard(result.get(0)));
        assertEquals("10", result.get(0).value);
    }

    @Test
    public void readsTagIdsWhenOnlyIdsArePresent() throws Exception {
        JSONObject link = new JSONObject().put("is_article", 1)
                .put("game_tag_appids", new JSONArray().put("20").put("21"));
        List<RichContent.Block> result = ArticleGameCards.merge(link,
                new ArrayList<RichContent.Block>());
        assertEquals(0, result.size());
    }

    @Test
    public void ignoresUnmarkedGamesArrayInsideTextNode() throws Exception {
        JSONObject link = new JSONObject().put("text", new JSONArray().put(
                new JSONObject().put("type", "text").put("games", new JSONArray().put(
                        new JSONObject().put("appid", "30").put("name", "测试游戏")))));
        List<RichContent.Block> result = ArticleGameCards.merge(link,
                new ArrayList<RichContent.Block>());
        assertEquals(0, result.size());
    }

    @Test
    public void readsOfficialCommunityPostPreloadContentModelList() throws Exception {
        JSONObject preload = new JSONObject().put("postContentSection", new JSONObject()
                .put("content", new JSONObject().put("contentModelList", new JSONArray()
                        .put(new JSONObject().put("type", "game_card")
                                .put("appid", "40")))));
        JSONObject link = new JSONObject().put("_community_post_preload", preload);

        List<RichContent.Block> result = ArticleGameCards.merge(link,
                new ArrayList<RichContent.Block>());

        assertEquals(1, result.size());
        assertEquals("40", result.get(0).value);
    }

    @Test
    public void readsOfficialWebComponentGamePayload() throws Exception {
        JSONArray games = new JSONArray().put(new JSONObject()
                .put("steam_appid", "50")
                .put("name", "网页组件游戏")
                .put("image", "https://example.com/game.jpg"));
        JSONObject preload = new JSONObject().put("preload_web_json_content",
                new JSONArray().put(new JSONObject()
                        .put("cpt", "game")
                        .put("data", games)).toString());
        JSONObject link = new JSONObject().put("_article_web_view", preload);

        List<RichContent.Block> result = ArticleGameCards.merge(link,
                new ArrayList<RichContent.Block>());

        assertEquals(1, result.size());
        assertEquals("50", result.get(0).value);
    }

    @Test
    public void keepsOrdinaryGameLinksOutOfFullCardExtraction() throws Exception {
        JSONObject link = new JSONObject().put("_article_web_view",
                new JSONObject().put("html", "<a data-appid=\"60\">网页游戏</a>"));

        List<RichContent.Block> result = ArticleGameCards.merge(link,
                new ArrayList<RichContent.Block>());

        assertEquals(0, result.size());
    }

    @Test
    public void readsOfficialDataGameIdLinksInsideHtmlText() throws Exception {
        String html = "<p>1. 荒野大镖客：救赎2</p>"
                + "<a data-link-type=\"game\" data-gameid=\"1174180\" href=\"#\">"
                + "荒野大镖客：救赎2</a>";
        JSONObject link = new JSONObject().put("text", new org.json.JSONArray().put(
                new JSONObject().put("type", "html").put("text", html)));

        List<RichContent.Block> result = ArticleGameCards.merge(link,
                new ArrayList<RichContent.Block>());

        assertEquals(1, result.size());
        assertEquals("1174180", result.get(0).value);
        assertTrue(RichGameCardParser.isCard(result.get(0)));
    }

    @Test
    public void readsOfficialDataGameIdWithoutClosingAnchor() throws Exception {
        String html = "<span data-gameid=\"50\">荒野大镖客：救赎2";
        JSONObject link = new JSONObject().put("text", new org.json.JSONArray().put(
                new JSONObject().put("type", "html").put("text", html)));

        assertEquals(1, ArticleGameCards.count(link));
    }

    @Test
    public void readsEscapedOfficialWebComponentPayload() throws Exception {
        String raw = new JSONArray().put(new JSONObject()
                .put("cpt", "game")
                .put("data", new JSONArray().put(new JSONObject()
                        .put("steam_appid", "70")
                        .put("name", "转义传输游戏")
                        .put("image", "https://example.com/escaped.jpg"))))
                .toString().replace("\"", "\\\"");
        JSONObject link = new JSONObject().put("_article_web_view",
                new JSONObject().put("preload_web_json_content", raw));

        List<RichContent.Block> result = ArticleGameCards.merge(link,
                new ArrayList<RichContent.Block>());

        assertEquals(1, result.size());
        assertEquals("70", result.get(0).value);
    }

    @Test
    public void detectsEscapedHtmlBeforeRequestingWebContent() throws Exception {
        String raw = new JSONArray().put(new JSONObject()
                .put("type", "html")
                .put("text", "正文"))
                .toString().replace("\"", "\\\"");
        JSONObject link = new JSONObject().put("text", raw);
        JSONObject body = new JSONObject().put("result", new JSONObject().put("link", link));

        assertTrue(ArticleGameCards.shouldLoadWeb(body, null));
    }

    @Test
    public void readsPreloadNestedInsideOfficialBodySection() throws Exception {
        String preload = new JSONArray().put(new JSONObject()
                .put("type", "game_card")
                .put("appid", "80")).toString();
        JSONObject link = new JSONObject().put("body", new JSONObject()
                .put("post_content_section", new JSONObject()
                        .put("preload_web_json_content", preload)));
        JSONObject normalized = DetailResponseNormalizer.normalize(
                new JSONObject().put("result", new JSONObject().put("link", link)));

        List<RichContent.Block> result = ArticleGameCards.merge(
                normalized.getJSONObject("result").getJSONObject("link"),
                new ArrayList<RichContent.Block>());
        assertEquals(1, result.size());
        assertEquals("80", result.get(0).value);
    }

    @Test
    public void ignoresPlatformOnlyGameMetadata() throws Exception {
        JSONObject link = new JSONObject().put("game_details", new JSONArray().put(
                new JSONObject().put("appid", "90").put("name", "PC游戏")
                        .put("platforms", new JSONArray().put("pc"))));
        assertEquals(0, ArticleGameCards.count(link));
    }

    @Test
    public void readsOfficialWebGameArrayFromGameField() throws Exception {
        JSONObject link = new JSONObject().put("game", new JSONArray()
                .put(new JSONObject().put("steam_appid", "1174180")
                        .put("name", "荒野大镖客：救赎2")
                        .put("image", "https://example.com/rdr2.jpg")
                        .put("score", "9.7")));

        List<RichContent.Block> result = ArticleGameCards.merge(link,
                new ArrayList<RichContent.Block>());

        assertEquals(1, result.size());
        assertEquals("1174180", result.get(0).value);
    }

    @Test
    public void readsTypedGameNodeFromOfficialWebContent() throws Exception {
        JSONObject node = new JSONObject()
                .put("type", "game")
                .put("app_info", new JSONObject().put("steam_appid", "1174180"));
        JSONObject link = new JSONObject().put("_article_web_view",
                new JSONObject().put("preload_web_json_content",
                        new JSONArray().put(node).toString()));

        List<RichContent.Block> result = ArticleGameCards.merge(link,
                new ArrayList<RichContent.Block>());

        assertEquals(1, result.size());
        assertEquals("1174180", result.get(0).value);
    }

    @Test
    public void readsNestedJsonStringFromWebResponse() throws Exception {
        String node = new JSONObject().put("type", "game_card")
                .put("appid", "1174180").toString();
        String nested = new JSONArray().put(node).toString();
        JSONObject link = new JSONObject().put("_article_web_view",
                new JSONObject().put("result", new JSONObject().put("content", nested)));

        List<RichContent.Block> result = ArticleGameCards.merge(link,
                new ArrayList<RichContent.Block>());

        assertEquals(1, result.size());
        assertEquals("1174180", result.get(0).value);
    }

    @Test
    public void readsGameComponentInsideHtmlAttribute() throws Exception {
        String html = "<div data-info=\"{&quot;cpt&quot;:&quot;game&quot;,"
                + "&quot;data&quot;:[{&quot;steam_appid&quot;:&quot;50&quot;,"
                + "&quot;name&quot;:&quot;网页游戏&quot;,&quot;image&quot;:&quot;https://example.com/game.jpg&quot;}] }\"></div>";
        JSONObject link = new JSONObject().put("_article_web_view",
                new JSONObject().put("html", html));

        List<RichContent.Block> result = ArticleGameCards.merge(link,
                new ArrayList<RichContent.Block>());

        assertEquals(1, result.size());
        assertEquals("50", result.get(0).value);
    }

    @Test
    public void rejectsPlatformOnlyGameComponent() throws Exception {
        JSONObject link = new JSONObject().put("text", new JSONArray().put(
                new JSONObject().put("cpt", "game")
                        .put("data", new JSONArray().put(new JSONObject()
                                .put("appid", "90").put("name", "PC游戏")
                                .put("platforms", new JSONArray().put("pc"))))));

        assertEquals(0, ArticleGameCards.count(link));
    }

    @Test
    public void keepsHtmlGameCardsAtTheirOriginalPositions() throws Exception {
        String html = "<p>开头文字</p>"
                + "<div data-gameid=\"101\"></div>"
                + "<p>中间文字</p>"
                + "<div data-gameid=\"202\"></div>"
                + "<p>结尾文字</p>";
        List<ArticleGameMarkupScanner.GameMarker> markers =
                ArticleGameMarkupScanner.scanGameMarkers(html);

        assertEquals(2, markers.size());
        assertEquals("101", markers.get(0).appId);
        assertEquals("202", markers.get(1).appId);
        String replaced = ArticleGameMarkupScanner.replaceGameMarkers(html);
        assertTrue(replaced.indexOf("heybox-game-101")
                < replaced.indexOf("中间文字"));
        assertTrue(replaced.indexOf("中间文字")
                < replaced.indexOf("heybox-game-202"));
    }

    @Test
    public void doesNotTreatHyphenatedGameLinksAsArticleCards() throws Exception {
        String html = "<p>正文</p><a data-game-id=\"303\">普通游戏链接</a><p>结尾</p>";
        assertEquals(0, ArticleGameMarkupScanner.scanGameMarkers(html).size());
        assertEquals(html, ArticleGameMarkupScanner.replaceGameMarkers(html));
    }

    @Test
    public void keepsIdOnlyWebCardButRejectsPlatformName() throws Exception {
        JSONObject web = new JSONObject().put("html",
                "<div data-info=\"{&quot;cpt&quot;:&quot;game&quot;,"
                        + "&quot;data&quot;:[{&quot;appid&quot;:&quot;91&quot;,"
                        + "&quot;name&quot;:&quot;荒野大镖客：救赎2&quot;}] }\"></div>");
        List<RichContent.Block> result = ArticleGameCards.merge(
                new JSONObject().put("_article_web_view", web),
                new ArrayList<RichContent.Block>());
        assertEquals(1, result.size());
        assertEquals("91", result.get(0).value);
    }
}
