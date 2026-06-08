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
    private static final Pattern INLINE_EMOJI = Pattern.compile(
            "(?is)<a\\b([^>]*(?:icon-url|icon-dark-url)\\s*=\\s*(['\"]).*?\\2[^>]*)>(.*?)</a>");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "(?is)(icon-url|icon-dark-url)\\s*=\\s*(['\"])(.*?)\\2");
    private static final Pattern JSON_IMAGE = Pattern.compile(
            "(?is)\"(?:url|src|original)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private RichContent() {}

    static List<Block> parse(String source, JSONArray fallbackImages) {
        List<Block> blocks = new ArrayList<>();
        Set<String> imageUrls = new HashSet<>();
        source = decodeTransport(source);
        if (!addStructured(blocks, imageUrls, source)) {
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

    private static boolean addStructured(List<Block> blocks, Set<String> imageUrls,
                                         String source) {
        String value = source == null ? "" : source.trim();
        if (value.isEmpty()) return true;
        int arrayStart = value.indexOf('[');
        int arrayEnd = value.lastIndexOf(']');
        if (arrayStart > 0 && arrayEnd > arrayStart) {
            String prefix = value.substring(0, arrayStart).trim();
            try {
                JSONArray array = new JSONArray(value.substring(arrayStart, arrayEnd + 1));
                addReadableFragment(blocks, imageUrls, prefix);
                addArray(blocks, imageUrls, array);
                String suffix = value.substring(arrayEnd + 1).trim();
                addReadableFragment(blocks, imageUrls, suffix);
                return true;
            } catch (Exception ignored) {
            }
        }
        try {
            addArray(blocks, imageUrls, new JSONArray(value));
            return true;
        } catch (Exception ignored) {
        }
        return addObjectStream(blocks, imageUrls, value);
    }

    private static void addArray(List<Block> blocks, Set<String> imageUrls, JSONArray content) {
        for (int i = 0; i < content.length(); i++) {
            Object raw = content.opt(i);
            if (raw instanceof JSONObject) {
                addObject(blocks, imageUrls, (JSONObject) raw);
            } else if (raw instanceof String) {
                addHtml(blocks, imageUrls, (String) raw);
            }
        }
    }

    private static void addObject(List<Block> blocks, Set<String> imageUrls, JSONObject item) {
        String type = item.optString("type");
        if ("img".equalsIgnoreCase(type) || "image".equalsIgnoreCase(type)) {
            addImage(blocks, imageUrls,
                    first(item.optString("url"), item.optString("src"),
                            item.optString("original"), item.optString("text")));
        } else {
            addHtml(blocks, imageUrls,
                    first(item.optString("text"), item.optString("content"),
                            item.optString("html")));
        }
    }

    private static boolean addObjectStream(List<Block> blocks, Set<String> imageUrls,
                                           String source) {
        boolean found = false;
        int start = -1;
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        int lastEnd = 0;
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
                continue;
            }
            if (current == '"') {
                quoted = true;
            } else if (current == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (current == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    try {
                        JSONObject object = new JSONObject(source.substring(start, i + 1));
                        if (!found) {
                            String prefix = source.substring(0, start).trim();
                            addReadableFragment(blocks, imageUrls, prefix);
                        }
                        addObject(blocks, imageUrls, object);
                        found = true;
                        lastEnd = i + 1;
                    } catch (Exception ignored) {
                    }
                    start = -1;
                }
            }
        }
        if (found) {
            String suffix = source.substring(lastEnd).trim();
            addReadableFragment(blocks, imageUrls, suffix);
            addLooseImages(blocks, imageUrls, source);
            return true;
        }
        if (looksStructured(source)) {
            addReadableFragment(blocks, imageUrls, source);
            addLooseImages(blocks, imageUrls, source);
            return true;
        }
        return false;
    }

    private static void addReadableFragment(List<Block> blocks, Set<String> imageUrls,
                                            String source) {
        if (source == null) return;
        String value = source.trim();
        if (value.isEmpty()) return;
        int marker = structuredMarker(value);
        if (marker > 0) {
            String readable = value.substring(0, marker);
            if (hasReadableText(readable)) addHtml(blocks, imageUrls, readable);
        } else if (marker < 0) {
            addHtml(blocks, imageUrls, value);
        }
    }

    private static int structuredMarker(String value) {
        int marker = -1;
        String[] candidates = {
                "\",\"type\"", "\"type\":", "\"url\":", "\"height\":",
                "\"width\":", "},{", "[{"
        };
        for (String candidate : candidates) {
            int index = value.indexOf(candidate);
            if (index >= 0 && (marker < 0 || index < marker)) marker = index;
        }
        int object = value.indexOf('{');
        if (object >= 0 && (marker < 0 || object < marker)) marker = object;
        return marker;
    }

    private static boolean hasReadableText(String value) {
        return value != null && value.replaceAll("[\\s\"',:;{}\\[\\]<>/\\\\]+", "")
                .length() > 0;
    }

    private static void addLooseImages(List<Block> blocks, Set<String> imageUrls,
                                       String source) {
        Matcher matcher = JSON_IMAGE.matcher(source);
        while (matcher.find()) {
            addImage(blocks, imageUrls, decodeJsonString(matcher.group(1)));
        }
    }

    private static String decodeJsonString(String value) {
        try {
            return new JSONArray("[\"" + value + "\"]").optString(0);
        } catch (Exception ignored) {
            return value;
        }
    }

    private static boolean looksStructured(String value) {
        if (value == null) return false;
        String clean = value.trim();
        return clean.startsWith("{") || clean.startsWith("[")
                || clean.contains("\",\"type\"") || clean.contains("\"url\":");
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
        html = normalizeInlineEmojis(html);
        Matcher matcher = IMAGE.matcher(html);
        int start = 0;
        while (matcher.find()) {
            addText(blocks, html.substring(start, matcher.start()));
            addImage(blocks, imageUrls, decodeHtml(matcher.group(2)));
            start = matcher.end();
        }
        addText(blocks, html.substring(start));
    }

    private static String normalizeInlineEmojis(String html) {
        Matcher matcher = INLINE_EMOJI.matcher(html);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String attrs = matcher.group(1);
            String label = decodeHtml(matcher.group(3)).trim();
            if (label.isEmpty()) label = "表情";
            String token = "[" + label.replaceAll("[\\[\\]\\s]+", "") + "]";
            String light = "";
            String dark = "";
            Matcher attr = ATTRIBUTE.matcher(attrs);
            while (attr.find()) {
                String name = attr.group(1).toLowerCase(Locale.ROOT);
                String url = decodeHtml(attr.group(3)).replace("\\/", "/");
                if ("icon-dark-url".equals(name)) dark = url;
                else light = url;
            }
            EmojiStore.register(token, light, dark);
            matcher.appendReplacement(output, Matcher.quoteReplacement(token));
        }
        matcher.appendTail(output);
        return output.toString();
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
                .replace("\\\"", "\"")
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
