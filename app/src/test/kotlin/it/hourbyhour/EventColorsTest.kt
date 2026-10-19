package it.hourbyhour

import org.junit.Assert.assertEquals
import org.junit.Test

class EventColorsTest {
    private val graphiteEvent = 0xFFE1E1E1.toInt()
    private val bananaEvent = 0xFFFBD75B.toInt()

    private fun hex(color: Int) = "#%08X".format(color)

    private fun colors(providerColor: Int, past: Boolean) = eventColors(providerColor, past)

    @Test
    fun upcomingEventsUseGoogleCalendarColors() {
        val colors = colors(graphiteEvent, past = false)

        assertEquals("#FF7E7E7E", hex(colors.lightFill))
        assertEquals("#FF808080", hex(colors.darkFill))
    }

    @Test
    fun pastEventsAreHalfTransparent() {
        // The widget background shows through, whatever its color: nothing depends on the palette.
        val colors = colors(graphiteEvent, past = true)

        assertEquals("#807E7E7E", hex(colors.lightFill))
        assertEquals("#80808080", hex(colors.darkFill))
    }

    @Test
    fun onAnOpaqueBackgroundPastEventsLookFadedTowardsIt() {
        // As before the fill was made transparent: half the event color, half the background.
        val fill = colors(graphiteEvent, past = true).lightFill

        assertEquals("#FFBEBEBE", hex(blend(fill or 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 1 - (fill ushr 24) / 255f)))
    }

    @Test
    fun theTextOfPastEventsSuitsTheirFadedLook() {
        // Faded towards a light background, graphite is light enough for dark text; on a dark one it stays white.
        val colors = colors(graphiteEvent, past = true)

        assertEquals("#FF202124", hex(colors.lightText))
        assertEquals("#FFFFFFFF", hex(colors.darkText))
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

    @Test
    fun withAlphaReplacesOnlyTheAlphaChannel() {
        assertEquals(0x80202124.toInt(), withAlpha(0xFF202124.toInt(), 0x80))
        assertEquals(0x00FFFFFF, withAlpha(0xFFFFFFFF.toInt(), 0))
        assertEquals(0xFF123456.toInt(), withAlpha(0x00123456, 300))
    }
}
