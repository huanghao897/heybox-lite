package com.ronan.heyboxlite

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.*
import org.junit.Test

class ComposeInlineLinksTest {
    @Test fun onlyValidWebLinksBecomeActions() {
        assertEquals("https://example.invalid/lottery?a=1&b=2",
            composeInlineLinkTarget(" https://example.invalid/lottery?a=1&b=2 "))
        assertEquals("https://example.invalid/post", composeInlineLinkTarget("//example.invalid/post"))
        assertEquals("http://example.invalid/post", composeInlineLinkTarget("http://example.invalid/post"))
        for (url in listOf("javascript:alert(1)", "file:///data/local/tmp",
            "content://provider/file", "intent://host#Intent;end", "https:///missing-host",
            "https://user:password@example.invalid/path", "https://example.invalid/a b",
            "heybox://openWebView", "/relative", "")) {
            assertEquals(url, "", composeInlineLinkTarget(url))
        }
    }

    @Test fun linkRangesFollowEmojiCyAndReplyPrefixWithoutLosingTheTarget() {
        val source = RichGameLinkMarkup.normalizeAnchors(
            "[cube_wave] <a data-link-type='text' href='https://example.invalid/lottery'>Lottery</a>")
        val model = buildComposeInlineTextModel(source, false, Color.Blue, true,
            ComposeInlineTextTestCatalog())
        val opened = mutableListOf<String>()
        val prefix = AnnotatedString("Iris replies Mira: ")
        val rendered = composeInlineLinkText(model, prefix, AnnotatedString(" today"), opened::add)
        val range = rendered.getLinkAnnotations(0, rendered.length).single()
        assertEquals("\uFFFCLottery", rendered.text.substring(range.start, range.end))
        assertTrue(range.start > prefix.length)
        val annotation = range.item as LinkAnnotation.Url
        assertEquals("https://example.invalid/lottery", annotation.url)
        annotation.linkInteractionListener!!.onClick(annotation)
        assertEquals(listOf(annotation.url), opened)
        assertEquals(prefix.text + model.text.text + " today", rendered.text)
    }

    @Test fun unsupportedTargetKeepsItsLabelAndBlueStyleButHasNoAction() {
        val source = RichGameLinkMarkup.normalizeAnchors(
            "<a data-link-type='text' href='javascript:alert(1)'>Lottery</a>")
        val model = buildComposeInlineTextModel(source, false, Color.Blue, false,
            ComposeInlineTextTestCatalog())
        assertEquals("\uFFFCLottery", model.text.text)
        assertEquals(Color.Blue, model.text.spanStyles.single().item.color)
        assertTrue(model.links.isEmpty())
    }
}
