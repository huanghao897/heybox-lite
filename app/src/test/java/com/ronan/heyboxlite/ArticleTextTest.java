package com.ronan.heyboxlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** 文章正文纯文本处理的回归网：断句、小标题/引用判定、前导标签抽取、分段。 */
public class ArticleTextTest {

    @Test
    public void sentenceEnd() {
        assertTrue(ArticleText.isSentenceEnd('。')); // 。
        assertTrue(ArticleText.isSentenceEnd('！')); // ！
        assertTrue(ArticleText.isSentenceEnd('？')); // ？
        assertTrue(ArticleText.isSentenceEnd('!'));
        assertTrue(ArticleText.isSentenceEnd('?'));
        assertFalse(ArticleText.isSentenceEnd('a'));
        assertFalse(ArticleText.isSentenceEnd('，')); // ，
    }

    @Test
    public void softBreak() {
        assertTrue(ArticleText.isSoftBreak('；')); // ；
        assertTrue(ArticleText.isSoftBreak(';'));
        assertFalse(ArticleText.isSoftBreak('。')); // 。
    }

    @Test
    public void inlineHeading() {
        assertTrue(ArticleText.isInlineHeading("1.设置"));
        assertTrue(ArticleText.isInlineHeading("一、总结"));
        assertFalse(ArticleText.isInlineHeading("普通没有编号的文字"));
        assertFalse(ArticleText.isInlineHeading("包含。句号"));   // 含句读，不当小标题
        assertFalse(ArticleText.isInlineHeading("ab"));            // 过短
        assertFalse(ArticleText.isInlineHeading(null));
    }

    @Test
    public void articleQuote() {
        assertTrue(ArticleText.isArticleQuote(">引用"));
        assertTrue(ArticleText.isArticleQuote("本文内容"));
        assertTrue(ArticleText.isArticleQuote("热点分析"));
        assertFalse(ArticleText.isArticleQuote("普通段落文字"));
        assertFalse(ArticleText.isArticleQuote(null));
    }

    @Test
    public void displayLabel() {
        assertEquals("提示", ArticleText.displayLabel("提示词："));
        assertEquals("提示", ArticleText.displayLabel("提示"));
        assertEquals("Prompt", ArticleText.displayLabel("Prompt:"));
        assertEquals("其他标题", ArticleText.displayLabel("其他标题"));
    }

    @Test
    public void consumeLeadingLabel_stripsAndRecords() {
        List<String> ps = new ArrayList<>();
        String rest = ArticleText.consumeLeadingArticleLabel(ps, "提示词：内容");
        assertEquals("内容", rest);
        assertEquals(1, ps.size());
        assertEquals("提示", ps.get(0));
    }

    @Test
    public void consumeLeadingLabel_noLabel() {
        List<String> ps = new ArrayList<>();
        assertEquals("正文没有标签", ArticleText.consumeLeadingArticleLabel(ps, "正文没有标签"));
        assertTrue(ps.isEmpty());
    }

    @Test
    public void articleParagraphs_splitsOnNewlines() {
        List<String> ps = ArticleText.articleParagraphs("第一段\n第二段");
        assertEquals(2, ps.size());
        assertEquals("第一段", ps.get(0));
        assertEquals("第二段", ps.get(1));
    }

    @Test
    public void articleParagraphs_emptyInputs() {
        assertTrue(ArticleText.articleParagraphs(null).isEmpty());
        assertTrue(ArticleText.articleParagraphs("").isEmpty());
        assertTrue(ArticleText.articleParagraphs("   ").isEmpty());
    }

    @Test
    public void normalizeBreaks_insertsBeforeNumberedItem() {
        assertTrue(ArticleText.normalizeArticleBreaks("结尾1.开始").contains("\n"));
        assertEquals("普通文本", ArticleText.normalizeArticleBreaks("普通文本"));
    }
}
