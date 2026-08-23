package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;

public class EmojiStoreTest {
    @BeforeClass
    public static void loadFallbackCatalog() throws Exception {
        OfficialEmojiFallback.loadJson("{\"base_url\":\"https://fallback/\","
                + "\"entries\":{\"cube_哇\":\"wow.png\"}}");
    }

    @Test
    public void longestResolvableCode_keepsTextAfterBareOfficialEmoji() {
        assertEquals("cube_哇",
                EmojiStore.longestResolvableCode("cube_哇哇，atm发帖了", false));
    }

    @Test
    public void longestResolvableCode_keepsBracketedOfficialEmoji() {
        assertEquals("[cube_哇]",
                EmojiStore.longestResolvableCode("[cube_哇]", false));
    }

    @Test
    public void remoteCatalogOverridesBundledFallback() {
        EmojiStore.register("cube_哇", "https://remote/wow.png", "");
        assertEquals("https://remote/wow.png", EmojiStore.url("cube_哇", false));
    }
}
