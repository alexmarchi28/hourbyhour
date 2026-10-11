package it.tregiorni

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSettingsTest {
    @Test
    fun defaultKeepsTheOriginalBackgroundAlpha() {
        // Before transparency could be chosen, the background color had alpha F2.
        assertEquals(0xF2, WidgetSettings().backgroundAlpha)
    }

    @Test
    fun transparencyMapsToAlpha() {
        assertEquals(255, WidgetSettings(0).backgroundAlpha)
        assertEquals(128, WidgetSettings(50).backgroundAlpha)
        assertEquals(0, WidgetSettings(100).backgroundAlpha)
    }

    @Test(expected = IllegalArgumentException::class)
    fun transparencyAboveOneHundredIsRejected() {
        WidgetSettings(101)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeTransparencyIsRejected() {
        WidgetSettings(-5)
    }

    @Test
    fun withAlphaReplacesOnlyTheAlphaChannel() {
        assertEquals(0x80202124.toInt(), withAlpha(0xFF202124.toInt(), 0x80))
        assertEquals(0x00FFFFFF, withAlpha(0xFFFFFFFF.toInt(), 0))
        assertEquals(0xFF123456.toInt(), withAlpha(0x00123456, 300))
    }
}
