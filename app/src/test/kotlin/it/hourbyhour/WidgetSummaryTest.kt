package it.hourbyhour

import it.hourbyhour.SettingSummary.Calendars
import it.hourbyhour.SettingSummary.Days
import it.hourbyhour.SettingSummary.Hours
import it.hourbyhour.SettingSummary.NoBirthdays
import it.hourbyhour.SettingSummary.Transparency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSummaryTest {
    private val calendars = setOf(1L, 2L, 3L)

    @Test
    fun theDefaultsShowOnlyTheDays() {
        assertEquals(listOf(Days(3, fromMonday = false)), WidgetSettings().summary(calendars))
    }

    @Test
    fun aWeekFromMondaySaysSo() {
        assertEquals(listOf(Days(7, fromMonday = true)), WidgetSettings(dayCount = 7, startOnMonday = true).summary(calendars))
    }

    @Test
    fun mondayWithTooFewDaysIsNotMentioned() {
        // Saved, but with four days the widget starts today.
        assertEquals(listOf(Days(4, fromMonday = false)), WidgetSettings(dayCount = 4, startOnMonday = true).summary(calendars))
    }

    @Test
    fun chosenCalendarsAreCounted() {
        assertEquals(listOf(Days(3, false), Calendars(2)), WidgetSettings(calendarIds = setOf(1L, 3L)).summary(calendars))
    }

    @Test
    fun calendarsThatNoLongerExistAreNotCounted() {
        assertEquals(listOf(Days(3, false), Calendars(1)), WidgetSettings(calendarIds = setOf(1L, 99L)).summary(calendars))
    }

    @Test
    fun noCalendarsChosenIsZero() {
        assertEquals(listOf(Days(3, false), Calendars(0)), WidgetSettings(calendarIds = emptySet()).summary(calendars))
    }

    @Test
    fun withoutTheCalendarListEveryChosenIdCounts() {
        // Before calendar access is granted, the calendars of the phone are unknown.
        assertEquals(listOf(Days(3, false), Calendars(2)), WidgetSettings(calendarIds = setOf(1L, 99L)).summary(null))
    }

    @Test
    fun hiddenBirthdaysAreMentioned() {
        assertEquals(listOf(Days(3, false), NoBirthdays), WidgetSettings(showBirthdays = false).summary(calendars))
    }

    @Test
    fun aTransparencyOtherThanTheDefaultIsMentioned() {
        assertEquals(listOf(Days(3, false), Transparency(40)), WidgetSettings(transparency = 40).summary(calendars))
    }

    @Test
    fun customHoursAreMentioned() {
        assertEquals(listOf(Days(3, false), Hours(7, 19)), WidgetSettings(customHours = true, hours = HourRange(7, 19)).summary(calendars))
        // The default hours too: they are chosen, no longer fitted to the events.
        assertEquals(listOf(Days(3, false), Hours(8, 20)), WidgetSettings(customHours = true).summary(calendars))
    }

    @Test
    fun hoursKeptWhileCustomHoursAreOffAreNotMentioned() {
        assertEquals(listOf(Days(3, false)), WidgetSettings(customHours = false, hours = HourRange(7, 19)).summary(calendars))
    }

    @Test
    fun everythingComesInAFixedOrder() {
        val settings = WidgetSettings(
            transparency = 100,
            dayCount = 5,
            startOnMonday = true,
            calendarIds = setOf(2L),
            showBirthdays = false,
            customHours = true,
            hours = HourRange(6, 22),
        )

        assertEquals(listOf(Days(5, true), Hours(6, 22), Calendars(1), NoBirthdays, Transparency(100)), settings.summary(calendars))
    }

    @Test
    fun widgetsAreNumberedInTheOrderTheyWereAdded() {
        // Ids grow as widgets are added.
        val saved = mapOf(42 to WidgetSettings(dayCount = 7), 17 to WidgetSettings())

        val entries = widgetEntries(intArrayOf(42, 17), { saved.getValue(it) }, calendars)

        assertEquals(listOf(17, 42), entries.map { it.widgetId })
        assertEquals(listOf(1, 2), entries.map { it.number })
        assertEquals(listOf(Days(7, false)), entries[1].summary)
    }

    @Test
    fun noWidgetsNoEntries() {
        assertTrue(widgetEntries(intArrayOf(), { WidgetSettings() }, calendars).isEmpty())
    }
}
