package it.hourbyhour

import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Instances
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarSourceTest {
    private fun events(vararg rows: Map<String, Any?>) =
        CalendarSource.readEvents(fakeCursor(CalendarSource.EVENT_COLUMNS, rows.toList()), untitled = "(Untitled)")

    private fun calendars(vararg rows: Map<String, Any?>) =
        CalendarSource.readCalendars(fakeCursor(CalendarSource.CALENDAR_COLUMNS, rows.toList()))

    private fun eventRow(vararg values: Pair<String, Any?>) = mapOf(
        Instances.TITLE to "Dentist",
        Instances.BEGIN to 1_000L,
        Instances.END to 2_000L,
        Instances.ALL_DAY to 0,
        Instances.DISPLAY_COLOR to 0xFF039BE5.toInt(),
        Instances.CALENDAR_ID to 4L,
        Instances.EVENT_ID to 77L,
    ) + values

    @Test
    fun readsEveryColumnOfAnEvent() {
        val event = CalendarEvent("Dentist", 1_000, 2_000, allDay = true, 0xFF039BE5.toInt(), calendarId = 4, eventId = 77)

        assertEquals(listOf(event), events(eventRow(Instances.ALL_DAY to 1)))
    }

    @Test
    fun keepsTheOrderOfTheRows() {
        val titles = events(eventRow(Instances.TITLE to "First"), eventRow(Instances.TITLE to "Second")).map { it.title }

        assertEquals(listOf("First", "Second"), titles)
    }

    @Test
    fun noRowsNoEvents() {
        assertEquals(emptyList<CalendarEvent>(), events())
    }

    @Test
    fun eventsWithoutTitleGetAName() {
        val titles = events(eventRow(Instances.TITLE to null), eventRow(Instances.TITLE to ""), eventRow(Instances.TITLE to "  "))
            .map { it.title }

        assertEquals(listOf("(Untitled)", "(Untitled)", "(Untitled)"), titles)
    }

    @Test
    fun readsTheAllDayFlag() {
        assertEquals(listOf(false, true), events(eventRow(Instances.ALL_DAY to 0), eventRow(Instances.ALL_DAY to 1)).map { it.allDay })
    }

    @Test
    fun colorsWithoutAlphaBecomeOpaque() {
        val colors = events(eventRow(Instances.DISPLAY_COLOR to 0x039BE5), eventRow(Instances.DISPLAY_COLOR to 0x80039BE5.toInt()))
            .map { it.color }

        assertEquals(listOf(0xFF039BE5.toInt(), 0xFF039BE5.toInt()), colors)
    }

    @Test
    fun readsEveryColumnOfACalendar() {
        val row = mapOf(Calendars._ID to 4L, Calendars.CALENDAR_DISPLAY_NAME to "Work", Calendars.CALENDAR_COLOR to 0x33B679)

        assertEquals(listOf(CalendarInfo(4, "Work", 0xFF33B679.toInt())), calendars(row))
    }

    @Test
    fun calendarWithoutNameGetsAnEmptyOne() {
        val row = mapOf(Calendars._ID to 4L, Calendars.CALENDAR_DISPLAY_NAME to null, Calendars.CALENDAR_COLOR to 0x33B679)

        assertEquals("", calendars(row).single().name)
    }
}
