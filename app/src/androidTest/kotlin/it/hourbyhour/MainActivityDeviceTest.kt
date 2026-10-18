package it.hourbyhour

import android.appwidget.AppWidgetManager
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class MainActivityDeviceTest {
    private val store = WidgetSettingsStore(targetContext)
    private var widget: TestWidget? = null

    @After
    fun removeWidget() {
        val placed = widget ?: return
        placed.close()
        store.delete(intArrayOf(placed.id))
        flushWidgetSettings()
    }

    /** A widget of the app placed by the test: for the app it is one more widget on the home screen. */
    private fun placeWidget(settings: WidgetSettings) = TestWidget().also {
        widget = it
        store.save(it.id, settings)
    }

    private fun MainActivity.entry(widgetId: Int): View? = findViewById<View>(R.id.widgets).findViewWithTag(widgetId)

    /** Opens the app and waits until [placed] is in its list of widgets. */
    private fun withWidgetListed(placed: TestWidget, block: (ActivityScenario<MainActivity>, MainActivity) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            waitFor("the widget in the list") { activity.entry(placed.id) != null }
            block(scenario, activity)
        }
    }

    @Test
    fun eachWidgetIsListedWithItsSettings() {
        val placed = placeWidget(WidgetSettings(dayCount = 7, startOnMonday = true, showBirthdays = false))

        withWidgetListed(placed) { _, activity ->
            val texts = onMain { activity.entry(placed.id)!!.descendants().filterIsInstance<TextView>().joinToString(" ") { it.text } }
            assertTrue(texts, targetContext.resources.getQuantityString(R.plurals.summary_days_from_monday, 7, 7) in texts)
            assertTrue(texts, targetContext.getString(R.string.summary_no_birthdays) in texts)
            assertEquals(View.VISIBLE, onMain { activity.findViewById<View>(R.id.widgets_section).visibility })
        }
    }

    @Test
    fun tappingAWidgetOpensItsSettings() {
        val placed = placeWidget(WidgetSettings())
        val monitor = instrumentation.addMonitor(WidgetSettingsActivity::class.java.name, null, false)
        try {
            withWidgetListed(placed) { _, activity ->
                onMain { activity.entry(placed.id)!!.performClick() }

                val settings = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
                assertNotNull("The settings did not open", settings)
                assertEquals(placed.id, settings.intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
                settings.finish()
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun aRemovedWidgetLeavesTheListWhenTheAppIsBack() {
        val placed = placeWidget(WidgetSettings())

        withWidgetListed(placed) { scenario, activity ->
            scenario.moveToState(Lifecycle.State.STARTED)
            placed.close()
            scenario.moveToState(Lifecycle.State.RESUMED)

            waitFor("the widget gone from the list") { activity.entry(placed.id) == null }
        }
    }

    @Test
    fun withCalendarAccessShowsAPreviewThatCannotBeTapped() {
        assumeTrue("Calendar access not granted", CalendarSource.hasPermission(targetContext))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }

            onMain {
                assertEquals(activity.getString(R.string.status_ready), activity.findViewById<TextView>(R.id.status).text.toString())
                assertEquals(View.GONE, activity.findViewById<View>(R.id.grant).visibility)
            }
            val preview = onMain { activity.findViewById<View>(R.id.preview) }
            waitFor("the preview") { preview.findViewById<View>(R.id.headers) != null }
            // A tap on the preview would leave the app.
            assertTrue(onMain { preview.descendants().none { it.hasOnClickListeners() } })
        }
    }
}
