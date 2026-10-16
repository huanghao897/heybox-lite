@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ronan.heyboxlite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.roundToInt

/** Slider bounds and direct-input parsing intentionally mirror NumericSettingDialog. */
internal data class NumericSettingValue(
    val minimum: Int,
    val sliderMaximum: Int,
    val step: Int,
    val allowAboveSliderMaximum: Boolean = false,
) {
    val lowerBound: Int = minOf(minimum, sliderMaximum)
    val upperBound: Int = maxOf(minimum, sliderMaximum)
    val sliderSteps: Int = ((upperBound - lowerBound) / step.coerceAtLeast(1) - 1)
        .coerceAtLeast(0)

    fun normalize(value: Int): Int = if (allowAboveSliderMaximum) {
        maxOf(lowerBound, value)
    } else {
        value.coerceIn(lowerBound, upperBound)
    }

    fun sliderValue(value: Int): Int = normalize(value).coerceIn(lowerBound, upperBound)

    fun snapSliderValue(raw: Float): Int {
        val increment = step.coerceAtLeast(1)
        val clamped = raw.coerceIn(lowerBound.toFloat(), upperBound.toFloat())
        val snapped = ((clamped - lowerBound) / increment).roundToInt() * increment + lowerBound
        return snapped.coerceIn(lowerBound, upperBound)
    }

    fun parse(raw: String): NumericValueParseResult {
        val value = try {
            if (raw.trim().isEmpty()) throw NumberFormatException()
            raw.trim().toLong()
        } catch (_: NumberFormatException) {
            return NumericValueParseResult.Invalid("请输入整数")
        }
        if (value < lowerBound.toLong()) {
            return NumericValueParseResult.Invalid("不能小于 $lowerBound")
        }
        if (!allowAboveSliderMaximum && value > upperBound.toLong()) {
            return NumericValueParseResult.Invalid("不能大于 $upperBound")
        }
        if (value > Int.MAX_VALUE.toLong()) {
            return NumericValueParseResult.Invalid("数值过大")
        }
        return NumericValueParseResult.Valid(value.toInt())
    }

    fun display(value: Int, unit: String): String {
        val suffix = unit.trim()
        return when {
            suffix.isEmpty() -> value.toString()
            suffix == "%" -> "$value%"
            else -> "$value $suffix"
        }
    }
}

internal sealed class NumericValueParseResult {
    data class Valid(val value: Int) : NumericValueParseResult()
    data class Invalid(val message: String) : NumericValueParseResult()
}

@Composable
private fun composeSettingSliderColors() = SliderDefaults.colors(
    thumbColor = if (LocalHeyboxTheme.current.dark) {
        androidx.compose.ui.graphics.Color(0xFFF5F5F7)
    } else {
        androidx.compose.ui.graphics.Color.White
    },
    activeTrackColor = if (LocalHeyboxTheme.current.dark) {
        androidx.compose.ui.graphics.Color(0xFF77777D)
    } else {
        androidx.compose.ui.graphics.Color(0xFF636368)
    },
    activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
    inactiveTrackColor = if (LocalHeyboxTheme.current.dark) {
        androidx.compose.ui.graphics.Color(0xFF3A3A3E)
    } else {
        androidx.compose.ui.graphics.Color(0xFFD1D1D6)
    },
    inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledThumbColor = LocalHeyboxTheme.current.subtle,
    disabledActiveTrackColor = LocalHeyboxTheme.current.subtle.copy(alpha = 0.45f),
    disabledActiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledInactiveTrackColor = LocalHeyboxTheme.current.subtle.copy(alpha = 0.28f),
    disabledInactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
)

@Composable
internal fun ComposeSettingRange(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    unit: String,
    icon: Int? = null,
    allowAboveSliderMaximum: Boolean = false,
    onValueChange: (Int) -> Unit,
) {
    val setting = remember(min, max, step, allowAboveSliderMaximum) {
        NumericSettingValue(min, max, step, allowAboveSliderMaximum)
    }
    var committed by remember(value, setting) {
        mutableStateOf(setting.normalize(value))
    }
    var slider by remember(value, setting) {
        mutableStateOf(setting.sliderValue(value).toFloat())
    }
    var dialogOpen by remember { mutableStateOf(false) }
    val display = setting.display(committed, unit)

    Column(modifier = Modifier.fillMaxWidth()) {
        WatchRow(
            title = title,
            value = display,
            icon = icon,
            onClick = { dialogOpen = true },
        )
        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
            Slider(
                value = slider,
                onValueChange = { raw ->
                    val bounded = setting.snapSliderValue(raw)
                    slider = bounded.toFloat()
                    committed = bounded
                    onValueChange(bounded)
                },
                valueRange = setting.lowerBound.toFloat()..setting.upperBound.toFloat(),
                steps = 0,
                colors = composeSettingSliderColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(watchDp(28))
                    .padding(horizontal = watchDp(11)),
            )
        }
    }

    if (dialogOpen) {
        ComposeNumericSettingDialog(
            title = title,
            unit = unit,
            setting = setting,
            current = committed,
            onDismiss = { dialogOpen = false },
            onValueChange = { next ->
                committed = setting.normalize(next)
                slider = setting.sliderValue(next).toFloat()
                onValueChange(next)
            },
        )
    }
}

@Composable
private fun ComposeNumericSettingDialog(
    title: String,
    unit: String,
    setting: NumericSettingValue,
    current: Int,
    onDismiss: () -> Unit,
    onValueChange: (Int) -> Unit,
) {
    var input by remember(setting, current) { mutableStateOf(current.toString()) }
    var slider by remember(setting, current) {
        mutableStateOf(setting.sliderValue(current).toFloat())
    }
    var error by remember { mutableStateOf<String?>(null) }
    val state = LocalHeyboxTheme.current

    WatchDialog(title, onDismiss, actions = {
        WatchActionButton("取消", onDismiss, Modifier.weight(1f))
        WatchActionButton("确定", {
            when (val parsed = setting.parse(input)) {
                is NumericValueParseResult.Valid -> {
                    onValueChange(parsed.value)
                    onDismiss()
                }
                is NumericValueParseResult.Invalid -> error = parsed.message
            }
        }, Modifier.weight(1f))
    }) {
        Text(
            text = if (setting.allowAboveSliderMaximum) {
                "滑杆显示常用范围，可直接输入更高数值"
            } else {
                "拖动调整，或点击数值直接输入"
            },
            color = state.muted,
            fontSize = watchSp(11f),
        )
        BasicTextField(
            value = input,
            onValueChange = {
                input = it.filter(Char::isDigit)
                error = null
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = watchDp(44))
                .background(state.panelElevated, RoundedCornerShape(watchDp(10)))
                .padding(horizontal = watchDp(10), vertical = watchDp(9))
                .semantics { contentDescription = title },
            textStyle = TextStyle(color = state.text, fontSize = watchSp(18f),
                fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(state.text),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (error != null) {
            Text(error.orEmpty(), color = state.accent, fontSize = watchSp(10f))
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
            Slider(
                value = slider,
                onValueChange = { raw ->
                    val bounded = setting.snapSliderValue(raw)
                    slider = bounded.toFloat()
                    input = bounded.toString()
                    error = null
                    onValueChange(bounded)
                },
                valueRange = setting.lowerBound.toFloat()..setting.upperBound.toFloat(),
                steps = 0,
                colors = composeSettingSliderColors(),
                modifier = Modifier.fillMaxWidth().height(watchDp(32)),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text(setting.display(setting.lowerBound, unit),
                color = state.muted, fontSize = watchSp(10f))
            Spacer(Modifier.weight(1f))
            Text(setting.display(setting.upperBound, unit) +
                if (setting.allowAboveSliderMaximum) "+" else "",
                color = state.muted, fontSize = watchSp(10f))
        }
    }
}
