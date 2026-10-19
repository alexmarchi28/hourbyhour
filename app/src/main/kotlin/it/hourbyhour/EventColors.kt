package it.hourbyhour

import kotlin.math.pow

/** Fill and text colors of an event, for the light and the dark theme. */
data class EventColors(val lightFill: Int, val lightText: Int, val darkFill: Int, val darkText: Int)

/**
 * The colors of an event as Google Calendar shows them. Past events are half transparent, so the
 * upcoming ones stand out: the widget background shows through, and since the launcher resolves its
 * color nothing here depends on it (it follows the system palette at once, see WidgetViews).
 */
fun eventColors(providerColor: Int, past: Boolean): EventColors {
    fun colors(dark: Boolean): Pair<Int, Int> {
        val color = GoogleColors.forDisplay(providerColor, dark)
        if (!past) return color to textOn(color)
        // The text suits the faded look on a typical background of the theme.
        val faded = blend(color, if (dark) DARK_BACKGROUND else LIGHT_BACKGROUND, PAST_FADE)
        return withAlpha(color, PAST_ALPHA) to textOn(faded)
    }
    val (lightFill, lightText) = colors(dark = false)
    val (darkFill, darkText) = colors(dark = true)
    return EventColors(lightFill, lightText, darkFill, darkText)
}

/** Dark text on light colors, white text on dark ones. */
fun textOn(background: Int): Int = if (luminance(background) > 0.5) DARK_TEXT else WHITE

/** Mixes the RGB channels of [from] towards [to] by [ratio]; the result is opaque. */
fun blend(from: Int, to: Int, ratio: Float): Int {
    fun channel(shift: Int) = (((from shr shift) and 0xFF) * (1 - ratio) + ((to shr shift) and 0xFF) * ratio).toInt()
    return OPAQUE or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** [color] with its alpha channel replaced by [alpha] (0-255). */
fun withAlpha(color: Int, alpha: Int): Int = (color and 0xFFFFFF) or (alpha.coerceIn(0, 255) shl 24)

/** Relative luminance of an sRGB color, as android.graphics.Color.luminance computes it. */
private fun luminance(color: Int): Double {
    fun linear(channel: Int): Double {
        val c = channel / 255.0
        return if (c >= 0.04045) ((c + 0.055) / 1.055).pow(2.4) else c / 12.92
    }
    return 0.2126 * linear(color shr 16 and 0xFF) + 0.7152 * linear(color shr 8 and 0xFF) + 0.0722 * linear(color and 0xFF)
}

/** How much past events fade: their fill keeps half its opacity. */
private const val PAST_FADE = 0.5f
private const val PAST_ALPHA = 0x80

/** Backgrounds of the widget in each theme, only to choose the text of past events. */
private const val LIGHT_BACKGROUND = 0xFFFFFFFF.toInt()
private const val DARK_BACKGROUND = 0xFF202124.toInt()

private const val OPAQUE = 0xFF000000.toInt()
private const val DARK_TEXT = 0xFF202124.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
