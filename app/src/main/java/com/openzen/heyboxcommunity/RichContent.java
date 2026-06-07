package com.openzen.heyboxcommunity;

import android.text.Html;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RichContent {
    static final class Block {
        final boolean image;
        final String value;

        private Block(boolean image, String value) {
            this.image = image;
            this.value = value;
        }
    }

    private static final Pattern IMAGE = Pattern.compile(
            "(?is)<img\\b[^>]*?(?:data-original|data-src|src)\\s*=\\s*(['\"])(.*?)\\1[^>]*>");

    private RichContent() {}

    static List<Block> parse(String source, JSONArray fallbackImages) {
        List<Block> blocks = new ArrayList<>();
        Set<String> imageUrls = new HashSet<>();
        source = decodeTransport(source);
        try {
            JSONArray content = new JSONArray(source);
            for (int i = 0; i < content.length(); i++) {
                JSONObject item = content.optJSONObject(i);
                if (item == null) continue;
                String type = item.optString("type");
                if ("img".equals(type) || "image".equals(type)) {
                    addImage(blocks, imageUrls,
                            first(item.optString("url"), item.optString("src"), item.optString("text")));
                } else {
                    addHtml(blocks, imageUrls,
                            first(item.optString("text"), item.optString("content")));
                }
            }
        } catch (Exception ignored) {
            addHtml(blocks, imageUrls, source);
        }

        if (fallbackImages != null) {
            for (int i = 0; i < fallbackImages.length(); i++) {
                Object value = fallbackImages.opt(i);
                if (value instanceof JSONObject) {
                    JSONObject image = (JSONObject) value;
                    addImage(blocks, imageUrls,
                            first(image.optString("url"), image.optString("src"), image.optString("original")));
                } else {
                    addImage(blocks, imageUrls, fallbackImages.optString(i));
                }
            }
        }
        return blocks;
    }

    static String plainText(String source) {
        List<Block> blocks = parse(source, null);
        StringBuilder value = new StringBuilder();
        for (Block block : blocks) {
            if (block.image || block.value.isEmpty()) continue;
            if (value.length() > 0) value.append('\n');
            value.append(block.value);
        }
        return value.toString().trim();
    }

    private static void addHtml(List<Block> blocks, Set<String> imageUrls, String html) {
        if (html == null || html.isEmpty()) return;
        Matcher matcher = IMAGE.matcher(html);
        int start = 0;
        while (matcher.find()) {
            addText(blocks, html.substring(start, matcher.start()));
            addImage(blocks, imageUrls, decodeHtml(matcher.group(2)));
            start = matcher.end();
        }
        addText(blocks, html.substring(start));
    }

    private static void addText(List<Block> blocks, String html) {
        String value = decodeHtml(html)
                .replace('\u00a0', ' ')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (!value.isEmpty()) blocks.add(new Block(false, value));
    }

    private static void addImage(List<Block> blocks, Set<String> imageUrls, String url) {
        if (url == null) return;
        String value = decodeHtml(url).replace("\\/", "/").trim();
        if (value.regionMatches(true, 0, "http:", 0, 5)) value = "https:" + value.substring(5);
        if (!value.startsWith("https://")) return;
        if (imageUrls.add(value)) blocks.add(new Block(true, value));
    }

    private static String decodeHtml(String value) {
        if (value == null || value.isEmpty()) return "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString();
        }
        return legacyFromHtml(value);
    }

    @SuppressWarnings("deprecation")
    private static String legacyFromHtml(String value) {
        return Html.fromHtml(value).toString();
    }

    private static String decodeTransport(String value) {
        if (value == null || value.isEmpty()) return "";
        String decoded = value
                .replace("\\u003c", "<")
                .replace("\\u003e", ">")
                .replace("\\u0026", "&")
                .replace("\\/", "/");
        for (int i = 0; i < 2; i++) {
            String lower = decoded.toLowerCase(Locale.ROOT);
            if (!lower.contains("%3c") && !lower.contains("%5b")
                    && !lower.contains("%7b")) break;
            try {
                String next = URLDecoder.decode(decoded.replace("+", "%2B"),
                        "UTF-8");
                if (next.equals(decoded)) break;
                decoded = next;
            } catch (Exception ignored) {
                break;
            }
        }
        return decoded;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }
}
