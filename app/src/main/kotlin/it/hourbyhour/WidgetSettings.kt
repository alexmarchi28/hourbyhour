package it.hourbyhour

import java.time.LocalDate
import kotlin.math.roundToInt

/** The preferences of one widget, chosen in WidgetSettingsActivity. */
data class WidgetSettings(
    /** Transparency of the widget background in percent: 0 is opaque, 100 shows only the events. */
    val transparency: Int = DEFAULT_TRANSPARENCY,
    /** How many days the widget shows, from 1 to [MAX_DAY_COUNT]. */
    val dayCount: Int = DEFAULT_DAY_COUNT,
    /** Start from Monday instead of today. Applies from [MIN_DAYS_FROM_MONDAY] days up, see [startsOnMonday]. */
    val startOnMonday: Boolean = false,
    /** The calendars to show, by provider id; null shows them all, including calendars added later. */
    val calendarIds: Set<Long>? = null,
    /** Show birthdays, which are a calendar of their own in Google Calendar (see [CalendarEvent.birthday]). */
    val showBirthdays: Boolean = true,
    /** Show at least [hours] instead of fitting the hours to the events, see [minimumHours]. */
    val customHours: Boolean = false,
    /** The hours chosen for the grid, kept while [customHours] is off. */
    val hours: HourRange = DEFAULT_HOURS,
) {
    init {
        require(transparency in TRANSPARENCY_RANGE) { "Transparency must be in $TRANSPARENCY_RANGE, not $transparency" }
        require(dayCount in DAY_COUNT_RANGE) { "The day count must be in $DAY_COUNT_RANGE, not $dayCount" }
        require(hours.start in HOURS_RANGE && hours.end in HOURS_RANGE && hours.start < hours.end) {
            "The hours must be within a day, not $hours"
        }
    }

    /** The hours the grid shows at least (it grows to fit the events outside them); null fits them to the events. */
    val minimumHours: HourRange? get() = hours.takeIf { customHours }

    /** Alpha of the widget background, from 255 (opaque) to 0 (invisible). */
    val backgroundAlpha: Int get() = (255 * (100 - transparency) / 100f).roundToInt()

    /** Whether the widget shows the events of the calendar with [calendarId]. */
    fun showsCalendar(calendarId: Long): Boolean = calendarIds == null || calendarId in calendarIds

    /** Whether the widget shows [event]: birthdays follow their own choice, not their calendar's, as in Google Calendar. */
    fun shows(event: CalendarEvent): Boolean = if (event.birthday) showBirthdays else showsCalendar(event.calendarId)

    /** Whether [startOnMonday] applies with this many days: with fewer days the widget always starts today. */
    val canStartOnMonday: Boolean get() = dayCount >= MIN_DAYS_FROM_MONDAY

    /** Whether the widget shows a week from Monday. */
    val startsOnMonday: Boolean get() = startOnMonday && canStartOnMonday

    /**
     * The first day shown when it is [today]. From Monday, once the chosen days of this week are
     * over (Saturday and Sunday with Monday to Friday, Sunday with Monday to Saturday) the widget
     * shows the next week, which is the one still to come.
     */
    fun firstDay(today: LocalDate): LocalDate {
        if (!startsOnMonday) return today
        val monday = mondayOf(today)
        return if (today.isAfter(monday.plusDays(dayCount - 1L))) monday.plusWeeks(1) else monday
    }

    companion object {
        /** Transparency in percent, from opaque to invisible. */
        val TRANSPARENCY_RANGE = 0..100

        /** The look of the widget before transparency could be chosen: a background at 95%. */
        const val DEFAULT_TRANSPARENCY = 5

        /** The slider moves in steps of this many percent. */
        const val TRANSPARENCY_STEP = 5

        /** The days the widget showed before they could be chosen. */
        const val DEFAULT_DAY_COUNT = 3
        const val MAX_DAY_COUNT = 7
        val DAY_COUNT_RANGE = 1..MAX_DAY_COUNT

        /** The hours a range can start and end at: from midnight to the next midnight. */
        val HOURS_RANGE = 0..24

        /** The hours the widget shows without events, offered when custom hours are turned on. */
        val DEFAULT_HOURS = HourRange.WITHOUT_EVENTS

        /** The fewest days that can start from Monday: Monday to Friday. */
        const val MIN_DAYS_FROM_MONDAY = 5

        /**
         * The [calendarIds] for the calendars [checked] among [available]: checking them all means
         * "all calendars", so calendars added later show up too.
         */
        fun calendarSelection(checked: Set<Long>, available: Collection<Long>): Set<Long>? =
            if (checked.containsAll(available)) null else checked
    }
}
