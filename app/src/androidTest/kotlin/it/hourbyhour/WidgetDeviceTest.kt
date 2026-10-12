package it.hourbyhour

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import java.time.LocalDate
import org.junit.After
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
    fun refreshRedrawsEveryWidget() {
        store.save(widget.id, WidgetSettings(dayCount = 2))
        targetContext.sendBroadcast(Intent(HourByHourWidget.ACTION_REFRESH).setClass(targetContext, HourByHourWidget::class.java))

        waitFor("two days") { headers().size == 2 }
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

    @Test
    fun removingTheWidgetForgetsItsSettings() {
        store.save(widget.id, WidgetSettings(transparency = 50, dayCount = 6))

        widget.close()

        waitFor("the settings to go") { store.load(widget.id) == WidgetSettings() }
    }
}
