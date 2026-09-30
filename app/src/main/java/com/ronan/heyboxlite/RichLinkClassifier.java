package com.ronan.heyboxlite;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLDecoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RichLinkClassifier {
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "(?is)([a-z0-9_-]+)\\s*=\\s*(['\"])(.*?)\\2");

    private RichLinkClassifier() {}

    static final class GameLinkInfo {
        final String appId;
        final String gameType;
        final String hsrc;
        final String skuId;

        GameLinkInfo(String appId, String gameType, String hsrc, String skuId) {
            this.appId = clean(appId);
            this.gameType = clean(gameType);
            this.hsrc = clean(hsrc);
            this.skuId = clean(skuId);
        }

        boolean valid() {
            return !this.appId.isEmpty();
        }

        String cacheKey() {
            return this.appId + "|" + this.gameType + "|" + this.hsrc + "|" + this.skuId;
        }
    }

    static boolean isContentLink(String attributes) {
        return isGameLink(attributes) || isTextLink(attributes);
    }

    static boolean isGameLink(String attributes) {
        String type = attribute(attributes, "data-link-type");
        if (type.isEmpty()) type = attribute(attributes, "data-type");
        if ("game".equals(type) || !gameIdAttribute(attributes).isEmpty()) {
            return true;
        }
        return isGameHref(attribute(attributes, "href"))
                || isGameHref(attribute(attributes, "data-href"));
    }

    static boolean isTextLink(String attributes) {
        return "text".equals(attribute(attributes, "data-link-type"));
    }

    static boolean isGameHref(String href) {
        if (href == null || href.isEmpty()) return false;
        String value = href.trim().toLowerCase(Locale.ROOT);
        if (value.contains("opengamedetail") || value.contains("open_game_detail")) {
            return true;
        }
        boolean heyboxLink = value.startsWith("heybox://")
                || value.startsWith("xhh://");
        return heyboxLink && value.contains("app_id") && value.contains("game_type");
    }

    static GameLinkInfo gameLinkInfo(String attributes) {
        String raw = attributes == null ? "" : attributes;
        if (!isGameLink(raw)) return null;

        String appId = gameIdAttribute(raw);
        String gameType = firstAttribute(raw, "game-type", "data-game-type",
                "data-platform", "platform");
        String hsrc = firstAttribute(raw, "h-src", "data-h-src", "hsrc");
        String skuId = firstAttribute(raw, "sku-id", "data-sku-id", "skuid");
        GameLinkInfo deepLink = parseDeepLink(attributeRaw(raw, "href"));
        if (deepLink != null) {
            if (appId.isEmpty()) appId = deepLink.appId;
            if (gameType.isEmpty()) gameType = deepLink.gameType;
            if (hsrc.isEmpty()) hsrc = deepLink.hsrc;
            if (skuId.isEmpty()) skuId = deepLink.skuId;
        }
        // The official editor emits a game link with only data-game-id and
        // routes it as a PC game when no platform is supplied.
        if (gameType.isEmpty() && !appId.isEmpty()) gameType = "pc";
        GameLinkInfo result = new GameLinkInfo(appId, gameType, hsrc, skuId);
        return result.valid() ? result : null;
    }

    private static String gameIdAttribute(String attributes) {
        String value = firstAttribute(attributes, "data-game-id", "data-app-id",
                "data-appid", "data-steam-appid", "game-id", "appid");
        if (!value.isEmpty()) return value;
        String type = attribute(attributes, "data-link-type");
        if (type.isEmpty()) type = attribute(attributes, "data-type");
        return "game".equals(type) ? attributeRaw(attributes, "data-id") : "";
    }

    private static String firstAttribute(String attributes, String... names) {
        for (String name : names) {
            String value = attributeRaw(attributes, name);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String attribute(String attributes, String expected) {
        return attributeRaw(attributes, expected).toLowerCase(Locale.ROOT);
    }

    private static String attributeRaw(String attributes, String expected) {
        Matcher matcher = ATTRIBUTE.matcher(attributes == null ? "" : attributes);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT).replace('_', '-');
            if (expected.equals(name)) return matcher.group(3).trim();
        }
        return "";
    }

    private static GameLinkInfo parseDeepLink(String href) {
        if (href == null || href.trim().isEmpty()) return null;
        String value = href.trim();
        String decoded = value;
        for (int i = 0; i < 2; i++) {
            String next;
            try {
                next = URLDecoder.decode(decoded, "UTF-8");
            } catch (Exception ignored) {
                break;
            }
            if (next.equals(decoded)) break;
            decoded = next;
        }
        String lower = decoded.toLowerCase(Locale.ROOT);
        int scheme = lower.indexOf("heybox://");
        if (scheme < 0) scheme = lower.indexOf("xhh://");
        String route = scheme >= 0 ? decoded.substring(scheme) : decoded;
        int objectStart = route.indexOf('{');
        if (objectStart >= 0) {
            int objectEnd = route.lastIndexOf('}');
            if (objectEnd > objectStart) {
                try {
                    JSONObject object = new JSONObject(route.substring(objectStart, objectEnd + 1));
                    return new GameLinkInfo(
                            object.optString("app_id", object.optString("appid")),
                            object.optString("game_type", object.optString("platf")),
                            object.optString("h_src", object.optString("hsrc")),
                            object.optString("sku_id", object.optString("skuid")));
                } catch (JSONException ignored) {
                    // Some official links use query-style route arguments.
                }
            }
        }
        return new GameLinkInfo(
                queryValue(route, "app_id", "appid"),
                queryValue(route, "game_type", "platf"),
                queryValue(route, "h_src", "hsrc"),
                queryValue(route, "sku_id", "skuid"));
    }

    private static String queryValue(String value, String... names) {
        if (value == null) return "";
        for (String name : names) {
            Matcher matcher = Pattern.compile(
                    "(?i)(?:[?&#,]|^)" + Pattern.quote(name) + "\\s*[:=]\\s*"
                            + "(?:\"([^\"]*)\"|'([^']*)'|([^&#,}\\s]+))")
                    .matcher(value);
            if (matcher.find()) {
                return clean(matcher.group(1) != null ? matcher.group(1)
                        : matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
            }
        }
        return "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
