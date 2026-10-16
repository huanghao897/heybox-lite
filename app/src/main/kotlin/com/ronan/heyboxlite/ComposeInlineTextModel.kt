package com.ronan.heyboxlite

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

internal interface ComposeInlineTextEmojiCatalog {
    fun longestResolvableCode(value: String, darkMode: Boolean): String
    fun url(code: String, darkMode: Boolean): String
    fun isLoaded(): Boolean
    fun whenReady(ready: Runnable)
}

internal object ExistingComposeInlineTextEmojiCatalog : ComposeInlineTextEmojiCatalog {
    override fun longestResolvableCode(value: String, darkMode: Boolean): String =
        EmojiStore.longestResolvableCode(value, darkMode)

    override fun url(code: String, darkMode: Boolean): String = EmojiStore.url(code, darkMode)
    override fun isLoaded(): Boolean = EmojiStore.isLoaded()
    override fun whenReady(ready: Runnable) = EmojiStore.whenReady(ready)
}

internal enum class ComposeInlineTextKind { EMOJI, GAME, CY }

internal data class ComposeInlineTextAttachment(
    val id: String,
    val start: Int,
    val end: Int,
    val kind: ComposeInlineTextKind,
    val url: String = "",
)

internal data class ComposeInlineTextModel(
    val text: AnnotatedString,
    val attachments: List<ComposeInlineTextAttachment>,
    val hasEmojiTokens: Boolean,
)

// Match the native token boundaries, including bare official codes followed by ordinary text.
private val composeInlineTextToken = Pattern.compile(
    "\\[[^\\]\\s]{1,48}\\]|(?<![A-Za-z0-9_./-])(?:cube|heygirl)_[^\\s\\[\\]<>\"']{1,48}",
)

internal fun buildComposeInlineTextModel(
    source: String,
    darkMode: Boolean,
    linkColor: Color,
    cy: Boolean,
    catalog: ComposeInlineTextEmojiCatalog = ExistingComposeInlineTextEmojiCatalog,
): ComposeInlineTextModel {
    val parsed = RichGameLinkMarkup.parse(source)
    val prefix = if (cy) "Cy " else ""
    val display = prefix + parsed.text
    val attachments = mutableListOf<ComposeInlineTextAttachment>()
    if (cy) attachments += ComposeInlineTextAttachment("cy", 0, 2, ComposeInlineTextKind.CY)
    for (link in parsed.links) {
        val start = prefix.length + link.start
        attachments += ComposeInlineTextAttachment("game:$start", start, start + 1,
            ComposeInlineTextKind.GAME)
    }

    val matcher = composeInlineTextToken.matcher(parsed.text)
    var hasEmojiTokens = false
    while (matcher.find()) {
        hasEmojiTokens = true
        val code = catalog.longestResolvableCode(matcher.group(), darkMode)
        if (code.isEmpty()) continue
        val url = catalog.url(code, darkMode)
        if (url.isEmpty()) continue
        val start = prefix.length + matcher.start()
        attachments += ComposeInlineTextAttachment("emoji:$start", start, start + code.length,
            ComposeInlineTextKind.EMOJI, url)
    }

    val ordered = attachments.sortedBy { it.start }
    val text = buildAnnotatedString {
        var cursor = 0
        for (attachment in ordered) {
            append(display, cursor, attachment.start)
            // Keeping the original alternate text preserves offsets, semantics and loading fallback.
            appendInlineContent(attachment.id, display.substring(attachment.start, attachment.end))
            cursor = attachment.end
        }
        append(display, cursor, display.length)
        for (link in parsed.links) {
            addStyle(SpanStyle(color = linkColor), prefix.length + link.start,
                prefix.length + link.end)
        }
    }
    return ComposeInlineTextModel(text, ordered, hasEmojiTokens)
}

/** EmojiStore cannot unsubscribe; release the composition captured by a queued callback instead. */
internal class ComposeInlineTextReadyCallback(onReady: () -> Unit) : Runnable {
    private val callback = AtomicReference<(() -> Unit)?>(onReady)

    override fun run() {
        callback.getAndSet(null)?.invoke()
    }

    fun cancel() {
        callback.set(null)
    }
}
