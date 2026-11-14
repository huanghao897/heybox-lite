package com.ronan.heyboxlite

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import java.net.URI
import java.net.URISyntaxException

/** Content may supply labels for unsupported routes, but only web URLs become actions. */
internal fun composeInlineLinkTarget(value: String): String {
    val trimmed = value.trim()
    val target = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
    val uri = try { URI(target) } catch (_: URISyntaxException) { return "" }
    if (!(uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) ||
        uri.host.isNullOrBlank() || uri.rawUserInfo != null) return ""
    return target
}

internal fun composeInlineLinkText(
    model: ComposeInlineTextModel,
    prefix: AnnotatedString,
    suffix: AnnotatedString,
    onOpenLink: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    append(prefix)
    append(model.text)
    append(suffix)
    for (link in model.links) {
        addLink(LinkAnnotation.Url(link.url, linkInteractionListener = { onOpenLink(link.url) }),
            prefix.length + link.start, prefix.length + link.end)
    }
}
