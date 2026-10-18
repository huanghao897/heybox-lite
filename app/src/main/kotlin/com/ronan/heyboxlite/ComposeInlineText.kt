package com.ronan.heyboxlite

import android.graphics.Bitmap
import android.text.TextUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Compose-only inline images; the existing catalog and loader retain cache/network ownership.
 * A non-null ellipsize uses trailing ellipsis, supported by the project's Compose 1.7 runtime.
 */
@Composable
internal fun ComposeInlineText(
    source: String,
    darkMode: Boolean,
    textColor: Color,
    linkColor: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    lineHeight: TextUnit = 18.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    cy: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    ellipsize: TextUtils.TruncateAt? = null,
    imageLoader: ComposeImageLoader = ExistingComposeInlineEmojiImageLoader,
    emojiCatalog: ComposeInlineTextEmojiCatalog = ExistingComposeInlineTextEmojiCatalog,
    prefix: AnnotatedString = AnnotatedString(""),
    suffix: AnnotatedString = AnnotatedString(""),
    letterSpacing: TextUnit = 0.sp,
) {
    var catalogRevision by remember(source, darkMode, emojiCatalog) { mutableIntStateOf(0) }
    val model = remember(source, darkMode, linkColor, cy, emojiCatalog, catalogRevision) {
        buildComposeInlineTextModel(source, darkMode, linkColor, cy, emojiCatalog)
    }
    val displayText = remember(model.text, prefix, suffix) {
        buildAnnotatedString {
            append(prefix)
            append(model.text)
            append(suffix)
        }
    }
    DisposableEffect(source, darkMode, emojiCatalog, model.hasEmojiTokens) {
        val ready = ComposeInlineTextReadyCallback { catalogRevision++ }
        if (model.hasEmojiTokens && !emojiCatalog.isLoaded()) emojiCatalog.whenReady(ready)
        onDispose { ready.cancel() }
    }

    val density = LocalDensity.current
    val fontSizePx = with(density) { fontSize.toPx() }
    val emojiHeightPx = (fontSizePx * 1.18f).roundToInt().coerceAtLeast(16)
    val gameHeightPx = maxOf(with(density) { 13.dp.roundToPx() }, (fontSizePx * 0.92f).roundToInt())
    val gameGapPx = with(density) { 3.dp.roundToPx() }
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    for (attachment in model.attachments) {
        key(attachment.id, attachment.url) {
            when (attachment.kind) {
                ComposeInlineTextKind.EMOJI -> {
                    val bitmap = rememberComposeInlineTextBitmap(attachment.url, emojiHeightPx, imageLoader)
                    if (bitmap != null) {
                        val image = remember(bitmap) { bitmap.asImageBitmap() }
                        val widthPx = (bitmap.width * emojiHeightPx.toFloat() / bitmap.height.coerceAtLeast(1))
                            .roundToInt().coerceAtLeast(16)
                        inlineContent[attachment.id] = InlineTextContent(
                            Placeholder(with(density) { widthPx.toSp() },
                                with(density) { emojiHeightPx.toSp() }, PlaceholderVerticalAlign.TextCenter),
                        ) {
                            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit)
                        }
                    }
                }
                ComposeInlineTextKind.GAME -> {
                    val painter = painterResource(R.drawable.ic_game_link)
                    inlineContent[attachment.id] = InlineTextContent(
                        Placeholder(with(density) { (gameHeightPx + gameGapPx).toSp() },
                            with(density) { gameHeightPx.toSp() }, PlaceholderVerticalAlign.TextCenter),
                    ) {
                        Image(painter, contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(end = 3.dp),
                            contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(linkColor))
                    }
                }
                ComposeInlineTextKind.CY -> {
                    val painter = painterResource(R.drawable.official_cy_badge)
                    val heightPx = with(density) { 15.dp.roundToPx() }.coerceAtLeast(1)
                    val ratio = if (painter.intrinsicSize.height > 0f) {
                        painter.intrinsicSize.width / painter.intrinsicSize.height
                    } else 1f
                    val widthPx = (heightPx * ratio).roundToInt().coerceAtLeast(1)
                    inlineContent[attachment.id] = InlineTextContent(
                        Placeholder(with(density) { widthPx.toSp() },
                            with(density) { heightPx.toSp() }, PlaceholderVerticalAlign.TextCenter),
                    ) {
                        Image(painter, contentDescription = null, modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit)
                    }
                }
            }
        }
    }

    BasicText(
        text = displayText,
        modifier = modifier,
        style = TextStyle(color = textColor, fontSize = fontSize, lineHeight = lineHeight,
            fontWeight = fontWeight, fontFamily = FontFamily.SansSerif, letterSpacing = letterSpacing,
            platformStyle = PlatformTextStyle(includeFontPadding = false)),
        maxLines = maxLines,
        overflow = if (ellipsize == null) TextOverflow.Clip else TextOverflow.Ellipsis,
        inlineContent = inlineContent,
    )
}

@Composable
private fun rememberComposeInlineTextBitmap(url: String, targetPx: Int, loader: ComposeImageLoader): Bitmap? {
    var bitmap by remember(url, targetPx, loader) { mutableStateOf(loader.cached(url, targetPx)) }
    DisposableEffect(url, targetPx, loader) {
        var active = true
        val cancel = if (bitmap == null) {
            loader.loadCancellable(url, targetPx) { if (active) bitmap = it }
        } else null
        onDispose {
            active = false
            cancel?.invoke()
        }
    }
    return bitmap
}
