package it.hourbyhour

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaTest {
    private val zone = ZoneId.of("Europe/Rome")

    private fun midnight(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun clockAt(time: LocalDateTime): Clock = Clock.fixed(time.atZone(zone).toInstant(), zone)

    @Test
    fun windowGoesFromTheDayBeforeMondayToTheDayAfterTheNextSunday() {
        // October 2026: Monday 12 to Sunday 18, then Monday 19 to Sunday 25.
        for (day in 12..18) {
            val now = LocalDateTime.of(2026, 10, day, 10, 0).atZone(zone)

            val (begin, end) = Agenda.window(now)

            assertEquals("$now", midnight(LocalDate.of(2026, 10, 11)), begin)
            assertEquals("$now", midnight(LocalDate.of(2026, 10, 27)), end)
        }
    }

    @Test
    fun windowEndsAtLocalMidnightAcrossAChangeOfClocks() {
        // On 25 October 2026 clocks in Rome go back from 03:00 to 02:00.
        val now = LocalDateTime.of(2026, 10, 20, 0, 30).atZone(zone)

        val (begin, end) = Agenda.window(now)

        assertEquals("2026-10-18T00:00+02:00[Europe/Rome]", ZonedDateTime.ofInstant(Instant.ofEpochMilli(begin), zone).toString())
        assertEquals("2026-11-03T00:00+01:00[Europe/Rome]", ZonedDateTime.ofInstant(Instant.ofEpochMilli(end), zone).toString())
    }

    @Test
    fun readAsksForTheWindowAtTheTimeOfTheClock() {
        val clock = clockAt(LocalDateTime.of(2026, 10, 18, 23, 59))
        var asked: Pair<Long, Long>? = null
        val event = CalendarEvent("Cena", 1L, 2L, false, 0)

        val agenda = Agenda.read(clock) { begin, end ->
            asked = Pair(begin, end)
            listOf(event)
        }

        assertEquals(LocalDateTime.of(2026, 10, 18, 23, 59).atZone(zone), agenda.now)
        assertEquals(listOf(event), agenda.events)
        assertEquals(Agenda.window(agenda.now), asked)
        // Sunday still belongs to the week from Monday 12.
        assertEquals(midnight(LocalDate.of(2026, 10, 11)), asked!!.first)
    }
}
