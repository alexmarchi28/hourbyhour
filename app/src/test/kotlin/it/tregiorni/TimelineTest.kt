package it.tregiorni

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val today = LocalDate.of(2026, 10, 12)

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun utcMidnight(day: Int) =
        LocalDate.of(2026, 10, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun timed(title: String, begin: Long, end: Long) = CalendarEvent(title, begin, end, false, 0)

    private fun allDay(title: String, firstDay: Int, lastDay: Int) =
        CalendarEvent(title, utcMidnight(firstDay), utcMidnight(lastDay + 1), true, 0)

    private fun split(vararg events: CalendarEvent) = splitIntoDays(events.toList(), today, 3, zone)

    private fun segment(start: Int, end: Int) = Segment(timed("e", 0, 0), start, end)

    @Test
    fun timedEventBecomesSegmentOfItsDay() {
        val days = split(timed("Dentista", at(13, 9, 30), at(13, 10, 15)))

        assertEquals(listOf(0, 1, 0), days.map { it.timed.size })
        val segment = days[1].timed.single()
        assertEquals(9 * 60 + 30, segment.startMinute)
        assertEquals(10 * 60 + 15, segment.endMinute)
    }

    @Test
    fun eventCrossingMidnightIsClippedToEachDay() {
        val days = split(timed("Festa", at(12, 22), at(13, 2)))

        assertEquals(22 * 60, days[0].timed.single().startMinute)
        assertEquals(24 * 60, days[0].timed.single().endMinute)
        assertEquals(0, days[1].timed.single().startMinute)
        assertEquals(2 * 60, days[1].timed.single().endMinute)
        assertEquals(0, days[2].timed.size)
    }

    @Test
    fun eventEndingAtMidnightDoesNotReachNextDay() {
        val days = split(timed("Sera", at(12, 21), at(13, 0)))

        assertEquals(listOf(1, 0, 0), days.map { it.timed.size })
        assertEquals(24 * 60, days[0].timed.single().endMinute)
    }

    @Test
    fun allDayEventsAreReadInUtcDates() {
        // In Rome the UTC midnight of the 13th is 02:00 on the 13th: the event must not leak into the 12th or 14th.
        val days = split(allDay("Compleanno", 13, 13))

        assertEquals(listOf(0, 1, 0), days.map { it.allDay.size })
        assertEquals(listOf(0, 0, 0), days.map { it.timed.size })
    }

    @Test
    fun multiDayAllDayEventCoversEveryDay() {
        val days = split(allDay("Ferie", 10, 20))

        assertEquals(listOf(1, 1, 1), days.map { it.allDay.size })
    }

    @Test
    fun longTimedEventGoesToBannerOfTouchedDays() {
        val days = split(timed("Viaggio", at(12, 15), at(13, 18)))

        assertEquals(listOf(1, 1, 0), days.map { it.allDay.size })
        assertEquals(listOf(0, 0, 0), days.map { it.timed.size })
    }

    @Test
    fun eventsOutsideTheWindowAreIgnored() {
        val days = split(timed("Ieri", at(11, 9), at(11, 10)), timed("Dopo", at(15, 9), at(15, 10)))

        assertEquals(listOf(0, 0, 0), days.map { it.timed.size + it.allDay.size })
    }

    @Test
    fun zeroDurationEventIsKept() {
        val days = split(timed("Promemoria", at(14, 8), at(14, 8)))

        val segment = days[2].timed.single()
        assertEquals(8 * 60, segment.startMinute)
        assertEquals(8 * 60, segment.endMinute)
    }

    @Test
    fun hourRangeDefaultsWhenThereAreNoTimedEvents() {
        assertEquals(HourRange(8, 20), hourRange(split(allDay("Ferie", 12, 12))))
    }

    @Test
    fun hourRangeSpansEarliestStartToLatestEnd() {
        val days = split(timed("A", at(12, 7, 30), at(12, 9)), timed("B", at(14, 18), at(14, 20, 10)))

        assertEquals(HourRange(7, 21), hourRange(days))
    }

    @Test
    fun hourRangeIsWidenedToMinimumSpan() {
        val days = split(timed("A", at(12, 9, 30), at(12, 11, 15)))

        assertEquals(HourRange(8, 14), hourRange(days, minHours = 6))
    }

    @Test
    fun hourRangeWidensBackwardsAtEndOfDay() {
        val days = split(timed("A", at(12, 22), at(12, 23, 30)))

        assertEquals(HourRange(18, 24), hourRange(days, minHours = 6))
    }

    @Test
    fun separateSegmentsUseOneColumn() {
        val placed = placeSegments(listOf(segment(540, 600), segment(600, 660)), minDuration = 0)

        assertEquals(listOf(0, 0), placed.map { it.column })
        assertEquals(listOf(1, 1), placed.map { it.columns })
    }

    @Test
    fun overlappingSegmentsShareTheWidth() {
        val placed = placeSegments(listOf(segment(540, 660), segment(600, 630)), minDuration = 0)

        assertEquals(listOf(0, 1), placed.map { it.column })
        assertEquals(listOf(2, 2), placed.map { it.columns })
    }

    @Test
    fun freedColumnIsReusedInTheSameGroup() {
        // A overlaps B, B overlaps C, A ends before C: C goes back to A's column.
        val a = segment(540, 600)
        val b = segment(570, 690)
        val c = segment(630, 720)
        val placed = placeSegments(listOf(c, a, b), minDuration = 0).associate { it.segment to it }

        assertEquals(0, placed.getValue(a).column)
        assertEquals(1, placed.getValue(b).column)
        assertEquals(0, placed.getValue(c).column)
        assertEquals(listOf(2, 2, 2), listOf(a, b, c).map { placed.getValue(it).columns })
    }

    @Test
    fun shortSegmentsOverlapThroughMinimumDuration() {
        val placed = placeSegments(listOf(segment(600, 600), segment(605, 605)), minDuration = 15)

        assertEquals(listOf(0, 1), placed.map { it.column })
    }

    @Test
    fun queryWindowAddsADayOnEachSide() {
        val (begin, end) = queryWindow(today, 3, zone)

        assertEquals(at(11, 0), begin)
        assertEquals(at(16, 0), end)
    }

    @Test
    fun segmentsUseWallClockMinutesWhenClocksGoForward() {
        // On 29 March 2026 clocks in Rome jump from 02:00 to 03:00: the day lasts 23 hours.
        val day = LocalDate.of(2026, 3, 29)
        fun march(hour: Int) = LocalDateTime.of(2026, 3, 29, hour, 0).atZone(zone).toInstant().toEpochMilli()

        val days = splitIntoDays(listOf(timed("A", march(1), march(4)), timed("B", march(22), march(0) + 23 * 3_600_000L)), day, 1, zone)

        assertEquals(listOf(60 to 240, 22 * 60 to 24 * 60), days[0].timed.map { it.startMinute to it.endMinute })
    }

    @Test
    fun segmentsUseWallClockMinutesWhenClocksGoBack() {
        // On 25 October 2026 clocks in Rome go back from 03:00 to 02:00: the day lasts 25 hours.
        val dayStart = LocalDate.of(2026, 10, 25).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = LocalDate.of(2026, 10, 26).atStartOfDay(zone).toInstant().toEpochMilli()
        val evening = LocalDateTime.of(2026, 10, 25, 22, 0).atZone(zone).toInstant().toEpochMilli()

        val days = splitIntoDays(listOf(timed("All night", dayStart, dayStart + 5 * 3_600_000L), timed("Evening", evening, dayEnd)), LocalDate.of(2026, 10, 25), 1, zone)

        // Five real hours from midnight end at 04:00 on the clock.
        assertEquals(listOf(0 to 240, 22 * 60 to 24 * 60), days[0].timed.map { it.startMinute to it.endMinute })
        assertEquals(dayEnd - dayStart, 25 * 3_600_000L)
    }

    @Test
    fun segmentsAreSortedByStartThenLongestFirst() {
        val days = split(timed("Short", at(12, 9), at(12, 10)), timed("Late", at(12, 11), at(12, 12)), timed("Long", at(12, 9), at(12, 11)))

        assertEquals(listOf("Long", "Short", "Late"), days[0].timed.map { it.event.title })
    }
}
