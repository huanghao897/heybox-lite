package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/** Shared block predicates kept outside the parser to keep its size bounded. */
final class RichContentSupport {
    private RichContentSupport() {}

    static boolean isReadableBlock(RichContent.Block block) {
        return block != null && !block.image && !RichGameCardParser.isCard(block);
    }

    static boolean hasGameCards(List<RichContent.Block> blocks) {
        if (blocks == null) return false;
        for (RichContent.Block block : blocks) {
            if (RichGameCardParser.isCard(block)) return true;
        }
        return false;
    }

    static int gameCount(List<RichContent.Block> blocks) {
        if (blocks == null) return 0;
        int count = 0;
        for (RichContent.Block block : blocks) {
            if (RichGameCardParser.isCard(block)) count++;
        }
        return count;
    }

    /** Parses an explicit cpt=game payload without treating platform metadata as a card. */
    static void addGamePayload(List<RichContent.Block> blocks, Object raw, int depth) {
        if (depth > 32 || raw == null || raw == JSONObject.NULL) return;
        if (raw instanceof JSONObject) {
            JSONObject object = (JSONObject) raw;
            if (RichGameCardParser.appId(object).length() > 0
                    && !RichGameCardParser.isPlatformDescriptor(object)) {
                blocks.add(RichGameCardParser.block(object));
                return;
            }
            addGamePayload(blocks, object.opt("base_infos"), depth + 1);
            addGamePayload(blocks, object.opt("base_info"), depth + 1);
            addGamePayload(blocks, object.opt("result"), depth + 1);
            addGamePayload(blocks, object.opt("data"), depth + 1);
            return;
        }
        if (raw instanceof JSONArray) {
            JSONArray array = (JSONArray) raw;
            for (int i = 0; i < array.length(); i++) {
                addGamePayload(blocks, array.opt(i), depth + 1);
            }
            return;
        }
        if (!(raw instanceof String)) return;
        String value = RichTransportDecoder.decodeJson((String) raw).trim();
        try {
            if (value.startsWith("{")) {
                addGamePayload(blocks, new JSONObject(value), depth + 1);
            } else if (value.startsWith("[")) {
                addGamePayload(blocks, new JSONArray(value), depth + 1);
            }
        } catch (JSONException ignored) {
            // Ordinary text is parsed by RichContent.
        }
    }
}
