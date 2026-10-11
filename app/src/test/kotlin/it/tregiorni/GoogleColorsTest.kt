package it.tregiorni

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleColorsTest {
    private fun hex(color: Int) = "#%06X".format(color and 0xFFFFFF)

    private fun assertDisplayed(providerColor: Int, light: String, dark: String) {
        assertEquals("light", light, hex(GoogleColors.forDisplay(providerColor, dark = false)))
        assertEquals("dark", dark, hex(GoogleColors.forDisplay(providerColor, dark = true)))
    }

    // Expected values sampled from screenshots of Google Calendar 2026.38 on a real phone.

    @Test
    fun graphiteEventMatchesGoogleCalendar() = assertDisplayed(0xFFE1E1E1.toInt(), light = "#7E7E7E", dark = "#808080")

    @Test
    fun tomatoEventMatchesGoogleCalendar() = assertDisplayed(0xFFDC2127.toInt(), light = "#E3573A", dark = "#DD583D")

    @Test
    fun customCalendarColorMatchesGoogleCalendar() =
        assertDisplayed(0xFF5C93E5.toInt(), light = "#637FC4", dark = "#667FC2")

    @Test
    fun oldCalendarAndEventColorsMapToTheCurrentPalette() {
        assertEquals("#616161", hex(GoogleColors.toCurrentPalette(0xFFC2C2C2.toInt())))
        assertEquals("#616161", hex(GoogleColors.toCurrentPalette(0xFFE1E1E1.toInt())))
        assertEquals("#E67C73", hex(GoogleColors.toCurrentPalette(0xFFFF887C.toInt())))
        assertEquals("#4285F4", hex(GoogleColors.toCurrentPalette(0xFF9FC6E7.toInt())))
    }

    @Test
    fun currentPaletteColorsAreKept() {
        assertEquals("#039BE5", hex(GoogleColors.toCurrentPalette(0xFF039BE5.toInt())))
    }

    @Test
    fun brightCustomColorsGetDarkerAndMoreSaturated() {
        assertEquals("#2962B7", hex(GoogleColors.toCurrentPalette(0xFF5C93E5.toInt())))
    }

    @Test
    fun darkCustomColorsAreKept() {
        assertEquals("#2962B7", hex(GoogleColors.toCurrentPalette(0xFF2962B7.toInt())))
    }

    @Test
    fun greysStayGrey() {
        val light = GoogleColors.forDisplay(0xFF000000.toInt(), dark = false)
        assertEquals(light shr 16 and 0xFF, light and 0xFF)
        assertEquals(light shr 8 and 0xFF, light and 0xFF)
    }
}
