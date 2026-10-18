package it.hourbyhour

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * The events read from the calendar at [now], for the current and the next week: every widget (and
 * the preview, while settings change) takes its days from here without reading the calendar again.
 */
data class Agenda(val events: List<CalendarEvent>, val now: ZonedDateTime) {
    fun snapshot(settings: WidgetSettings): Snapshot {
        val firstDay = settings.firstDay(now.toLocalDate())
        val shown = events.filter { settings.shows(it) }
        val days = splitIntoDays(shown, firstDay, settings.dayCount, now.zone)
        return Snapshot(days, now, hourRange(days, settings.minimumHours))
    }

    companion object {
        /**
         * Two weeks from Monday of the current week cover every setting: up to seven days from
         * today, and a week from Monday, this one or the next.
         */
        const val DAYS = 14
    }
}

/** What the widget shows: the days with their events and the [hours] of the grid, as seen at [now]. */
data class Snapshot(val days: List<Day>, val now: ZonedDateTime, val hours: HourRange = hourRange(days))

/** Monday of the week of [date], which is [date] itself on a Monday. */
fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
