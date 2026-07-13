package com.ronan.heyboxlite;

import java.util.ArrayList;
import java.util.List;

/**
 * 文章正文的纯文本处理：分段、断句、编号小标题与引用判定、前导标签抽取。
 * 全部为无状态静态方法，不依赖 Activity/视图，便于复用与单测。
 */
final class ArticleText {
    private ArticleText() {}

    /** 作者手打的编号短行（如“1.设置（推荐）”“三、心得”）当作小标题渲染，与官方排版一致。 */
    static boolean isInlineHeading(String paragraph) {
        String clean = paragraph == null ? "" : paragraph.trim();
        if (clean.length() < 3 || clean.length() > 26) {
            return false;
        }
        if (clean.matches(".*[。，,;；!！?？…].*")) {
            return false;
        }
        return clean.matches("^(?:[0-9０-９]{1,2}[.．、](?![0-9０-９])|[一二三四五六七八九十]{1,2}[、.．]).+");
    }

    static boolean isArticleQuote(String paragraph) {
        if (paragraph == null) {
            return false;
        }
        String clean = paragraph.trim();
        return clean.startsWith(">") || clean.startsWith("阅读对象") || clean.startsWith("专业黑话") || clean.startsWith("扩展阅读") || clean.startsWith("包含AI") || clean.startsWith("本文") || clean.startsWith("热点");
    }

    static List<String> articleParagraphs(String source) {
        List<String> paragraphs = new ArrayList<>();
        String value = source == null ? "" : source.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (value.isEmpty()) {
            return paragraphs;
        }
        String value2 = consumeLeadingArticleLabel(paragraphs, value);
        if (value2.isEmpty()) {
            return paragraphs;
        }
        String value3 = normalizeArticleBreaks(value2);
        if (value3.contains("\n")) {
            String[] parts = value3.split("\\n+");
            for (String part : parts) {
                String clean = part.trim();
                if (!clean.isEmpty()) {
                    paragraphs.add(clean);
                }
            }
            return paragraphs;
        }
        StringBuilder current = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < value3.length(); i++) {
            char ch = value3.charAt(i);
            current.append(ch);
            if (!Character.isWhitespace(ch)) {
                visible++;
            }
            if ((isSentenceEnd(ch) && visible >= 28) || (isSoftBreak(ch) && visible >= 68)) {
                String paragraph = current.toString().trim();
                if (!paragraph.isEmpty()) {
                    paragraphs.add(paragraph);
                }
                current.setLength(0);
                visible = 0;
            }
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            paragraphs.add(tail);
        }
        return paragraphs;
    }

    static String normalizeArticleBreaks(String value) {
        String output = value.replaceAll("([\\u3002\\uff01\\uff1f!?])\\s*(?=([\\uff08(][0-9\\u4e00-\\u9fff]{1,3}[\\uff09)]))", "$1\n");
        return output.replaceAll("\\s+(?=([\\uff08(][0-9\\u4e00-\\u9fff]{1,3}[\\uff09)]))", "\n").replaceAll("([^\\n])\\s+(?=([\\u4e00-\\u9fff]{1,4}\\u3001))", "$1\n").replaceAll("([^\\n])\\s+(?=([0-9]{1,2}[.\\uff0e][^0-9]))", "$1\n").replaceAll("([\\u4e00-\\u9fffA-Za-z])(?=([0-9]{1,2}[.\\uff0e][\\u4e00-\\u9fff]))", "$1\n").replaceAll("([\\u3002\\uff01\\uff1f!?])(?=([0-9]{1,2}[.\\uff0e][\\u4e00-\\u9fff]))", "$1\n");
    }

    static String consumeLeadingArticleLabel(List<String> paragraphs, String value) {
        String[] labels = {"提示词：", "提示", "提示", "Prompt:", "Prompt"};
        for (String label : labels) {
            if (value.equals(label)) {
                paragraphs.add(displayLabel(label));
                return "";
            }
            if (value.startsWith(label + " ") || value.startsWith(label + "\n") || ((label.endsWith(":") || label.endsWith("")) && value.startsWith(label))) {
                paragraphs.add(displayLabel(label));
                return value.substring(label.length()).trim();
            }
        }
        return value;
    }

    static String displayLabel(String label) {
        return label.startsWith("提示") ? "提示" : label.startsWith("Prompt") ? "Prompt" : label;
    }

    static boolean isSentenceEnd(char ch) {
        return ch == 12290 || ch == 65281 || ch == 65311 || ch == '!' || ch == '?';
    }

    static boolean isSoftBreak(char ch) {
        return ch == 65307 || ch == ';';
    }
}
