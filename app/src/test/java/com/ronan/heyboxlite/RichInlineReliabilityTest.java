package com.ronan.heyboxlite;

import static org.junit.Assert.*;

import android.app.Application;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class RichInlineReliabilityTest {
    private static final String EMOJI = "<a icon-url='https://fixtures.invalid/emoji/cube_lead.png' "
            + "icon-dark-url='https://fixtures.invalid/emoji/cube_lead_dark.png'>cube_lead</a>";
    private static final String LOTTERY = "<a data-link-type='text' "
            + "href='https://example.invalid/lottery?route=%7B%22id%22%3A1%7D&amp;x=2' "
            + "icon-url='https://fixtures.invalid/ticket.png' "
            + "icon-dark-url='https://fixtures.invalid/ticket-dark.png'>Lottery</a>";

    @Test public void leadingEmojiSurvivesHtmlAndStructuredTextBlocks() throws Exception {
        String source = "<p>" + EMOJI + " ordinary text</p>";
        assertEquals("[cube_lead] ordinary text", RichContent.commentText(source));
        List<RichContent.Block> html = RichContent.parse(source, null);
        assertEquals(1, html.size());
        assertEquals("[cube_lead] ordinary text", html.get(0).value);
        String json = new JSONArray().put(new JSONObject().put("type", "text")
                .put("text", source)).toString();
        assertEquals("[cube_lead] ordinary text", RichContent.commentText(json));
        assertEquals("https://fixtures.invalid/emoji/cube_lead_dark.png",
                EmojiStore.url("[cube_lead]", true));
    }

    @Test public void emojiOnlyHtmlIsNotJsonNoiseAndUnknownTokensRemainReadable() {
        assertEquals("[cube_lead]", RichContent.commentText("<p>" + EMOJI + "</p>"));
        assertEquals("[not_catalogued] tail",
                RichContent.commentText("<p>[not_catalogued] tail</p>"));
        assertTrue(RichContent.parse("<p>[{\"type\":\"img\"}]</p>", null).isEmpty());
    }

    @Test public void jsonLookingUnknownTokensKeepBracketsInCommentsAndDetailBodies() {
        for (String code : new String[]{"not_catalogued", "true_emoji", "false_emoji",
                "null_emoji", "42emoji", "-emoji"}) {
            String token = "[" + code + "]";
            String text = token + " tail";
            assertEquals(code, text, RichContent.commentText(text));
            assertEquals(code, text, RichContent.commentText("<p>" + text + "</p>"));
            assertEquals(code, token, RichContent.parseDetailBody(token, false).blocks.get(0).value);
            assertEquals(code, text, RichContent.parseDetailBody(text, false).blocks.get(0).value);
        }
    }

    @Test public void actualJsonArraysRemainStructured() throws Exception {
        String source = new JSONArray().put(new JSONObject().put("type", "text")
                .put("text", "Structured text")).toString();
        assertEquals("Structured text", RichContent.commentText(source));
        assertEquals("Structured text", RichContent.parseDetailBody(source, false).blocks.get(0).value);
        for (String array : new String[]{"[]", "[null]", "[true,false]",
                "[-1,0,2.5,3e-2]"}) {
            assertTrue(array, RichContent.parse(array, null).isEmpty());
            assertTrue(array, RichContent.parseDetailBody(array, false).blocks.isEmpty());
        }
    }

    @Test public void officialTextLinksRetainMetadataBesideEmojiAndGameLinks() {
        String game = "<a data-link-type='game' data-game-id='42'>Game</a>";
        String parsed = RichContent.commentText("<p>" + EMOJI + " " + LOTTERY + " " + game + "</p>");
        assertEquals("[cube_lead] Lottery Game", RichInlineRenderer.plainText(parsed));
        List<RichGameLinkMarkup.Link> links = RichGameLinkMarkup.parse(parsed).links;
        assertEquals(2, links.size());
        RichGameLinkMarkup.Link lottery = links.get(0);
        assertTrue(lottery.textLink);
        assertNull(lottery.game);
        assertEquals("https://example.invalid/lottery?route=%7B%22id%22%3A1%7D&x=2", lottery.href);
        assertEquals("https://fixtures.invalid/ticket-dark.png", lottery.iconUrl(true));
        assertFalse(links.get(1).textLink);
        assertEquals("42", links.get(1).game.appId);
        assertEquals("[cube_lead] Lottery Game", RichContent.plainText(parsed));
    }

    @Test public void metadataDoesNotMakeAShortLinkReplaceTheFullComment() {
        assertEquals("The full comment must stay visible", RichContent.commentText(
                "The full comment must stay visible", LOTTERY));
        String rich = RichContent.commentText("Lottery", LOTTERY);
        assertTrue(RichGameLinkMarkup.hasMarkup(rich));
        assertEquals("Lottery", RichInlineRenderer.plainText(rich));
    }

    @Test public void anchorsKeepLabelsThroughEscapedTransportAndRepeatedParsing() throws Exception {
        String json = new JSONArray().put(new JSONObject().put("type", "text")
                .put("text", LOTTERY)).toString();
        String normalized = RichContent.commentText(json);
        for (int pass = 0; pass < 3; pass++) {
            assertEquals("Lottery", RichInlineRenderer.plainText(normalized));
            assertEquals(1, RichGameLinkMarkup.parse(normalized).links.size());
            normalized = RichContent.commentText(normalized);
        }
        String escaped = LOTTERY.replace("<", "&lt;").replace(">", "&gt;");
        assertEquals("Lottery", RichContent.plainText(escaped));
        assertEquals(1, RichGameLinkMarkup.parse(RichContent.commentText(escaped)).links.size());
    }
}
