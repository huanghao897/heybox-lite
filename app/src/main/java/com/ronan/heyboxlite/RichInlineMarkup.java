package com.ronan.heyboxlite;

import android.os.Build;
import android.text.Html;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalizes official inline attachments before block HTML is flattened. */
final class RichInlineMarkup {
    private static final Pattern INLINE_EMOJI = Pattern.compile(
            "(?is)<a\\b([^>]*(?:icon[-_]?url|icon[-_]?dark[-_]?url)[^>]*)>(.*?)</a>");
    private static final Pattern INLINE_IMAGE_EMOJI = Pattern.compile("(?is)<img\\b([^>]*)>");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "(?is)([a-z0-9_-]+)\\s*=\\s*(['\"])(.*?)\\2");
    private static final Pattern JSON_PRIMITIVE = Pattern.compile(
            "(?:true|false|null|-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)"
                    + "(?=\\s*[,\\]])");

    private RichInlineMarkup() {}

    static boolean isLikelyJsonArray(String value) {
        if (value == null) return false;
        String clean = value.trim();
        if (!clean.startsWith("[")) return false;
        String first = clean.substring(1).trim();
        if (first.isEmpty()) return false;
        char initial = first.charAt(0);
        // org.json accepts bare words, including unknown bracketed emoji tokens.
        return initial == ']' || initial == '{' || initial == '[' || initial == '"'
                || JSON_PRIMITIVE.matcher(first).lookingAt();
    }

    /** 图片标签上直接携带的图注（编辑器写入的描述属性）。 */
    static String captionFromTag(String tag) {
        if (tag == null || tag.isEmpty()) return "";
        String attrs = tag.replaceFirst("(?is)^\\s*<img\\b", "")
                .replaceFirst("(?is)/?>\\s*$", "");
        return first(attribute(attrs, "desc"),
                attribute(attrs, "data-desc"),
                attribute(attrs, "data-caption"),
                attribute(attrs, "caption"));
    }
    static String imageFromTag(String tag) {
        if (tag == null || tag.isEmpty()) return "";
        String attrs = tag.replaceFirst("(?is)^\\s*<img\\b", "")
                .replaceFirst("(?is)/?>\\s*$", "");
        String found = first(attribute(attrs, "data-original"),
                attribute(attrs, "data-src"),
                attribute(attrs, "data-url"),
                attribute(attrs, "data-image"),
                attribute(attrs, "data-image-url"),
                attribute(attrs, "data-original-src"),
                attribute(attrs, "origin-url"),
                attribute(attrs, "original-url"),
                attribute(attrs, "origin-src"),
                attribute(attrs, "original-src"),
                attribute(attrs, "src"),
                attribute(attrs, "url"));
        if (!found.isEmpty()) return found;
        Matcher attr = ATTRIBUTE.matcher(attrs);
        while (attr.find()) {
            String name = attr.group(1).toLowerCase(Locale.ROOT).replace('_', '-');
            if (name.contains("url") || name.contains("src")
                    || name.contains("image") || name.contains("original")) {
                String value = decodeHtml(attr.group(3)).replace("\\/", "/");
                if (looksImageUrl(value)) return value;
            }
        }
        return "";
    }
    static String normalize(String html) {
        html = RichGameLinkMarkup.normalizeAnchors(decodeAttributeEntities(html));
        Matcher matcher = INLINE_EMOJI.matcher(html);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String attrs = matcher.group(1);
            String label = decodeHtml(matcher.group(2)).trim();
            if (label.isEmpty()) label = "表情";
            EmojiUrls urls = extractEmojiUrls(attrs);
            if (urls.hasUrl() && !RichLinkClassifier.isContentLink(attrs)) {
                String token = token(label);
                EmojiStore.register(token, urls.light, urls.dark);
                matcher.appendReplacement(output, Matcher.quoteReplacement(token));
            }
        }
        matcher.appendTail(output);
        return normalizeInlineImageEmojis(output.toString());
    }

    private static String normalizeInlineImageEmojis(String html) {
        Matcher matcher = INLINE_IMAGE_EMOJI.matcher(html);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String attrs = matcher.group(1);
            EmojiUrls urls = extractEmojiUrls(attrs);
            String label = first(attribute(attrs, "alt"),
                    attribute(attrs, "data-name"),
                    attribute(attrs, "data-code"),
                    attribute(attrs, "data-key"),
                    attribute(attrs, "data-emoji-name"),
                    attribute(attrs, "data-emoji"),
                    attribute(attrs, "title"),
                    attribute(attrs, "name"));
            String light = first(urls.light, attribute(attrs, "src"),
                    attribute(attrs, "data-src"), attribute(attrs, "data-original"),
                    imageFromTag("<img " + attrs + ">"));
            if (!looksEmojiUrl(light) && !looksEmojiUrl(urls.dark)
                    && !attrs.toLowerCase(Locale.ROOT).contains("emoji")) {
                continue;
            }
            if (label.isEmpty()) label = emojiLabelFromUrl(light);
            if (label.isEmpty()) label = "表情";
            String token = token(decodeHtml(label));
            EmojiStore.register(token, light, urls.dark);
            matcher.appendReplacement(output, Matcher.quoteReplacement(token));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static EmojiUrls extractEmojiUrls(String attrs) {
        String light = "";
        String dark = "";
        Matcher attr = ATTRIBUTE.matcher(attrs);
        while (attr.find()) {
            String name = attr.group(1).toLowerCase(Locale.ROOT).replace('_', '-');
            String url = decodeHtml(attr.group(3)).replace("\\/", "/");
            if (name.contains("dark") && name.contains("url")) {
                dark = url;
            } else if ((name.contains("icon") && name.contains("url"))
                    || "src".equals(name) || "data-src".equals(name)
                    || "data-original".equals(name)) {
                light = url;
            }
        }
        return new EmojiUrls(light, dark);
    }

    private static String attribute(String attrs, String expected) {
        Matcher attr = ATTRIBUTE.matcher(attrs);
        String target = expected.toLowerCase(Locale.ROOT);
        while (attr.find()) {
            String name = attr.group(1).toLowerCase(Locale.ROOT).replace('_', '-');
            if (target.equals(name)) return decodeHtml(attr.group(3)).replace("\\/", "/");
        }
        return "";
    }

    private static String token(String label) {
        String clean = label == null ? "" : label.replaceAll("[\\[\\]\\s]+", "");
        return "[" + (clean.isEmpty() ? "表情" : clean) + "]";
    }

    private static boolean looksEmojiUrl(String url) {
        if (url == null) return false;
        String value = url.toLowerCase(Locale.ROOT);
        return value.contains("/emoji/") || value.contains("emoji")
                || value.contains("icon-url")
                || value.contains("cube_")
                || value.contains("heygirl_");
    }

    private static String emojiLabelFromUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        String value = decodeHtml(url).replace("\\/", "/");
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int hash = value.indexOf('#');
        if (hash >= 0) value = value.substring(0, hash);
        int slash = value.lastIndexOf('/');
        String name = slash >= 0 ? value.substring(slash + 1) : value;
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.matches("(?i)(cube|heygirl)[-_].+") ? name : "";
    }
    static boolean looksImageUrl(String url) {
        if (url == null) return false;
        String value = url.toLowerCase(Locale.ROOT);
        return value.startsWith("http://") || value.startsWith("https://")
                || value.startsWith("//")
                || value.contains("imgheybox")
                || value.contains("image")
                || value.contains(".jpg")
                || value.contains(".jpeg")
                || value.contains(".png")
                || value.contains(".webp")
                || value.contains(".gif");
    }

    static String decodeAttributeEntities(String value) {
        if (value == null) return "";
        String decoded = value;
        for (int i = 0; i < 2; i++) {
            String next = decoded
                    .replace("&lt;", "<")
                    .replace("&LT;", "<")
                    .replace("&#60;", "<")
                    .replace("&#x3c;", "<")
                    .replace("&#X3C;", "<")
                    .replace("&gt;", ">")
                    .replace("&GT;", ">")
                    .replace("&#62;", ">")
                    .replace("&#x3e;", ">")
                    .replace("&#X3E;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#34;", "\"")
                    .replace("&#x22;", "\"")
                    .replace("&#X22;", "\"")
                    .replace("&apos;", "'")
                    .replace("&#39;", "'")
                    .replace("&amp;", "&")
                    .replace("&AMP;", "&");
            if (next.equals(decoded)) break;
            decoded = next;
        }
        return decoded;
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

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }

    private static final class EmojiUrls {
        final String light;
        final String dark;

        EmojiUrls(String light, String dark) {
            this.light = light == null ? "" : light;
            this.dark = dark == null ? "" : dark;
        }

        boolean hasUrl() {
            return !light.isEmpty() || !dark.isEmpty();
        }
    }

}
