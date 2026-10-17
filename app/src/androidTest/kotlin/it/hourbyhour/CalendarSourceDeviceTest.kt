package it.hourbyhour

import android.content.ContentValues
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Reads events from the calendar provider of the phone, from calendars that the test creates on
 * the phone only (local calendars, never synced to Google) and removes afterwards.
 */
class CalendarSourceDeviceTest {
    private val zone = ZoneId.systemDefault()

    // A Monday far from the real events.
    private val day = LocalDate.of(2031, 3, 10)

    private fun at(hour: Int, minute: Int = 0, plusDays: Long = 0) =
        day.plusDays(plusDays).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private lateinit var calendars: TestCalendars
    private var calendarA = 0L
    private var calendarB = 0L
    private var calendarZ = 0L
    private var notSynced = 0L

    @Before
    fun createCalendars() {
        calendars = TestCalendars()
        // A is hidden in the provider: the app ignores that flag, the widget settings decide.
        calendarA = calendars.insertCalendar("HourByHour test A", COLOR_A, visible = false)
        calendarB = calendars.insertCalendar("HourByHour test B", COLOR_B)
        calendarZ = calendars.insertCalendar("HourByHour test Z", COLOR_B, primary = true)
        notSynced = calendars.insertCalendar("HourByHour test not synced", COLOR_B, synced = false)
    }

    @After
    fun deleteCalendars() {
        if (::calendars.isInitialized) calendars.close()
    }

    private fun insertEvent(calendar: Long, title: String?, begin: Long, end: Long, more: ContentValues.() -> Unit = {}) =
        calendars.insertEvent(calendar, title, begin, end, more)

    /** The events the app reads from Monday to Thursday, from the test calendars only. */
    private fun load() = CalendarSource.load(targetContext, at(0), at(0, plusDays = 4))
        .filter { it.calendarId in setOf(calendarA, calendarB, calendarZ, notSynced) }

    private fun opaque(color: Int) = color or 0xFF000000.toInt()

    @Test
    fun readsAnEventWithItsTimesCalendarAndColor() {
        val id = insertEvent(calendarB, "Dentist", at(10), at(11, 30))

        assertEquals(listOf(CalendarEvent("Dentist", at(10), at(11, 30), false, opaque(COLOR_B), calendarB, id)), load())
    }

    @Test
    fun eventColorWinsOverTheCalendarColor() {
        insertEvent(calendarB, "Dentist", at(10), at(11)) { put(Events.EVENT_COLOR, EVENT_COLOR) }

        assertEquals(opaque(EVENT_COLOR), load().single().color)
    }

    @Test
    fun allDayEventsAreReadAsAllDay() {
        val midnight = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        calendars.insertAllDay(calendarB, "Holiday", day)

        val holiday = load().single()
        assertTrue(holiday.allDay)
        assertEquals(midnight, holiday.begin)
    }

    @Test
    fun recurringEventsGiveOneEventPerOccurrence() {
        val id = insertEvent(calendarB, "Run", at(7), at(8)) {
            remove(Events.DTEND)
            put(Events.DURATION, "PT1H")
            put(Events.RRULE, "FREQ=DAILY;COUNT=3")
        }

        val runs = load()
        assertEquals(listOf(at(7), at(7, plusDays = 1), at(7, plusDays = 2)), runs.map { it.begin })
        assertTrue(runs.all { it.eventId == id })
    }

    @Test
    fun eventsWithoutTitleGetAName() {
        insertEvent(calendarB, null, at(10), at(11))

        assertEquals(targetContext.getString(R.string.untitled_event), load().single().title)
    }

    @Test
    fun cancelledEventsAndDeclinedInvitationsAreLeftOut() {
        insertEvent(calendarB, "Kept", at(9), at(10))
        insertEvent(calendarB, "Cancelled", at(10), at(11)) { put(Events.STATUS, Events.STATUS_CANCELED) }
        insertEvent(calendarB, "Declined", at(11), at(12)) { put(Events.SELF_ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_DECLINED) }

        assertEquals(listOf("Kept"), load().map { it.title })
    }

    @Test
    fun calendarsNotSyncedToThePhoneAreLeftOut() {
        insertEvent(notSynced, "Not synced", at(10), at(11))
        insertEvent(calendarA, "Hidden but synced", at(10), at(11))

        assertEquals(listOf("Hidden but synced"), load().map { it.title })
        assertTrue(CalendarSource.loadCalendars(targetContext).none { it.id == notSynced })
    }

    @Test
    fun eventsOutsideTheWindowAreLeftOut() {
        insertEvent(calendarB, "Before", at(10, plusDays = -1), at(11, plusDays = -1))
        insertEvent(calendarB, "Inside", at(10), at(11))
        insertEvent(calendarB, "After", at(10, plusDays = 4), at(11, plusDays = 4))

        assertEquals(listOf("Inside"), load().map { it.title })
    }

    @Test
    fun eventsComeInOrderOfStart() {
        insertEvent(calendarB, "Second", at(12), at(13))
        insertEvent(calendarA, "First", at(9), at(10))
        insertEvent(calendarB, "Third", at(9, plusDays = 1), at(10, plusDays = 1))

        assertEquals(listOf("First", "Second", "Third"), load().map { it.title })
    }

    @Test
    fun birthdaysOfContactsAreMarkedAndTakeTheirColor() {
        val birthday = calendars.insertAllDay(calendarB, "Gregorio", day)
        calendars.markAsBirthday(birthday)
        insertEvent(calendarB, "Dentist", at(10), at(11))

        val events = load().associateBy { it.title }
        assertEquals(true to GoogleColors.BIRTHDAYS, events.getValue("Gregorio").let { it.birthday to it.color })
        assertEquals(false to opaque(COLOR_B), events.getValue("Dentist").let { it.birthday to it.color })
        assertEquals(calendarB, events.getValue("Gregorio").calendarId)
    }

    @Test
    fun calendarsComePrimaryFirstThenByName() {
        val calendars = CalendarSource.loadCalendars(targetContext)
        val ours = calendars.filter { it.id in setOf(calendarA, calendarB, calendarZ) }

        assertEquals(listOf("HourByHour test Z", "HourByHour test A", "HourByHour test B"), ours.map { it.name })
        assertEquals(CalendarInfo(calendarA, "HourByHour test A", opaque(COLOR_A)), ours[1])
    }

    private companion object {
        const val COLOR_A = 0x33B679
        const val COLOR_B = 0x039BE5
        const val EVENT_COLOR = 0xD50000
    }
}
