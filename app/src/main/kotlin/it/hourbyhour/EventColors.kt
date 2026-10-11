package it.hourbyhour

import kotlin.math.pow

/** Fill and text colors of an event, for the light and the dark theme. */
data class EventColors(val lightFill: Int, val lightText: Int, val darkFill: Int, val darkText: Int)

/**
 * The colors of an event as Google Calendar shows them. Past events are faded towards the widget
 * background of each theme, so the upcoming ones stand out.
 */
fun eventColors(providerColor: Int, past: Boolean, lightBackground: Int, darkBackground: Int): EventColors {
    fun fill(dark: Boolean): Int {
        val color = GoogleColors.forDisplay(providerColor, dark)
        return if (past) blend(color, if (dark) darkBackground else lightBackground, 0.5f) else color
    }
    val light = fill(dark = false)
    val dark = fill(dark = true)
    return EventColors(light, textOn(light), dark, textOn(dark))
}

/** Dark text on light colors, white text on dark ones. */
fun textOn(background: Int): Int = if (luminance(background) > 0.5) DARK_TEXT else WHITE

/** Mixes the RGB channels of [from] towards [to] by [ratio]; the result is opaque. */
fun blend(from: Int, to: Int, ratio: Float): Int {
    fun channel(shift: Int) = (((from shr shift) and 0xFF) * (1 - ratio) + ((to shr shift) and 0xFF) * ratio).toInt()
    return OPAQUE or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** Relative luminance of an sRGB color, as android.graphics.Color.luminance computes it. */
private fun luminance(color: Int): Double {
    fun linear(channel: Int): Double {
        val c = channel / 255.0
        return if (c >= 0.04045) ((c + 0.055) / 1.055).pow(2.4) else c / 12.92
    }
    return 0.2126 * linear(color shr 16 and 0xFF) + 0.7152 * linear(color shr 8 and 0xFF) + 0.0722 * linear(color and 0xFF)
}

private const val OPAQUE = 0xFF000000.toInt()
private const val DARK_TEXT = 0xFF202124.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
