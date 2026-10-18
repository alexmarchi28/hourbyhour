package it.hourbyhour

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class WidgetSettingsActivityDeviceTest {
    private val store = WidgetSettingsStore(targetContext)
    private lateinit var widget: TestWidget

    @Before
    fun placeWidget() {
        widget = TestWidget()
    }

    @After
    fun removeWidget() {
        if (!::widget.isInitialized) return
        widget.close()
        // The app forgets them too, but maybe only after the test process has ended.
        store.delete(intArrayOf(widget.id))
        flushWidgetSettings()
    }

    private fun open(widgetId: Int = widget.id): ActivityScenario<WidgetSettingsActivity> = ActivityScenario.launchActivityForResult(
        Intent(targetContext, WidgetSettingsActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
    )

    /** Runs [block] on the activity, on the main thread. */
    private fun <T> ActivityScenario<WidgetSettingsActivity>.read(block: (WidgetSettingsActivity) -> T): T {
        var result: T? = null
        onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun <V : View> ActivityScenario<WidgetSettingsActivity>.view(id: Int): V = read { it.findViewById<V>(id) }

    private fun ActivityScenario<WidgetSettingsActivity>.text(id: Int) = read { it.findViewById<TextView>(id).text.toString() }

    private fun ActivityScenario<WidgetSettingsActivity>.slide(id: Int, progress: Int) = onActivity {
        it.findViewById<SeekBar>(id).progress = progress
    }

    /**
     * Runs [close] on the activity and waits for it to be destroyed. ActivityScenario.close() would otherwise
     * find it still on its way out and finish it again, waiting up to 45 s for helper activities.
     */
    private fun ActivityScenario<WidgetSettingsActivity>.leave(close: (WidgetSettingsActivity) -> Unit) {
        onActivity(close)
        val deadline = SystemClock.uptimeMillis() + 10_000
        // state throws while the activity is between two lifecycle steps.
        while (runCatching { state }.getOrNull() != Lifecycle.State.DESTROYED) {
            if (SystemClock.uptimeMillis() > deadline) throw AssertionError("Timed out waiting for the settings to close")
            Thread.sleep(50)
        }
    }

    private fun ActivityScenario<WidgetSettingsActivity>.calendarBoxes(): List<CheckBox> {
        val container = view<View>(R.id.calendars)
        waitFor("the calendars") { container.childrenOf(R.id.calendars).isNotEmpty() }
        return onMain { container.childrenOf(R.id.calendars).filterIsInstance<CheckBox>() }
    }

    @Test
    fun widgetsOfOtherAppsAreRefused() {
        open(AppWidgetManager.INVALID_APPWIDGET_ID).use { scenario ->
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }
    }

    @Test
    fun showsTheSavedSettings() {
        store.save(widget.id, WidgetSettings(transparency = 40, dayCount = 6, startOnMonday = true))

        open().use { scenario ->
            assertEquals("40%", scenario.text(R.id.transparency_value))
            assertEquals("6", scenario.text(R.id.day_count_value))
            assertEquals(8, scenario.read { it.findViewById<SeekBar>(R.id.transparency).progress })
            assertEquals(5, scenario.read { it.findViewById<SeekBar>(R.id.day_count).progress })
            assertTrue(scenario.read { it.findViewById<Switch>(R.id.start_on_monday).isChecked })
        }
    }

    @Test
    fun mondayStartCanBeChosenFromFiveDays() {
        open().use { scenario ->
            assertFalse(scenario.read { it.findViewById<Switch>(R.id.start_on_monday).isEnabled })

            scenario.slide(R.id.day_count, 4)

            assertEquals("5", scenario.text(R.id.day_count_value))
            assertTrue(scenario.read { it.findViewById<Switch>(R.id.start_on_monday).isEnabled })
        }
    }

    @Test
    fun mondayStartIsSaved() {
        store.save(widget.id, WidgetSettings(dayCount = 5))

        open().use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.start_on_monday).performClick() }
            scenario.leave { it.findViewById<View>(R.id.done).performClick() }

            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        }
        assertEquals(WidgetSettings(dayCount = 5, startOnMonday = true), store.load(widget.id))
    }

    @Test
    fun hoursFitTheEventsUntilCustomHoursAreTurnedOn() {
        open().use { scenario ->
            assertFalse(scenario.read { it.findViewById<Switch>(R.id.custom_hours).isChecked })
            assertFalse(scenario.read { it.findViewById<SeekBar>(R.id.hours_from).isEnabled })
            assertFalse(scenario.read { it.findViewById<SeekBar>(R.id.hours_to).isEnabled })
            assertEquals("8:00", scenario.text(R.id.hours_from_value))
            assertEquals("20:00", scenario.text(R.id.hours_to_value))

            scenario.onActivity { it.findViewById<View>(R.id.custom_hours).performClick() }

            assertTrue(scenario.read { it.findViewById<SeekBar>(R.id.hours_from).isEnabled })
            assertTrue(scenario.read { it.findViewById<SeekBar>(R.id.hours_to).isEnabled })
        }
    }

    @Test
    fun showsTheSavedHours() {
        store.save(widget.id, WidgetSettings(customHours = true, hours = HourRange(7, 19)))

        open().use { scenario ->
            assertTrue(scenario.read { it.findViewById<Switch>(R.id.custom_hours).isChecked })
            assertEquals("7:00", scenario.text(R.id.hours_from_value))
            assertEquals("19:00", scenario.text(R.id.hours_to_value))
            assertEquals(7, scenario.read { it.findViewById<SeekBar>(R.id.hours_from).progress })
            assertEquals(19, scenario.read { it.findViewById<SeekBar>(R.id.hours_to).progress })
        }
    }

    @Test
    fun theEndStaysAfterTheStart() {
        store.save(widget.id, WidgetSettings(customHours = true))

        open().use { scenario ->
            scenario.slide(R.id.hours_from, 21)

            assertEquals("21:00", scenario.text(R.id.hours_from_value))
            assertEquals("22:00", scenario.text(R.id.hours_to_value))
            assertEquals(22, scenario.read { it.findViewById<SeekBar>(R.id.hours_to).progress })

            scenario.slide(R.id.hours_to, 6)

            assertEquals("5:00", scenario.text(R.id.hours_from_value))
            assertEquals(5, scenario.read { it.findViewById<SeekBar>(R.id.hours_from).progress })
        }
    }

    @Test
    fun chosenHoursAreSaved() {
        open().use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.custom_hours).performClick() }
            scenario.slide(R.id.hours_from, 7)
            scenario.slide(R.id.hours_to, 19)
            scenario.leave { it.findViewById<View>(R.id.done).performClick() }

            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        }
        assertEquals(WidgetSettings(customHours = true, hours = HourRange(7, 19)), store.load(widget.id))
    }

    @Test
    fun calendarsAreAllCheckedAtFirst() {
        open().use { scenario ->
            val boxes = scenario.calendarBoxes()
            assumeTrue("No calendars on the phone", boxes.isNotEmpty())

            val names = CalendarSource.loadCalendars(targetContext).map { it.name } + targetContext.getString(R.string.birthdays)
            assertEquals(names, onMain { boxes.map { it.text.toString() } })
            assertTrue(onMain { boxes.all { it.isChecked } })
        }
    }

    private fun ActivityScenario<WidgetSettingsActivity>.birthdaysBox() = calendarBoxes().last()

    @Test
    fun birthdaysCanBeHiddenApartFromTheCalendars() {
        open().use { scenario ->
            val birthdays = scenario.birthdaysBox()
            assumeTrue("No calendars on the phone", birthdays.text == targetContext.getString(R.string.birthdays))
            onMain { birthdays.performClick() }
            scenario.leave { it.findViewById<View>(R.id.done).performClick() }

            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        }
        assertEquals(WidgetSettings(showBirthdays = false), store.load(widget.id))
    }

    @Test
    fun hiddenBirthdaysShowUnchecked() {
        store.save(widget.id, WidgetSettings(showBirthdays = false))

        open().use { scenario ->
            val birthdays = scenario.birthdaysBox()
            assumeTrue("No calendars on the phone", birthdays.text == targetContext.getString(R.string.birthdays))

            assertFalse(onMain { birthdays.isChecked })
        }
    }

    @Test
    fun doneSavesTheSettingsAndRedrawsTheWidget() {
        val view = widget.createView()
        val calendars = CalendarSource.loadCalendars(targetContext)
        assumeTrue("No calendars on the phone", calendars.isNotEmpty())

        open().use { scenario ->
            scenario.slide(R.id.transparency, 10)
            scenario.slide(R.id.day_count, 6)
            val boxes = scenario.calendarBoxes()
            onMain { boxes.first().performClick() }
            scenario.leave { it.findViewById<View>(R.id.done).performClick() }

            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals(widget.id, scenario.result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
        }
        val expected = WidgetSettings(
            transparency = 50,
            dayCount = 7,
            calendarIds = WidgetSettings.calendarSelection(calendars.drop(1).map { it.id }.toSet(), calendars.map { it.id }),
        )
        assertEquals(expected, store.load(widget.id))
        waitFor("the widget redrawn with seven days") { view.childrenOf(R.id.headers).size == 7 }
    }

    @Test
    fun changesNotSavedYetSurviveTheScreenBeingRecreated() {
        // Birthdays come last, after the calendars, whenever the phone has some.
        var hasCalendars = false
        // As when the phone rotates or switches to the dark theme with the screen open.
        open().use { scenario ->
            scenario.slide(R.id.transparency, 10)
            scenario.slide(R.id.day_count, 5)
            scenario.onActivity { it.findViewById<View>(R.id.custom_hours).performClick() }
            scenario.slide(R.id.hours_from, 7)
            val boxes = scenario.calendarBoxes()
            hasCalendars = boxes.isNotEmpty()
            if (hasCalendars) onMain { boxes.last().performClick() }

            scenario.recreate()

            assertEquals("50%", scenario.text(R.id.transparency_value))
            assertEquals("6", scenario.text(R.id.day_count_value))
            assertEquals("7:00", scenario.text(R.id.hours_from_value))
            assertTrue(scenario.read { it.findViewById<Switch>(R.id.custom_hours).isChecked })
            assertTrue(scenario.read { it.findViewById<SeekBar>(R.id.hours_from).isEnabled })
            assertEquals(7, scenario.read { it.findViewById<SeekBar>(R.id.hours_from).progress })
            if (hasCalendars) {
                val birthdays = scenario.calendarBoxes().last()
                assertFalse(onMain { birthdays.isChecked })
            }
            scenario.leave { it.findViewById<View>(R.id.done).performClick() }
        }
        val saved = store.load(widget.id)
        assertEquals(50, saved.transparency)
        assertEquals(6, saved.dayCount)
        assertEquals(HourRange(7, 20), saved.hours)
        assertTrue(saved.customHours)
        assertEquals(!hasCalendars, saved.showBirthdays)
    }

    @Test
    fun leavingWithoutDoneKeepsTheSettings() {
        open().use { scenario ->
            scenario.slide(R.id.transparency, 10)
            scenario.leave { it.finish() }

            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }
        assertEquals(WidgetSettings(), store.load(widget.id))
    }
}
