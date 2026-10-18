package it.hourbyhour

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSettingsEditorTest {
    private val editor = WidgetSettingsEditor(WidgetSettings())

    @Test
    fun theSlidersShowTheSettings() {
        val shown = WidgetSettingsEditor(WidgetSettings(transparency = 40, dayCount = 6, hours = HourRange(7, 19)))

        assertEquals(8, shown.transparencyProgress)
        assertEquals(5, shown.dayCountProgress)
        assertEquals(7, shown.hoursFromProgress)
        assertEquals(19, shown.hoursToProgress)
    }

    @Test
    fun theSlidersCoverTheRanges() {
        assertEquals(20, WidgetSettingsEditor.TRANSPARENCY_MAX)
        assertEquals(6, WidgetSettingsEditor.DAY_COUNT_MAX)
        assertEquals(23, WidgetSettingsEditor.HOURS_FROM_MAX)
        assertEquals(1, WidgetSettingsEditor.HOURS_TO_MIN)
        assertEquals(24, WidgetSettingsEditor.HOURS_TO_MAX)
    }

    @Test
    fun movingTheSlidersChangesTheSettings() {
        assertEquals(100, editor.transparencyMoved(WidgetSettingsEditor.TRANSPARENCY_MAX).settings.transparency)
        assertEquals(0, editor.transparencyMoved(0).settings.transparency)
        assertEquals(1, editor.dayCountMoved(0).settings.dayCount)
        assertEquals(7, editor.dayCountMoved(WidgetSettingsEditor.DAY_COUNT_MAX).settings.dayCount)
    }

    @Test
    fun mondayStartCanBeChosenFromFiveDays() {
        assertFalse(editor.dayCountMoved(3).startOnMondayEnabled)
        assertTrue(editor.dayCountMoved(4).startOnMondayEnabled)
        // Kept below five days, where it has no effect.
        assertTrue(editor.startOnMondayChecked(true).dayCountMoved(0).settings.startOnMonday)
    }

    @Test
    fun hourBarsAreEnabledOnlyWithCustomHours() {
        assertFalse(editor.hoursEnabled)
        assertTrue(editor.customHoursChecked(true).hoursEnabled)
        assertEquals(WidgetSettings.DEFAULT_HOURS, editor.customHoursChecked(true).customHoursChecked(false).settings.hours)
    }

    @Test
    fun eachHourBarPushesTheOther() {
        val late = editor.hoursFromMoved(21)
        assertEquals(HourRange(21, 22), late.settings.hours)
        assertEquals(22, late.hoursToProgress)

        val early = late.hoursToMoved(6)
        assertEquals(HourRange(5, 6), early.settings.hours)
        assertEquals(5, early.hoursFromProgress)
    }

    @Test
    fun uncheckingACalendarShowsOnlyTheOthers() {
        val available = listOf(1L, 2L, 3L)

        val edited = editor.calendarChecked(2, false, available)

        assertEquals(setOf(1L, 3L), edited.settings.calendarIds)
        assertFalse(edited.isChecked(2))
        assertTrue(edited.isChecked(3))
    }

    @Test
    fun checkingEveryCalendarAgainMeansAllCalendars() {
        val available = listOf(1L, 2L, 3L)

        val edited = editor.calendarChecked(2, false, available).calendarChecked(2, true, available)

        assertNull(edited.settings.calendarIds)
    }

    @Test
    fun calendarsThatNoLongerExistAreForgottenOnTheFirstChange() {
        val stale = WidgetSettingsEditor(WidgetSettings(calendarIds = setOf(1L, 9L)))

        val edited = stale.calendarChecked(2, true, listOf(1L, 2L, 3L))

        assertEquals(setOf(1L, 2L), edited.settings.calendarIds)
    }

    @Test
    fun noCalendarCheckedStillShowsBirthdays() {
        val available = listOf(1L)

        val edited = editor.calendarChecked(1, false, available)

        assertEquals(emptySet<Long>(), edited.settings.calendarIds)
        assertTrue(edited.settings.showBirthdays)
        assertFalse(edited.birthdaysChecked(false).settings.showBirthdays)
    }

    @Test
    fun settingsBeingEditedSurviveSerialization() {
        // Kept in the saved state of the screen when Android recreates it (rotation, dark theme).
        val settings = WidgetSettings(
            transparency = 40,
            dayCount = 6,
            startOnMonday = true,
            calendarIds = setOf(3L, 12L),
            showBirthdays = false,
            customHours = true,
            hours = HourRange(7, 19),
        )
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { out -> out.writeObject(settings) } }.toByteArray()

        assertEquals(settings, ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() })
    }
}
