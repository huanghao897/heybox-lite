package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Boundary guard for malformed rich content. Recursion limits handle normal
 * input; this class is the single last-resort guard for a parser stack error.
 */
final class RichContentRecovery {
    private RichContentRecovery() {
    }

    static List<RichContent.Block> parseObject(JSONObject source, JSONArray fallbackImages) {
        try {
            return RichContent.parseInternal(source, fallbackImages);
        } catch (StackOverflowError error) {
            return RichContent.fallbackBlocks(fallbackImages);
        }
    }

    static List<RichContent.Block> parseText(String source, JSONArray fallbackImages) {
        try {
            return RichContent.parseTextInternal(source, fallbackImages);
        } catch (StackOverflowError error) {
            return RichContent.fallbackBlocks(fallbackImages);
        }
    }
}
