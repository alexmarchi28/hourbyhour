package it.hourbyhour

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
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

        /** The time span to read from the calendar at [now], as epoch millis [first, second). */
        fun window(now: ZonedDateTime): Pair<Long, Long> = queryWindow(mondayOf(now.toLocalDate()), DAYS, now.zone)

        /** The agenda at the time of [clock], with the [events] read for its [window]. */
        fun read(clock: Clock, events: (begin: Long, end: Long) -> List<CalendarEvent>): Agenda {
            val now = ZonedDateTime.now(clock)
            val (begin, end) = window(now)
            return Agenda(events(begin, end), now)
        }
    }
}

/** What the widget shows: the days with their events and the [hours] of the grid, as seen at [now]. */
data class Snapshot(val days: List<Day>, val now: ZonedDateTime, val hours: HourRange = hourRange(days))

/** Monday of the week of [date], which is [date] itself on a Monday. */
fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * The time span to query for [dayCount] days from [firstDay], as epoch millis [first, second). It has
 * one extra day on each side: all-day events are stored at UTC midnights and can start or end outside
 * the local days. [splitIntoDays] keeps only what falls on the shown days.
 */
fun queryWindow(firstDay: LocalDate, dayCount: Int, zone: ZoneId): Pair<Long, Long> = Pair(
    firstDay.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    firstDay.plusDays(dayCount + 1L).atStartOfDay(zone).toInstant().toEpochMilli(),
)
