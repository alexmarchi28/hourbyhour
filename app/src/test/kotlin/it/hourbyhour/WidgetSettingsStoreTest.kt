package it.hourbyhour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSettingsStoreTest {
    private val preferences = FakeSharedPreferences()
    private val store = WidgetSettingsStore(preferences)

    private val custom = WidgetSettings(transparency = 40, dayCount = 6, startOnMonday = true, calendarIds = setOf(3L, 12L), showBirthdays = false)

    @Test
    fun widgetWithoutSavedSettingsGetsTheDefaults() {
        assertEquals(WidgetSettings(), store.load(7))
    }

    @Test
    fun savedSettingsLoadBack() {
        store.save(7, custom)

        assertEquals(custom, store.load(7))
    }

    @Test
    fun eachWidgetHasItsOwnSettings() {
        store.save(7, custom)

        assertEquals(WidgetSettings(), store.load(8))
    }

    @Test
    fun keyNamesStayTheSame() {
        // Widgets already on the home screen keep their settings only if the keys do not change.
        store.save(7, custom)

        assertEquals(setOf("transparency_7", "days_7", "from_monday_7", "calendars_7", "birthdays_7"), preferences.all.keys)
        assertEquals(setOf("3", "12"), preferences.getStringSet("calendars_7", null))
    }

    @Test
    fun choosingAllCalendarsAgainForgetsTheSavedOnes() {
        store.save(7, custom)
        store.save(7, custom.copy(calendarIds = null))

        assertNull(store.load(7).calendarIds)
        assertTrue("calendars_7" !in preferences.all)
    }

    @Test
    fun noCalendarCheckedIsNotAllCalendars() {
        store.save(7, custom.copy(calendarIds = emptySet()))

        assertEquals(emptySet<Long>(), store.load(7).calendarIds)
    }

    @Test
    fun deletingForgetsEverySettingOfTheRemovedWidgets() {
        store.save(7, custom)
        store.save(8, custom)
        store.save(9, custom)

        store.delete(intArrayOf(7, 9))

        assertEquals(setOf("transparency_8", "days_8", "from_monday_8", "calendars_8", "birthdays_8"), preferences.all.keys)
    }

    @Test
    fun widgetsSavedBeforeBirthdaysCouldBeChosenShowThem() {
        preferences.edit().putInt("days_7", 5).putStringSet("calendars_7", setOf("3")).apply()

        assertEquals(WidgetSettings(dayCount = 5, calendarIds = setOf(3L), showBirthdays = true), store.load(7))
    }

    @Test
    fun valuesOutOfRangeAreBroughtBackInRange() {
        preferences.edit().putInt("transparency_7", 150).putInt("days_7", 0).apply()
        assertEquals(WidgetSettings(transparency = 100, dayCount = 1), store.load(7))

        preferences.edit().putInt("transparency_7", -10).putInt("days_7", 9).apply()
        assertEquals(WidgetSettings(transparency = 0, dayCount = WidgetSettings.MAX_DAY_COUNT), store.load(7))
    }

    @Test
    fun unreadableCalendarIdsAreSkipped() {
        preferences.edit().putStringSet("calendars_7", setOf("3", "x", "")).apply()

        assertEquals(setOf(3L), store.load(7).calendarIds)
    }
}
