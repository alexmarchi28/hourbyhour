package it.tregiorni

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetUpdaterTest {
    private val zone = ZoneId.of("Europe/Rome")

    private fun millis(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun local(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().toString()

    @Test
    fun refreshesAtTheNextQuarterHour() {
        assertEquals("2026-10-11T17:45:05", local(nextRefreshTime(millis("2026-10-11T17:44:12"))))
    }

    @Test
    fun refreshOnAQuarterHourWaitsForTheNextOne() {
        assertEquals("2026-10-11T18:15:05", local(nextRefreshTime(millis("2026-10-11T18:00:00"))))
    }

    @Test
    fun refreshesRightAfterMidnightToRollTheDaysOver() {
        assertEquals("2026-10-12T00:00:05", local(nextRefreshTime(millis("2026-10-11T23:52:00"))))
    }

    @Test
    fun midnightAlignsInTimeZonesWithHalfHourOffsets() {
        val india = ZoneId.of("Asia/Kolkata")
        val now = LocalDateTime.parse("2026-10-11T23:50:00").atZone(india).toInstant().toEpochMilli()

        assertEquals("2026-10-12T00:00:05", Instant.ofEpochMilli(nextRefreshTime(now)).atZone(india).toLocalDateTime().toString())
    }
}
