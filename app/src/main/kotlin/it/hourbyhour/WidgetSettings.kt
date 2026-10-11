package it.hourbyhour

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** The preferences of one widget, chosen in WidgetSettingsActivity. */
data class WidgetSettings(
    /** Transparency of the widget background in percent: 0 is opaque, 100 shows only the events. */
    val transparency: Int = DEFAULT_TRANSPARENCY,
    /** How many days the widget shows, from 1 to [MAX_DAY_COUNT]. */
    val dayCount: Int = DEFAULT_DAY_COUNT,
    /** Start from Monday instead of today. Applies from [MIN_DAYS_FROM_MONDAY] days up, see [startsOnMonday]. */
    val startOnMonday: Boolean = false,
) {
    init {
        require(transparency in 0..100) { "Transparency must be between 0 and 100, not $transparency" }
        require(dayCount in 1..MAX_DAY_COUNT) { "The day count must be between 1 and $MAX_DAY_COUNT, not $dayCount" }
    }

    /** Alpha of the widget background, from 255 (opaque) to 0 (invisible). */
    val backgroundAlpha: Int get() = (255 * (100 - transparency) / 100f).roundToInt()

    /** Whether the widget shows a week from Monday: with fewer days it always starts today. */
    val startsOnMonday: Boolean get() = startOnMonday && dayCount >= MIN_DAYS_FROM_MONDAY

    /**
     * The first day shown when it is [today]. From Monday, once the chosen days of this week are
     * over (Saturday and Sunday with Monday to Friday, Sunday with Monday to Saturday) the widget
     * shows the next week, which is the one still to come.
     */
    fun firstDay(today: LocalDate): LocalDate {
        if (!startsOnMonday) return today
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return if (today.isAfter(monday.plusDays(dayCount - 1L))) monday.plusWeeks(1) else monday
    }

    companion object {
        /** The look of the widget before transparency could be chosen: a background at 95%. */
        const val DEFAULT_TRANSPARENCY = 5

        /** The slider moves in steps of this many percent. */
        const val TRANSPARENCY_STEP = 5

        /** The days the widget showed before they could be chosen. */
        const val DEFAULT_DAY_COUNT = 3
        const val MAX_DAY_COUNT = 7

        /** The fewest days that can start from Monday: Monday to Friday. */
        const val MIN_DAYS_FROM_MONDAY = 5
    }
}

/** [color] with its alpha channel replaced by [alpha] (0-255). */
fun withAlpha(color: Int, alpha: Int): Int = (color and 0xFFFFFF) or (alpha.coerceIn(0, 255) shl 24)
