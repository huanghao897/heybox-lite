package com.ronan.heyboxlite;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/** Small parser for the official typed-text game card node. */
final class RichGameCardParser {
    private RichGameCardParser() {
    }

    static boolean isType(String type) {
        if (type == null) return false;
        String value = type.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return "game".equals(value) || "game_card".equals(value)
                || "gamecard".equals(value) || "game_card_content".equals(value);
    }

    static boolean isGameContainer(JSONObject item) {
        if (item == null) return false;
        // The official web content model uses cpt=game. Generic component/kind
        // fields are also used by platform and section descriptors.
        String component = item.optString("cpt", "").trim();
        if (component.isEmpty()) return false;
        String value = component.toLowerCase(Locale.ROOT).replace('-', '_');
        return "game".equals(value) || "game_card".equals(value)
                || "gamecard".equals(value) || value.endsWith("_game");
    }

    static Object gamePayload(JSONObject item) {
        if (item == null) return null;
        Object result = item.opt("data");
        if (result == null || result == JSONObject.NULL) result = item.opt("game");
        if (result == null || result == JSONObject.NULL) result = item.opt("games");
        if (result == null || result == JSONObject.NULL) result = item.opt("value");
        if (result == null || result == JSONObject.NULL) result = item.opt("items");
        if (result == null || result == JSONObject.NULL) result = item.opt("content");
        if (result == null || result == JSONObject.NULL) result = item.opt("data_info");
        if (result == null || result == JSONObject.NULL) result = item.opt("dataInfo");
        if (result instanceof JSONObject) {
            JSONObject object = (JSONObject) result;
            Object baseInfos = object.opt("base_infos");
            if (baseInfos != null && baseInfos != JSONObject.NULL) return baseInfos;
            JSONObject nestedResult = object.optJSONObject("result");
            if (nestedResult != null) {
                Object nestedBaseInfos = nestedResult.opt("base_infos");
                if (nestedBaseInfos != null && nestedBaseInfos != JSONObject.NULL) {
                    return nestedBaseInfos;
                }
            }
        }
        return result;
    }

    static RichContent.Block block(JSONObject item) {
        if (item == null) return null;
        String appId = first(item.optString("appid"), item.optString("app_id"),
                item.optString("appId"), item.optString("steam_appid"));
        JSONObject appInfo = objectValue(item.opt("app_info"));
        if (appId.isEmpty() && appInfo != null) {
            appId = first(appInfo.optString("appid"), appInfo.optString("app_id"),
                    appInfo.optString("appId"), appInfo.optString("steam_appid"));
        }
        return RichContent.Block.gameCard(item, appId);
    }

    static boolean isGameNode(JSONObject item) {
        if (item == null) return false;
        String type = item.optString("type", "").trim().toLowerCase(Locale.ROOT)
                .replace('-', '_');
        if (!isType(type) || appId(item).isEmpty()) return false;
        // `type=game` is an official typed-text card form. Its app_info may
        // contain only the app id; the detail endpoint fills the card fields.
        return true;
    }

    /** Full GameObj-like metadata, as opposed to a section/platform descriptor. */
    static boolean hasRenderableData(JSONObject item) {
        if (item == null || appId(item).isEmpty()) return false;
        JSONObject nested = objectValue(item.opt("app_info"));
        JSONObject value = nested == null ? item : nested;
        // `game_details` also contains platform/section descriptors with an
        // appid and a display name. They are not cards. A real GameObj has a
        // cover, or at least one store metric that the card can show.
        return hasAny(value, "image", "share_img", "share_bg_img", "cover",
                "cover_url")
                || hasAny(value, "score", "score_desc", "follow_num", "followers",
                "heybox_price", "price", "price_info", "is_free", "cost_coin",
                "original_coin");
    }

    static boolean isPlatformDescriptor(JSONObject item) {
        if (item == null) return false;
        JSONObject nested = objectValue(item.opt("app_info"));
        JSONObject value = nested == null ? item : nested;
        String name = firstField(value, "name", "title", "game_name")
                .toLowerCase(Locale.ROOT);
        if (name.isEmpty()) return false;
        String compact = name.replaceAll("\\s+", "");
        if ("pc游戏".equals(compact) || "steam移动端".equals(compact)
                || "手机游戏".equals(compact) || "移动端".equals(compact)
                || "主机游戏".equals(compact) || "steam".equals(compact)
                || "pc".equals(compact)) {
            return true;
        }
        return !hasRenderableData(item) && (compact.endsWith("游戏")
                && (compact.startsWith("pc") || compact.startsWith("steam")));
    }

    static String appId(JSONObject item) {
        if (item == null) return "";
        String id = first(item.optString("appid"), item.optString("app_id"),
                item.optString("appId"), item.optString("steam_appid"));
        if (!id.isEmpty()) return id;
        JSONObject nested = objectValue(item.opt("app_info"));
        return nested == null ? "" : first(nested.optString("appid"),
                nested.optString("app_id"), nested.optString("appId"),
                nested.optString("steam_appid"));
    }

    static boolean isCard(RichContent.Block block) {
        return block != null && block.kind == RichContent.Block.GAME_CARD;
    }

    private static JSONObject objectValue(Object value) {
        if (value instanceof JSONObject) return (JSONObject) value;
        if (!(value instanceof String)) return null;
        String text = ((String) value).trim();
        if (!text.startsWith("{")) return null;
        try {
            return new JSONObject(text);
        } catch (JSONException ignored) {
            return null;
        }
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static String firstField(JSONObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static boolean hasAny(JSONObject object, String... keys) {
        if (object == null) return false;
        for (String key : keys) {
            Object value = object.opt(key);
            if (value == null || value == JSONObject.NULL) continue;
            if (value instanceof String && ((String) value).trim().isEmpty()) continue;
            return true;
        }
        return false;
    }
}
