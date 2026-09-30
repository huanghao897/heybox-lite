package com.ronan.heyboxlite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class GameCardData {
    final String appId;
    final String name;
    final String coverUrl;
    final String platforms;
    final String score;
    final String followers;
    final String currentPrice;
    final String originalPrice;
    final String discount;
    final boolean free;

    private GameCardData(String appId, String name, String coverUrl,
                         String platforms, String score, String followers,
                         String currentPrice, String originalPrice,
                         String discount, boolean free) {
        this.appId = appId;
        this.name = name;
        this.coverUrl = coverUrl;
        this.platforms = platforms;
        this.score = score;
        this.followers = followers;
        this.currentPrice = currentPrice;
        this.originalPrice = originalPrice;
        this.discount = discount;
        this.free = free;
    }

    static GameCardData from(JSONObject response, String fallbackAppId) {
        JSONObject value = selectGameObject(response, fallbackAppId);
        if (value == null) return null;

        JSONObject nested = firstObject(value, "app_info", "game", "game_detail",
                "game_info", "data");
        if (nested != null && hasGameFields(nested)) value = nested;

        String appId = first(value, "steam_appid", "appid", "app_id", "appId");
        if (appId.isEmpty()) appId = fallbackAppId == null ? "" : fallbackAppId;
        String name = first(value, "name", "game_name", "title", "name_en");
        String cover = first(value, "image", "game_img", "share_img",
                "share_bg_img", "appicon", "game_icon", "cover", "cover_url");
        String platforms = platforms(value);
        String score = first(value, "score_desc", "score", "impression_score");
        String followers = first(value, "follow_num", "followers");
        if (followers.isEmpty()) {
            JSONObject userNum = value.optJSONObject("user_num");
            followers = userNum == null ? "" : first(userNum, "follow_num", "followers");
        }

        JSONObject platform = firstObjectFromArray(value, "platform_infos");
        JSONObject price = platform == null ? null : platform.optJSONObject("price");
        if (price == null) price = firstObject(value, "price", "price_info");
        String current = price == null ? "" : first(price, "current", "current_price",
                "cost_rmb", "value");
        String original = price == null ? "" : first(price, "initial", "initial_price");
        String discount = price == null ? "" : numericDiscount(price);
        boolean free = value.optBoolean("is_free", false)
                || (price != null && price.optBoolean("is_free", false))
                || isZero(current);

        JSONObject heyboxPrice = value.optJSONObject("heybox_price");
        if (heyboxPrice != null) {
            if (current.isEmpty()) current = coinPrice(heyboxPrice,
                    "cost_coin", "current_coin", "cost");
            if (original.isEmpty()) original = coinPrice(heyboxPrice,
                    "original_coin", "initial_coin", "original");
            if (discount.isEmpty()) discount = numericDiscount(heyboxPrice);
            if (heyboxPrice.optBoolean("is_free", false)) free = true;
        }
        if (current.isEmpty() && value.has("cost_coin")) {
            current = coinPrice(value, "cost_coin");
        }
        if (original.isEmpty() && value.has("original_coin")) {
            original = coinPrice(value, "original_coin");
        }

        if (name.isEmpty() && cover.isEmpty() && score.isEmpty()
                && current.isEmpty() && platforms.isEmpty() && appId.isEmpty()) {
            return null;
        }
        return new GameCardData(appId, name, cover, platforms, score, followers,
                current, original, discount, free);
    }

    static GameCardData fromEmbedded(JSONObject gameCard, String fallbackAppId) {
        return from(gameCard, fallbackAppId);
    }

    boolean hasVisibleDetails() {
        return !this.coverUrl.isEmpty() || !this.score.isEmpty()
                || !this.followers.isEmpty() || !this.currentPrice.isEmpty()
                || !this.originalPrice.isEmpty() || this.free;
    }

    private static JSONObject selectGameObject(JSONObject response, String fallbackAppId) {
        if (response == null) return null;
        JSONObject result = response.optJSONObject("result");
        JSONObject value = result == null ? response : result;
        JSONArray baseInfos = value.optJSONArray("base_infos");
        if (baseInfos == null && result != null) {
            baseInfos = response.optJSONArray("base_infos");
        }
        if (baseInfos != null && baseInfos.length() > 0) {
            String expected = clean(fallbackAppId);
            for (int i = 0; i < baseInfos.length(); i++) {
                JSONObject candidate = baseInfos.optJSONObject(i);
                if (candidate != null && !expected.isEmpty()
                        && expected.equals(gameId(candidate))) {
                    return candidate;
                }
            }
            return null;
        }
        JSONObject baseInfo = value.optJSONObject("base_info");
        if (baseInfo != null) {
            return matches(baseInfo, fallbackAppId) ? baseInfo : null;
        }
        if (!matches(value, fallbackAppId)) return null;
        return value;
    }

    private static boolean matches(JSONObject value, String fallbackAppId) {
        String expected = clean(fallbackAppId);
        String actual = gameId(value);
        return expected.isEmpty() || actual.isEmpty() || expected.equals(actual);
    }

    private static String gameId(JSONObject value) {
        return first(value, "steam_appid", "appid", "app_id", "appId");
    }

    private static JSONObject firstObjectFromArray(JSONObject value, String key) {
        JSONArray array = value == null ? null : value.optJSONArray(key);
        if (array == null) return null;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object != null) return object;
        }
        return null;
    }

    private static JSONObject firstObject(JSONObject value, String... keys) {
        if (value == null) return null;
        for (String key : keys) {
            Object raw = value.opt(key);
            if (raw instanceof JSONObject) return (JSONObject) raw;
            if (raw instanceof String) {
                String text = ((String) raw).trim();
                if (text.startsWith("{")) {
                    try {
                        return new JSONObject(text);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasGameFields(JSONObject value) {
        return value.has("steam_appid") || value.has("appid") || value.has("app_id")
                || value.has("name") || value.has("game_name")
                || value.has("image") || value.has("appicon")
                || value.has("platform_infos") || value.has("heybox_price");
    }

    private static String platforms(JSONObject value) {
        List<String> names = new ArrayList<>();
        JSONArray list = value.optJSONArray("platforms");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                addPlatform(names, list.optString(i));
            }
        }
        if (names.isEmpty()) {
            JSONArray infos = value.optJSONArray("platform_infos");
            if (infos != null) {
                for (int i = 0; i < infos.length(); i++) {
                    JSONObject info = infos.optJSONObject(i);
                    if (info != null) {
                        addPlatform(names, first(info, "name", "key", "platf", "game_type"));
                    }
                }
            }
        }
        if (names.isEmpty()) {
            addPlatform(names, first(value, "platf", "game_type"));
        }
        StringBuilder result = new StringBuilder();
        for (String name : names) {
            if (result.length() > 0) result.append(" · ");
            result.append(name);
        }
        return result.toString();
    }

    private static void addPlatform(List<String> values, String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        String name;
        if ("pc".equals(value) || "steam".equals(value)
                || "platform_steam".equals(value) || "hardware".equals(value)) {
            name = "PC";
        } else if ("mobile".equals(value)) {
            name = "移动端";
        } else if ("ps".equals(value) || "ps4".equals(value)
                || "psn".equals(value) || "platform_ps".equals(value)) {
            name = "PlayStation";
        } else if ("xbox".equals(value) || "platform_xbox".equals(value)) {
            name = "Xbox";
        } else if ("switch".equals(value) || "ns".equals(value)
                || "platform_switch".equals(value)) {
            name = "Switch";
        } else if ("epic".equals(value)) {
            name = "Epic";
        } else {
            name = raw.trim();
        }
        if (!values.contains(name)) values.add(name);
    }

    private static String coinPrice(JSONObject value, String... keys) {
        for (String key : keys) {
            Object raw = value.opt(key);
            if (raw == null || raw == JSONObject.NULL) continue;
            if (raw instanceof Number) {
                return decimal(((Number) raw).doubleValue() / 1000.0d);
            }
            if (!(raw instanceof String)) continue;
            String text = ((String) raw).trim();
            if (text.isEmpty()) continue;
            try {
                return decimal(Double.parseDouble(text) / 1000.0d);
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return "";
    }

    private static boolean isZero(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        String clean = value.trim().replace("¥", "").replace("￥", "");
        try {
            return Double.parseDouble(clean) == 0.0d;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String decimal(double value) {
        DecimalFormat format = new DecimalFormat("0.##",
                DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(value);
    }

    private static String first(JSONObject value, String... keys) {
        if (value == null) return "";
        for (String key : keys) {
            Object item = value.opt(key);
            if (item == null || item == JSONObject.NULL) continue;
            if (!(item instanceof String) && !(item instanceof Number)
                    && !(item instanceof Boolean)) continue;
            String text = String.valueOf(item).trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String numericDiscount(JSONObject value) {
        String raw = first(value, "discount", "discount_rate");
        if (raw.isEmpty()) return "";
        try {
            double number = Double.parseDouble(raw.replace("%", "").trim());
            if (number < 0 || number > 100) return "";
            return decimal(number);
        } catch (NumberFormatException ignored) {
            return "";
        }
    }
}
