package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EmojiStoreTest {
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
}
