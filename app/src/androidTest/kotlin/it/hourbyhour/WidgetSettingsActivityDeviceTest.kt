package it.hourbyhour

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
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
        if (::widget.isInitialized) widget.close()
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
            scenario.onActivity { it.findViewById<View>(R.id.done).performClick() }

            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        }
        assertEquals(WidgetSettings(dayCount = 5, startOnMonday = true), store.load(widget.id))
    }

    @Test
    fun calendarsAreAllCheckedAtFirst() {
        open().use { scenario ->
            val boxes = scenario.calendarBoxes()
            assumeTrue("No calendars on the phone", boxes.isNotEmpty())

            assertEquals(CalendarSource.loadCalendars(targetContext).map { it.name }, onMain { boxes.map { it.text.toString() } })
            assertTrue(onMain { boxes.all { it.isChecked } })
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
            scenario.onActivity { it.findViewById<View>(R.id.done).performClick() }

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
    fun leavingWithoutDoneKeepsTheSettings() {
        open().use { scenario ->
            scenario.slide(R.id.transparency, 10)
            scenario.onActivity { it.finish() }

            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }
        assertEquals(WidgetSettings(), store.load(widget.id))
    }
}
