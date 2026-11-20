package com.lonx.lyrico.data.model.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeColorTest {
    @Test
    fun restoresExistingStoredPaletteValues() {
        val storedValues = listOf(
            0xFF3482FF.toInt(), 0xFF36D167.toInt(), 0xFFFFB21D.toInt(),
            0xFFFF5722.toInt(), 0xFF7C4DFF.toInt(), 0xFFE91E63.toInt(),
            0xFF00BCD4.toInt()
        )

        assertEquals(ThemeColor.entries.drop(1), storedValues.map(ThemeColor::fromArgb))
        assertEquals(storedValues, ThemeColor.entries.drop(1).map { it.argb })
    }

    @Test
    fun missingOrUnknownStoredColorUsesSystemDefault() {
        assertEquals(ThemeColor.DEFAULT, ThemeColor.fromArgb(null))
        assertEquals(ThemeColor.DEFAULT, ThemeColor.fromArgb(0))
        assertEquals(ThemeColor.DEFAULT, ThemeColor.fromArgb(0xFF123456.toInt()))
        assertEquals(ThemeColor.DEFAULT, ThemeColor.fromArgb(0x803482FF.toInt()))
        assertNull(ThemeColor.DEFAULT.argb)
    }
}
