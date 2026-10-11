package it.hourbyhour

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Turns the event colors stored in the calendar provider into the colors Google Calendar shows.
 *
 * The provider keeps the colors of the old Google Calendar API palette (Graphite is #E1E1E1, a light
 * grey). The Google Calendar app first maps them to its current palette (Graphite #616161), then
 * adapts lightness and chroma to the theme. Both steps reproduce the Google Calendar app 2026.38
 * with the default contrast, so the results match it to the last digit.
 */
object GoogleColors {

    /** The color Google Calendar shows for [providerColor] in the light or dark theme. Opaque. */
    fun forDisplay(providerColor: Int, dark: Boolean): Int = adaptToTheme(toCurrentPalette(providerColor), dark)

    /**
     * Maps an old palette color to the current one. Colors already in the current palette are kept;
     * custom colors that are too bright get darker and more saturated, as Google Calendar does.
     */
    fun toCurrentPalette(color: Int): Int {
        val rgb = color and 0xFFFFFF
        OLD_TO_CURRENT[rgb]?.let { return OPAQUE or it }
        if (rgb in CURRENT) return OPAQUE or rgb
        val hsv = rgbToHsv(rgb)
        if (hsv[2] > 0.79f) {
            hsv[1] = minOf(hsv[1] * 1.3f, 1f)
            hsv[2] *= 0.8f
        }
        return hsvToColor(hsv)
    }

    /** Changes lightness and chroma (in CIE LCh) to suit the theme, keeping the hue. */
    fun adaptToTheme(color: Int, dark: Boolean): Int {
        val (l, a, b) = rgbToLab(color)
        val chroma = hypot(a, b)
        // The hue goes through degrees like in Google Calendar, so that rounding matches.
        var hue = Math.toDegrees(atan2(b, a)) % 360
        if (hue != 0.0 && hue < 0) hue += 360
        if (hue <= 0.0001) hue = 0.0
        val curves = if (dark) DARK else LIGHT
        val newChroma = curves.chroma.map(chroma)
        val radians = Math.toRadians(hue)
        return labToRgb(curves.lightness.map(l), newChroma * cos(radians), newChroma * sin(radians))
    }

    /** Linear on [from, knee] and on [knee, to], mapped to [low] and [high]. */
    private class Curve(
        val from: Double,
        val to: Double,
        val knee: Double,
        val low: ClosedFloatingPointRange<Double>,
        val high: ClosedFloatingPointRange<Double>,
    ) {
        fun map(x: Double): Double =
            if (x > knee && to != knee) lerp(x, knee, to, high) else lerp(x, from, knee, low)

        private fun lerp(x: Double, start: Double, end: Double, out: ClosedFloatingPointRange<Double>) =
            out.start + (x - start) / (end - start) * (out.endInclusive - out.start)
    }

    private class Curves(val lightness: Curve, val chroma: Curve)

    private val LIGHT = Curves(
        lightness = Curve(0.0, 100.0, 100.0, 20.0..100.0, 0.0..0.0),
        chroma = Curve(0.0, 127.0, 10.0, 0.0..10.0, 10.0..95.0),
    )
    private val DARK = Curves(
        lightness = Curve(0.0, 100.0, 60.0, 40.0..60.0, 60.0..95.0),
        chroma = Curve(0.0, 127.0, 10.0, 0.0..10.0, 10.0..90.0),
    )

    private const val OPAQUE = 0xFF000000.toInt()

    private const val TOMATO = 0xD50000
    private const val TANGERINE = 0xF4511E
    private const val PUMPKIN = 0xEF6C00
    private const val MANGO = 0xF09300
    private const val BANANA = 0xF6BF26
    private const val CITRON = 0xE4C441
    private const val AVOCADO = 0xC0CA33
    private const val PISTACHIO = 0x7CB342
    private const val BASIL = 0x0B8043
    private const val EUCALYPTUS = 0x009688
    private const val SAGE = 0x33B679
    private const val PEACOCK = 0x039BE5
    private const val COBALT = 0x4285F4
    private const val BLUEBERRY = 0x3F51B5
    private const val LAVENDER = 0x7986CB
    private const val WISTERIA = 0xB39DDB
    private const val AMETHYST = 0x9E69AF
    private const val GRAPE = 0x8E24AA
    private const val RADICCHIO = 0xAD1457
    private const val CHERRY_BLOSSOM = 0xD81B60
    private const val FLAMINGO = 0xE67C73
    private const val COCOA = 0x795548
    private const val GRAPHITE = 0x616161
    private const val BIRCH = 0xA79B8E

    private val CURRENT = setOf(
        TOMATO, TANGERINE, PUMPKIN, MANGO, BANANA, CITRON, AVOCADO, PISTACHIO, BASIL, EUCALYPTUS, SAGE, PEACOCK,
        COBALT, BLUEBERRY, LAVENDER, WISTERIA, AMETHYST, GRAPE, RADICCHIO, CHERRY_BLOSSOM, FLAMINGO, COCOA, GRAPHITE, BIRCH,
    )

    private val OLD_TO_CURRENT = mapOf(
        // Calendar colors of the old API palette (color index 1-24).
        0xAC725E to COCOA, 0xD06B64 to FLAMINGO, 0xF83A22 to TOMATO, 0xFA573C to TANGERINE,
        0xFF7537 to PUMPKIN, 0xFFAD46 to MANGO, 0x42D692 to EUCALYPTUS, 0x16A765 to BASIL,
        0x7BD148 to PISTACHIO, 0xB3DC6C to AVOCADO, 0xFBE983 to CITRON, 0xFAD165 to BANANA,
        0x92E1C0 to SAGE, 0x9FE1E7 to PEACOCK, 0x9FC6E7 to COBALT, 0x4986E7 to BLUEBERRY,
        0x9A9CFF to LAVENDER, 0xB99AFF to WISTERIA, 0xC2C2C2 to GRAPHITE, 0xCABDBF to BIRCH,
        0xCCA6AC to RADICCHIO, 0xF691B2 to CHERRY_BLOSSOM, 0xCD74E6 to GRAPE, 0xA47AE2 to AMETHYST,
        // Event colors of the old API palette (color index 1-11).
        0xA4BDFC to LAVENDER, 0x7AE7BF to SAGE, 0xDBADFF to GRAPE, 0xFF887C to FLAMINGO,
        0xFBD75B to BANANA, 0xFFB878 to TANGERINE, 0x46D6DB to PEACOCK, 0xE1E1E1 to GRAPHITE,
        0x5484ED to BLUEBERRY, 0x51B749 to BASIL, 0xDC2127 to TOMATO,
        // Near copies of the current palette that Google Calendar also recognizes.
        0xD40000 to TOMATO, 0xF3501D to TANGERINE, 0xEE6B00 to PUMPKIN, 0xEF9200 to MANGO,
        0xF5BE25 to BANANA, 0xE2CB4C to CITRON, 0xBFC932 to AVOCADO, 0x7BB241 to PISTACHIO,
        0x0A7F42 to BASIL, 0x009587 to EUCALYPTUS, 0x029AE4 to PEACOCK, 0x4184F3 to COBALT,
        0x3F5CA9 to BLUEBERRY, 0x7885CA to LAVENDER, 0xB29CDA to WISTERIA, 0x8D23A9 to GRAPE,
        0xAC1356 to RADICCHIO, 0xD71A5F to CHERRY_BLOSSOM, 0xE57B72 to FLAMINGO, 0x785447 to COCOA,
        0x636363 to GRAPHITE,
    )

    // HSV conversions as in android.graphics.Color (Skia), in single precision, so that custom
    // colors round exactly like in Google Calendar. Pure Kotlin so they run in unit tests too.

    private fun rgbToHsv(rgb: Int): FloatArray {
        val r = rgb shr 16 and 0xFF
        val g = rgb shr 8 and 0xFF
        val b = rgb and 0xFF
        val max = maxOf(r, g, b)
        val delta = max - minOf(r, g, b)
        val v = max / 255f
        if (delta == 0) return floatArrayOf(0f, 0f, v)
        var h = when (max) {
            r -> (g - b).toFloat() / delta
            g -> 2f + (b - r).toFloat() / delta
            else -> 4f + (r - g).toFloat() / delta
        } * 60f
        if (h < 0f) h += 360f
        return floatArrayOf(h, delta.toFloat() / max, v)
    }

    private fun hsvToColor(hsv: FloatArray): Int {
        val s = hsv[1].coerceIn(0f, 1f)
        val v = hsv[2].coerceIn(0f, 1f)
        val vByte = roundToInt(v * 255f)
        if (s <= 1f / 4096f) return rgb(vByte, vByte, vByte)
        val hx = if (hsv[0] < 0f || hsv[0] >= 360f) 0f else hsv[0] / 60f
        val sector = floor(hx)
        val f = hx - sector
        val p = roundToInt((1f - s) * v * 255f)
        val q = roundToInt((1f - s * f) * v * 255f)
        val t = roundToInt((1f - s * (1f - f)) * v * 255f)
        return when (sector.toInt()) {
            0 -> rgb(vByte, t, p)
            1 -> rgb(q, vByte, p)
            2 -> rgb(p, vByte, t)
            3 -> rgb(p, q, vByte)
            4 -> rgb(t, p, vByte)
            else -> rgb(vByte, p, q)
        }
    }

    private fun roundToInt(x: Float) = floor(x + 0.5f).toInt()

    // CIE Lab conversions with the D65 white point, as in androidx.core.graphics.ColorUtils.

    private const val WHITE_X = 95.047
    private const val WHITE_Y = 100.0
    private const val WHITE_Z = 108.883
    private const val EPSILON = 0.008856
    private const val KAPPA = 903.3

    private fun rgbToLab(color: Int): Triple<Double, Double, Double> {
        fun linear(channel: Int): Double {
            val c = channel / 255.0
            return if (c < 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = linear(color shr 16 and 0xFF)
        val g = linear(color shr 8 and 0xFF)
        val b = linear(color and 0xFF)
        val x = 100 * (r * 0.4124 + g * 0.3576 + b * 0.1805)
        val y = 100 * (r * 0.2126 + g * 0.7152 + b * 0.0722)
        val z = 100 * (r * 0.0193 + g * 0.1192 + b * 0.9505)
        fun pivot(t: Double) = if (t > EPSILON) cbrt(t) else (KAPPA * t + 16) / 116
        val fx = pivot(x / WHITE_X)
        val fy = pivot(y / WHITE_Y)
        val fz = pivot(z / WHITE_Z)
        return Triple(maxOf(0.0, 116 * fy - 16), 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun labToRgb(l: Double, a: Double, b: Double): Int {
        val fy = (l + 16) / 116
        val fx = a / 500 + fy
        val fz = fy - b / 200
        val xr = fx.pow(3).let { if (it > EPSILON) it else (116 * fx - 16) / KAPPA }
        val yr = if (l > KAPPA * EPSILON) fy.pow(3) else l / KAPPA
        val zr = fz.pow(3).let { if (it > EPSILON) it else (116 * fz - 16) / KAPPA }
        val x = xr * WHITE_X
        val y = yr * WHITE_Y
        val z = zr * WHITE_Z
        fun channel(linear: Double): Int {
            val c = if (linear > 0.0031308) 1.055 * linear.pow(1 / 2.4) - 0.055 else 12.92 * linear
            return Math.round(c * 255).toInt().coerceIn(0, 255)
        }
        return rgb(
            channel((x * 3.2406 + y * -1.5372 + z * -0.4986) / 100),
            channel((x * -0.9689 + y * 1.8758 + z * 0.0415) / 100),
            channel((x * 0.0557 + y * -0.2040 + z * 1.0570) / 100),
        )
    }

    private fun rgb(r: Int, g: Int, b: Int) = OPAQUE or (r shl 16) or (g shl 8) or b
}
