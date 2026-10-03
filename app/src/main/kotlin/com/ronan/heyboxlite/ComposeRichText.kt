package com.ronan.heyboxlite

import android.graphics.drawable.Drawable
import android.text.Spannable
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            TextView(context).apply {
                includeFontPadding = false
                setTextIsSelectable(false)
                isLongClickable = false
            }
        },
        update = { view ->
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.value)
            view.setTextColor(textColor.toArgb())
            view.setLineSpacing(0f, (lineHeight.value / fontSize.value).coerceAtLeast(1f))
            view.typeface = if (fontWeight.weight >= FontWeight.Medium.weight) {
                android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            } else {
                android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            }
            val decorator = if (cy) EmojiRenderer.Decorator { span -> applyCyBadge(view, span) } else null
            RichInlineRenderer.set(view, if (cy) "Cy $source" else source,
                darkMode, linkColor.toArgb(), decorator)
        },
    )
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
