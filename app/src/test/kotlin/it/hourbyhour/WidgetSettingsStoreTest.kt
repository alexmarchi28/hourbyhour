package it.hourbyhour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSettingsStoreTest {
    private val preferences = FakeSharedPreferences()
    private val store = WidgetSettingsStore(preferences)

    private val custom = WidgetSettings(
        transparency = 40,
        dayCount = 6,
        startOnMonday = true,
        calendarIds = setOf(3L, 12L),
        showBirthdays = false,
        customHours = true,
        hours = HourRange(7, 19),
        systemColors = true,
    )

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

        assertEquals(
            setOf("transparency_7", "days_7", "from_monday_7", "calendars_7", "birthdays_7", "custom_hours_7", "hours_from_7", "hours_to_7", "system_colors_7"),
            preferences.all.keys,
        )
        assertEquals(7, preferences.getInt("hours_from_7", 0))
        assertEquals(19, preferences.getInt("hours_to_7", 0))
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

        assertEquals(
            setOf("transparency_8", "days_8", "from_monday_8", "calendars_8", "birthdays_8", "custom_hours_8", "hours_from_8", "hours_to_8", "system_colors_8"),
            preferences.all.keys,
        )
    }

    @Test
    fun deletingForgetsKeysTheStoreNoLongerWritesButNotOtherWidgetsWithSimilarIds() {
        // A setting saved by an older version, or added later without updating delete, goes too.
        preferences.edit().putInt("old_setting_7", 1).putInt("days_17", 4).putInt("days_70", 4).apply()

        store.delete(intArrayOf(7))

        assertEquals(setOf("days_17", "days_70"), preferences.all.keys)
    }

    @Test
    fun widgetsSavedBeforeBirthdaysCouldBeChosenShowThem() {
        preferences.edit().putInt("days_7", 5).putStringSet("calendars_7", setOf("3")).apply()

        assertEquals(WidgetSettings(dayCount = 5, calendarIds = setOf(3L), showBirthdays = true), store.load(7))
    }

    @Test
    fun widgetsSavedBeforeSystemColorsCouldBeChosenKeepTheirColors() {
        preferences.edit().putInt("days_7", 5).apply()

        assertEquals(WidgetSettings(dayCount = 5, systemColors = false), store.load(7))
    }

    @Test
    fun valuesOutOfRangeAreBroughtBackInRange() {
        preferences.edit().putInt("transparency_7", 150).putInt("days_7", 0).apply()
        assertEquals(WidgetSettings(transparency = 100, dayCount = 1), store.load(7))

        preferences.edit().putInt("transparency_7", -10).putInt("days_7", 9).apply()
        assertEquals(WidgetSettings(transparency = 0, dayCount = WidgetSettings.MAX_DAY_COUNT), store.load(7))
    }

    @Test
    fun widgetsSavedBeforeHoursCouldBeChosenFitThemToTheEvents() {
        preferences.edit().putInt("days_7", 5).apply()

        assertEquals(WidgetSettings(dayCount = 5, customHours = false, hours = HourRange(8, 20)), store.load(7))
    }

    @Test
    fun hoursOutOfRangeAreBroughtBackInRange() {
        preferences.edit().putInt("hours_from_7", -3).putInt("hours_to_7", 30).apply()
        assertEquals(HourRange(0, 24), store.load(7).hours)

        // The end is kept after the start, as when choosing them.
        preferences.edit().putInt("hours_from_7", 20).putInt("hours_to_7", 8).apply()
        assertEquals(HourRange(20, 21), store.load(7).hours)

        preferences.edit().putInt("hours_from_7", 24).putInt("hours_to_7", 24).apply()
        assertEquals(HourRange(23, 24), store.load(7).hours)
    }

    @Test
    fun unreadableCalendarIdsAreSkipped() {
        preferences.edit().putStringSet("calendars_7", setOf("3", "x", "")).apply()

        assertEquals(setOf(3L), store.load(7).calendarIds)
    }
}
