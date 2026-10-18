package com.ronan.heyboxlite

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** A saved reading snapshot makes settings changes observable by all reading surfaces. */
internal data class ComposeReadingSettings(
    val pagePadding: Int = 8,
    val bodyTextScale: Int = 100,
    val letterSpacing: Int = 0,
    val paragraphSpacing: Int = 9,
    val lineSpacing: Int = 122,
    val bold: Boolean = true,
) {
    companion object {
        fun from(session: SessionStore) = ComposeReadingSettings(session.pagePadding(),
            session.bodyTextScale(), session.bodyLetterSpacing(), session.bodyParagraphSpacing(),
            session.bodyLineSpacing(), session.bodyBold())
    }
}

@Composable
internal fun composeDetailBodyTypography(kind: Int, session: SessionStore? = null): TextStyle {
    val theme = LocalHeyboxTheme.current
    val reading = session?.let(ComposeReadingSettings::from) ?: theme.reading
    val size = when (kind) {
        RichContent.Block.HEADING -> if (theme.roundScreen) 15f else 16f
        RichContent.Block.CAPTION -> 11f
        RichContent.Block.QUOTE -> if (theme.roundScreen) 12.5f else 13.5f
        else -> if (theme.roundScreen) 13f else 14.5f
    }
    val fontSize = (size * theme.textScale * reading.bodyTextScale / 100f).sp
    val multiplier = when (kind) {
        RichContent.Block.HEADING -> 1.16f
        RichContent.Block.CAPTION -> 1.3f
        RichContent.Block.QUOTE -> maxOf(1.14f, reading.lineSpacing / 100f)
        else -> maxOf(1.18f, reading.lineSpacing / 100f)
    }
    val weight = when {
        kind == RichContent.Block.HEADING -> FontWeight.Bold
        kind == RichContent.Block.TEXT && reading.bold -> FontWeight.Medium
        else -> FontWeight.Normal
    }
    val letterSpacing = when (kind) {
        RichContent.Block.CAPTION -> 0.em
        RichContent.Block.QUOTE -> (reading.letterSpacing / 220f).em
        else -> (reading.letterSpacing / 200f).em
    }
    return TextStyle(fontSize = fontSize, fontWeight = weight, letterSpacing = letterSpacing,
        lineHeight = readingLineHeight(fontSize, multiplier))
}

@Composable
internal fun composePrimaryCommentTypography(roundScreen: Boolean): TextStyle {
    val theme = LocalHeyboxTheme.current
    val reading = theme.reading
    val fontSize = (if (roundScreen) 12.5.sp else 13.sp) * theme.textScale
    return TextStyle(fontSize = fontSize,
        fontWeight = if (reading.bold) FontWeight.Medium else FontWeight.Normal,
        letterSpacing = (reading.letterSpacing / 200f).em,
        lineHeight = readingLineHeight(fontSize, reading.lineSpacing / 100f))
}

@Composable
private fun readingLineHeight(fontSize: TextUnit, multiplier: Float): TextUnit {
    val density = LocalDensity.current
    val extra = with(density) { watchDp(1).roundToPx() }
    return remember(fontSize, density, extra, multiplier) {
        // Preserve TextView's font-metric spacing rather than multiplying the nominal sp size.
        val paint = Paint().apply {
            typeface = Typeface.DEFAULT
            textSize = with(density) { fontSize.toPx() }
        }
        with(density) { (paint.getFontMetricsInt(null) * multiplier + extra).roundToInt().toSp() }
    }
}
