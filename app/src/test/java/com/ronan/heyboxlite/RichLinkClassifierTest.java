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
    }

    @Test
    public void leavesEmojiAnchorsAvailableForEmojiRendering() {
        assertFalse(RichLinkClassifier.isContentLink(
                "icon-url=\"https://img/emoji/cube_smile.png\""));
    }
}
