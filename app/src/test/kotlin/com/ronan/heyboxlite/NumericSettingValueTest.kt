package com.ronan.heyboxlite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumericSettingValueTest {
    private val crown = NumericSettingValue(
        CrownScrollController.MIN_SPEED_PERCENT,
        CrownScrollController.SLIDER_MAX_SPEED_PERCENT,
        step = 5,
        allowAboveSliderMaximum = true,
    )

    @Test fun customValuesKeepTheirExactValueWhileOnlyTheSliderIsBounded() {
        listOf(201, 501, 100_000, Int.MAX_VALUE).forEach { value ->
            assertEquals(NumericValueParseResult.Valid(value), crown.parse(" $value "))
            assertEquals(value, crown.normalize(value))
            assertEquals(CrownScrollController.SLIDER_MAX_SPEED_PERCENT, crown.sliderValue(value))
            assertEquals("$value%", crown.display(value, "%"))
        }
    }

    @Test fun rejectsInvalidNonPositiveAndUnrepresentableCustomValues() {
        listOf("", " ", "0", "-5", "4", "1.5", "NaN", "Infinity", "-Infinity",
            "2147483648", "9223372036854775808", "9".repeat(80)).forEach { raw ->
            assertTrue(raw, crown.parse(raw) is NumericValueParseResult.Invalid)
        }
        assertEquals(NumericValueParseResult.Valid(CrownScrollController.MIN_SPEED_PERCENT),
            crown.parse(CrownScrollController.MIN_SPEED_PERCENT.toString()))
    }

    @Test fun slidersStillSnapAndStayWithinTheirOriginalRange() {
        assertEquals(5, crown.snapSliderValue(0f))
        assertEquals(150, crown.snapSliderValue(151f))
        assertEquals(200, crown.snapSliderValue(500f))
        assertEquals(5, crown.normalize(0))
    }

    @Test fun otherNumericSettingsRemainBoundedUnlessTheyOptIn() {
        val bounded = NumericSettingValue(5, 200, step = 5)

        assertEquals(200, bounded.normalize(501))
        assertEquals(200, bounded.sliderValue(501))
        assertTrue(bounded.parse("501") is NumericValueParseResult.Invalid)
        assertEquals(NumericValueParseResult.Valid(150), bounded.parse("150"))
    }
}
