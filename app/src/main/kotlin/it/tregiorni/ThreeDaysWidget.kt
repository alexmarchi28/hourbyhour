package it.tregiorni

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlin.concurrent.thread

/** The home screen widget. It only displays events: it has no click actions. */
class ThreeDaysWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED -> updateAsync { WidgetUpdater.updateAll(context) }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        updateAsync { WidgetUpdater.update(context, manager, widgetIds) }
    }

    /** Called when the widget is resized: redraw the bitmap at the new size. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, widgetId: Int, newOptions: Bundle) {
        updateAsync { WidgetUpdater.update(context, manager, intArrayOf(widgetId)) }
    }

    override fun onDisabled(context: Context) {
        WidgetUpdater.cancelRefresh(context)
        CalendarChangeJob.cancel(context)
    }

    /** Reading the calendar and drawing must not run on the main thread. */
    private fun updateAsync(block: () -> Unit) {
        val pending = goAsync()
        thread {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "it.tregiorni.action.REFRESH"
    }
}
