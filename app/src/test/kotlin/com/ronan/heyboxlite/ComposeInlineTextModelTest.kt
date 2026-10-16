package com.ronan.heyboxlite

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ComposeInlineTextModelTest {
    @Test fun emojiPlainTextGameNameAndCyKeepTheirOffsetsAndStylesTogether() {
        val catalog = ComposeInlineTextTestCatalog().apply {
            register("cube_wave", "wave-light", "wave-dark")
            register("heygirl_hi", "hi-light", "hi-dark")
        }
        val source = "Lead [cube_wave] cube_waveSuffix \uE100Game [heygirl_hi]\uE101 tail"
        val model = buildComposeInlineTextModel(source, true, Color.Blue, true, catalog)

        assertEquals("Cy Lead [cube_wave] cube_waveSuffix \uFFFCGame [heygirl_hi] tail", model.text.text)
        assertTrue(model.hasEmojiTokens)
        assertEquals(listOf(ComposeInlineTextKind.CY, ComposeInlineTextKind.EMOJI,
            ComposeInlineTextKind.EMOJI, ComposeInlineTextKind.GAME, ComposeInlineTextKind.EMOJI),
            model.attachments.map { it.kind })
        assertEquals(listOf("wave-dark", "wave-dark", "hi-dark"),
            model.attachments.filter { it.kind == ComposeInlineTextKind.EMOJI }.map { it.url })
        val emoji = model.attachments.filter { it.kind == ComposeInlineTextKind.EMOJI }
        assertEquals(listOf("[cube_wave]", "cube_wave", "[heygirl_hi]"),
            emoji.map { model.text.text.substring(it.start, it.end) })
        assertTrue(model.text.text.substring(emoji[1].end).startsWith("Suffix "))
        val style = model.text.spanStyles.single()
        assertEquals(Color.Blue, style.item.color)
        assertEquals("\uFFFCGame [heygirl_hi]", model.text.text.substring(style.start, style.end))
        for (attachment in model.attachments) {
            assertTrue(model.text.getStringAnnotations(attachment.start, attachment.end)
                .any { it.item == attachment.id && it.start == attachment.start && it.end == attachment.end })
        }
        assertEquals(model.attachments.size, model.attachments.map { it.id }.distinct().size)
    }

    @Test fun unknownTokensAndCodesInsideUrlsOrIdentifiersStayReadable() {
        val catalog = ComposeInlineTextTestCatalog().apply { register("cube_wave", "wave") }
        val source = "[not_in_catalog] https://host/cube_wave.png xcube_wave path/cube_wave"
        val model = buildComposeInlineTextModel(source, false, Color.Blue, false, catalog)

        assertEquals(source, model.text.text)
        assertTrue(model.hasEmojiTokens)
        assertTrue(model.attachments.isEmpty())
        assertTrue(model.text.spanStyles.isEmpty())
    }

    @Test fun emptyTextDoesNotSubscribeAndCyIsAnIndependentInlineAttachment() {
        val catalog = ComposeInlineTextTestCatalog()
        val empty = buildComposeInlineTextModel("", false, Color.Blue, false, catalog)
        assertEquals("", empty.text.text)
        assertFalse(empty.hasEmojiTokens)
        assertTrue(empty.attachments.isEmpty())
        val badge = buildComposeInlineTextModel("", false, Color.Blue, true, catalog)
        assertEquals("Cy ", badge.text.text)
        assertEquals(ComposeInlineTextKind.CY, badge.attachments.single().kind)
        assertFalse(badge.hasEmojiTokens)
    }

    @Test fun bareOfficialChineseCodesKeepAdjacentTextAndPunctuation() {
        val catalog = ComposeInlineTextTestCatalog().apply {
            register("cube_\u54c7", "wow")
            register("heygirl_\u5f00\u5fc3", "happy")
        }
        val source = "cube_\u54c7\u54c7\uff0catm\u53d1\u5e16\u4e86 [heygirl_\u5f00\u5fc3]\u540e\u6587"
        val model = buildComposeInlineTextModel(source, false, Color.Blue, false, catalog)

        assertEquals(source, model.text.text)
        assertEquals(listOf("cube_\u54c7", "[heygirl_\u5f00\u5fc3]"),
            model.attachments.map { model.text.text.substring(it.start, it.end) })
        assertTrue(model.text.text.substring(model.attachments.first().end).startsWith("\u54c7\uff0catm"))
    }

    @Test fun existingStoreAdapterResolvesBracketCubeAndHeygirlWithoutSwallowingSuffixText() {
        EmojiStore.register("cube_compose_inline_739", "https://fixtures.invalid/cube-light",
            "https://fixtures.invalid/cube-dark")
        EmojiStore.register("heygirl_compose_inline_842", "https://fixtures.invalid/girl-light",
            "https://fixtures.invalid/girl-dark")
        val model = buildComposeInlineTextModel(
            "[cube_compose_inline_739] cube_compose_inline_739Suffix heygirl_compose_inline_842!",
            true, Color.Blue, false)

        assertEquals(listOf("https://fixtures.invalid/cube-dark", "https://fixtures.invalid/cube-dark",
            "https://fixtures.invalid/girl-dark"), model.attachments.map { it.url })
        assertTrue(model.text.text.contains("739Suffix"))
        assertEquals('!', model.text.text.last())
    }

    @Test fun normalizedOfficialGameMarkupUsesTheExistingParser() {
        val source = RichGameLinkMarkup.normalizeAnchors(
            "Before <a data-link-type=\"game\" data-game-id=\"42\">Game</a> after")
        val model = buildComposeInlineTextModel(source, false, Color.Cyan, false,
            ComposeInlineTextTestCatalog())

        assertEquals("Before \uFFFCGame after", model.text.text)
        assertEquals(ComposeInlineTextKind.GAME, model.attachments.single().kind)
        val span = model.text.spanStyles.single()
        assertEquals(Color.Cyan, span.item.color)
        assertEquals("\uFFFCGame", model.text.text.substring(span.start, span.end))
    }

    @Test fun readyCallbackIsOneShotAndCancellationRejectsLateCatalogCompletion() {
        var calls = 0
        val ready = ComposeInlineTextReadyCallback { calls++ }
        ready.run()
        ready.run()
        assertEquals(1, calls)
        val cancelled = ComposeInlineTextReadyCallback { calls++ }
        cancelled.cancel()
        cancelled.run()
        assertEquals(1, calls)
    }
}
