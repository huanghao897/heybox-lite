package com.ronan.heyboxlite

import androidx.compose.ui.text.MultiParagraph
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import kotlin.math.ceil

/** Match String Text's painted paragraph, not its re-created semantics maxWidth. */
internal fun paintedTextLayout(raw: TextLayoutResult): TextLayoutResult {
    val input = raw.layoutInput
    val width = if (!input.softWrap && input.overflow != TextOverflow.Ellipsis) {
        ceil(raw.multiParagraph.intrinsics.maxIntrinsicWidth).toInt().coerceAtLeast(raw.size.width)
    } else raw.size.width
    // Reuse the resolved fonts and retain the real measured size: clipping and maxLines
    // violations must remain observable even when semantics used a wider paragraph.
    return TextLayoutResult(input, MultiParagraph(raw.multiParagraph.intrinsics,
        Constraints(maxWidth = width, maxHeight = input.constraints.maxHeight),
        maxLines = input.maxLines, ellipsis = input.overflow == TextOverflow.Ellipsis), raw.size)
}
