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

    @Test
    public void unchangedRegistrationKeepsRevisionWhileLightAndDarkUpdatesInvalidate() {
        String code = "cube_revision_stable_976";
        int before = EmojiStore.revision();
        EmojiStore.register(code, "http://fixtures.invalid/revision-light.png",
                "//fixtures.invalid/revision-dark.png");
        int registered = EmojiStore.revision();
        assertEquals(before + 1, registered);
        EmojiStore.register("[" + code + "]", " https://fixtures.invalid/revision-light.png ",
                "https://fixtures.invalid/revision-dark.png");
        EmojiStore.register(code, "", "");
        assertEquals(registered, EmojiStore.revision());
        assertEquals("https://fixtures.invalid/revision-light.png", EmojiStore.url(code, false));
        assertEquals("https://fixtures.invalid/revision-dark.png", EmojiStore.url(code, true));
        EmojiStore.register(code, "https://fixtures.invalid/revision-light-v2.png", "");
        assertEquals(registered + 1, EmojiStore.revision());
        assertEquals("https://fixtures.invalid/revision-dark.png", EmojiStore.url(code, true));
        EmojiStore.register(code, "", "https://fixtures.invalid/revision-dark-v2.png");
        assertEquals(registered + 2, EmojiStore.revision());
        assertEquals("https://fixtures.invalid/revision-dark-v2.png", EmojiStore.url(code, true));
        EmojiStore.register(code, "https://fixtures.invalid/revision-light-v2.png",
                "https://fixtures.invalid/revision-dark-v2.png");
        assertEquals(registered + 2, EmojiStore.revision());
    }
}
