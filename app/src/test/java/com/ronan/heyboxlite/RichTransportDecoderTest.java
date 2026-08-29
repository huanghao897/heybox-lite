package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RichTransportDecoderTest {
    @Test
    public void preservesEncodedGameRouteInsideHtml() {
        String source = "<a href=\"heybox://%7B%22protocol_type%22%3A"
                + "%22openGameDetail%22%2C%22app_id%22%3A42%7D\">游戏名称</a>";

        assertEquals(source, RichTransportDecoder.decode(source));
    }

    @Test
    public void decodesTransportEncodedJson() {
        assertEquals("[{\"text\":\"正文\"}]",
                RichTransportDecoder.decodeJson(
                        "%5B%7B%22text%22%3A%22正文%22%7D%5D"));
    }

    @Test
    public void decodesEscapedHtmlWithoutTouchingGameRoute() {
        String source = "\\u003ca href=\\\"heybox://%7B%22protocol_type%22%3A"
                + "%22openGameDetail%22%7D\\\"\\u003e游戏名称\\u003c/a\\u003e";
        String expected = "<a href=\"heybox://%7B%22protocol_type%22%3A"
                + "%22openGameDetail%22%7D\">游戏名称</a>";

        assertEquals(expected, RichTransportDecoder.decode(source));
    }
}
