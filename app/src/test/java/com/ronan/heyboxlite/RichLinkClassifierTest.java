package com.ronan.heyboxlite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RichLinkClassifierTest {
    @Test
    public void recognizesGameAndTextLinksAsContent() {
        assertTrue(RichLinkClassifier.isContentLink(
                "data-link-type=\"game\" data-game-id=\"42\" icon-url=\"game.png\""));
        assertTrue(RichLinkClassifier.isContentLink(
                "data-link-type=\"text\" icon-url=\"article.png\""));
        assertTrue(RichLinkClassifier.isGameLink(
                "data-link-type='game'data-game-id='42'"));
    }

    @Test
    public void leavesEmojiAnchorsAvailableForEmojiRendering() {
        assertFalse(RichLinkClassifier.isContentLink(
                "icon-url=\"https://img/emoji/cube_smile.png\""));
    }

    @Test
    public void recognizesOfficialGameDetailDeepLinks() {
        String href = "heybox://%7B%22protocol_type%22%3A%22openGameDetail%22%2C"
                + "%22app_id%22%3A607080%2C%22game_type%22%3A%22pc%22%7D";

        assertTrue(RichLinkClassifier.isGameLink("href=\"" + href + "\""));
        assertTrue(RichLinkClassifier.isGameHref(
                "xhh://route?protocol_type=open_game_detail&app_id=42&game_type=pc"));
        assertFalse(RichLinkClassifier.isGameHref(
                "heybox://%7B%22protocol_type%22%3A%22openUserProfile%22%7D"));
    }
}
