package it.hourbyhour

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

    /** Current palette color to its displayed color in the light and dark theme. */
    private val adapted = listOf(
        Triple(0xD50000, "#E3573A", "#DD583D"), // Tomato
        Triple(0xF4511E, "#FA7B50", "#E26B44"), // Tangerine
        Triple(0xEF6C00, "#F78C48", "#DB793A"), // Pumpkin
        Triple(0xF09300, "#F7A94F", "#DE9642"), // Mango
        Triple(0xF6BF26, "#FBCB62", "#E6BA57"), // Banana
        Triple(0xE4C441, "#ECCF6F", "#D8BD63"), // Citron
        Triple(0xC0CA33, "#D0D467", "#BDC15B"), // Avocado
        Triple(0x7CB342, "#99C26C", "#87AD5C"), // Pistachio
        Triple(0x0B8043, "#509967", "#4E9364"), // Basil
        Triple(0x009688, "#54AB9F", "#479A8F"), // Eucalyptus
        Triple(0x33B679, "#6AC594", "#5AAF81"), // Sage
        Triple(0x039BE5, "#66AEE9", "#5399D0"), // Peacock
        Triple(0x4285F4, "#7A9CF4", "#6B8BDD"), // Cobalt
        Triple(0x3F51B5, "#6E71C2", "#7577C4"), // Blueberry
        Triple(0x7986CB, "#959DD5", "#848BC0"), // Lavender
        Triple(0xB39DDB, "#C3B0E3", "#AE9DCC"), // Wisteria
        Triple(0x9E69AF, "#B186BE", "#A47CB0"), // Amethyst
        Triple(0x8E24AA, "#A559B8", "#AA61BB"), // Grape
        Triple(0xAD1457, "#C05376", "#C35C7C"), // Radicchio
        Triple(0xD81B60, "#E25E7E", "#D85C79"), // Cherry blossom
        Triple(0xE67C73, "#ED978E", "#D4847B"), // Flamingo
        Triple(0x795548, "#967468", "#99786C"), // Cocoa
        Triple(0x616161, "#7E7E7E", "#808080"), // Graphite
        Triple(0xA79B8E, "#BAAEA1", "#A5998C"), // Birch
    )

    @Test
    fun wholePaletteMatchesAnIndependentImplementation() {
        // Expected values computed by a separate Python implementation of the same steps.
        for ((color, light, dark) in adapted) {
            val name = "#%06X".format(color)
            assertEquals("$name light", light, hex(GoogleColors.adaptToTheme(color, dark = false)))
            assertEquals("$name dark", dark, hex(GoogleColors.adaptToTheme(color, dark = true)))
        }
    }

    @Test
    fun everyOldPaletteColorMapsLikeGoogleCalendar() {
        // Old API colors (calendar index 1-24, then event index 1-11) and their current color,
        // as listed in the Google Calendar app.
        val calendarColors = listOf(
            0xAC725E to 0x795548, 0xD06B64 to 0xE67C73, 0xF83A22 to 0xD50000, 0xFA573C to 0xF4511E,
            0xFF7537 to 0xEF6C00, 0xFFAD46 to 0xF09300, 0x42D692 to 0x009688, 0x16A765 to 0x0B8043,
            0x7BD148 to 0x7CB342, 0xB3DC6C to 0xC0CA33, 0xFBE983 to 0xE4C441, 0xFAD165 to 0xF6BF26,
            0x92E1C0 to 0x33B679, 0x9FE1E7 to 0x039BE5, 0x9FC6E7 to 0x4285F4, 0x4986E7 to 0x3F51B5,
            0x9A9CFF to 0x7986CB, 0xB99AFF to 0xB39DDB, 0xC2C2C2 to 0x616161, 0xCABDBF to 0xA79B8E,
            0xCCA6AC to 0xAD1457, 0xF691B2 to 0xD81B60, 0xCD74E6 to 0x8E24AA, 0xA47AE2 to 0x9E69AF,
        )
        val eventColors = listOf(
            0xA4BDFC to 0x7986CB, 0x7AE7BF to 0x33B679, 0xDBADFF to 0x8E24AA, 0xFF887C to 0xE67C73,
            0xFBD75B to 0xF6BF26, 0xFFB878 to 0xF4511E, 0x46D6DB to 0x039BE5, 0xE1E1E1 to 0x616161,
            0x5484ED to 0x3F51B5, 0x51B749 to 0x0B8043, 0xDC2127 to 0xD50000,
        )
        for ((old, current) in calendarColors + eventColors) {
            assertEquals("#%06X".format(old), "#%06X".format(current), hex(GoogleColors.toCurrentPalette(old)))
        }
    }

    @Test
    fun alphaOfTheProviderColorIsIgnored() {
        assertEquals(GoogleColors.forDisplay(0xFFE1E1E1.toInt(), dark = true), GoogleColors.forDisplay(0x00E1E1E1, dark = true))
    }
}
