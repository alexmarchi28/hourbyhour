package it.hourbyhour

import kotlin.math.roundToInt

/** The preferences of one widget, chosen in WidgetSettingsActivity. */
data class WidgetSettings(
    /** Transparency of the widget background in percent: 0 is opaque, 100 shows only the events. */
    val transparency: Int = DEFAULT_TRANSPARENCY,
) {
    init {
        require(transparency in 0..100) { "Transparency must be between 0 and 100, not $transparency" }
    }

    /** Alpha of the widget background, from 255 (opaque) to 0 (invisible). */
    val backgroundAlpha: Int get() = (255 * (100 - transparency) / 100f).roundToInt()

    companion object {
        /** The look of the widget before transparency could be chosen: a background at 95%. */
        const val DEFAULT_TRANSPARENCY = 5

        /** The slider moves in steps of this many percent. */
        const val TRANSPARENCY_STEP = 5
    }
}

/** [color] with its alpha channel replaced by [alpha] (0-255). */
fun withAlpha(color: Int, alpha: Int): Int = (color and 0xFFFFFF) or (alpha.coerceIn(0, 255) shl 24)
