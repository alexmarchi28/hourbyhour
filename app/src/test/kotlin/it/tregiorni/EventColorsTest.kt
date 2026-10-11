package it.tregiorni

import org.junit.Assert.assertEquals
import org.junit.Test

class EventColorsTest {
    private val lightBackground = 0xFFFFFFFF.toInt()
    private val darkBackground = 0xFF202124.toInt()
    private val graphiteEvent = 0xFFE1E1E1.toInt()
    private val bananaEvent = 0xFFFBD75B.toInt()

    private fun hex(color: Int) = "#%08X".format(color)

    private fun colors(providerColor: Int, past: Boolean) =
        eventColors(providerColor, past, lightBackground, darkBackground)

    @Test
    fun upcomingEventsUseGoogleCalendarColors() {
        val colors = colors(graphiteEvent, past = false)

        assertEquals("#FF7E7E7E", hex(colors.lightFill))
        assertEquals("#FF808080", hex(colors.darkFill))
    }

    @Test
    fun pastEventsAreFadedTowardsEachThemeBackground() {
        val colors = colors(graphiteEvent, past = true)

        assertEquals("#FFBEBEBE", hex(colors.lightFill))
        assertEquals("#FF505052", hex(colors.darkFill))
    }

    @Test
    fun darkFillsGetWhiteText() {
        val colors = colors(graphiteEvent, past = false)

        assertEquals("#FFFFFFFF", hex(colors.lightText))
        assertEquals("#FFFFFFFF", hex(colors.darkText))
    }

    @Test
    fun lightFillsGetDarkText() {
        // Banana is #FBCB62 in the light theme.
        assertEquals("#FF202124", hex(colors(bananaEvent, past = false).lightText))
    }

    @Test
    fun blendIsOpaqueAndProportional() {
        assertEquals("#FF000000", hex(blend(0x00000000, 0x00000000, 0.5f)))
        assertEquals("#FF404040", hex(blend(0xFF000000.toInt(), 0xFF808080.toInt(), 0.5f)))
        assertEquals("#FF808080", hex(blend(0xFF000000.toInt(), 0xFF808080.toInt(), 1f)))
    }
}
