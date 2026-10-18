package it.hourbyhour

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The widget as the launcher gets it: placed, updated, resized and removed. */
class WidgetDeviceTest {
    private val manager = AppWidgetManager.getInstance(targetContext)
    private val store = WidgetSettingsStore(targetContext)
    private lateinit var widget: TestWidget
    private lateinit var view: AppWidgetHostView

    @Before
    fun placeWidget() {
        widget = TestWidget()
        view = widget.createView()
        // Placing the widget draws it with the default settings.
        waitFor("the first drawing") { headers().size == WidgetSettings.DEFAULT_DAY_COUNT }
    }

    @After
    fun removeWidget() {
        if (!::widget.isInitialized) return
        widget.close()
        // The app forgets them too, but maybe only after the test process has ended.
        store.delete(intArrayOf(widget.id))
        flushWidgetSettings()
    }

    private fun headers() = view.childrenOf(R.id.headers).map { it as TextView }

    @Test
    fun startsFromTodayWhichStandsOut() {
        val first = onMain { headers().first() }

        assertTrue(first.text.endsWith(LocalDate.now().dayOfMonth.toString()))
        assertTrue(first.typeface.isBold)
    }

    @Test
    fun drawsWithTheSettingsOfTheWidget() {
        store.save(widget.id, WidgetSettings(dayCount = 5))
        WidgetUpdater.update(targetContext, manager, intArrayOf(widget.id))

        waitFor("five days") { headers().size == 5 }
    }

    @Test
    fun drawsTheChosenHours() {
        // The whole day: whatever events the phone has, they fit in it, so the grid shows just that.
        store.save(widget.id, WidgetSettings(customHours = true, hours = HourRange(0, 24)))
        WidgetUpdater.update(targetContext, manager, intArrayOf(widget.id))

        waitFor("an hour row for each hour of the day") { view.childrenOf(R.id.hours).size == 24 }
        assertEquals("0", onMain { view.childrenOf(R.id.hours).first().findViewById<TextView>(R.id.hour_label).text.toString() })
    }

    @Test
    fun refreshRedrawsEveryWidget() {
        store.save(widget.id, WidgetSettings(dayCount = 2))
        targetContext.sendBroadcast(Intent(HourByHourWidget.ACTION_REFRESH).setClass(targetContext, HourByHourWidget::class.java))

        waitFor("two days") { headers().size == 2 }
    }

    @Test
    fun redrawRedrawsTheWidgetsItNames() {
        store.save(widget.id, WidgetSettings(dayCount = 4))
        HourByHourWidget.redraw(targetContext, intArrayOf(widget.id))

        waitFor("four days") { headers().size == 4 }
    }

    @Test
    fun resizingRedrawsForTheNewSize() {
        fun resize(widthDp: Int) = manager.updateAppWidgetOptions(
            widget.id,
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 400)
            },
        )

        resize(400)
        waitFor("weekday names") { headers().first().text.any { it.isLetter() } }
        resize(100)
        // Too narrow for the weekday: only the day of the month.
        waitFor("only dates") { headers().first().text.all { it.isDigit() } }
    }

    /** The all-day chip titled [title], if the widget shows it. */
    private fun chip(title: String) =
        view.descendants().filterIsInstance<TextView>().firstOrNull { it.id == R.id.banner_chip && it.text == title }

    @Test
    fun birthdaysShowInTheirOwnColorAndCanBeHidden() {
        TestCalendars().use { calendars ->
            val calendar = calendars.insertCalendar("HourByHour test", 0x039BE5)
            calendars.markAsBirthday(calendars.insertAllDay(calendar, "Test birthday", LocalDate.now()))
            // No calendar chosen: birthdays are chosen apart.
            store.save(widget.id, WidgetSettings(calendarIds = emptySet()))
            WidgetUpdater.update(targetContext, manager, intArrayOf(widget.id))

            waitFor("the birthday") { chip("Test birthday") != null }
            val dark = targetContext.resources.configuration.isNightModeActive
            assertEquals(GoogleColors.forDisplay(GoogleColors.BIRTHDAYS, dark), onMain { chip("Test birthday")!!.backgroundTintList!!.defaultColor })

            store.save(widget.id, WidgetSettings(calendarIds = emptySet(), showBirthdays = false))
            WidgetUpdater.update(targetContext, manager, intArrayOf(widget.id))

            waitFor("the birthday to go") { chip("Test birthday") == null }
        }
    }

    @Test
    fun removingTheWidgetForgetsItsSettings() {
        store.save(widget.id, WidgetSettings(transparency = 50, dayCount = 6))

        widget.close()

        waitFor("the settings to go") { store.load(widget.id) == WidgetSettings() }
    }
}
