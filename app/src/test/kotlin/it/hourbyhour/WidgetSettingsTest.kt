package it.hourbyhour

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSettingsTest {
    @Test
    fun defaultKeepsTheOriginalBackgroundAlpha() {
        // Before transparency could be chosen, the background color had alpha F2.
        assertEquals(0xF2, WidgetSettings().backgroundAlpha)
    }

    @Test
    fun transparencyMapsToAlpha() {
        assertEquals(255, WidgetSettings(0).backgroundAlpha)
        assertEquals(128, WidgetSettings(50).backgroundAlpha)
        assertEquals(0, WidgetSettings(100).backgroundAlpha)
    }

    @Test(expected = IllegalArgumentException::class)
    fun transparencyAboveOneHundredIsRejected() {
        WidgetSettings(101)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeTransparencyIsRejected() {
        WidgetSettings(-5)
    }

    @Test
    fun withAlphaReplacesOnlyTheAlphaChannel() {
        assertEquals(0x80202124.toInt(), withAlpha(0xFF202124.toInt(), 0x80))
        assertEquals(0x00FFFFFF, withAlpha(0xFFFFFFFF.toInt(), 0))
        assertEquals(0xFF123456.toInt(), withAlpha(0x00123456, 300))
    }

    // October 2026: Monday 12 to Sunday 18, then Monday 19.
    private fun october(day: Int) = LocalDate.of(2026, 10, day)

    private fun fromMonday(days: Int) = WidgetSettings(dayCount = days, startOnMonday = true)

    @Test
    fun defaultShowsThreeDaysFromToday() {
        assertEquals(3, WidgetSettings().dayCount)
        assertEquals(october(14), WidgetSettings().firstDay(october(14)))
    }

    @Test
    fun mondayStartNeedsAtLeastFiveDays() {
        assertFalse(fromMonday(4).startsOnMonday)
        assertEquals(october(16), fromMonday(4).firstDay(october(16)))
        assertTrue(fromMonday(5).startsOnMonday)
    }

    @Test
    fun workWeekStartsOnMondayUntilFriday() {
        for (day in 12..16) assertEquals("October $day", october(12), fromMonday(5).firstDay(october(day)))
    }

    @Test
    fun workWeekMovesToTheNextWeekAtTheWeekend() {
        assertEquals(october(19), fromMonday(5).firstDay(october(17)))
        assertEquals(october(19), fromMonday(5).firstDay(october(18)))
    }

    @Test
    fun sixDaysMoveToTheNextWeekOnlyOnSunday() {
        assertEquals(october(12), fromMonday(6).firstDay(october(17)))
        assertEquals(october(19), fromMonday(6).firstDay(october(18)))
    }

    @Test
    fun wholeWeekAlwaysShowsTheCurrentWeek() {
        for (day in 12..18) assertEquals("October $day", october(12), fromMonday(7).firstDay(october(day)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDaysAreRejected() {
        WidgetSettings(dayCount = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun moreThanSevenDaysAreRejected() {
        WidgetSettings(dayCount = 8)
    }

    @Test
    fun byDefaultEveryCalendarIsShownIncludingNewOnes() {
        assertTrue(WidgetSettings().shows(2))
        assertTrue(WidgetSettings().shows(12345))
    }

    @Test
    fun aSelectionShowsOnlyItsCalendars() {
        val settings = WidgetSettings(calendarIds = setOf(2, 6))

        assertTrue(settings.shows(2))
        assertTrue(settings.shows(6))
        assertFalse(settings.shows(5))
    }

    @Test
    fun checkingEveryCalendarMeansAllCalendars() {
        assertEquals(null, WidgetSettings.calendarSelection(setOf(2, 5, 6), listOf(2, 5, 6)))
        // Calendars that no longer exist do not count.
        assertEquals(null, WidgetSettings.calendarSelection(setOf(2, 5, 6, 99), listOf(2, 5, 6)))
    }

    @Test
    fun checkingSomeCalendarsKeepsThem() {
        assertEquals(setOf(2L, 6L), WidgetSettings.calendarSelection(setOf(2, 6), listOf(2, 5, 6)))
    }

    @Test
    fun checkingNoCalendarShowsNothing() {
        val selection = WidgetSettings.calendarSelection(emptySet(), listOf(2, 5, 6))

        assertEquals(emptySet<Long>(), selection)
        assertFalse(WidgetSettings(calendarIds = selection).shows(2))
    }
}
