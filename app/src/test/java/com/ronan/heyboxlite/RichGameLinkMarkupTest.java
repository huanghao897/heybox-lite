package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RichGameLinkMarkupTest {
    @Test
    public void keepsOnlyGameNameAndSemanticMarker() {
        String source = "前文<a data-link-type=\"game\" data-game-id=\"42\" "
                + "href=\"/game/42\" target=\"_blank\">意航员2</a>后文";

        String normalized = RichGameLinkMarkup.normalizeAnchors(source);
        RichGameLinkMarkup.Parsed parsed = RichGameLinkMarkup.parse(normalized);

        assertEquals("前文意航员2后文", RichGameLinkMarkup.plainText(normalized));
        assertTrue(parsed.text.contains("意航员2"));
        assertEquals(1, parsed.links.size());
        assertEquals(-1, parsed.text.indexOf("data-link-type"));
    }

    @Test
    public void acceptsAttributesWithoutSeparatingWhitespace() {
        String source = "<a data-link-type=\"game\"data-game-id=\"9\"href=\"/9\">盒友游戏</a>";

        String normalized = RichGameLinkMarkup.normalizeAnchors(source);

        assertEquals("盒友游戏", RichGameLinkMarkup.plainText(normalized));
        assertEquals(1, RichGameLinkMarkup.parse(normalized).links.size());
    }

    @Test
    public void repairsLeakedGameAttributeFragment() {
        String source = "这它喵玩个鬼：data-link-type=\"game\"data-game-id=\"9\""
                + "href=\"/9\"target=\"_blank\">盒友不语只是一味评论";

        String normalized = RichGameLinkMarkup.normalizeAnchors(source);

        assertEquals("这它喵玩个鬼：盒友不语只是一味评论",
                RichGameLinkMarkup.plainText(normalized));
        assertEquals(1, RichGameLinkMarkup.parse(normalized).links.size());
        assertEquals("这它喵玩个鬼：盒友不语只是一味评论",
                RichGameLinkMarkup.plainText(RichContent.commentText(source)));
    }

    @Test
    public void stripsInternalAttributesFromTextLinks() {
        String source = "<a data-link-type=\"text\" href=\"/topic\" "
                + "icon-url=\"icon.png\">活动说明</a>";

        assertEquals("活动说明", RichGameLinkMarkup.normalizeAnchors(source));
    }

    @Test
    public void leavesEmojiAnchorsForEmojiParser() {
        String source = "<a icon-url=\"https://img/emoji/cube_smile.png\">笑</a>";

        assertEquals(source, RichGameLinkMarkup.normalizeAnchors(source));
    }
}
