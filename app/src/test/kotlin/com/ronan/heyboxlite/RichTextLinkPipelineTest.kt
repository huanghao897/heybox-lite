package com.ronan.heyboxlite

import android.app.Application
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RichTextLinkPipelineTest {
    private val url = "https://api.xiaoheihe.cn/v3/bbs/app/api/web/share?link_id=123456&h_src=link"
    private val title = "男油物的日常传小道，授大业：《不问凡尘》深度测评"

    @Test fun bodyLinkWithAnEmbeddedIconRemainsOneTextLinkInsteadOfLeakingItsPayload() {
        val html = "<p>2.点赞评论此贴→<a data-link-type='text' href='$url'>" +
            "<img src='https://example.invalid/link-icon.png'/><span>$title</span></a></p>" +
            "<p>全程秒搞定，不用繁琐任务。</p>"
        assertBodyLink(html, title)
    }

    @Test fun lineBreaksAndFormattingInsideAnAnchorCannotSplitTheInternalLink() {
        val html = "<p>参与活动 <a href='$url'> <b>文章标题</b><br/>第二行标题 </a> 后文</p>"
        assertBodyLink(html, "文章标题 第二行标题")
    }

    @Test fun automaticSentenceBreaksCannotSplitAPunctuatedLinkTitle() {
        val label = "这是一篇标题包含很多内容的文章，先看这里！然后继续阅读第二部分？"
        assertBodyLink("前文<a href='$url'>$label</a>后文。", label)
    }

    @Test fun automaticNumberedBreaksCannotSplitTheLinkTitle() {
        assertBodyLink("前文<a href='$url'>游戏评测1.前言 2.正文</a>后文", "游戏评测1.前言 2.正文")
    }

    @Test fun imageOnlyAnchorStillBecomesAnArticleImage() {
        val image = "https://example.invalid/photo.jpg"
        val result = DetailContentParser().resolve(JSONObject().put("text",
            "<p>正文</p><a href='$url'><img src='$image'/></a>"), "", null)
        assertEquals(listOf(image), result.blocks.filter { it.image }.map { it.value })
        assertTrue(result.blocks.filterNot { it.image }.none { RichGameLinkMarkup.hasMarkup(it.value) })
    }

    @Test fun emojiInsideALinkRetainsItsTokenAndDoesNotBecomeABodyImage() {
        val emoji = "https://fixtures.invalid/emoji/cube_smile.png"
        assertBodyLink("<p>参与 <a href='$url'>标题<img alt='cube_smile' src='$emoji'/></a></p>",
            "标题[cube_smile]")
        assertEquals(emoji, EmojiStore.url("[cube_smile]", false))
    }

    private fun assertBodyLink(html: String, expectedTitle: String) {
        val result = DetailContentParser().resolve(JSONObject().put("text", html), "", null)
        assertFalse("An inline link icon is not an article image", result.blocks.any { it.image })
        val models = result.blocks.flatMap { block -> ArticleText.articleParagraphs(block.value) }
            .map { buildComposeInlineTextModel(it, true, Color.Blue, false, ComposeInlineTextTestCatalog()) }
        val text = models.joinToString("\n") { it.text.text }
        assertTrue("The link title must survive the complete detail pipeline: $text", text.contains(expectedTitle))
        assertFalse("Encoded link metadata must never become body copy", text.contains("0068007400740070"))
        val link = models.flatMap { it.links }.single()
        assertEquals(url, link.url)
        val model = models.single { it.links.isNotEmpty() }
        assertEquals("\uFFFC$expectedTitle", model.text.text.substring(link.start, link.end))
        assertTrue(model.text.spanStyles.any { it.start == link.start && it.end == link.end && it.item.color == Color.Blue })
    }
}
