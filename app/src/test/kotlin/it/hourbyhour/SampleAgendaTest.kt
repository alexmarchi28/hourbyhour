package it.hourbyhour

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleAgendaTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val monday = LocalDate.of(2026, 10, 12)
    private val title: (SampleTitle) -> String = { it.name }

    private fun events(firstDay: LocalDate = monday, zone: ZoneId = this.zone) = SampleAgenda.events(firstDay, zone, title)

    private fun CalendarEvent.date(): LocalDate = java.time.Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()

    @Test
    fun theSameDayGivesTheSameEvents() {
        assertEquals(events(), events())
    }

    @Test
    fun theEventsFillAWeekFromTheFirstDay() {
        val dates = events().map { it.date() }.toSet()

        assertEquals((0L until 7).map { monday.plusDays(it) }.toSet(), dates)
    }

    @Test
    fun anotherFirstDayMovesEveryEventByTheSameDays() {
        val later = events(monday.plusDays(3))

        assertEquals(events().map { it.date().plusDays(3) }, later.map { it.date() })
        assertEquals(events().map { it.title }, later.map { it.title })
    }

    @Test
    fun timedEventsKeepTheirLocalTimes() {
        val standUp = events().first { it.title == "STAND_UP" }

        assertEquals(LocalTime.of(9, 0), java.time.Instant.ofEpochMilli(standUp.begin).atZone(zone).toLocalTime())
        assertEquals(LocalTime.of(9, 15), java.time.Instant.ofEpochMilli(standUp.end).atZone(zone).toLocalTime())
    }

    @Test
    fun allDayEventsAreStoredAtUtcMidnightsLikeTheProvider() {
        val allDay = events().filter { it.allDay }

        assertTrue(allDay.isNotEmpty())
        for (event in allDay) {
            assertEquals(0L, event.begin % MILLIS_PER_DAY)
            assertEquals(0L, event.end % MILLIS_PER_DAY)
        }
    }

    @Test
    fun everyTitleComesFromTheLookup() {
        assertEquals(SampleTitle.entries.map { it.name }.toSet(), events().map { it.title }.toSet())
    }

    @Test
    fun everyTitleHasItsOwnText() {
        assertEquals(SampleTitle.entries.size, SampleTitle.entries.map { it.stringId }.toSet().size)
    }

    @Test
    fun theDefaultWidgetShowsEventsOnEveryDay() {
        val today = LocalDate.of(2026, 3, 29) // The day summer time starts in Rome.

        val days = SampleAgenda.agenda(today, zone, title).snapshot(WidgetSettings()).days

        assertEquals(listOf(today, today.plusDays(1), today.plusDays(2)), days.map { it.date })
        assertTrue(days.all { it.timed.isNotEmpty() })
    }

    @Test
    fun theAgendaIsSeenOnItsFirstDayInTheMorning() {
        val now = SampleAgenda.agenda(monday, zone, title).now

        assertEquals(monday.atTime(SampleAgenda.NOW).atZone(zone), now)
    }

    private companion object {
        const val MILLIS_PER_DAY = 24 * 3_600_000L
    }
}
