package com.ronan.heyboxlite

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.TextUtils
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

/** Reuses the native rich-text renderer until the Compose detail host reaches parity. */
@Composable
internal fun ComposeRichText(
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
) {
    val density = LocalDensity.current
    val binding = ComposeRichTextBinding(
        source = source,
        darkMode = darkMode,
        textColor = textColor.toArgb(),
        linkColor = linkColor.toArgb(),
        textSizePx = with(density) { fontSize.toPx() },
        lineSpacing = (lineHeight.value / fontSize.value).coerceAtLeast(1f),
        mediumWeight = fontWeight.weight >= FontWeight.Medium.weight,
        cy = cy,
        maxLines = maxLines,
        ellipsize = ellipsize,
        density = density.density,
    )
    AndroidView(
        modifier = modifier,
        factory = { ComposeRichTextView(it) },
        update = { it.bind(binding) },
    )
}

internal data class ComposeRichTextBinding(
    val source: String,
    val darkMode: Boolean,
    val textColor: Int,
    val linkColor: Int,
    val textSizePx: Float,
    val lineSpacing: Float,
    val mediumWeight: Boolean,
    val cy: Boolean,
    val maxLines: Int,
    val ellipsize: TextUtils.TruncateAt?,
    val density: Float,
) {
    fun needsRender(previous: ComposeRichTextBinding?): Boolean = previous == null ||
        source != previous.source || darkMode != previous.darkMode ||
        linkColor != previous.linkColor || textSizePx != previous.textSizePx ||
        cy != previous.cy || density != previous.density
}

// Keep platform TextView metrics and span behavior aligned with the native rich/emoji renderer.
@SuppressLint("AppCompatCustomView")
internal class ComposeRichTextView(context: Context) : TextView(context) {
    // EmojiRenderer owns the default View tag to reject stale asynchronous callbacks.
    private var binding: ComposeRichTextBinding? = null

    init {
        includeFontPadding = false
        setTextIsSelectable(false)
        isLongClickable = false
    }

    fun bind(next: ComposeRichTextBinding) {
        val previous = binding
        if (previous == next) return
        if (previous?.maxLines != next.maxLines) maxLines = next.maxLines
        if (previous?.ellipsize != next.ellipsize) ellipsize = next.ellipsize
        if (previous?.textSizePx != next.textSizePx) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, next.textSizePx)
        }
        if (previous?.textColor != next.textColor) setTextColor(next.textColor)
        if (previous?.lineSpacing != next.lineSpacing) setLineSpacing(0f, next.lineSpacing)
        if (previous?.mediumWeight != next.mediumWeight) {
            typeface = Typeface.create(if (next.mediumWeight) "sans-serif-medium" else "sans-serif",
                Typeface.NORMAL)
        }
        if (next.needsRender(previous)) {
            val decorator = if (next.cy) {
                EmojiRenderer.Decorator { span -> applyCyBadge(this, span) }
            } else null
            RichInlineRenderer.set(this, if (next.cy) "Cy ${next.source}" else next.source,
                next.darkMode, next.linkColor, decorator)
        }
        binding = next
    }
}

private fun applyCyBadge(view: TextView, span: Spannable) {
    if (span.length < 2) return
    val drawable: Drawable = view.context.getDrawable(R.drawable.official_cy_badge)?.mutate() ?: return
    val height = (15f * view.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    val width = if (drawable.intrinsicHeight > 0) {
        (height * drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight).roundToInt().coerceAtLeast(1)
    } else height
    drawable.setBounds(0, 0, width, height)
    span.setSpan(CenteredImageSpan(drawable), 0, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
}
