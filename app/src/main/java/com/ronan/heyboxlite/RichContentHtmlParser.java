package com.ronan.heyboxlite;

import android.os.Build;
import android.text.Html;

import com.ronan.heyboxlite.RichContent.Block;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts HTML segments into ordered text, image and game blocks. */
final class RichContentHtmlParser {
    private static final Pattern IMAGE = Pattern.compile("(?is)<img\\b[^>]*>");
    private static final Pattern HEADING_TAG = Pattern.compile(
            "(?is)<(h[1-6]|figcaption)\\b[^>]*>(.*?)</\\1>");
    private static final Pattern BLOCKQUOTE_TAG = Pattern.compile(
            "(?is)<blockquote\\b[^>]*>(.*?)</blockquote>");

    private RichContentHtmlParser() {}

    static void addHtml(List<Block> blocks, Set<String> imageUrls, String html) {
        addArticleHtml(blocks, imageUrls, html);
    }

    static void addArticleHtml(List<Block> blocks, Set<String> imageUrls, String html) {
        if (html == null || html.isEmpty()) return;
        html = ArticleGameMarkupScanner.replaceGameMarkers(html);
        html = RichInlineMarkup.normalize(html);
        Matcher matcher = IMAGE.matcher(html);
        int start = 0;
        while (matcher.find()) {
            addArticleSegment(blocks, html.substring(start, matcher.start()));
            addImage(blocks, imageUrls, RichInlineMarkup.imageFromTag(matcher.group()));
            addCaption(blocks, RichInlineMarkup.captionFromTag(matcher.group()));
            start = matcher.end();
        }
        addArticleSegment(blocks, html.substring(start));
    }

    // Split quotes before headings so each block retains its original role.
    private static void addArticleSegment(List<Block> blocks, String html) {
        if (html == null || html.isEmpty()) return;
        Matcher matcher = BLOCKQUOTE_TAG.matcher(html);
        int start = 0;
        while (matcher.find()) {
            addHeadingSegment(blocks, html.substring(start, matcher.start()));
            addQuote(blocks, matcher.group(1));
            start = matcher.end();
        }
        addHeadingSegment(blocks, html.substring(start));
    }

    private static void addHeadingSegment(List<Block> blocks, String html) {
        if (html == null || html.isEmpty()) return;
        Matcher matcher = HEADING_TAG.matcher(html);
        int start = 0;
        while (matcher.find()) {
            addArticleText(blocks, html.substring(start, matcher.start()));
            String tag = matcher.group(1).toLowerCase(Locale.ROOT);
            // The official editor uses h4 for captions, not headings.
            if (tag.startsWith("f") || "h4".equals(tag)) {
                addCaption(blocks, matcher.group(2));
            } else {
                addHeading(blocks, matcher.group(2));
            }
            start = matcher.end();
        }
        addArticleText(blocks, html.substring(start));
    }

    static void addQuote(List<Block> blocks, String html) {
        if (html == null || html.isEmpty()) return;
        String value = html
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)<li\\b[^>]*>", "\n- ")
                .replaceAll("(?is)</(?:p|div|li|h[1-6])>", "\n")
                .replaceAll("(?is)<[^>]+>", "");
        value = decodeHtml(value)
                .replace('\u00a0', ' ')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (value.isEmpty() || isStructuredNoise(value)) return;
        blocks.add(new Block(Block.QUOTE, value));
    }

    private static void addArticleText(List<Block> blocks, String html) {
        if (html == null || html.isEmpty()) return;
        html = ArticleGameMarkupScanner.replaceGameMarkers(html);
        List<String> parts = ArticleGameMarkupScanner.splitGameTokens(html);
        if (parts.size() > 1 || ArticleGameMarkupScanner.isGameToken(parts.get(0))) {
            for (String part : parts) {
                if (ArticleGameMarkupScanner.isGameToken(part)) {
                    blocks.add(ArticleGameMarkupScanner.block(
                            ArticleGameMarkupScanner.gameTokenId(part)));
                } else {
                    addArticleText(blocks, part);
                }
            }
            return;
        }
        String value = html
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)<li\\b[^>]*>", "\n- ")
                .replaceAll("(?is)</(?:p|div|section|article|h[1-6]|blockquote|li|ul|ol|figure|figcaption)>", "\n")
                .replaceAll("(?is)<(?:p|div|section|article|h[1-6]|blockquote|ul|ol|figure|figcaption)\\b[^>]*>", "\n");
        addText(blocks, value);
    }

    private static void addText(List<Block> blocks, String html) {
        String value = decodeHtml(html)
                .replace('\u00a0', ' ')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        value = stripStructuredNoise(value);
        if (isStructuredNoise(value)) return;
        if (value.isEmpty()) return;
        for (Block block : blocks) {
            if (!block.image && !RichGameCardParser.isCard(block)
                    && value.equals(block.value)) return;
        }
        blocks.add(new Block(Block.TEXT, value));
    }

    static void addHeading(List<Block> blocks, String value) {
        String clean = cleanInlineText(value);
        if (clean.isEmpty() || clean.length() > 64 || isStructuredNoise(clean)) return;
        blocks.add(new Block(Block.HEADING, clean));
    }

    static void addCaption(List<Block> blocks, String value) {
        String clean = cleanInlineText(value);
        if (clean.isEmpty() || isStructuredNoise(clean)) return;
        if (RichInlineMarkup.looksImageUrl(clean)) return;
        // Preserve long captions; only exceptionally long ones become body text.
        blocks.add(new Block(clean.length() > 800 ? Block.TEXT : Block.CAPTION, clean));
    }

    private static String cleanInlineText(String value) {
        if (value == null || value.isEmpty()) return "";
        String stripped = value.replaceAll("(?is)<[^>]+>", " ");
        return decodeHtml(stripped)
                .replace('\u00a0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String stripStructuredNoise(String source) {
        String value = source == null ? "" : source.trim();
        if (value.isEmpty()) return "";
        int marker = noiseMarker(value);
        if (marker == 0) return "";
        if (marker > 0) {
            value = value.substring(0, marker).trim();
            value = value.replaceAll("[\\s,\uFF0C;\uFF1B:\uFF1A\"'\u201C\u201D]+$", "").trim();
        }
        return value;
    }

    private static int noiseMarker(String value) {
        int marker = -1;
        String[] candidates = {
                "\",\"type\"", "\"type\":", "\"url\":", "\"height\":",
                "\"width\":", "{\"height\"", "{\"width\"", "{\"url\"",
                "{\"type\"", "},{", "[{", "imageMogr2/", "imageMogr/"
        };
        for (String candidate : candidates) {
            int index = value.indexOf(candidate);
            if (index >= 0 && (marker < 0 || index < marker)) marker = index;
        }
        return marker;
    }

    private static boolean isStructuredNoise(String value) {
        if (value == null) return true;
        String clean = value.trim();
        if (clean.isEmpty()) return true;
        String compact = clean.replaceAll("\\s+", "");
        return compact.length() <= 1
                || compact.startsWith("{") || compact.startsWith("[{") || compact.startsWith("[\"")
                || compact.startsWith("},") || compact.startsWith("\",")
                || compact.startsWith("imageMogr") || compact.startsWith("?imageMogr")
                || compact.contains("\"type\":\"img\"")
                || compact.contains("\"url\":\"")
                || compact.contains("\"height\":\"")
                || compact.contains("\"width\":\"")
                || (compact.startsWith("https://") && compact.contains("imgheybox")
                && compact.contains("/thumb."));
    }

    static void addImage(List<Block> blocks, Set<String> imageUrls, String url) {
        if (url == null) return;
        String value = decodeHtml(url).replace("\\/", "/").trim();
        if (value.startsWith("//")) value = "https:" + value;
        if (value.regionMatches(true, 0, "http:", 0, 5)) value = "https:" + value.substring(5);
        if (value.regionMatches(true, 0, "imgheybox", 0, 9)) value = "https://" + value;
        if (!value.startsWith("https://")) return;
        if (imageUrls.add(RichContent.imageKey(value))) blocks.add(new Block(Block.IMAGE, value));
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
}
