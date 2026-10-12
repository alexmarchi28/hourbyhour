package it.hourbyhour

import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** An event instance as read from the calendar provider. Times are epoch millis. */
data class CalendarEvent(
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    /** The calendar the event belongs to, as in the calendar provider. */
    val calendarId: Long = 0,
    /** The event in the calendar provider; occurrences of a recurring event share it. */
    val eventId: Long = 0,
)

/** A calendar of the phone whose events are synced, as offered in the widget settings. */
data class CalendarInfo(val id: Long, val name: String, val color: Int)

/** The part of a timed event that falls on one day, in minutes from midnight (0..1440). */
data class Segment(val event: CalendarEvent, val startMinute: Int, val endMinute: Int)

/** A segment drawn in [column] of [columns] side-by-side columns, so overlapping events stay visible. */
data class PlacedSegment(val segment: Segment, val column: Int, val columns: Int)

/** What the widget shows: the days with their events, as seen at [now]. */
data class Snapshot(val days: List<Day>, val now: ZonedDateTime)

/**
 * The events read from the calendar at [now], for the current and the next week: every widget (and
 * the preview, while settings change) takes its days from here without reading the calendar again.
 */
data class Agenda(val events: List<CalendarEvent>, val now: ZonedDateTime) {
    fun snapshot(settings: WidgetSettings): Snapshot {
        val firstDay = settings.firstDay(now.toLocalDate())
        val shown = events.filter { settings.shows(it.calendarId) }
        return Snapshot(splitIntoDays(shown, firstDay, settings.dayCount, now.zone), now)
    }

    companion object {
        /**
         * Two weeks from Monday of the current week cover every setting: up to seven days from
         * today, and a week from Monday, this one or the next.
         */
        const val DAYS = 14

        fun firstDay(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}

/** One column of the widget. [allDay] holds the events shown in the banner row above the grid. */
data class Day(val date: LocalDate, val allDay: List<CalendarEvent>, val timed: List<Segment>)

/** The hours shown on the grid, from [start] (inclusive) to [end] (exclusive), within 0..24. */
data class HourRange(val start: Int, val end: Int) {
    val hours: Int get() = end - start
}

private const val MINUTES_PER_DAY = 24 * 60
private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

/**
 * Distributes [events] over [dayCount] days starting at [firstDay].
 *
 * All-day events and timed events lasting 24 hours or more go to the banner row of every day
 * they touch, as Google Calendar does. Shorter timed events are clipped to each day they cross.
 */
fun splitIntoDays(events: List<CalendarEvent>, firstDay: LocalDate, dayCount: Int, zone: ZoneId): List<Day> =
    (0 until dayCount).map { offset ->
        val date = firstDay.plusDays(offset.toLong())
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val allDay = mutableListOf<CalendarEvent>()
        val timed = mutableListOf<Segment>()
        for (event in events) {
            if (isBanner(event)) {
                if (bannerCovers(event, date, zone)) allDay += event
                continue
            }
            val touchesDay = if (event.end > event.begin) {
                event.begin < dayEnd && event.end > dayStart
            } else {
                event.begin in dayStart until dayEnd
            }
            if (!touchesDay) continue
            val start = minuteOfDay(maxOf(event.begin, dayStart), dayEnd, zone)
            val end = minuteOfDay(minOf(event.end, dayEnd), dayEnd, zone)
            timed += Segment(event, start, maxOf(start, end))
        }
        Day(date, allDay, timed.sortedWith(compareBy<Segment> { it.startMinute }.thenByDescending { it.endMinute }))
    }

/**
 * The time span to query for [dayCount] days from [firstDay], as epoch millis [first, second). It has
 * one extra day on each side: all-day events are stored at UTC midnights and can start or end outside
 * the local days. [splitIntoDays] keeps only what falls on the shown days.
 */
fun queryWindow(firstDay: LocalDate, dayCount: Int, zone: ZoneId): Pair<Long, Long> = Pair(
    firstDay.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    firstDay.plusDays(dayCount + 1L).atStartOfDay(zone).toInstant().toEpochMilli(),
)

private fun isBanner(event: CalendarEvent) = event.allDay || event.end - event.begin >= MILLIS_PER_DAY

/** All-day events are stored at UTC midnights, so their dates must be read in UTC. */
private fun bannerCovers(event: CalendarEvent, date: LocalDate, zone: ZoneId): Boolean {
    val dateZone = if (event.allDay) ZoneOffset.UTC else zone
    val first = Instant.ofEpochMilli(event.begin).atZone(dateZone).toLocalDate()
    val last = Instant.ofEpochMilli(maxOf(event.begin, event.end - 1)).atZone(dateZone).toLocalDate()
    return date in first..last
}

private fun minuteOfDay(millis: Long, dayEnd: Long, zone: ZoneId): Int {
    if (millis >= dayEnd) return MINUTES_PER_DAY
    val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
    return time.hour * 60 + time.minute
}

/**
 * The hours to show: from the earliest start to the latest end of the timed events,
 * widened to at least [minHours] so a single short event does not fill the whole widget.
 */
fun hourRange(days: List<Day>, minHours: Int = 6, default: HourRange = HourRange(8, 20)): HourRange {
    val segments = days.flatMap { it.timed }
    if (segments.isEmpty()) return default
    var start = segments.minOf { it.startMinute } / 60
    var end = (segments.maxOf { it.endMinute } + 59) / 60
    val target = minHours.coerceIn(1, 24)
    while (end - start < target) {
        if (end < 24) end++
        if (end - start < target && start > 0) start--
    }
    return HourRange(start, end)
}

/**
 * Assigns side-by-side columns to overlapping segments. Each group of transitively overlapping
 * segments is split into as many columns as it needs. Segments shorter than [minDuration] minutes
 * (the thinnest bar the widget draws) count as lasting [minDuration], because that is how much space
 * they take.
 */
fun placeSegments(segments: List<Segment>, minDuration: Int): List<PlacedSegment> {
    val sorted = segments.sortedWith(compareBy<Segment> { it.startMinute }.thenByDescending { it.endMinute })
    val placed = mutableListOf<PlacedSegment>()
    val group = mutableListOf<Pair<Segment, Int>>()
    val columnEnds = mutableListOf<Int>()
    var groupEnd = Int.MIN_VALUE

    fun closeGroup() {
        group.forEach { (segment, column) -> placed += PlacedSegment(segment, column, columnEnds.size) }
        group.clear()
        columnEnds.clear()
    }

    for (segment in sorted) {
        val end = maxOf(segment.endMinute, segment.startMinute + minDuration)
        if (segment.startMinute >= groupEnd) closeGroup()
        var column = columnEnds.indexOfFirst { it <= segment.startMinute }
        if (column == -1) {
            column = columnEnds.size
            columnEnds += end
        } else {
            columnEnds[column] = end
        }
        group += segment to column
        groupEnd = if (group.size == 1) end else maxOf(groupEnd, end)
    }
    closeGroup()
    return placed
}
